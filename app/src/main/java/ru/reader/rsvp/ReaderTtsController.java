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
        begin(context,local,uri,word,false,false);
    }
    static void begin(Context context,boolean local,String uri,int word,
            boolean autoPlay,boolean forceEdge){
        if(local){
            context.startService(new Intent(context,AudioPlaybackService.class)
                .setAction(AudioPlaybackService.TTS)
                .putExtra("bookUri",uri).putExtra("word_index",word)
                .putExtra("auto_on_selection",autoPlay));
        }else{
            context.startService(new Intent(context,EdgeStreamingService.class)
                .setAction(EdgeStreamingService.ACTION_BEGIN)
                .putExtra(EdgeStreamingService.EXTRA_URI,uri)
                .putExtra(EdgeStreamingService.EXTRA_ANCHOR,word)
                .putExtra(EdgeStreamingService.EXTRA_FORCE_EDGE,forceEdge));
        }
    }
    static void stopLocal(Context context){
        AudioPlaybackService old=AudioPlaybackService.instance;
        if(old!=null&&old.isTtsMode()){
            ReaderDiagnosticLog.event("TTS_SWITCH","TTS_BACKUP_STOP_REQUESTED backup="+
                old.isBackupEdgeSession());
            context.startService(new Intent(context,AudioPlaybackService.class)
                .setAction(AudioPlaybackService.STOP));
        }
    }
    static void localPauseOrResume(Context context){
        AudioPlaybackService s=AudioPlaybackService.instance;
        boolean playing=s!=null&&s.isTtsMode()&&!s.isPaused();
        context.startService(new Intent(context,AudioPlaybackService.class)
            .setAction(playing?AudioPlaybackService.PAUSE:AudioPlaybackService.PLAY)
            .putExtra("bookUri",context.getSharedPreferences("reader",0).getString("uri","")));
    }
    static void localSelect(Context context,int word){
        context.startService(new Intent(context,AudioPlaybackService.class).setAction("tts_select_word")
            .putExtra("word_index",word));
    }
}
