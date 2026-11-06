#!/usr/bin/env python3
"""Compile v1.11.133 MangaLib and TTS selection sources against lightweight Android/OkHttp stand-ins.
This is a type and behavior smoke test, NOT an Android SDK/Gradle compilation.
"""
import pathlib, tempfile, textwrap, subprocess, sys
root=pathlib.Path(__file__).resolve().parents[1]
source=root/'app/src/main/java/ru/reader/rsvp'

def write(base,name,contents):
    p=base/'src'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(textwrap.dedent(contents),encoding='utf8');return p
with tempfile.TemporaryDirectory(prefix='chitalka133_') as t:
    t=pathlib.Path(t)
    stubs={
    'android/net/Uri.java':r'''
        package android.net;
        import java.util.*;
        public class Uri{
            final java.net.URI uri;
            private Uri(String s){uri=java.net.URI.create(s);}
            public static Uri parse(String s){return new Uri(s);}
            public String getScheme(){return uri.getScheme();}
            public String getHost(){return uri.getHost();}
            public List<String> getPathSegments(){List<String> out=new ArrayList<>();for(String a:uri.getPath().split("/"))if(!a.isEmpty())out.add(a);return out;}
        }
    ''',
    'android/graphics/BitmapFactory.java':r'''
        package android.graphics;
        public final class BitmapFactory {
            public static class Options {public boolean inJustDecodeBounds;public int outWidth,outHeight;}
            public static Object decodeByteArray(byte[] b,int from,int n,Options o){
                if(n>=3&&(b[0]&255)==255&&(b[1]&255)==216){o.outWidth=100;o.outHeight=100;}
                return null;
            }
        }
    ''',
    'android/content/Context.java':r'''
        package android.content;
        public class Context {
            public static final int MODE_PRIVATE=0;
            public final SharedPreferences prefs=new SharedPreferences();
            public SharedPreferences getSharedPreferences(String s,int mode){return prefs;}
            public static final String NOTIFICATION_SERVICE="notification";
            public Object getSystemService(String name){return new android.app.NotificationManager();}
        }
    ''',
    'android/content/SharedPreferences.java':r'''
        package android.content;
        import java.util.*;
        public class SharedPreferences {
            public final Map<String,Object> map=new HashMap<>();
            public boolean getBoolean(String k,boolean d){return (boolean)map.getOrDefault(k,d);}
            public String getString(String k,String d){return (String)map.getOrDefault(k,d);}
            public Editor edit(){return new Editor();}
            public class Editor{
                public Editor putBoolean(String k,boolean v){map.put(k,v);return this;}
                public Editor putString(String k,String v){map.put(k,v);return this;}
                public void apply(){}
            }
        }
    ''',
    'android/os/Build.java':r'''
        package android.os;public class Build{public static class VERSION{public static int SDK_INT=35;}}
    ''',
    'android/app/Notification.java':r'''
        package android.app;public class Notification{}
    ''',
    'android/app/NotificationChannel.java':r'''
        package android.app;public class NotificationChannel{public NotificationChannel(String s,String n,int p){}}
    ''',
    'android/app/NotificationManager.java':r'''
        package android.app;public class NotificationManager{public static final int IMPORTANCE_LOW=1;public void createNotificationChannel(NotificationChannel c){}}
    ''',
    'android/content/pm/ServiceInfo.java':r'''
        package android.content.pm;public class ServiceInfo{public static final int FOREGROUND_SERVICE_TYPE_DATA_SYNC=1;}
    ''',
    'androidx/annotation/NonNull.java':r'''
        package androidx.annotation;public @interface NonNull{}
    ''',
    'androidx/core/app/NotificationCompat.java':r'''
        package androidx.core.app;
        public final class NotificationCompat{
            public static class Builder{
                public Builder(android.content.Context c,String id){}
                public Builder setSmallIcon(int id){return this;}
                public Builder setContentTitle(String text){return this;}
                public Builder setContentText(String text){return this;}
                public Builder setProgress(int max,int current,boolean indeterminate){return this;}
                public Builder setOngoing(boolean v){return this;}
                public android.app.Notification build(){return new android.app.Notification();}
            }
        }
    ''',
    'androidx/work/ForegroundInfo.java':r'''
        package androidx.work;public class ForegroundInfo{
            public ForegroundInfo(int n,android.app.Notification note){}
            public ForegroundInfo(int n,android.app.Notification note,int foregroundType){}
        }
    ''',
    'androidx/work/WorkerParameters.java':r'''
        package androidx.work;public class WorkerParameters{}
    ''',
    'androidx/work/Data.java':r'''
        package androidx.work;
        public class Data{
            public String getString(String k){return "";}
            public static class Builder{
                public Builder putInt(String name,int n){return this;}
                public Data build(){return new Data();}
            }
        }
    ''',
    'androidx/work/Worker.java':r'''
        package androidx.work;
        import android.content.Context;
        public abstract class Worker{
            private final Context ctx;
            public Worker(Context c,WorkerParameters p){ctx=c;}
            public Context getApplicationContext(){return ctx;}
            public Data getInputData(){return new Data();}
            public int getRunAttemptCount(){return 0;}
            public boolean isStopped(){return false;}
            public Object setProgressAsync(Data d){return null;}
            public Object setForegroundAsync(ForegroundInfo f){return null;}
            public abstract Result doWork();
            public static class Result{
                public static Result success(){return new Result();}
                public static Result failure(){return new Result();}
                public static Result retry(){return new Result();}
            }
        }
    ''',
    'ru/reader/rsvp/MangaLibStorage.java':r'''
        package ru.reader.rsvp;
        import android.content.Context;
        import java.io.File;
        public class MangaLibStorage{
            public static class Job{
                public String status,error,series,number,volume,chapter,slug,file;
                public long branch;public int done,total;
                public String id(){return "";}
            }
            public static Job find(Context c,String id){return new Job();}
            public static void put(Context c,Job j){}
            public static File chapterFile(Context c,Job j){return new File("/tmp/test.cbz");}
            public static void registerComic(Context c,Job j){}
        }
    ''',
    'ru/reader/rsvp/R.java':r'''
        package ru.reader.rsvp;public final class R{public static final class drawable{public static final int ic_downloads=1;}}
    ''',
    'org/json/JSONArray.java':r'''
        package org.json;
        public class JSONArray{public int length(){return 0;}public JSONObject optJSONObject(int i){return null;}public String optString(int i,String d){return d;}public int optInt(int i,int d){return d;}}
    ''',
    'org/json/JSONObject.java':r'''
        package org.json;
        public class JSONObject{
            public JSONObject(){}public JSONObject(String s){}
            public JSONObject optJSONObject(String k){return null;}public JSONArray optJSONArray(String k){return null;}public Object opt(String k){return null;}
            public String optString(String k,String def){return def;}public String optString(String k){return "";}public long optLong(String k,long d){return d;}public long optLong(String k){return 0;}
            public boolean optBoolean(String k,boolean d){return d;}
        }
    ''',
    'okhttp3/HttpUrl.java':r'''
        package okhttp3;
        public class HttpUrl{
            final java.net.URI uri;
            private HttpUrl(String text){this.uri=java.net.URI.create(text);}
            public static HttpUrl parse(String text){try{java.net.URI u=java.net.URI.create(text);if(u.getHost()==null)return null;return new HttpUrl(text);}catch(Exception ex){return null;}}
            public String scheme(){return uri.getScheme();}public String host(){return uri.getHost();}
            public int port(){return uri.getPort()<0?443:uri.getPort();}
            public String encodedPath(){String p=uri.getRawPath();return p==null||p.isEmpty()?"/":p;}
            public String encodedQuery(){return uri.getRawQuery();}
            public Builder newBuilder(){return new Builder(this);}
            public String toString(){return uri.toString();}
            public static class Builder{
                final HttpUrl base;
                Builder(HttpUrl b){base=b;}
                public Builder addQueryParameter(String name,String val){return this;}
                public HttpUrl build(){return base;}
            }
        }
    ''',
    'okhttp3/Request.java':r'''
        package okhttp3;
        import java.util.*;
        public class Request{
            final HttpUrl url;final Map<String,String> headers;
            private Request(HttpUrl u,Map<String,String> h){url=u;headers=h;}
            public HttpUrl url(){return url;}
            public static class Builder{
                HttpUrl url;Map<String,String> headers=new HashMap<>();
                public Builder url(HttpUrl u){url=u;return this;}
                public Builder url(String u){url=HttpUrl.parse(u);return this;}
                public Builder header(String key,String value){headers.put(key,value);return this;}
                public Request build(){return new Request(url,headers);}
            }
        }
    ''',
    'okhttp3/ResponseBody.java':r'''
        package okhttp3;
        import java.io.*;
        public class ResponseBody{
            private final byte[] bytes;
            public ResponseBody(byte[] b){bytes=b;}
            public long contentLength(){return bytes.length;}
            public InputStream byteStream(){return new ByteArrayInputStream(bytes);}
            public String string(){return new String(bytes,java.nio.charset.StandardCharsets.UTF_8);}
        }
    ''',
    'okhttp3/Response.java':r'''
        package okhttp3;
        public class Response implements AutoCloseable {
            private final Request request;private final int code;private final byte[] bytes;private final String mime;
            Response(Request r,int code,byte[] bytes,String mime){request=r;this.code=code;this.bytes=bytes;this.mime=mime;}
            public Request request(){return request;}public int code(){return code;}public boolean isSuccessful(){return code>=200&&code<300;}
            public String header(String key,String d){return mime;}
            public ResponseBody body(){return new ResponseBody(bytes);}
            public void close(){}
        }
    ''',
    'okhttp3/OkHttpClient.java':r'''
        package okhttp3;
        import java.util.*;
        import java.util.concurrent.TimeUnit;
        public class OkHttpClient{
            public static final Map<String,Integer> status=new HashMap<>();
            public static final Map<String,byte[]> response=new HashMap<>();
            public static final List<String> calls=new ArrayList<>();
            public static String accept="";
            public static class Builder{
                public Builder connectTimeout(int n,TimeUnit u){return this;}
                public Builder readTimeout(int n,TimeUnit u){return this;}
                public Builder followRedirects(boolean b){return this;}
                public OkHttpClient build(){return new OkHttpClient();}
            }
            public Call newCall(Request request){return new Call(request);}
            public static class Call{
                Request req;public Call(Request r){req=r;}
                public Response execute(){String url=req.url().toString();calls.add(url);
                    accept=req.headers.getOrDefault("Accept","");
                    return new Response(req,status.getOrDefault(url,200),response.getOrDefault(url,new byte[]{1,2,3}),"text/html");
                }
            }
        }
    ''',
    'ru/reader/rsvp/TestV133.java':r'''
        package ru.reader.rsvp;
        import java.lang.reflect.*;
        import java.util.*;
        import android.content.Context;
        import okhttp3.OkHttpClient;
        public class TestV133{
            static void ok(boolean v,String reason){if(!v)throw new AssertionError(reason);}
            public static void main(String[] args) throws Exception{
                Context ctx=new Context();
                ctx.prefs.map.put("tts_resume_previous_sentence",false);
                ok(!TtsResumeChoice.previous(ctx),"migration old off to current");
                TtsResumeChoice.set(ctx,TtsResumeChoice.PREVIOUS);
                ok(TtsResumeChoice.previous(ctx),"save previous");
                TtsResumeChoice.set(ctx,TtsResumeChoice.CURRENT);
                ok(!TtsResumeChoice.previous(ctx),"save current");
                SentenceIndex ix=SentenceIndex.fromText("Первая фраза. Вторая фраза! Третья фраза.");
                ok(ix.size()==3,"real sentence boundaries");
                TtsSentenceHistory h=new TtsSentenceHistory();h.bind(ix);h.capturePause(3);
                ok(h.previousOnResume(3)==ix.startAt(0),"resume previous");
                ok(h.currentOnResume(3)==ix.startAt(1),"resume current");
                Method m=MangaLibApi.class.getDeclaredMethod("imageUrls",String.class,List.class);m.setAccessible(true);
                List<String> servers=Arrays.asList("https://a.mixlib.me","https://b.imglib.info");
                @SuppressWarnings("unchecked") List<String> urls=(List<String>)m.invoke(null,"/images/chapter/1.jpg",servers);
                ok(urls.size()==2&&urls.get(0).contains("a.mixlib.me")&&urls.get(1).contains("b.imglib.info"),"mirror URL list");
                @SuppressWarnings("unchecked") List<String> blocked=(List<String>)m.invoke(null,"https://evil.com/track",servers);
                ok(blocked.isEmpty(),"offsite URLs rejected");
                byte[] jpg=new byte[200];jpg[0]=(byte)255;jpg[1]=(byte)216;jpg[2]=(byte)255;
                String first=urls.get(0), second=urls.get(1);
                OkHttpClient.calls.clear();OkHttpClient.response.put(first,"<html>bot error</html>".getBytes());
                OkHttpClient.response.put(second,jpg);
                byte[] got=new MangaLibApi().image(new MangaLibApi.Page(urls,4));
                ok(got.length==200,"HTML response retries on mirror with JPEG");
                ok(OkHttpClient.calls.size()==2,"one bounded fallback");
                ok(!OkHttpClient.accept.toLowerCase(Locale.ROOT).contains("avif"),"AVIF not advertised");
                OkHttpClient.calls.clear();OkHttpClient.status.put(first,403);
                try{new MangaLibApi().image(new MangaLibApi.Page(urls,5));throw new AssertionError("403 must abort");}
                catch(MangaLibApi.AccessException expected){}
                ok(OkHttpClient.calls.size()==1,"403 never bypassed via another CDN");
                OkHttpClient.status.remove(first);
                OkHttpClient.calls.clear();OkHttpClient.status.put(first,429);
                try{new MangaLibApi().image(new MangaLibApi.Page(urls,6));throw new AssertionError("429 must defer");}
                catch(MangaLibApi.RateLimitException expected){}
                ok(OkHttpClient.calls.size()==1,"429 not bypassed; worker can retry later");
                OkHttpClient.status.remove(first);
                OkHttpClient.calls.clear();OkHttpClient.response.put(first,"<html>invalid image</html>".getBytes());
                OkHttpClient.response.put(second,"{\"error\":true}".getBytes());
                try{new MangaLibApi().image(new MangaLibApi.Page(urls,7));throw new AssertionError("both invalid should reject");}
                catch(java.io.IOException expected){ok(expected.getMessage().contains("Страница 7"),"page-specific error");}
                System.out.println("PASS TTS migration, current/previous sentences, mirror fallback, 403, MIME, image validation");
            }
        }
    '''
    }
    paths=[write(t,name,body) for name,body in stubs.items()]
    paths += [source/x for x in ['MangaLibApi.java','MangaLibDownloadWorker.java','TtsResumeChoice.java','TtsSentenceHistory.java','SentenceIndex.java','SentenceChunks.java']]
    dest=t/'classes';dest.mkdir()
    p=subprocess.run(['javac','-encoding','UTF-8','-source','17','-target','17','-d',str(dest),*map(str,paths)],capture_output=True,text=True)
    if p.returncode:
        print('TYPECHECK FAILED:\n'+p.stderr);sys.exit(1)
    p=subprocess.run(['java','-cp',str(dest),'ru.reader.rsvp.TestV133'],capture_output=True,text=True)
    if p.returncode:
        print('SMOKE TEST FAILED:\n'+p.stdout+'\n'+p.stderr);sys.exit(1)
    print(p.stdout.strip())
