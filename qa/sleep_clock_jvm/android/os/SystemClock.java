package android.os;
public final class SystemClock {
    private static long clock=1000L;
    public static long elapsedRealtime(){return clock;}
    public static void advance(long millis){clock+=millis;}
}
