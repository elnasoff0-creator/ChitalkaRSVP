# PROChitalka v1.11.117-beta CLEAN FIX4

**Baseline:** v1.11.117-beta CLEAN FIX3. **Unchanged:** versionName `1.11.117-beta`, versionCode `11700`.

## Changes

1. **Pet motion:** Restored `ChteicoPetView.java` animation from v1.11.116-beta. Removed strong ValueAnimator deformation and its scaleX/scaleY pulses and vertical translation. Kept the approved small up/down synchronized wing-frame-scale animation. Accelerated by approximately 30%: wing-frame interval changed from 820 ms to 630 ms; motion duration changed from 740 ms to 568 ms. Applies to both mini-pet and full Chteico because both use the shared view. All stage-dependent mini sizing and emotion overlays remain intact.
2. **Chteico controls:** Added filled rounded-square (44 dp, 12 dp radius) backgrounds for menu and settings controls in full `ChteicoActivity`. These honor `ReaderButtonColors.neutral()` and adapt icon contrast to the button surface; existing stat/text buttons remain unchanged.
3. **Reward grid:** Kept up to 3 equal-width award cells per row, including an incomplete final row. Previously 1 or 2 existing award cells would grow and occupy 100% of the row. After the last award, transparent spacer views reserve empty slots; they are invisible, not fake award cards. With 2 rewards, tiles occupy 2/3 of the row.

## Verification

- 39/39 repo test/check scripts passed (30 shell/source checks and 9 Python source checks), executed in two batches because the full orchestration process exceeded its execution window.
- Java parser: 111 Java files syntactically valid; Android type resolution/build not tested.
- XML parser: 103 XML resources syntactically valid.
- FIX4 regression checks: restored motion, interval, 2 rounded toolbar controls, invariant 3-column grid, and unchanged version.
- Full ZIP integrity and Git PATCH application verified, source tree identical to full release after applying patch.

**Limitations:** Android SDK, Gradle and device were unavailable. No APK or on-device animation test was produced. UI layout/rendering behavior must be checked in Android.
