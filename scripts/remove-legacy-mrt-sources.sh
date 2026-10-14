#!/usr/bin/env bash
set -euo pipefail
# CI safety net for upgrading repositories by copying new files over old ones.
# A clean repository should not contain any of these files in the first place.
ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
JAVA="$ROOT/app/src/main/java"
if [ -d "$JAVA" ]; then
  while IFS= read -r -d '' file; do
    echo "Removing retired MRT source: ${file#"$ROOT"/}"
    rm -f -- "$file"
  done < <(find "$JAVA" -type f \( -name 'Mrt*.java' -o -name 'Mrt*.kt' \) -print0)
fi
ASSET="$ROOT/app/src/main/assets/mrt_online_capture.js"
if [ -f "$ASSET" ]; then
  echo "Removing retired MRT browser asset: ${ASSET#"$ROOT"/}"
  rm -f -- "$ASSET"
fi
# Fail loudly if old service references somehow survived under another name.
if [ -d "$JAVA" ]; then
  if grep -R -n -E 'AudioModeCoordinator\.STOP_MRT|androidx\.webkit\.' "$JAVA" --include='*.java' --include='*.kt'; then
    echo 'ERROR: Retired MRT code still referenced in active Android sources.' >&2
    exit 1
  fi
fi
if grep -q -E 'Mrt(Download|Playback|Online|FileExplorer|Native)' "$ROOT/app/src/main/AndroidManifest.xml"; then
  echo 'ERROR: AndroidManifest.xml still registers removed MRT components.' >&2
  exit 1
fi
echo 'Legacy MRT sources: clean'
