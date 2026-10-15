#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/SearchPagingTest.java" <<'JAVA'
package ru.reader.rsvp;
public final class SearchPagingTest {
  public static void main(String[] args){
    String site="2 цикла Поднятие уровня в одиночку Найдено 22 книги Читает Zodik";
    if(KvuheSearchPaging.countBooks(site)!=22||KvuheSearchPaging.countSeries(site)!=2)throw new AssertionError("Search counts");
    if(KvuheSearchPaging.countBooks("Книги 489")!=489)throw new AssertionError("Alternative count");
    if(KvuheSearchPaging.countSeries("1 цикл")!=1)throw new AssertionError("Singular series count");
    if(KvuheSearchPaging.countBooks("только книга")!=-1)throw new AssertionError("Unknown counts");
    String base="https://knigavuhe.org/search/?q=%D0%A2%D0%B5%D0%B1%D0%B5";
    String second=KvuheSearchPaging.following(base);
    if(!second.equals(base+"&page=2"))throw new AssertionError("Page 2 query");
    if(!KvuheSearchPaging.following("https://knigavuhe.org/search/?page=1&q=x").equals("https://knigavuhe.org/search/?q=x&page=2"))throw new AssertionError("Page-first query");
    if(!KvuheSearchPaging.following(second).endsWith("&page=3"))throw new AssertionError("Page 3 query");
    System.out.println("PASS: actual search count forms and URL pagination");
  }
}
JAVA
javac -d "$TMP" app/src/main/java/ru/reader/rsvp/KvuheSearchPaging.java "$TMP/SearchPagingTest.java"
java -cp "$TMP" ru.reader.rsvp.SearchPagingTest
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
java=Path('app/src/main/java/ru/reader/rsvp')
p=(java/'KvuheSearchParser.java').read_text()
s=(java/'KvuheSearchActivity.java').read_text()
book=(java/'KvuheBookActivity.java').read_text()
series=(java/'KvuheSeriesActivity.java').read_text()
for part in ('div.bookkitem','/series/','/book/','data-original','data-src','data-page','KvuheSearchPaging.following','fetchSeries'):
 assert part in p,part
for part in ('Книги','Циклы','Книг в списке','KvuheBookActivity.class','KvuheSeriesActivity.class','CoverImageLoader.show','Показать ещё'):
 assert part in s,part
for part in ('Скачать выбранные главы','Скачать всю аудиокнигу','MP3 не найден','preview.chapters','CoverImageLoader.show','confirm(true)'):
 assert part in book,part
assert 'KvuheSearchParser.fetchSeries' in series
assert 'new Intent(KvuheSeriesActivity.this,KvuheBookActivity.class)' in series
assert 'KvuheParser.fetch(s)' in (java/'UrlImportActivity.java').read_text()
assert 'BookImportManager.putNew' in (java/'UrlImportActivity.java').read_text()
assert 'KvuheDownloader.download' in (java/'DownloadWorker.java').read_text()
ns='{http://schemas.android.com/apk/res/android}'
manifest=ET.parse('app/src/main/AndroidManifest.xml')
for activity in ('.KvuheBookActivity','.KvuheSeriesActivity'):
 assert any(e.get(ns+'name')==activity for e in manifest.findall('.//activity'))
print('PASS: search/series/preview actions and original download queue wiring')
PY
