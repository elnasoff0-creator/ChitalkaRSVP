package ru.reader.rsvp;
import android.content.Context;
import android.os.SystemClock;
public class SleepClockTest {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        Context ctx=new Context();
        PlaybackSleepCountdown tts=new PlaybackSleepCountdown(ctx,PlaybackSleepCountdown.TTS_KEY);
        PlaybackSleepCountdown audio=new PlaybackSleepCountdown(ctx,PlaybackSleepCountdown.AUDIOBOOK_KEY);
        tts.setMinutes(1);audio.setMinutes(5);
        SystemClock.advance(3600000);
        check(tts.remainingMillis()==60000,"should not run without play");
        tts.tick(true);SystemClock.advance(10000);tts.tick(true);
        check(tts.remainingMillis()==50000,"10s actual TTS listening");
        check(audio.remainingMillis()==300000,"audiobook timer independent");
        tts.freeze();SystemClock.advance(90000);
        check(tts.remainingMillis()==50000,"pause preserves remaining time");
        PlaybackSleepCountdown restored=new PlaybackSleepCountdown(ctx,PlaybackSleepCountdown.TTS_KEY);
        check(restored.remainingMillis()==50000,"persist across service switching");
        restored.tick(true);SystemClock.advance(50000);
        check(restored.tick(true),"should expire after 50s audible");
        check(!restored.tick(true),"no repeated expiration");
        check(audio.remainingMillis()==300000,"audiobook still untouched");
        audio.tick(true);SystemClock.advance(2500);audio.tick(true);
        check(audio.remainingMillis()==297500,"audiobook independent playback");
        audio.tick(false);SystemClock.advance(999999);audio.tick(false);
        check(audio.remainingMillis()==297500,"no background countdown");
        System.out.println("PASS: independent audiobook/TTS clocks, play-only, pause, restore, expiration");
    }
}
