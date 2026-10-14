# ChitalkaRSVP 1.11.29-beta — fix GitHub Actions release build

The failure in `:app:compileReleaseJavaWithJavac` was caused by old
`MrtDownloadService.java` and `MrtPlaybackService.java` still present in the
GitHub repository despite being absent from the v1.11.28 source archive.
Those classes referenced removed `STOP_MRT` and `androidx.webkit` symbols.

This version adds `scripts/remove-legacy-mrt-sources.sh` to both GitHub Actions
workflows *before tests and compilation*. It removes only obsolete `Mrt*.java`
and `Mrt*.kt` files and the old browser capture script; leaves Edge TTS intact.
Includes an overlay-upgrade regression test, and fixes the debug workflow's
`.cjs` test glob when no matching test files exist.

**Important:** CI cleans its temporary checkout, not your GitHub repository.
When applying this update to an existing repository, also **delete the old
tracked MRT files in Git** (e.g. `git rm -f app/src/main/java/ru/reader/rsvp/Mrt*.java`)
and commit the deletions. Do not just overwrite old source files.

There is no `androidx.webkit` dependency required. On-device testing and a
successful Android SDK build are still needed for final APK verification.
