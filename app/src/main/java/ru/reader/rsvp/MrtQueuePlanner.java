package ru.reader.rsvp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** Pure queue algorithm; no playback commands and no WebView dependency. */
public final class MrtQueuePlanner {
    private MrtQueuePlanner() {}
    public static ArrayList<Integer> window(int activeStart, int totalWords, int lookahead,
                                            IntUnaryOperator findEnd) {
        ArrayList<Integer> starts = new ArrayList<>();
        if(totalWords <= 0)return starts;
        int cursor = Math.max(0, Math.min(totalWords-1, activeStart));
        int size = Math.max(3, Math.min(10, lookahead));
        for (int i=0;i<size && cursor<totalWords;i++) {
            int end=findEnd.applyAsInt(cursor);
            if(end<=cursor || end>totalWords)break;
            starts.add(cursor);
            cursor=end;
        }
        return starts;
    }
}
