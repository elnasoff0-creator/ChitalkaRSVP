#!/usr/bin/env python3
"""Compile dashboard with API-shaped JVM doubles; check routes and read-only wiring."""
from pathlib import Path
import tempfile, subprocess, re, shutil
r=Path(__file__).resolve().parents[1]; j=r/'app/src/main/java/ru/reader/rsvp'
stubs={
'android/content/Context.java':'''package android.content; public class Context {public SharedPreferences getSharedPreferences(String k,int m){return new SharedPreferences();}public ContentResolver getContentResolver(){return new ContentResolver();}}''',
'android/content/SharedPreferences.java':'''package android.content; public class SharedPreferences{public String getString(String k,String d){return d;}public int getInt(String k,int d){return d;}public long getLong(String k,long d){return d;}public boolean contains(String k){return false;}}''',
'android/content/ContentResolver.java':'''package android.content; public class ContentResolver {public java.io.InputStream openInputStream(android.net.Uri u)throws java.io.IOException{return null;}}''',
'android/net/Uri.java':'''package android.net;public class Uri{public static Uri parse(String s){return new Uri();}public String getScheme(){return "content";}}''',
'android/util/Base64.java':'''package android.util; public class Base64{public static final int URL_SAFE=8,NO_WRAP=2,NO_PADDING=1;public static String encodeToString(byte[] b,int flags){return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);}}''',
'android/media/MediaMetadataRetriever.java':'''package android.media;public class MediaMetadataRetriever{public void setDataSource(android.content.Context c,android.net.Uri u){}public byte[] getEmbeddedPicture(){return null;}public void release(){}}''',
'android/graphics/Bitmap.java':'''package android.graphics;public class Bitmap{public enum CompressFormat{JPEG}public int getWidth(){return 1;}public int getHeight(){return 1;}public static Bitmap createScaledBitmap(Bitmap b,int w,int h,boolean f){return b;}public boolean compress(CompressFormat f,int q,java.io.OutputStream o){return true;}}''',
'android/graphics/BitmapFactory.java':'''package android.graphics;public class BitmapFactory{public static class Options{public boolean inJustDecodeBounds;public int outWidth,outHeight,inSampleSize;}public static Bitmap decodeByteArray(byte[] b,int o,int l,Options opts){return null;}}''',
'org/json/JSONArray.java':'''package org.json;import java.util.*;public class JSONArray{private final List<Object> a=new ArrayList<>();public JSONArray(){}public JSONArray(String s){}public int length(){return a.size();}public JSONObject optJSONObject(int i){return (JSONObject)a.get(i);}public String optString(int i){return String.valueOf(a.get(i));}public JSONArray put(Object o){a.add(o);return this;}public String toString(){return a.toString();}}''',
'org/json/JSONObject.java':'''package org.json;import java.util.*;public class JSONObject{private final Map<String,Object> m=new LinkedHashMap<>();public JSONObject put(String k,Object v){m.put(k,v);return this;}public String optString(String k,String d){return (String)m.getOrDefault(k,d);}public int optInt(String k,int d){return (Integer)m.getOrDefault(k,d);}public long optLong(String k,long d){return (Long)m.getOrDefault(k,d);}public String toString(){return m.toString();}}''',
'ru/reader/rsvp/Deps.java':'''package ru.reader.rsvp;import android.content.*;import java.util.*;import java.io.*;
class ChteicoStore{static SharedPreferences prefs(Context c){return new SharedPreferences(){public int getInt(String k,int d){return k.equals("finished_books_count")?10:k.equals("best_streak")?30:d;}};}static int comicPages(Context c){return 73;}}
class BookTitleStore{static final String TEXT="text";static String title(Context c,String kind,String id,String name){return name;}}
class AudioLibrary{static class Book{String id="private:uri",name="Audio",coverUri="";ArrayList<String> uris=new ArrayList<>();}static ArrayList<Book> load(Context c){return new ArrayList<>(Arrays.asList(new Book()));}}
class ComicLibrary{static class Comic{String uri="private:comic",title="Comic";int chapterTotal=1,chapter=0,page=0,scroll=0;int progressPercent(){return -1;}String compactProgress(){return "Стр. 1";}}static ArrayList<Comic> list(Context c){return new ArrayList<>(Arrays.asList(new Comic()));}}
class WebTransferDownloadStore{static class Entry{String name="File",state="done";long received=10,total=10,updated=1;int fileCount=1;}static ArrayList<Entry> all(Context c){return new ArrayList<>(Arrays.asList(new Entry()));}}
class AudiobookCoverStore{static String localUri(Context c,String id){return "";}}
class ComicCovers{static android.graphics.Bitmap cover(Context c,ComicLibrary.Comic b)throws Exception{return null;}}
class HtmlFolderLibrary{static byte[] coverBytes(Context c,String uri){return null;}}
class RanobesStorage{static byte[] localCover(Context c,String uri){return null;}}
class CoverLoader{static File target(Context c,String uri){return new File("/nonexistent");}static byte[] epub(byte[] b)throws Exception{return null;}static byte[] fb2(byte[] b)throws Exception{return null;}}
''',
'ru/reader/rsvp/TestDashboard.java':'''package ru.reader.rsvp;import android.content.Context;public class TestDashboard{public static void main(String[] args)throws Exception{Context c=new Context();String result=new String(WebTransferDashboard.snapshot(c),java.nio.charset.StandardCharsets.UTF_8);for(String s:new String[]{"Audio","Comic","comicPages=73","finishedBooks=10","10 прочитанных книг","30 дней подряд","progress=-1","known=false","history="})if(!result.contains(s))throw new AssertionError(s+": "+result);if(result.contains("private:uri")||result.contains("private:comic"))throw new AssertionError("raw path exposed");if(WebTransferDashboard.cover(c,"../../etc/passwd")!=null)throw new AssertionError("arbitrary cover path accepted");System.out.println("PASS: dashboard projection, reward totals, unknown dates, path isolation");}}'''
}
with tempfile.TemporaryDirectory() as tmp:
 t=Path(tmp)
 for name,body in stubs.items():
  p=t/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body)
 subprocess.run(['javac','-encoding','UTF-8','-d',str(t),*[str(t/n) for n in stubs],str(j/'WebTransferDashboard.java')],check=True)
 subprocess.run(['java','-cp',str(t),'ru.reader.rsvp.TestDashboard'],check=True)
server=(j/'WebTransferServer.java').read_text();assert server.index('WebTransferPolicy.sameOrigin')<server.index('if(!authenticated(headers))')<server.index('if("/api/dashboard".equals(path)')<server.index('if(inflight.incrementAndGet()')
settings=(j/'SettingsActivity.java').read_text();assert 'viewport.addView(settingsScroll' in settings and 'BrowserTabs.fixedViewport(this,viewport,list)' in settings and 'fixedViewport(this,settingsScroll' not in settings
activity=(j/'ChteicoActivity.java').read_text();dead=activity.split('if(stage==ChteicoStore.DEAD){',1)[1].split('return;',1)[0];assert 'addStatsCard();addRewardsCard();addGameStatistics();' in dead
web=(r/'app/src/main/assets/webtransfer/index.html').read_text();assert all('id="tab-'+t+'"' in web for t in ['transfer','library','statistics','rewards']);assert 'textContent=text' in web and 'innerHTML' not in web
if shutil.which('node'):subprocess.run(['node','--check','-'],input=re.search(r'<script>(.*?)</script>',web,re.S).group(1),text=True,check=True)
print('PASS: authenticated dashboard routes, fixed parent viewport, visible lifetime statistics, four browser tabs')
