# ChitalkaRSVP v1.11.30-beta — Edge TTS only

This project includes system Android TTS, the native Edge TTS client and its
independent persistent download queue, background playback and MP3 cache.
No MRT Activity, Java classes or WebView-based TTS engine is present.

## Important for an EXISTING GitHub repository

**Do not simply upload this ZIP over the old project and assume Java files are
removed.** GitHub retains previously committed `MrtDownloadService.java` and
`MrtPlaybackService.java` until their deletions are committed to Git.

The v1.11.30 release and debug workflows include an INLINE cleanup step which
removes old `Mrt*.java`/`Mrt*.kt` files from their temporary runner checkout
BEFORE compilation. The job log must contain:

`v1.11.30 - remove obsolete MRT Java before compilation`

If your job still logs `Run gradle assembleRelease --stacktrace` and has no
cleanup step, you are running the OLD workflow! Commit
`.github/workflows/release.yml` from THIS archive to the SAME branch/ref used
by Actions, or delete the obsolete Java sources in the GitHub web interface.

### Permanent fix (recommended, one-time)

In a local Git clone of the GitHub repository, from its root:

```bash
find app/src/main/java -type f \( -name 'Mrt*.java' -o -name 'Mrt*.kt' \) -print -delete
git add -A app/src/main/java
git commit -m "Remove abandoned MRT Android sources"
git push
```

Run these commands on a branch that contains the Edge-only updated source tree.
If Git reports additional modified files, inspect `git status` before commit.
Then commit the new `.github/workflows/release.yml` and
`.github/workflows/build-debug-apk.yml` from this archive as well.

A workflow_dispatch build always uses the workflow revision for the selected
branch; a tag build uses the workflow/source revision at its tag. Do not reuse
an old tag when trying to build updated code.

### Build

JDK 17; Android SDK API 35; Gradle 8.7.

Run: `gradle assembleRelease --stacktrace`

Android SDK and signing credentials are not part of this source ZIP. The
release workflow signs the output using repository GitHub Actions secrets.
No on-device functional test is claimed.
