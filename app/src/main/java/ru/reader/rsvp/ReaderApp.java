package ru.reader.rsvp;

import android.app.Application;
import android.app.Activity;
import android.os.Bundle;

/** Migrate preferences as soon as the upgraded app starts. */
public final class ReaderApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
            @Override public void onActivityCreated(Activity a,Bundle b){SafeSystemBars.install(a);}
            @Override public void onActivityStarted(Activity a){}
            @Override public void onActivityResumed(Activity a){}
            @Override public void onActivityPaused(Activity a){}
            @Override public void onActivityStopped(Activity a){}
            @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
            @Override public void onActivityDestroyed(Activity a){}
        });
        EdgeSettings.migrate(getSharedPreferences("reader",MODE_PRIVATE));
        LinkDownloadStore.recoverInterrupted(this);
    }
}
