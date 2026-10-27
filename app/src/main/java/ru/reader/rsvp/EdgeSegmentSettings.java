package ru.reader.rsvp;
import android.content.SharedPreferences;

/** Sentence-first segmentation is fixed at 800 chars for both synthesis paths. */
public final class EdgeSegmentSettings {
    public static final String KEY="edge_segment_characters";
    public static final int DEFAULT=SentenceChunks.LIMIT;
    public static final int MIN=SentenceChunks.LIMIT;
    public static final int MAX=SentenceChunks.LIMIT;
    private EdgeSegmentSettings(){}
    public static int get(SharedPreferences preferences){return DEFAULT;}
    public static int clamp(int ignored){return DEFAULT;}
}
