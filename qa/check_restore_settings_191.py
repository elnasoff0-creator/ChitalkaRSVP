from pathlib import Path
r=Path(__file__).resolve().parents[1]
java=lambda name:(r/'app/src/main/java/ru/reader/rsvp'/name).read_text(encoding='utf-8')
settings=java('SettingsActivity.java')
reader=java('ReaderActivity.java')
comic=java('ComicReaderActivity.java')
lib=java('ComicLibrary.java')
manga=java('MangaLibStorage.java')
config=(r/'app/build.gradle').read_text()
assert "versionCode 19300; versionName '1.11.193-beta'" in config
# On the General tab typography must not appear; on Reading tab the whole block is first.
general=settings.split('if(currentTab==4){drawChteicoSettings();return;}',1)[1].split('private void drawTypographySettings',1)[0]
assert 'preview=new TextView(this)' not in general
reading=settings.split('void drawRsvpSettings(){',1)[1].split('void drawHighlightSettings()',1)[0]
assert reading.index('drawTypographySettings();') < reading.index('readingSectionDivider();') < reading.index('header("Режимы чтения");')
block=settings.split('private void drawTypographySettings(){',1)[1].split('private static String lineSpacingLabel',1)[0]
assert block.index('preview=new TextView(this)')<block.index('header("Шрифт")')<block.index('Добавить шрифт')<block.index('header("Размер шрифта")')<block.index('header("Межстрочный интервал")')
assert 'setLineSpacing(dp(Math.max(8,size/3)),1.12f*lineSpacingPercent/100f)' in reader
assert 'oldLineSpacing!=lineSpacingPercent' in reader
# A fresh ReaderActivity must not restore the old manual viewport on top of the speech index.
assert 'viewportWord=-1;followSuspended=false;' in reader
assert 'positionReaderAtWord(index,initialEpoch,0,true,false)' in reader
assert 'RESTORE_RETRY' in reader and 'RESTORE_APPLIED' in reader
assert 'RETURN_FAST_POSITION' in reader
# Manga restoration needs stable chapter ID, page ratio, and protection against late JS restores.
assert 'chapterId' in lib and 'pageFraction' in lib
assert 'ch.stableId="mangalib:"+j.id()' in manga
assert 'ComicRestorePolicy.resolveChapter' in comic
assert 'onPositionChanged(int token,int page,int offset,double fraction)' in comic
assert 'restore(offset,index,ratio)' in comic
assert 'restoreWhenReady(offset,index,ratio,token)' in comic
assert '__comicRestoreCancelled' in comic
assert '1800L' not in comic
assert 'COMIC_RESTORE' in comic
print('PASS source integration: reading tab, position restoration, comic cancellation and logging')
