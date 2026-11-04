package ru.reader.rsvp;

import android.content.Context;
import android.content.Intent;

/** Switch audio sources only upon a true PLAY command, not OPEN or PREPARE. */
final class AudioModeCoordinator {
    static final String STOP_EDGE="ru.reader.rsvp.STOP_EDGE_PLAYBACK";
    static void stopEdge(Context context) {
        Intent intent=new Intent(STOP_EDGE);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
    static void pauseLocal(Context context){
        AudioPlaybackService service=AudioPlaybackService.instance;
        if(service!=null&&!service.isPaused())service.pauseForAnotherSource();
    }
    private AudioModeCoordinator() {}
}
