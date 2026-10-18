#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
cat > "$T/gradle" <<'SH'
#!/usr/bin/env bash
echo '> Task :app:compileReleaseJavaWithJavac FAILED'
echo '/home/runner/work/app/src/main/java/ru/reader/rsvp/SyntheticFixture.java:53: error: cannot find symbol'
echo ' symbol: class Example'
echo 'FAILURE: Build failed with an exception.'
exit 1
SH
chmod +x "$T/gradle"
set +e
PATH="$T:$PATH" GITHUB_STEP_SUMMARY="$T/isolated-summary.md" GRADLE_BUILD_LOG="$T/log.txt" GRADLE_DIAGNOSTICS="$T/diagnostics.txt" bash scripts/gradle-with-diagnostics.sh assembleRelease > "$T/stdout.txt" 2>&1
STATUS=$?
set -e
test "$STATUS" -eq 1
grep -q 'SyntheticFixture.java:53: error: cannot find symbol' "$T/log.txt"
grep -q 'SyntheticFixture.java:53: error: cannot find symbol' "$T/diagnostics.txt"
tail -n 12 "$T/stdout.txt" | grep -q 'Full original log:'
# The deliberately failed dummy compile MUST NOT contaminate the GitHub job summary.
test -f "$T/isolated-summary.md"
grep -q 'SyntheticFixture.java:53' "$T/isolated-summary.md"
echo 'Gradle failure diagnostics (isolated synthetic test): PASS' 
