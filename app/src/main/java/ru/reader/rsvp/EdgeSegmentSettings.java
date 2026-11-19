package ru.reader.rsvp;
import android.content.SharedPreferences;

/** Compatibility settings. 800 is only a soft target, not a hard cut-off:
 * complete grammatical sentences may be longer and are never sliced.
 * Adaptive Edge grouping is managed by EdgeSpeechLayout per book.
 */
public final class EdgeSegmentSettings {
    public static final String KEY="edge_segment_characters";
    public static final int DEFAULT=SentenceChunks.LIMIT;
    public static final int MIN=SentenceChunks.LIMIT;
    public static final int MAX=SentenceChunks.LIMIT;
    private EdgeSegmentSettings(){}
    public static int get(SharedPreferences preferences){return DEFAULT;}
    public static int clamp(int ignored){return DEFAULT;}
}
