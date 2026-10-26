package ru.reader.rsvp;

import java.util.List;

/** Compatibility facade. All networking and streaming live in EdgeRealtimeTransport.
 * Existing full-book and live-play consumers use the same transport implementation.
 */
final class EdgeTtsClient {
    interface Callback {
        void progress(int chunks,long bytes);
        default boolean streamOnly(){return false;}
        default void audio(byte[] bytes,int start,int count){}
        default void words(List<EdgeWordTimings.Boundary> boundaries){}
        void finished(byte[] mp3,int chunks,String error);
        default void finished(byte[] mp3,int chunks,String error,List<EdgeWordTimings.Boundary> boundaries){
            finished(mp3,chunks,error);
        }
    }
    private final EdgeRealtimeTransport transport;
    private EdgeTtsClient(EdgeRealtimeTransport value){transport=value;}
    static EdgeTtsClient synthesize(String text,String voice,String rate,Callback cb){
        return synthesize(text,voice,rate,EdgeRequestGate.PLAY,cb);
    }
    static EdgeTtsClient synthesize(String text,String voice,String rate,int priority,Callback cb){
        EdgeRealtimeTransport transport=new EdgeRealtimeTransport(text,voice,rate,priority,cb);
        EdgeTtsClient client=new EdgeTtsClient(transport);
        transport.begin();return client;
    }
    void cancel(){transport.cancel();}
}
