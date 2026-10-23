# Rerun

Old-school cable TV, built from your own Plex library. For Fire TV and Android TV.

Turn it on and something's already playing. Flip channels and you land in the middle of whatever's on. There's a 90s cable guide, a CRT tube look, static between channels, and "technical difficulties" when your server naps.

- **Network channels:** Disney Channel, Disney XD, Nickelodeon, Cartoon Network, Boomerang, Toonami, PBS Kids, SYFY, USA, HBO and more. They're built from what you own.
- **Streaming originals go to their network's cable channel,** by owner and audience:
  - Disney+ kids shows go to Disney Channel.
  - Marvel and live-action Star Wars go to **Touchstone**.
  - Paramount+ kids shows go to Nickelodeon.
  - Max originals go to HBO.
  - Hulu originals go to FX.
  - Netflix, Prime Video and Apple TV+ originals are spread by genre.
- **Theme channels:** Horror, Sci-Fi, Comedy, Action, Family, 80s/90s Movies, Saturday Morning.
- **Custom channels:** pick genres, shows, collections, decades and ratings right on the TV.
- **Empty channels stay hidden** until you have at least 4 hours of content for them.
- **Locked 48-hour schedule.** The guide never lies, and library changes never yank you out of a movie.
- **Read-only.** Nothing you watch is reported back to Plex. Your Continue Watching stays clean.

## Install on a Fire TV

Requirements:
- JDK 17
- Android SDK (platform 35)
- `adb` on PATH

1. On the TV, unlock Developer Options: Settings → My Fire TV → About → click the device name 7 times.
2. Turn on **ADB Debugging** in Developer Options.
3. Note the TV's IP address, under About → Network.
4. On your PC:

```powershell
.\scripts\install.ps1 -TvIp 192.168.1.50
```

5. On the TV, the app shows a 4-character code. Enter it at **plex.tv/link**, pick your server, and wait for the first sync.

To remove it: `.\scripts\restore.ps1 -TvIp 192.168.1.50`

## Remote

| Button | Watching | Guide |
|---|---|---|
| ▲ / ▼ | Channel up / down | Move between channels |
| ◀ | Last channel | Earlier |
| ▶ / OK | Info banner | Later / tune in |
| Hold OK 2s | Cycle CRT look (Off / Subtle / Full) | — |
| ☰ | Guide | Edit the focused channel |
| Back | Guide | Back to TV. Press Back again right away to exit. |

Channels and Settings sit at the top of the guide: press ▲ from the first row.

## Commercials (optional)

`tools/commercials/fetch_archive_item.py` downloads every video in an Internet Archive item and queues each one for a local upscaler. Upscaled clips go into a Plex library named **Commercials**. Rerun will play them between shows starting in Phase 3. Until then, gaps show an "Up Next" card.

Vintage commercials are generally still under copyright. Keep downloaded copies for personal viewing, and never commit media to this repository.

## Development

- `core/` is pure Kotlin: catalog, routing, scheduler, tune math, and Plex parsing. It has unit tests: `./gradlew :core:test`.
- `app/` is the Android shell: Compose UI, Media3 player, and Room.
- `tools/ktest/` runs the core tests with only a Kotlin compiler jar, for machines without Maven access.

Design spec and plans live in `docs/superpowers/`.

## Credits

VT323 font © The VT323 Project Authors, SIL Open Font License 1.1 (`licenses/VT323-OFL.txt`). Not affiliated with Plex or any network named here; channel names describe where shows aired.
