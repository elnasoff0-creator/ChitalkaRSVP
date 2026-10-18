# ChitalkaRSVP v1.11.45-beta

Clean Android source project, `versionCode 11052`. It has **one** GitHub Actions workflow for a signed release:
`.github/workflows/release.yml`.

## Signed APK with GitHub Actions

Set these existing **Repository secrets** in GitHub: **Settings > Secrets and variables > Actions**:

- `RELEASE_KEYSTORE_B64`: Base64 of the original signed-release Android keystore (single line)
- `RELEASE_KEYSTORE_PASSWORD`: keystore password
- `RELEASE_KEY_PASSWORD`: signing key password
- `RELEASE_KEY_ALIAS`: signing key alias

The workflow installs Java 17 and Android SDK 35, builds `:app:assembleRelease`, signs the APK with `zipalign` and `apksigner`, verifies the signature and uploads the APK and checksum to **Actions > Artifacts**. It runs on a push to `main`, an explicit **Run workflow**, or a version tag. A tag such as `v1.11.45-beta` also creates a GitHub prerelease.

**Use the same keystore** as previous releases to preserve upgrade compatibility. Never commit credentials, keystores or the `dist/` directory. No debug APK workflow, MRT sources, old tests or diagnostic scripts are included.

## Changes in v1.11.45-beta

- Removed the redundant `Читалка` heading from the reader toolbar; kept toolbar actions.
- Settings > Reading now offers independent `Во весь экран` switches for **Обычный**, **Скорочтение**, and **Курсор**.
- **Обычный**: opens directly in immersive full screen when enabled. A single short tap on the reading text shows or hides the toolbars. Swiping and scrolling never toggle them. While word-selection mode is active, tapping selects a word rather than toggling toolbars.
- **Скорочтение**: when full screen is enabled, Play hides toolbar, progress, speed, other controls, text context and Android system bars, retaining just the centered RSVP word and one floating Pause button. Pause restores the original interface and paused text context with selected word.
- **Курсор**: when full screen is enabled, Play keeps the text and active word visible, shows just one floating Pause button and hides other UI. Touch-to-select words continues to work. Pause restores the toolbar and controls.
- The immersive layout changes `View` visibility and layout parameters rather than reparsing EPUB/FB2 or recreating the reader. Existing lazy word-indexed chunk loading and playback progress are preserved.

Only Android device / CI can validate full UI and performance behavior. The source ZIP alone is not a compiled or signed APK.
