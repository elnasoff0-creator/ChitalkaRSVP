package ru.reader.rsvp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Private bounded application log; never use this API for SSML, book text, URLs,
 * cookies, WebSocket headers, authorization tokens, or full exception messages.
 * Android's READ_LOGS permission is intentionally NOT used.
 */
final class ReaderDiagnosticLog {
    private static final String CURRENT="prochitalka_log_0.txt";
    private static final String PREVIOUS="prochitalka_log_1.txt";
    private static final int MAX_BYTES=512*1024;
    private static final int MAX_VIEW_CHARS=420_000;
    private static volatile File folder;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"ReaderDiagnosticLog");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;
    });
    private static final Handler MAIN=new Handler(Looper.getMainLooper());

    private ReaderDiagnosticLog(){}
    static void init(Context context){
        if(folder==null){synchronized(ReaderDiagnosticLog.class){
            if(folder==null)folder=new File(context.getApplicationContext().getFilesDir(),"diagnostics");
        }}
        event("APP","started");
    }
    static void event(String component,String description){
        File target=folder;
        if(target==null)return;
        // Inputs MUST be pre-sanitized constant event names and numeric diagnostics.
        // A defensive filter prevents accidental credential or raw URL disclosure.
        String c=clean(component,40),d=clean(description,240);
        IO.execute(()->{
            try{
                if(!target.exists()&&!target.mkdirs())return;
                File now=new File(target,CURRENT);
                String line=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(new Date())
                    +" | "+c+" | "+d+"\n";
                if(now.exists() && now.length()+line.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES){
                    File previous=new File(target,PREVIOUS);
                    if(previous.exists())previous.delete();
                    if(!now.renameTo(previous))now.delete();
                }
                try(BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(now,true),StandardCharsets.UTF_8))){writer.write(line);}
            }catch(Exception e){Log.w("ReaderDiagnosticLog","Log I/O failed ("+e.getClass().getSimpleName()+")");}
        });
    }
    /** Do not export private request headers, raw URLs, or user text in the log. */
    private static String clean(String value,int max){
        if(value==null)return "";
        String s=value.replace('\r',' ').replace('\n',' ');
        // Even accidental URLs or authorization values are removed from log output.
        s=s.replaceAll("(?i)https?://\\S+|wss?://\\S+","[URL]");
        s=s.replaceAll("(?i)authorization\\s*[:=]\\s*Bearer\\s+\\S+","[REDACTED]");
        s=s.replaceAll("(?i)(authorization|cookie|sec-ms-gec|subscription-key|connectionid)\\s*[:=]\\s*\\S+","[REDACTED]");
        return s.length()>max?s.substring(0,max):s;
    }
    static void snapshot(Consumer<String> callback){
        IO.execute(()->{
            String content;
            try{content=readAll(MAX_VIEW_CHARS);}catch(Exception e){content="Ошибка чтения журнала: "+e.getClass().getSimpleName();}
            String result=content;
            MAIN.post(()->callback.accept(result));
        });
    }
    static void clear(Runnable complete){
        IO.execute(()->{
            if(folder!=null){new File(folder,CURRENT).delete();new File(folder,PREVIOUS).delete();
                new File(folder,"prochitalka_log_export.txt").delete();}
            MAIN.post(complete);
        });
    }
    static void prepareExport(Consumer<File> callback){
        IO.execute(()->{
            File output=null;
            try{
                if(folder!=null && (folder.exists()||folder.mkdirs())){
                    output=new File(folder,"prochitalka_log_export.txt");
                    try(BufferedWriter w=new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(output,false),StandardCharsets.UTF_8))){
                        w.write("ПРОЧиталка · диагностический журнал (локальные события)\n");
                        w.write("Без исходного текста книг и учётных данных.\n\n");
                        w.write(readAll(MAX_BYTES*2+4096));
                    }
                }
            }catch(Exception e){output=null;Log.w("ReaderDiagnosticLog","Export I/O failed");}
            File file=output;
            MAIN.post(()->callback.accept(file));
        });
    }
    private static String readAll(int limit) throws IOException {
        File dir=folder;
        if(dir==null)return "Журнал ещё не создан.";
        StringBuilder sb=new StringBuilder();
        for(String name:new String[]{PREVIOUS,CURRENT}){
            File f=new File(dir,name);
            if(!f.isFile())continue;
            try(BufferedReader r=new BufferedReader(new InputStreamReader(
                new FileInputStream(f),StandardCharsets.UTF_8))){
                String line;
                while((line=r.readLine())!=null){sb.append(line).append('\n');}
            }
        }
        if(sb.length()==0)return "Событий пока нет. Запустите Edge TTS и обновите журнал.";
        if(sb.length()>limit)return "… показаны последние события (полный журнал доступен через «Поделиться») …\n\n"+
            sb.substring(sb.length()-limit);
        return sb.toString();
    }
}
