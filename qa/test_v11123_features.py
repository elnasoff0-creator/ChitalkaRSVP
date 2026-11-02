#!/usr/bin/env python3
"""1.11.123 source contract and numerical jump calibration. No Android SDK runtime."""
from pathlib import Path
import xml.etree.ElementTree as ET
from math import sqrt
root=Path(__file__).resolve().parents[1]
p=root/'app/src/main/java/ru/reader/rsvp'
def source(name):return (p/name).read_text()
main=source('MainActivity.java'); nav=source('UniversalDrawer.java')
menu=source('DrawerMenuStore.java'); editor=source('DrawerEditorActivity.java')
home=source('HomeSectionOrder.java'); pet=source('ChteicoActivity.java')
runner=source('ChteicoRunnerActivity.java'); emotions=source('ChteicoEmotions.java')
help=source('ActivityInfo.java')
assert 'versionCode 12400' in (root/'app/build.gradle').read_text()
assert "versionName '1.11.124-beta'" in (root/'app/build.gradle').read_text()
assert '.DrawerEditorActivity' in (root/'app/src/main/AndroidManifest.xml').read_text()
assert 'for(String id:DrawerMenuStore.order(a))' in nav and 'DrawerMenuStore.visible(a,id)' in nav
assert 'DrawerEditorActivity.class' in nav
assert 'case "comic":comic(a)' in nav and 'case "audio":audiobook(a)' in nav
assert 'mainImport(a,"comic")' in nav and 'mainImport(a,"audio")' in nav and 'mainImport(a,"book")' in nav
assert 'ComicReaderActivity.class' in nav and 'AudioPlayerActivity.class' in nav and 'ReaderActivity.class' in nav
assert 'drawer_order_v123' in menu and 'drawer_name_v123_' in menu and 'drawer_hidden_v123' in menu
assert 'setOnDragListener' in editor and 'Переименовать пункт' in editor
assert 'String[] DEFAULT={"home","reading","audio","comic","downloads","pet","settings","update","help"}' in menu
assert 'new String[]{"Книгу","Аудиокнигу","Комикс"}' in main
assert 'TextView addComic=buttonish' not in main
assert 'consumeDrawerAction' in main and 'showDrawerImport' in main
assert 'enterHomeEditing()' in main and 'chooseHiddenHomeBlock' in main
assert 'new int[]{readingStart,libraryStart,comicStart,audioStart,audioLibraryStart[0],petStart' in main
assert 'home_sections_hidden_v123' in home and 'home_section_order_v123' in home
assert 'startDragAndDrop' in home and 'if(editing)' in home
assert 'long press' in home.lower() and 'visible(prefs,id,false)' in home
assert 'if(horizontal){if(pet!=null)pet.run();}' in emotions and 'vertical=!horizontal' in emotions
assert 'return !vertical;' in emotions and 'requestDisallowInterceptTouchEvent(true)' in emotions
assert 'R.drawable.ic_chteico_game' in pet and 'addGameStatistics();' in pet
assert '"Игровой опыт"' in pet and '"Забегов"' in pet and '"Рекорд"' in pet
assert 'LinearLayout gameCard=card()' not in pet
assert 'Чтейцо — Прыжки через книги' in help
icon=root/'app/src/main/res/drawable/ic_chteico_game.xml'
assert icon.exists();ET.parse(icon)
assert '0xfffff9f2' in runner and 'dp(260)' in runner
assert 'stage==1?72:stage==2?88:108' in runner
assert 'bookHeights' in runner and 'dp(90)' in runner
assert 'dp(1365)*dt' in runner and 'dp(1254)*active' in runner
assert 'holdSeconds<.240f' in runner
for hold in (0,0.08,0.16,0.24):
    dt=.001;vy=sqrt(2*1365*30);y=0;peak=0;t=0
    for _ in range(1300):
        lift=1254*min(dt,max(0,hold-t))
        vy+=lift-1365*dt
        y=max(0,min(90,y+vy*dt));peak=max(peak,y)
        t+=dt
        if y<=0 and t>.02:break
    expected=30+60*hold/.24
    assert abs(peak-expected)<2.0,(hold,peak,expected)
print('PASS v1.11.123: drawer routes/editor, intelligent import, home editing, pet swipe, runner UI, jump 30-90dp')
