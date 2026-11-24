package ru.reader.rsvp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** A foreground lifetime is tied to an explicitly started Wi-Fi session. */
public final class WebTransferService extends Service {
    static final String ACTION_START="ru.reader.rsvp.WEBTRANSFER_START";
    static final String ACTION_STOP="ru.reader.rsvp.WEBTRANSFER_STOP";
    static final String CHANNEL="web_transfer_v192";
    private static volatile WebTransferServer current;
    private static volatile String lastError="";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable watchdog=new Runnable(){@Override public void run(){
        WebTransferServer server=current;
        if(server==null){stopSelf();return;}
        int minutes=server.idleMinutes();
        if(!server.wifiSame() || (minutes>0&&server.activeUploads()==0&&server.idleMs()>minutes*60000L)){
            lastError=!server.wifiSame()?"Подключение Wi-Fi потеряно":"Сервер остановлен после простоя";
            stopServiceSession();return;
        }
        handler.postDelayed(this,2000);
    }};
    static boolean cancelTransfer(Context context,String id){
        WebTransferServer server=current;
        if(server==null)return false;
        return server.cancelTransfer(id);
    }
    static WebTransferServer current(){return current;}
    static String lastError(){return lastError;}
    static void notifyCompleted(Context c,String name){
        if(!WebTransferPrefs.notifications(c))return;
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        try{
            Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
            Notification n=b.setSmallIcon(R.drawable.ic_wifi_transfer).setContentTitle("Web Transfer · Файл получен")
                .setContentText(name).setAutoCancel(true).build();
            ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(19202,n);
        }catch(Exception ignored){}
    }
    @Override public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26){
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Передача книг по Wi-Fi",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Активная локальная передача файлов на устройство");
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"":intent.getAction();
        if(ACTION_STOP.equals(action)){stopServiceSession();return START_NOT_STICKY;}
        if(!ACTION_START.equals(action)){stopSelf();return START_NOT_STICKY;}
        try{
            if(current==null||!current.running()){current=WebTransferServer.start(this);lastError="";}
            Intent screen=new Intent(this,WebTransferActivity.class);
            PendingIntent open=PendingIntent.getActivity(this,0,screen,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Intent stop=new Intent(this,WebTransferService.class).setAction(ACTION_STOP);
            PendingIntent end=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
            Notification note=builder.setSmallIcon(R.drawable.ic_wifi_transfer).setContentTitle("ПРОЧиталка · Передача по Wi-Fi")
                .setContentText("Сервер работает: "+current.address()).setOngoing(true).setContentIntent(open)
                .addAction(R.drawable.ic_wifi_transfer,"Остановить",end).build();
            if(Build.VERSION.SDK_INT>=29)startForeground(19201,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(19201,note);
            handler.removeCallbacks(watchdog);handler.postDelayed(watchdog,2000);
        }catch(Exception error){lastError=error.getMessage()==null?"Не удалось запустить сервер":error.getMessage();stopServiceSession();}
        return START_NOT_STICKY;
    }
    private void stopServiceSession(){handler.removeCallbacks(watchdog);WebTransferServer previous=current;current=null;if(previous!=null)previous.close();stopForeground(true);stopSelf();}
    @Override public void onDestroy(){handler.removeCallbacks(watchdog);WebTransferServer s=current;current=null;if(s!=null)s.close();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
