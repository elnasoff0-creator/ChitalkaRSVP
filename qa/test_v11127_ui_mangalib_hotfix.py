#!/usr/bin/env python3
"""Regression contracts for 127 beta UI/MangaLib. No live API or Android build."""
from pathlib import Path
import re, subprocess, tempfile, textwrap, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
get=lambda n:(src/(n+'.java')).read_text(encoding='utf-8')
comic=get('ComicReaderActivity');main=get('MainActivity');api=get('MangaLibApi');manga=get('MangaLibSearchActivity');links=get('LinkDownloadActivity')
checks={
 'no comic toolbar gradient':'toolbarFade' not in comic and 'toolbarStack.setBackgroundColor(Color.TRANSPARENT)' in comic,
 'one right-side metadata chip':'titleChip.setBackground(rounded(neutral,13))' in comic and 'toolbar.addView(titleChip,titleLp)' in comic,
 'both title and chapter within toolbar':'titleChip.addView(comicHeading' in comic and 'titleChip.addView(chapterInfo' in comic,
 'two comic-mode icons kept':'R.drawable.ic_comic_mode_pages' in comic and 'R.drawable.ic_comic_mode_feed' in comic,
 'tap works in either comic mode':'if(comic!=null&&!gestureMoved&&!touchPinching&&' in comic and 'setChromeVisible(!chromeVisible)' in comic,
 'feed scroll does not show or hide panels':'setOnScrollChangeListener' not in comic and 'feedScrollAccum' not in comic,
 'horizontal swipe and zoom preserved':'scroll-snap-type:x mandatory' in comic and 'setBuiltInZoomControls(true)' in comic,
 'floating bottom panel remains':'main.addView(footerOverlay,bottomParams)' in comic and 'chaptersAction.setBackground(rounded(neutral,13))' in comic,
 'no reading resume header':'TextView section=text(HomeSectionOrder.title(prefs,"continue")' not in main,
 'no audio resume header':'TextView recentHeading=text(HomeSectionOrder.title(prefs,"audioContinue")' not in main,
 'no comics resume header':'TextView sectionName=text(HomeSectionOrder.title(prefs,"comicContinue")' not in main,
 'resume action remains':main.count('"Продолжить"')>=1 and '"▶ Продолжить"' in main,
 'rounded square comic cover':'frame.setClipToOutline(true)' in main and 'comicCover(recent,88,88)' in main,
 'existing library progress remains':'compactProgress()' in main,
 'MangaLib referer and Site ID':'header("Referer","https://mangalib.me/")' in api and 'header("Site-Id","1")' in api,
 'MangaLib query params remain':'addQueryParameter("site_id[]","1")' in api,
 'MangaLib direct links':'slugFromLink' in api and 'queryFromLink' in api and 'titleBySlug(slug)' in manga,
 'direct-link download rerouted':'MangaLibApi.isTitleLink(input)' in links and 'new Intent(this,MangaLibSearchActivity.class)' in links,
 'MangaLib image content signature validated':'Недопустимые данные изображения' in api and 'if(!allowedImage' in api,
 'MangaLib public access handling':'AccessException' in api and '429' in api,
 'MangaLib pages and serialization unchanged':'MangaLibDownloadWorker' in (src/'MangaLibDownloadWorker.java').read_text() and 'ComicInfo.xml' in (src/'MangaLibDownloadWorker.java').read_text(),
 'version 128 retained':"versionCode 12800; versionName '1.11.128-beta'" in (root/'app/build.gradle').read_text(),
}
for label,passed in checks.items():
 if not passed:raise AssertionError('FAIL: '+label)
for file in (root/'app/src/main').rglob('*.xml'):ET.parse(file)
print('PASS: '+str(len(checks))+' source contracts and Android XML parsing')

