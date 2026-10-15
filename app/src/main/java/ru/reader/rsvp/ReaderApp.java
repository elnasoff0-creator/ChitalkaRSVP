package ru.reader.rsvp;

import android.app.Application;

/** Migrate preferences as soon as the upgraded app starts. */
public final class ReaderApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        EdgeSettings.migrate(getSharedPreferences("reader",MODE_PRIVATE));
        LinkDownloadStore.recoverInterrupted(this);
    }
}
