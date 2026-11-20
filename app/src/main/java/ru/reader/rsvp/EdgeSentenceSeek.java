package ru.reader.rsvp;

import java.util.List;

/** Exact, source-verified replay into an existing cached MP3 group.
 * Missing WordBoundary for the first word is NOT permission to seek into the
 * second one. The caller must synthesize an intact selected sentence instead.
 */
final class EdgeSentenceSeek {
    private static final long PRE_ROLL_MS = 105L;
    private EdgeSentenceSeek() {}

    /** @return safe source-clock seek, or -1 for isolated-sentence fallback. */
    static long at(int relativeChar, String source, List<EdgeWordTimings.Word> words) {
        if (relativeChar <= 0) return 0L; // Start of the actual clip, not a guessed marker.
        if (source == null || relativeChar >= source.length() ||
                words == null || words.isEmpty()) return -1L;
        for (int i=0;i<words.size();i++) {
            EdgeWordTimings.Word word=words.get(i);
            if (word.from < 0 || word.to <= word.from || word.to > source.length()) continue;
            if (word.to <= relativeChar) continue;
            if (word.from > relativeChar && !onlyUnspokenSeparators(source,relativeChar,word.from)) {
                // A missing source word is audible speech, not whitespace.
                // Never mistake the next WordBoundary for the first word.
                return -1L;
            }
            return safePreRoll(word,i>0?words.get(i-1):null);
        }
        return -1L;
    }

    private static boolean onlyUnspokenSeparators(String text,int from,int to) {
        if (from < 0 || to > text.length() || to <= from) return false;
        for (int i=from;i<to;i++) {
            char c=text.charAt(i);
            if (Character.isWhitespace(c)) continue;
            switch (Character.getType(c)) {
                case Character.CONNECTOR_PUNCTUATION:
                case Character.DASH_PUNCTUATION:
                case Character.START_PUNCTUATION:
                case Character.END_PUNCTUATION:
                case Character.INITIAL_QUOTE_PUNCTUATION:
                case Character.FINAL_QUOTE_PUNCTUATION:
                case Character.OTHER_PUNCTUATION:
                    continue;
                default:
                    return false; // A letter, number or symbol may be spoken.
            }
        }
        return true;
    }

    /** MP3 frame seeks and delayed UI ticks must not truncate the onset.
     * Avoid re-playing an earlier complete word: clamp to the last reliable
     * previous-word end when it is available. There is no safe fallback to a
     * different word, and the source time remains unchanged for sidecars.
     */
    private static long safePreRoll(EdgeWordTimings.Word target,EdgeWordTimings.Word previous) {
        long start=Math.max(0L,target.startMs-PRE_ROLL_MS);
        if (previous!=null && previous.startMs<target.startMs && previous.durationMs>0) {
            long previousEnd=previous.startMs+previous.durationMs;
            start=Math.max(start,Math.min(target.startMs,previousEnd));
        }
        return start;
    }
}
