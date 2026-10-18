package ru.reader.rsvp;

/** Pure chapter-boundary decisions for signed relative audiobook seeking. */
public final class AudioSeekBounds {
    private AudioSeekBounds(){}
    /** -1 previous chapter, 0 remain, +1 next chapter. */
    public static int step(int index,int count,long chapterDuration,long wantedPosition){
        if(wantedPosition<0&&index>0)return -1;
        if(chapterDuration>0&&wantedPosition>=chapterDuration&&index+1<count)return 1;
        return 0;
    }
    public static long remaining(long wantedPosition,long chapterDuration,int step){
        return step>0?wantedPosition-chapterDuration:wantedPosition;
    }
    public static int clamped(long wantedPosition,long chapterDuration){
        return (int)Math.max(0,Math.min(Integer.MAX_VALUE,Math.min(chapterDuration,wantedPosition)));
    }
}
