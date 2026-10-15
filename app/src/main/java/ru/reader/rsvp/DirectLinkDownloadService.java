package ru.reader.rsvp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.DocumentsContract;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;

/** Independent foreground downloader: resume into .part, validate and publish MP3/FB2/EPUB via SAF. */
public final class DirectLinkDownloadService extends Service {
    static final String START="ru.reader.rsvp.LINK_START", PAUSE="ru.reader.rsvp.LINK_PAUSE",CANCEL="ru.reader.rsvp.LINK_CANCEL",EXTRA_ID="batch_id",CHANNEL="direct_link_downloads";
    private static final int NOTIFICATION_ID=4118;
    private volatile boolean halt,cancel;
    private Thread worker;
    private String activeId="";
    @Override public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(CHANNEL,"Загрузка файлов по ссылке",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(ch);}}
    private Notification note(String text,int count,int total){Notification.Builder n=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return n.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Скачать по ссылке").setContentText(text).setOnlyAlertOnce(true).setOngoing(true).setProgress(Math.max(1,total),Math.min(count,Math.max(1,total)),false).build();}
    @Override public int onStartCommand(Intent intent,int flags,int id){if(intent==null)return START_NOT_STICKY;String action=intent.getAction(),batchId=intent.getStringExtra(EXTRA_ID);if(batchId==null)return START_NOT_STICKY;
        if(PAUSE.equals(action)||CANCEL.equals(action)){
            LinkDownloadStore.Batch b=LinkDownloadStore.get(this,batchId);if(b!=null){b.status=CANCEL.equals(action)?"cancelled":"paused";LinkDownloadStore.put(this,b);}
            if(batchId.equals(activeId)){halt=true;cancel=CANCEL.equals(action);}
            return START_NOT_STICKY;
        }
        if(START.equals(action)){
            LinkDownloadStore.Batch batch=LinkDownloadStore.get(this,batchId);if(batch==null)return START_NOT_STICKY;
            batch.status="queued";batch.error="";LinkDownloadStore.put(this,batch);
            if(worker==null||!worker.isAlive()){
                halt=false;cancel=false;startForeground(NOTIFICATION_ID,note("Подготовка файлов…",0,1));
                worker=new Thread(this::drain,"direct-link-queue");worker.start();
            }
            return START_STICKY;
        }return START_NOT_STICKY;
    }
    private File part(String id,int index){File root=getExternalFilesDir(null);if(root==null)root=getFilesDir();File dir=new File(root,"direct-link-parts");if(!dir.exists())dir.mkdirs();return new File(dir,id+"_"+index+".part");}
    private DocumentFile destination(LinkDownloadStore.Batch b)throws Exception{
        Uri tree=Uri.parse(b.treeUri);DocumentFile folder=DocumentFile.fromTreeUri(this,tree);
        if(folder==null||!folder.isDirectory()||!folder.canWrite())throw new Exception("Нет доступа к выбранной папке. Выберите её заново.");
        if(!b.subfolder.isEmpty()){
            DocumentFile child=folder.findFile(b.subfolder);
            if(child==null)child=folder.createDirectory(b.subfolder);
            if(child==null||!child.isDirectory()||!child.canWrite())throw new Exception("Не удалось создать папку "+b.subfolder);
            folder=child;
        }
        return folder;
    }
    private static String unique(DocumentFile folder,String name,String format){
        String ending="."+format;String stem=name.toLowerCase(java.util.Locale.ROOT).endsWith(ending)?name.substring(0,name.length()-ending.length()):name;
        String result=name;for(int n=2;folder.findFile(result)!=null&&n<10000;n++)result=stem+" ("+n+")"+ending;
        if(folder.findFile(result)!=null)throw new IllegalArgumentException("Слишком много совпадающих имён");
        return result;
    }
    private void saveCompleted(LinkDownloadStore.Batch b,LinkDownloadStore.Entry e,File temp)throws Exception{
        DocumentFile folder=destination(b);
        String filename=unique(folder,LinkFileTypes.safeFilename(e.name,e.format),e.format);
        DocumentFile document=folder.createFile(LinkFileTypes.mime(e.format),filename);
        if(document==null)throw new Exception("Нельзя создать файл в выбранной папке");
        try(InputStream in=new FileInputStream(temp);OutputStream out=getContentResolver().openOutputStream(document.getUri(),"w")){
            if(out==null)throw new Exception("Нет разрешения на запись");byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))>=0){if(halt)throw new InterruptedException("Загрузка остановлена");out.write(buffer,0,n);}out.flush();
        }catch(Exception ex){document.delete();throw ex;}
        e.savedUri=document.getUri().toString();e.name=filename;e.status="completed";e.bytes=temp.length();temp.delete();
        if(!"mp3".equals(e.format))LinkDownloadStore.registerTextBook(this,e);
    }
    private void download(LinkDownloadStore.Batch b,int index)throws Exception{
        LinkDownloadStore.Entry entry=b.files.get(index);
        if("completed".equals(entry.status)&&!entry.savedUri.isEmpty()){
            try{if(DocumentFile.fromSingleUri(this,Uri.parse(entry.savedUri)).exists())return;}catch(Exception ignored){}
        }
        // Refresh expiring public Yandex share links before each request.
        String address=DirectLinkResolver.refresh(entry);
        File temp=part(b.id,index);long position=temp.exists()?temp.length():0;
        HttpURLConnection conn=DirectLinkResolver.connect(address,position>0?"bytes="+position+"-":null);
        try {
            int code=conn.getResponseCode();
            String contentRange=conn.getHeaderField("Content-Range");
            boolean exactRange=contentRange!=null&&contentRange.startsWith("bytes "+position+"-");
            if(position>0&&(code!=206||!exactRange)){
                temp.delete();position=0;conn.disconnect();conn=DirectLinkResolver.connect(address,null);code=conn.getResponseCode();
            }
            if(code!=200&&code!=206)throw new Exception("Сервер не предоставил файл ("+code+")");
            String mime=conn.getContentType()==null?"":conn.getContentType().toLowerCase(java.util.Locale.ROOT);
            if(!LinkFileTypes.mimeAllowed(entry.format,mime))throw new Exception("Неверный формат ответа: "+mime);
            long contentLength=conn.getContentLengthLong();entry.size=entry.size>0?entry.size:contentLength>0?contentLength+position:-1;
            entry.status="running";entry.error="";entry.bytes=position;LinkDownloadStore.put(this,b);
            try(InputStream in=conn.getInputStream();FileOutputStream out=new FileOutputStream(temp,position>0)){
                byte[] bytes=new byte[32768];int len;long last=android.os.SystemClock.elapsedRealtime();
                while((len=in.read(bytes))!=-1){if(halt||Thread.currentThread().isInterrupted())throw new InterruptedException("Остановлено");out.write(bytes,0,len);entry.bytes+=len;
                    long now=android.os.SystemClock.elapsedRealtime();if(now-last>=650){last=now;LinkDownloadStore.put(this,b);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID,note(entry.name+" · "+(entry.bytes/1024)+" КБ",index,b.files.size()));}
                }
                out.flush();
            }
            if(entry.size>0&&temp.length()<entry.size)throw new Exception("Получен неполный файл");
            LinkFileTypes.verify(temp,entry.format);
            saveCompleted(b,entry,temp);LinkDownloadStore.put(this,b);
        }finally{conn.disconnect();}
    }
    private void drain(){try {
        for(;;){LinkDownloadStore.Batch next=null;
            for(LinkDownloadStore.Batch b:LinkDownloadStore.load(this))if("queued".equals(b.status)){next=b;break;}
            if(next==null)break;
            activeId=next.id;halt=false;cancel=false;next.status="running";LinkDownloadStore.put(this,next);
            try{
                destination(next);
                for(int i=0;i<next.files.size();i++){
                    if(halt)break;LinkDownloadStore.Entry f=next.files.get(i);
                    if("completed".equals(f.status)&&!f.savedUri.isEmpty())continue;
                    try{download(next,i);}catch(InterruptedException ex){break;}catch(Exception ex){f.status="error";f.error=ex.getMessage()==null?"Ошибка скачивания":ex.getMessage();next.error=f.error;LinkDownloadStore.put(this,next);}
                }
                if(halt)next.status=cancel?"cancelled":"paused";
                else next.status=next.completed()==next.files.size()?"completed":"error";
            }catch(Exception ex){next.status="error";next.error=ex.getMessage()==null?"Ошибка папки":ex.getMessage();}
            next.updated=System.currentTimeMillis();LinkDownloadStore.put(this,next);
            activeId="";
        }
        }finally{activeId="";stopForeground(true);stopSelf();}
    }
    @Override public void onDestroy(){halt=true;super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
