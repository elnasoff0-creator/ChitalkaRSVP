package ru.reader.rsvp;

import java.util.List;

/** Small compatibility facade for on-demand listening and offline book caching.
 * Both consumers use the same serialized, complete-MP3 EdgeSpeechEngine.
 */
final class EdgeTtsClient {
    interface Callback {
        void progress(int chunks,long bytes);
        default void audio(byte[] bytes,int offset,int length) {}
        default void metadata(List<EdgeWordTimings.Boundary> marks) {}
        void finished(byte[] mp3,int chunks,String error);
        default void finished(byte[] mp3,int chunks,String error,List<EdgeWordTimings.Boundary> boundaries){
            finished(mp3,chunks,error);
        }
    }
    private final EdgeSpeechEngine engine;
    private EdgeTtsClient(EdgeSpeechEngine engine){this.engine=engine;}
    static EdgeTtsClient synthesize(String text,String voice,String rate,Callback callback){
        return synthesize(text,voice,rate,EdgeSpeechQueue.PLAY,callback);
    }
    static EdgeTtsClient synthesize(String text,String voice,String rate,int priority,Callback callback){
        EdgeSpeechEngine engine=new EdgeSpeechEngine(text,voice,rate,priority,callback);
        EdgeTtsClient result=new EdgeTtsClient(engine);
        engine.start();return result;
    }
    void promoteToPlay(){engine.promoteToPlay();}
    void cancel(){engine.cancel();}
}
