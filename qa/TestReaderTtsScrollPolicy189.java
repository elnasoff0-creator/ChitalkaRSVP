package ru.reader.rsvp;

/** Pure tests: active-word zone, single-frame easing and unloaded rows. */
public final class TestReaderTtsScrollPolicy189 {
    static void eq(int value,int expected,String why){
        if(value!=expected)throw new AssertionError(why+": "+value+" expected "+expected);
    }
    static void yes(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        int top=88,bottom=704;
        // The start of a three-screen sentence being visible is NOT enough:
        // we always calculate from the currently SPOKEN word's Y coordinate.
        eq(ReaderTtsScrollPolicy.shift(410,top,bottom,false,5),0,"working zone");
        yes(ReaderTtsScrollPolicy.shift(635,top,bottom,false,5)>0,"lower threshold");
        yes(ReaderTtsScrollPolicy.shift(41,top,bottom,false,5)<0,"above reader");
        yes(ReaderTtsScrollPolicy.shift(410,top,bottom,true,5)!=0,"explicit return centres");
        eq(ReaderTtsScrollPolicy.shift(400,top,top+15,false,5),0,"tiny viewport");
        int previous=200;
        for(int i=0;i<100&&previous>0;i++){
            int step=ReaderTtsScrollPolicy.frameStep(previous,1,22);
            yes(step>0&&step<=22,"bounded smooth motion");
            previous-=step;
        }
        eq(previous,0,"easing converges without jumping");
        eq(ReaderTtsScrollPolicy.frameStep(-75,1,22),-15,"reverse smooth movement");
        eq(ReaderTtsScrollPolicy.missingDirection(18,3,7),1,"next RecyclerView chunk");
        eq(ReaderTtsScrollPolicy.missingDirection(2,3,7),-1,"earlier RecyclerView chunk");
        eq(ReaderTtsScrollPolicy.missingDirection(4,3,7),0,"row awaiting layout");
        System.out.println("PASS: active word follow, variable viewport, bounded retargetable scroll");
    }
}
