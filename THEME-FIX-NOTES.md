# PROChitalka - download UI theme correction

Base source: v1.11.167-beta CLEAN. This is a targeted source patch, NOT the full v1.11.168-beta feature release. The Gradle versionCode / versionName are intentionally unchanged.

## Findings
The main DownloadActivity, DownloadArchiveActivity and several secondary download screens had hardcoded sepia backgrounds and dark ink, ignoring the configured reader `bg`, `fg`, `button_color`, and chosen accent. Horizontal progress bars also inherited platform default tint. UnifiedDownloadManagerActivity already consumed most of the shared palette, but its progress indicators did not.

## Changes
- UiColorScheme: shared `foreground`, `muted`, `paletteSignature`, `success`, `error`, and `tintProgress` helpers.
- DownloadActivity: configurable background, panels, labels, icons, row surfaces and accent; covers and controls use tinted surfaces; progress bars and semantic status colors adapt to dark backgrounds; theme changes refresh the screen when it resumes.
- DownloadArchiveActivity: configurable palette and hint styling; search text survives activity recreation on theme changes.
- UnifiedDownloadManagerActivity: shared color lookup and themed overall/per-file progress bars, refreshes on theme changes.
- EdgeFullBookActivity and EdgeChapterDownloadsActivity: theme backgrounds/text/cards, themed progress bars and semantic status text.
- MangaLibChapterDownloadsActivity: theme backgrounds/text/cards, themed progress and error text.
- LinkDownloadActivity: theme cards, buttons, labels, hints, text-entry fields, selection boxes, errors; live palette redraw preserves entered URLs, subfolder and selected file checkboxes.
- LinkDownloadDetailsActivity: theme page, labels, buttons and file cards.

## QA
- Source-wide Java parsing: 188 Java files, 0 syntax errors (JDK compiler AST parser).
- Not compiled against the Android SDK; there is no SDK/Gradle environment in this container. APK execution and visual comparison across themes remain required.
- Native Android PopupMenu styling retains the existing app-wide static popup surface; custom accent/surface synchronization of native popups is an outstanding visual QA item for the approved v1.11.168-beta UI work. This patch does not rewrite menus.
- No changes to download queues, periodic scanning, caching, unified manager behavior, persistence, or version identifiers in this focused theme patch. Those features remain specified for v1.11.168-beta.

## Permanent UI rule for subsequent features
Any new view, widget, icon, progress bar, dialog and selection state must use the central UI palette (`UiColorScheme`, `ReaderButtonColors`) and match current app geometry and typography. Do not introduce absolute sepia/white/black UI surface constants. Theme switching must not silently discard the user's current input or progress.
