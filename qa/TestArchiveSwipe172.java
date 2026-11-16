package ru.reader.rsvp;

/** Pure JVM behavioural checks for horizontal archive gestures. */
public final class TestArchiveSwipe172 {
    private static int checked;
    private static void check(boolean value,String what){
        if(!value)throw new AssertionError(what);
        checked++;
    }
    public static void main(String[] args){
        check(ArchiveSwipeMath.horizontal(25,3,10,0),"right opens");
        check(!ArchiveSwipeMath.horizontal(-25,3,10,0),"left does not open");
        check(!ArchiveSwipeMath.horizontal(25,31,10,0),"scroll wins over gesture");
        check(!ArchiveSwipeMath.horizontal(5,0,10,0),"tap does not swipe");
        check(ArchiveSwipeMath.horizontal(-25,3,10,165),"open card can close by left");
        check(ArchiveSwipeMath.offset(0,70,165)==70f,"direct reveal offset");
        check(ArchiveSwipeMath.offset(0,-70,165)==0f,"left blocked while closed");
        check(ArchiveSwipeMath.offset(165,-100,165)==65f,"close offset");
        check(ArchiveSwipeMath.offset(0,250,165)==165f,"no overscroll");
        check(ArchiveSwipeMath.reveal(70f,165,true),"42 percent reveals");
        check(!ArchiveSwipeMath.reveal(68f,165,true),"below threshold closes");
        check(!ArchiveSwipeMath.reveal(160f,165,false),"cancel never opens");
        System.out.println("PASS: "+checked+" actual Java archive swipe calculations");
    }
}
