#!/usr/bin/env bash
# Usage: bash RESET-GITHUB-REPO.sh /path/to/local/ChitalkaRSVP [--push]
# Clean replacement of all tracked files while preserving the .git directory.
set -euo pipefail
if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo 'Usage: bash RESET-GITHUB-REPO.sh /path/to/local/ChitalkaRSVP [--push]' >&2
  exit 2
fi
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$1" && pwd)"
[[ "$SRC" != "$REPO" ]] || { echo 'Source and repository must differ.' >&2; exit 2; }
[[ -d "$REPO/.git" ]] || { echo 'Target must be a local Git clone (.git folder missing).' >&2; exit 2; }
[[ "$(git -C "$REPO" rev-parse --show-toplevel)" == "$REPO" ]] || { echo 'Target must be the repository root.' >&2; exit 2; }
[[ -z "$(git -C "$REPO" status --porcelain)" ]] || { echo 'Repository has uncommitted/untracked changes. Back up or commit first.' >&2; exit 2; }
[[ -f "$SRC/app/src/main/AndroidManifest.xml" && -f "$SRC/.github/workflows/release.yml" ]] || { echo 'Invalid Edge-only source tree.' >&2; exit 2; }
[[ ! -d "$SRC/.git" ]] || { echo 'Source must not include a .git folder.' >&2; exit 2; }
if find "$SRC/app/src/main/java" -type f \( -name 'Mrt*.java' -o -name 'Mrt*.kt' \) | grep -q .; then
  echo 'MRT files found in source; refusing to deploy.' >&2; exit 2
fi
BRANCH="$(git -C "$REPO" branch --show-current)"
[[ -n "$BRANCH" ]] || { echo 'Check out a branch, not an old tag.' >&2; exit 2; }
echo "Replacing repository contents on branch $BRANCH; preserving .git"
git -C "$REPO" rm -r -q -- .
# Copy dotfiles, .github workflows, and normal source files in one pass.
tar -C "$SRC" -cf - . | tar -C "$REPO" -xf -
git -C "$REPO" add -A
if find "$REPO/app/src/main/java" -type f \( -name 'Mrt*.java' -o -name 'Mrt*.kt' \) | grep -q .; then
  echo 'MRT sources remain after replacement.' >&2; exit 1
fi
git -C "$REPO" diff --cached --stat
if ! git -C "$REPO" diff --cached --quiet; then
  git -C "$REPO" commit -m 'Replace project with clean Edge-only sources; remove MRT'
fi
if [[ "${2:-}" == '--push' ]]; then
  git -C "$REPO" push origin "HEAD:$BRANCH"
  echo 'Pushed updated Edge-only sources to GitHub.'
else
  echo 'Clean replacement committed locally. Push with: git -C "<path>" push origin HEAD'
fi
