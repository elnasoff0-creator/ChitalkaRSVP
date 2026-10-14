package ru.reader.rsvp;

import android.content.Context;
import android.content.Intent;

/** When local playback begins, pause the independent Edge TTS pipeline. */
final class AudioModeCoordinator {
    static final String STOP_EDGE="ru.reader.rsvp.STOP_EDGE_PLAYBACK";
    static void stopEdge(Context context) {
        Intent intent=new Intent(STOP_EDGE);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
    private AudioModeCoordinator() {}
}
