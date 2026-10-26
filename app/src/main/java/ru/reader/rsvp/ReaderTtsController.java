package ru.reader.rsvp;

import android.content.Context;
import android.content.Intent;

/** Headless engine routing. ReaderActivity owns UI only; actual speech lives in
 * AudioPlaybackService or EdgeStreamingService/EdgePlaybackService.
 * No Activity is ever started when the user switches voice engines.
 */
final class ReaderTtsController {
    private ReaderTtsController(){}
    static void begin(Context context,boolean local,String uri,int word){
        if(local){
            AudioModeCoordinator.stopEdge(context);
            context.startService(new Intent(context,AudioPlaybackService.class).setAction(AudioPlaybackService.TTS)
                .putExtra("bookUri",uri).putExtra("word_index",word));
        }else{
            if(AudioPlaybackService.instance!=null&&AudioPlaybackService.instance.isTtsMode())
                context.startService(new Intent(context,AudioPlaybackService.class).setAction(AudioPlaybackService.STOP));
            context.startService(new Intent(context,EdgeStreamingService.class)
                .setAction(EdgeStreamingService.ACTION_BEGIN)
                .putExtra(EdgeStreamingService.EXTRA_URI,uri)
                .putExtra(EdgeStreamingService.EXTRA_ANCHOR,word));
        }
    }
    static void stopLocal(Context context){
        if(AudioPlaybackService.instance!=null&&AudioPlaybackService.instance.isTtsMode())
            context.startService(new Intent(context,AudioPlaybackService.class).setAction(AudioPlaybackService.STOP));
    }
    static void localPauseOrResume(Context context){
        AudioPlaybackService s=AudioPlaybackService.instance;
        boolean playing=s!=null&&s.isTtsMode()&&!s.isPaused();
        context.startService(new Intent(context,AudioPlaybackService.class)
            .setAction(playing?AudioPlaybackService.PAUSE:AudioPlaybackService.PLAY));
    }
    static void localSelect(Context context,int word){
        context.startService(new Intent(context,AudioPlaybackService.class).setAction("tts_select_word")
            .putExtra("word_index",word));
    }
}
