package ru.reader.rsvp;

/** Immutable book-wide UTF-16 selection. The RecyclerView rows are only views of this range.
 * End is exclusive, so punctuation and whitespace are preserved exactly when copied.
 */
final class ReaderSelectionRange {
    final int start;
    final int end;
    private ReaderSelectionRange(int start,int end){this.start=start;this.end=end;}

    static ReaderSelectionRange of(int first,int last,int textLength){
        int length=Math.max(0,textLength);
        int a=Math.max(0,Math.min(first,length));
        int b=Math.max(0,Math.min(last,length));
        return new ReaderSelectionRange(Math.min(a,b),Math.max(a,b));
    }
    boolean valid(){return end>start;}

    /** Return both the new range and the handle role after crossing the other endpoint. */
    Move drag(boolean movingStart,int at,int textLength){
        int position=Math.max(0,Math.min(at,Math.max(0,textLength)));
        if(movingStart){
            if(position>=end){
                int anchor=Math.min(end,Math.max(0,textLength-1));
                int nextEnd=Math.max(position,anchor+1);
                return new Move(of(anchor,nextEnd,textLength),false);
            }
            return new Move(of(position,end,textLength),true);
        }
        if(position<=start){
            int anchor=Math.max(1,start);
            int nextStart=Math.min(position,anchor-1);
            return new Move(of(nextStart,anchor,textLength),true);
        }
        return new Move(of(start,position,textLength),false);
    }
    static final class Move {
        final ReaderSelectionRange range;
        final boolean movingStart;
        Move(ReaderSelectionRange range,boolean movingStart){this.range=range;this.movingStart=movingStart;}
    }
}