# A targeted real JDK compilation for MangaLibApi, using minimal stubs for Android/OkHttp/JSON.
# Detects bad method references/regex escaping and exercises link handling without Android runtime.
stubs={
 'android/net/Uri.java':'''package android.net;import java.util.*;public class Uri{
 java.net.URI u;Uri(String s){u=java.net.URI.create(s);}public static Uri parse(String s){return new Uri(s);}
 public String getScheme(){return u.getScheme();}public String getHost(){return u.getHost();}
 public List<String> getPathSegments(){ArrayList<String> a=new ArrayList<>();String p=u.getPath();if(p!=null)for(String x:p.split("/"))if(!x.isEmpty())a.add(x);return a;}}
 ''',
 'org/json/JSONArray.java':'''package org.json;public class JSONArray{public int length(){return 0;}public JSONObject optJSONObject(int i){return null;}public String optString(int i,String d){return d;}}''',
 'org/json/JSONObject.java':'''package org.json;public class JSONObject{
 public JSONObject(){}public JSONObject(String s){}public JSONObject optJSONObject(String k){return null;}
 public JSONArray optJSONArray(String k){return null;}public String optString(String k,String d){return d;}public String optString(String k){return "";}
 public boolean optBoolean(String k,boolean d){return d;}public long optLong(String k,long d){return d;}
 public long optLong(String k){return 0;}}''',
 'okhttp3/HttpUrl.java':'''package okhttp3;public class HttpUrl{
 public static HttpUrl parse(String s){return new HttpUrl();}public Builder newBuilder(){return new Builder();}
 public String scheme(){return "https";}public String host(){return "api.cdnlibs.org";}
 public String encodedPath(){return "/";}public String toString(){return "https://api.cdnlibs.org";}
 public static class Builder{public Builder addQueryParameter(String k,String v){return this;}public HttpUrl build(){return new HttpUrl();}}}''',
 'okhttp3/Request.java':'''package okhttp3;public class Request{public HttpUrl url(){return new HttpUrl();}
 public static class Builder{public Builder url(HttpUrl u){return this;}public Builder header(String k,String v){return this;}public Request build(){return new Request();}}}''',
 'okhttp3/ResponseBody.java':'''package okhttp3;public class ResponseBody{public long contentLength(){return 100;}public String string(){return "{}";}public byte[] bytes(){return new byte[128];}}''',
 'okhttp3/Response.java':'''package okhttp3;public class Response implements java.lang.AutoCloseable{public Request request(){return new Request();}
 public int code(){return 200;}public boolean isSuccessful(){return true;}public ResponseBody body(){return new ResponseBody();}
 public String header(String key,String fallback){return fallback;}public void close(){}}''',
 'okhttp3/Call.java':'''package okhttp3;public class Call{public Response execute()throws java.io.IOException{return new Response();}}''',
 'okhttp3/OkHttpClient.java':'''package okhttp3;import java.util.concurrent.TimeUnit;public class OkHttpClient{
 public Call newCall(Request r){return new Call();}public static class Builder{
 public Builder connectTimeout(long i,TimeUnit t){return this;}public Builder readTimeout(long i,TimeUnit t){return this;}
 public Builder followRedirects(boolean b){return this;}public OkHttpClient build(){return new OkHttpClient();}}}''',
 'ru/reader/rsvp/TestMangaLinkHandling.java':'''package ru.reader.rsvp;public class TestMangaLinkHandling{
 static void check(boolean b,String label){if(!b)throw new AssertionError(label);}
 public static void main(String[] ignored){
 String slug="7965--chainsaw-man";
 check(slug.equals(MangaLibApi.slugFromLink("https://mangalib.me/ru/manga/7965--chainsaw-man")),"new link");
 check(slug.equals(MangaLibApi.slugFromLink("https://www.mangalib.org/ru/manga/7965--chainsaw-man?from=share")),"mirror query");
 check(slug.equals(MangaLibApi.slugFromLink("mangalib.me/ru/manga/7965--chainsaw-man")),"scheme-less link");
 check("chainsaw man".equals(MangaLibApi.queryFromLink("https://mangalib.me/ru/manga/chainsaw-man")),"old link fallback");
 check(MangaLibApi.isTitleLink("https://mangalib.me/ru/manga/chainsaw-man"),"route MangaLib");
 check(!MangaLibApi.isTitleLink("https://example.com/manga/chainsaw-man"),"exclude others");
 check(!MangaLibApi.isTitleLink("https://mangalib.me/login"),"exclude login");
 check(MangaLibApi.slugFromLink("https://evil.example/?x=7965--chainsaw-man").isEmpty(),"exclude other sites");
 System.out.println("PASS: 8 real JVM MangaLib link checks");}}
 ''',
}
with tempfile.TemporaryDirectory() as td:
 d=Path(td)
 for name,content in stubs.items():
  file=d/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(textwrap.dedent(content),encoding='utf-8')
 sources=list(d.rglob('*.java'))+[src/'MangaLibApi.java']
 subprocess.run(['javac','-encoding','UTF-8','-d',str(d/'out')]+[str(p) for p in sources],check=True)
 subprocess.run(['java','-cp',str(d/'out'),'ru.reader.rsvp.TestMangaLinkHandling'],check=True)
print('PASS: isolated real Javac + Java runtime for MangaLibApi')
