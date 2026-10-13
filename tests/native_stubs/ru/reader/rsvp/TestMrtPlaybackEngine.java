package ru.reader.rsvp;
import android.media.MediaPlayer;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;

public final class TestMrtPlaybackEngine {
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        File first=File.createTempFile("mrt-a", ".mp3"),second=File.createTempFile("mrt-b", ".mp3");
        ArrayList<String> events=new ArrayList<>();
        final MrtPlaybackEngine[] holder=new MrtPlaybackEngine[1];
        holder[0]=new MrtPlaybackEngine(new MrtPlaybackEngine.Listener() {
            @Override public void onStarted(Object clip){events.add("play:"+clip);}
            @Override public void onCompleted(Object clip){
                events.add("end:"+clip);
                if ("first".equals(clip)) holder[0].play(second,"second");
            }
            @Override public void onError(Object clip,String reason){events.add("error:"+clip);}
        });
        MrtPlaybackEngine engine=holder[0];
        engine.play(first,"first");
        check(MediaPlayer.ALL.size()==1 && MediaPlayer.ALL.get(0).startCalls==1, "first started");
        engine.warm(second,"second");
        check(MediaPlayer.ALL.size()==2 && MediaPlayer.ALL.get(1).startCalls==0, "prefetched second must not sound");
        check(engine.hasCurrent(),"native audio active");
        // Synthesis errors must never call the playback object; the manager can retry freely.
        engine.pause(); check(MediaPlayer.ALL.get(0).pauseCalls==1,"pause first");
        engine.resume();check(MediaPlayer.ALL.get(0).startCalls==2,"resume first");
        MediaPlayer.ALL.get(0).simulateCompletion();
        check(MediaPlayer.ALL.get(1).startCalls==1,"second starts only after first completes");
        check(events.get(0).equals("play:first") && events.get(1).equals("end:first") && events.get(2).equals("play:second"), "exact playback sequence");
        engine.stop();check(!engine.hasCurrent() && MediaPlayer.ALL.get(1).closed,"explicit stop releases current");
        System.out.println("PASS: independent native playback, paused/resumed first, warmed second starts only on completion");
        Files.deleteIfExists(first.toPath());Files.deleteIfExists(second.toPath());
    }
}
