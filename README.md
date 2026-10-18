# ChitalkaRSVP v1.11.44-beta

This is the cleaned Android source project. Build signed **release** APK via the single GitHub Actions workflow:
`.github/workflows/release.yml`.

## Required GitHub Actions repository secrets

At **Settings > Secrets and variables > Actions**, define these four **Repository secrets**:

- `RELEASE_KEYSTORE_B64` — Base64 of the **existing** Android release keystore `.jks` (single-line string).
- `RELEASE_KEYSTORE_PASSWORD` — password of that keystore.
- `RELEASE_KEY_PASSWORD` — password of its signing key.
- `RELEASE_KEY_ALIAS` — signing key alias.

The workflow builds `:app:assembleRelease`, uses `zipalign` and `apksigner` with Android SDK Build Tools 35.0.0, verifies the APK signature, and uploads a signed APK plus checksum under **Actions > Artifacts**.

It runs on push to `main` or **Run workflow**. A push of a version tag (`v1.11.44-beta`) also publishes a GitHub Release (marked pre-release for beta tags). Ordinary pushes to `main` do not publish GitHub Releases.

**Keep the same keystore** if you intend to install updates over prior signed releases. Never commit `.jks`, passwords, Base64 keystore data, or files from the `dist/` directory to git.

No debug APK workflow or archived test suites are included.

## v1.11.44-beta reading and UI changes

- RSVP text context remains visible while paused, is hidden during Play and uses the ordinary selected-word text color and scale (without background).
- Two independent button colors: `button_color` for normal controls and `button_accent_color` for Play, selected mode and primary actions. Both support the default option and custom RGB.
- Updated outlined pointing-hand Cursor icon and globe-with-refresh-arrows update icon.
- Retains lazy paragraph chunks, per-word position, and existing Android/Edge TTS.
