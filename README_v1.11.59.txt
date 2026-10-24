PROChitalka / Чтейцо v1.11.59-beta — CLEAN source package

Changes from v1.11.58-beta:
- Replaced nine VectorDrawable XML icons supplied by the user.
- Developer mode: toggle "Показать все награды (тест)", OFF by default,
  previews all six rewards without changing actual book/audio/streak counters.
- Pet frame height 306dp -> 260dp. Pet sprite stays 248x247dp.
- Centered opaque speech bubble above the eyes, overlapping the head.
- TapWaves adapt radial ray length to the compact 260dp frame.
- Regenerated all nine DOWN sprites FROM their corresponding UP sprites,
  with only wing geometry shifted downward/outward more strongly.
  Crest and face paths do not animate separately.
- Chick UP/DOWN feet extended slightly at the toe tips within 512x512 viewport.
- Existing stage mechanics and whole-sprite spring timing are unchanged.

Note: includes Android Studio Gradle source; APK is not included.
To build: Android Studio, JDK 17, compileSdk 35, Gradle plugin 8.6.1.
Existing signed-release keys are not included.
