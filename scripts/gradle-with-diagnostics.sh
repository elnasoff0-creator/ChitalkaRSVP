#!/usr/bin/env bash
# Keep a full, downloadable Gradle log and repeat the actionable errors at the
# end of the GitHub Actions job. Logs pasted from the end alone are insufficient.
set -uo pipefail
if [ "$#" -lt 1 ]; then
  echo 'Usage: bash scripts/gradle-with-diagnostics.sh <gradle task> [options]' >&2
  exit 2
fi
LOG="${GRADLE_BUILD_LOG:-build-log.txt}"
REPORT="${GRADLE_DIAGNOSTICS:-build-diagnostics.txt}"
mkdir -p "$(dirname "$LOG")" "$(dirname "$REPORT")"
: > "$LOG"
: > "$REPORT"
echo "Gradle command: gradle $* --console=plain"
gradle "$@" --console=plain 2>&1 | tee "$LOG"
code=${PIPESTATUS[0]}
if [ "$code" -eq 0 ]; then
  echo 'Gradle build: SUCCESS'
  printf 'Build succeeded.\n' > "$REPORT"
  exit 0
fi
{
  echo "Gradle build failed (exit code $code)."
  echo 'FIRST relevant error lines, in original log order:'
  # Prefer javac/Kotlin diagnostics and missing dependency messages over long stacks.
  grep -nE -m 35 '(^|[[:space:]])error:|\.java:[0-9]+:|\.kt:[0-9]+:|^e: |cannot find symbol|package .* does not exist|Could not (resolve|find)|Execution failed for task|A problem occurred|What went wrong:|FAILURE: Build failed|Unresolved reference' "$LOG" || true
  echo 'LAST 35 log lines:'
  tail -n 35 "$LOG"
  echo "Full original log: $LOG"
} | tee "$REPORT"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo '### Gradle failure — relevant diagnostics'
    echo '\`\`\`text'
    head -n 75 "$REPORT"
    echo '\`\`\`'
    echo 'Download the **gradle-build-logs** artifact for the complete output.'
  } >> "$GITHUB_STEP_SUMMARY"
fi
exit "$code"
