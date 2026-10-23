#!/usr/bin/env bash
# Compile and run the :core unit tests without Gradle (for environments that can't reach Maven).
# Usage: tools/ktest/run.sh <kotlin-compiler-jar> <kotlin-stdlib-jar> [extra-jars...] [-- name-filter]
# On a normal dev machine use `./gradlew :core:test` instead.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
compiler="$1"; stdlib="$2"; shift 2
extra=(); filter=""
while [ $# -gt 0 ]; do
  if [ "$1" = "--" ]; then filter="${2:-}"; break; fi
  extra+=("$1"); shift
done
cp_deps="$stdlib"
for j in "${extra[@]:-}"; do [ -n "$j" ] && cp_deps="$cp_deps:$j"; done
out="${KTEST_OUT:-$(mktemp -d)}"
rm -rf "$out" && mkdir -p "$out"
srcs=$(find "$repo/core/src/main/kotlin" "$repo/core/src/test/kotlin" -name '*.kt' 2>/dev/null || true)
java -cp "$compiler" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -Xallow-kotlin-package -jvm-target 17 \
  -cp "$cp_deps" -d "$out" \
  "$here/KotlinTestShim.kt" "$here/Runner.kt" $srcs 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS' || true
[ -f "$out/ktest/RunnerKt.class" ] || { echo "COMPILE FAILED"; exit 2; }
java -cp "$out:$cp_deps:$repo/core/src/test/resources" ktest.RunnerKt "$out" $filter 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS'
exit "${PIPESTATUS[0]}"
