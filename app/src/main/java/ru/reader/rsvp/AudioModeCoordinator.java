package ru.reader.rsvp;

import android.content.Context;
import android.content.Intent;

/** App-private command: starting a new source shuts down the previous MRT source. */
final class AudioModeCoordinator {
    static final String STOP_MRT = "ru.reader.rsvp.STOP_MRT_PLAYBACK";
    static final String STOP_EDGE = "ru.reader.rsvp.STOP_EDGE_PLAYBACK";
    static void stopEdge(Context context) {
        Intent intent=new Intent(STOP_EDGE); intent.setPackage(context.getPackageName());context.sendBroadcast(intent);
    }
    static void stopMrt(Context context) {
        stopEdge(context);
        Intent intent = new Intent(STOP_MRT);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
    private AudioModeCoordinator() {}
}
