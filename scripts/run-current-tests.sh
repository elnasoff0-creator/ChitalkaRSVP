#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Explicit list for 1.11.42. Older GitHub repositories can still contain
# obsolete MRT test scripts after ZIP files were copied over an earlier tree.
# Those retired scripts must not be run against the Edge-only code base.
CURRENT_TESTS=(
  test_audio_resume_seek_11139.sh
  test_direct_links_11137.sh
  test_downloads_11135.sh
  test_ebook_direct_links_11137.sh
  test_edge_audio_format.sh
  test_edge_full_book_11138.sh
  test_edge_isolation.sh
  test_edge_planner.sh
  test_edge_storage.sh
  test_edge_token.sh
  test_gradle_diagnostics.sh
  test_kvuhe_search_11138.sh
  test_offline_covers_11136.sh
  test_playback_audio_focus.sh
  test_reader_chunks_11142.sh
  test_reader_modes_11140.sh
  test_remove_legacy_mrt.sh
  test_ui_release_11135.sh
)

echo "Running ${#CURRENT_TESTS[@]} maintained Edge-only tests"
for file in "${CURRENT_TESTS[@]}"; do
  if [[ ! -f "tests/$file" ]]; then
    echo "ERROR: maintained test tests/$file is missing from the repository" >&2
    exit 1
  fi
  echo "--- tests/$file"
  bash "tests/$file"
done

# Warn about stale tests in old overlay-style repositories rather than
# accidentally running them; they should be deleted from git separately.
for file in tests/*.sh tests/*.cjs; do
  [[ -f "$file" ]] || continue
  basename="${file##*/}"
  found=false
  for known in "${CURRENT_TESTS[@]}"; do
    if [[ "$basename" == "$known" ]]; then found=true; break; fi
  done
  if [[ "$found" == false ]]; then
    echo "WARNING: legacy/unmanaged test skipped: $file (remove it from Git if obsolete)"
  fi
done

echo "PASS: all maintained v1.11.42-beta tests passed"
