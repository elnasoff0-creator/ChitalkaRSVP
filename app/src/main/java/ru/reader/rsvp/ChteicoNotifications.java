package ru.reader.rsvp;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.util.Calendar;

/** Optional local notifications; no internet, analytics or scheduled background player. */
public final class ChteicoNotifications extends BroadcastReceiver {
    private static final String CHANNEL="chteico_reminders";
    private static final int REQUEST=721;
    private static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,REQUEST,new Intent(c,ChteicoNotifications.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static void schedule(Context c){
        AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(manager==null)return;
        manager.cancel(alarm(c));
        android.content.SharedPreferences p=ChteicoStore.prefs(c);
        if(!ChteicoStore.enabled(c)||!(p.getBoolean("daily",false)||p.getBoolean("inactive",false)))return;
        int hour=p.getInt("reminder_hour",19);
        Calendar when=Calendar.getInstance();when.set(Calendar.HOUR_OF_DAY,hour);when.set(Calendar.MINUTE,0);when.set(Calendar.SECOND,0);
        if(when.getTimeInMillis()<=System.currentTimeMillis())when.add(Calendar.DAY_OF_YEAR,1);
        manager.setInexactRepeating(AlarmManager.RTC_WAKEUP,when.getTimeInMillis(),AlarmManager.INTERVAL_DAY,alarm(c));
    }
    private static void channel(Context c){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Чтейцо · напоминания",NotificationManager.IMPORTANCE_DEFAULT));}}
    static void notifyPet(Context c,String title,String message,int id){
        if(!ChteicoStore.enabled(c))return;
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        channel(c);Intent open=new Intent(c,ChteicoActivity.class);open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b=new NotificationCompat.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_chteico_egg)
            .setContentTitle(title).setContentText(message).setContentIntent(pi).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT);
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id,b.build());
    }
    public static void milestone(Context c,String title,String body){if(ChteicoStore.prefs(c).getBoolean("achievements_notice",false))notifyPet(c,title,body,725);}
    @Override public void onReceive(Context c,Intent intent){
        if(intent!=null&&Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){schedule(c);return;}
        if(!ChteicoStore.enabled(c))return;
        android.content.SharedPreferences p=ChteicoStore.prefs(c);long last=p.getLong("last_active",0),now=System.currentTimeMillis();
        if(p.getBoolean("inactive",false)&&last>0&&now-last>=3L*86400000L){
            notifyPet(c,"Чтейцо скучает","Заглянем в книгу или включим аудиокнигу?",723);
        }else if(p.getBoolean("daily",false)&&last>0&& !new java.text.SimpleDateFormat("yyyyMMdd",java.util.Locale.ROOT).format(new java.util.Date(last)).equals(new java.text.SimpleDateFormat("yyyyMMdd",java.util.Locale.ROOT).format(new java.util.Date(now)))){
            notifyPet(c,"Чтейцо ждёт вас","Немного чтения или аудио — и мы снова вместе!",724);
        }
    }
}
