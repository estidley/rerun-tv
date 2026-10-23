"""Download every original video from an Internet Archive item and queue each one for upscaling.

Standard library only. Re-running is safe: finished downloads are skipped (size check) and a
file is never queued twice.

Example (Windows):
    python fetch_archive_item.py --item 2000VariousCommercials ^
        --dest "D:\\Rerun\\Commercials\\source\\2000VariousCommercials" ^
        --out  "D:\\Rerun\\Commercials\\720p\\2000" ^
        --queue "C:\\path\\to\\LocalUpscaler\\queue" --target 720p --model fast

The queue folder format is the one LocalUpscaler's folder_queue.py reads: one JSON job per file
with the same fields as its POST /api/jobs, plus "output_dir".

These are personal-viewing copies. Keep them out of any public repository.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

VIDEO_EXT = {"mp4", "mpg", "mpeg", "avi", "mkv", "mov", "wmv", "flv", "m4v", "webm", "ts", "vob", "3gp"}
UA = {"User-Agent": "rerun-commercial-fetch/1.0"}


def safe_name(name: str) -> str:
    name = name.replace("/", "_").replace("\\", "_")
    name = re.sub(r'[<>:"|?*\x00-\x1f]', "_", name).strip(" .")
    return name[:180] or "file"


def originals(item: str) -> list[dict]:
    req = urllib.request.Request(f"https://archive.org/metadata/{urllib.parse.quote(item)}", headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        meta = json.load(r)
    files = meta.get("files", [])
    return [f for f in files
            if f.get("source") == "original" and f["name"].rsplit(".", 1)[-1].lower() in VIDEO_EXT]


def download(item: str, f: dict, dest: Path) -> Path:
    target = dest / safe_name(f["name"])
    size = int(f.get("size") or 0)
    if target.exists() and (size == 0 or target.stat().st_size == size):
        return target
    url = f"https://archive.org/download/{urllib.parse.quote(item)}/{urllib.parse.quote(f['name'])}"
    part = target.with_name(target.name + ".part")
    for attempt in range(1, 4):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=120) as r, part.open("wb") as out:
                while chunk := r.read(1 << 20):
                    out.write(chunk)
            if size and part.stat().st_size != size:
                raise IOError(f"size mismatch {part.stat().st_size} != {size}")
            part.replace(target)
            return target
        except Exception as exc:  # retry network hiccups
            print(f"  retry {attempt}/3 {f['name']}: {exc}", flush=True)
            time.sleep(3 * attempt)
    raise IOError(f"could not download {f['name']}")


def already_queued(queue: Path, stem: str) -> bool:
    if (queue / f"{stem}.json").exists():
        return True
    if any(queue.glob(f"{glob_escape(stem)}.*.running")):
        return True
    return (queue / "done" / f"{stem}.json").exists() or (queue / "failed" / f"{stem}.json").exists()


def glob_escape(s: str) -> str:
    return re.sub(r"([\[\]*?])", r"[\1]", s)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--item", required=True)
    ap.add_argument("--dest", required=True, type=Path, help="where originals are saved")
    ap.add_argument("--out", required=True, type=Path, help="where the upscaler writes results")
    ap.add_argument("--queue", required=True, type=Path, help="LocalUpscaler queue folder")
    ap.add_argument("--target", default="720p")
    ap.add_argument("--model", default="fast")
    ap.add_argument("--resize", default="fit", choices=["fit", "pad", "crop"])
    args = ap.parse_args()

    args.dest.mkdir(parents=True, exist_ok=True)
    args.out.mkdir(parents=True, exist_ok=True)
    args.queue.mkdir(parents=True, exist_ok=True)

    files = originals(args.item)
    total = sum(int(f.get("size") or 0) for f in files)
    print(f"{args.item}: {len(files)} videos, {total / 1e9:.2f} GB", flush=True)
    queued = skipped = failed = 0
    for i, f in enumerate(files, 1):
        try:
            path = download(args.item, f, args.dest)
        except IOError as exc:
            print(f"[{i}/{len(files)}] FAILED {exc}", flush=True)
            failed += 1
            continue
        stem = safe_name(Path(f["name"]).stem)
        if already_queued(args.queue, stem):
            skipped += 1
            print(f"[{i}/{len(files)}] have  {path.name}", flush=True)
            continue
        job = {
            "path": str(path), "model": args.model, "target": args.target, "resize_mode": args.resize,
            "output_fps": "source", "interpolation": "none", "seconds": 0, "output_dir": str(args.out),
        }
        tmp = args.queue / f"_{stem}.tmp"
        tmp.write_text(json.dumps(job, indent=2), encoding="utf-8")
        tmp.replace(args.queue / f"{stem}.json")
        queued += 1
        print(f"[{i}/{len(files)}] queued {path.name}", flush=True)
    print(f"done: {queued} queued, {skipped} already queued, {failed} failed", flush=True)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
