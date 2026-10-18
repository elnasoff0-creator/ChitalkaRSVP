# ChitalkaRSVP v1.11.46-beta

Full Java Android source project (`versionCode 11053`). The repository contains **one** GitHub Actions workflow for a signed Release APK: `.github/workflows/release.yml`.

## What changed in v1.11.46-beta

1. **Start delay for Speed Reading (RSVP) and Cursor**, configured independently in **Settings > Reading > respective mode**. Each mode has a default-off toggle and a 1–10 second slider, set to 2 seconds initially. Pressing Play with an enabled delay starts a *waiting* stage; reading position is not advanced until the countdown completes. Pause, screen changes or leaving the activity cancels the pending countdown.
2. **Transparent outlined countdown**, no numbers or fill. It starts as a complete circular stroke in the selected accent-button color and smoothly disappears clockwise over the delay. Speed Reading: the ring surrounds the word/focus-line group, centered on the word; it is rendered *behind* the word and focus lines. Cursor: it is centered on the reader screen. The floating Pause button stays accessible when immersive reading is enabled.
3. **Fixed Speed Reading fullscreen Y position**. The RSVP word and focus lines are no longer vertically re-centered by expanding a weighted pane on Play. The initial absolute screen-Y coordinate is captured before immersive layout changes, then restored in fullscreen as closely as the available viewport permits. Word and focus lines move as one group.
4. **Fullscreen vertical adjustment for RSVP**: -100% (top with padding), 0% (middle), +100% (bottom with padding), 1% increments. The default **Auto** mode preserves the original non-fullscreen word position. Moving the slider enables manual mode; an Auto button restores automatic placement. The last calculated automatic percentage is shown in settings as a reference.
5. All earlier reading, word selection, button palettes, and UI behaviors from v1.11.45-beta are retained.

## Signed Release APK using GitHub Actions

Configure the following existing **Repository secrets** under **Settings > Secrets and variables > Actions**:

- `RELEASE_KEYSTORE_B64`: base64-encoded original release keystore (one line)
- `RELEASE_KEYSTORE_PASSWORD`: keystore password
- `RELEASE_KEY_PASSWORD`: key password
- `RELEASE_KEY_ALIAS`: key alias

A push to `main` (or manual workflow dispatch) builds `:app:assembleRelease`, signs via `zipalign` + `apksigner`, verifies the signature, and uploads `ChitalkaRSVP-v1.11.46-beta.apk` and `SHA256SUMS.txt` under **Actions > Artifacts**. Tag `v1.11.46-beta` to also create a GitHub prerelease.

Keep using the **same signing key** as previous builds to preserve upgrade compatibility. Do not commit keystores, secrets, generated APKs, or `dist/`.

### GitHub Desktop commit message

**Summary**: `Add startup countdown and fullscreen RSVP positioning in v1.11.46-beta`

**Description**: `Implement independent 1–10s Play delays for RSVP and Cursor, animate a single disappearing clockwise outline ring, preserve original RSVP word/focus-line Y in fullscreen, and add Auto/manual vertical placement (-100% to +100%). Keep signed Release workflow.`

**Validation note**: Source syntax, Android XML, and workflow/ZIP integrity can be checked without Android SDK; the full APK must still be compiled by the signed GitHub Actions Release workflow and tested on an Android device.
