# ChitalkaRSVP 1.11.30-beta — stale MRT files in GitHub CI

The previous 14 javac errors all came from old `MrtDownloadService.java` and
`MrtPlaybackService.java` that remain tracked in the target GitHub repository.
Nothing in the current Edge-only source requires `androidx.webkit` or
`AudioModeCoordinator.STOP_MRT`.

- Put the cleanup inline in BOTH workflows immediately after checkout.
- Add a separate preflight assertion before Java compilation.
- Mark build step v1.11.30 so stale workflow runs are identifiable.
- Preserve independent Edge queue and cached MP3 files.
- Add repository-specific instructions to commit deletions of obsolete Java.

If the user still sees `Run gradle assembleRelease --stacktrace` with no
cleanup step, GitHub is running the old workflow, not this revision.
