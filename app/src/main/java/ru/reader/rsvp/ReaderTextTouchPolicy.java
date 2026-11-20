package ru.reader.rsvp;

/** Pure gesture gate: a RecyclerView scroll or a recycled row can never be a tap.
 * No Android TextView selection state is used and no speech position is mutated here.
 */
final class ReaderTextTouchPolicy {
    private int pointer=-1,row=-1;
    private float downX,downY;
    private boolean canceled,consumed;
    void down(int pointerId,int boundRow,float x,float y){
        pointer=pointerId;row=boundRow;downX=x;downY=y;canceled=false;consumed=false;
    }
    boolean move(int pointerId,float x,float y,int touchSlop){
        if(pointer<0||pointer!=pointerId){cancel();return false;}
        if(Math.abs(x-downX)>touchSlop||Math.abs(y-downY)>touchSlop)canceled=true;
        return !canceled;
    }
    boolean eligibleForLongPress(int boundRow){return !canceled&&!consumed&&pointer>=0&&row>0&&row==boundRow;}
    void consumeLongPress(){if(!canceled)consumed=true;}
    boolean up(int pointerId,int boundRow){
        boolean tap=!canceled&&!consumed&&pointer==pointerId&&row>0&&row==boundRow;
        cancel();return tap;
    }
    void cancel(){pointer=-1;row=-1;canceled=true;consumed=false;}
}
