package ru.reader.rsvp;

/** Rolling transport measurements, applied only when a new book's immutable
 * sentence catalogue is built. Never resegment an actively playing MP3.
 * Does not change a cache key merely because network latency changes.
 */
final class EdgeAdaptiveBlocks {
    private static double firstAudioMs=1350, totalMs=2400;
    private static int observations;
    private EdgeAdaptiveBlocks(){}
    static synchronized void observe(int priority,boolean success,long first,long total){
        if(priority!=EdgeSpeechQueue.PLAY)return;
        if(!success){
            if(first<0)first=3200;
            total=Math.max(3200,total);
        }
        if(first<0||total<=0)return;
        double weight=observations<3?0.35:0.18;
        firstAudioMs=firstAudioMs*(1-weight)+first*weight;
        totalMs=totalMs*(1-weight)+total*weight;
        observations++;
    }
    static synchronized int normalTarget(){
        if(observations==0)return 760;
        if(firstAudioMs>3000||totalMs>6000)return 600;
        if(firstAudioMs>1800||totalMs>3800)return 680;
        if(firstAudioMs<950&&totalMs<2100)return 800;
        return 740;
    }
    static synchronized int priorityTarget(){
        if(observations==0)return 480;
        if(firstAudioMs>3000||totalMs>6000)return 320;
        if(firstAudioMs>1800||totalMs>3800)return 400;
        return 500;
    }
}
