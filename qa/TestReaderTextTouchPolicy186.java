package ru.reader.rsvp;

public final class TestReaderTextTouchPolicy186 {
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        ReaderTextTouchPolicy g=new ReaderTextTouchPolicy();
        g.down(3,7,30,40);
        check(g.eligibleForLongPress(7),"long press initially allowed");
        check(g.move(3,32,43,9),"small movement allowed");
        check(g.up(3,7),"short tap delivered");
        g.down(3,7,30,40);
        check(!g.move(3,30,60,9),"scroll cancels selection timer");
        check(!g.eligibleForLongPress(7),"no long press during scroll");
        check(!g.up(3,7),"no tap after scroll");
        g.down(3,7,30,40);g.consumeLongPress();
        check(!g.up(3,7),"a long press must never seek TTS on release");
        g.down(3,7,30,40);check(!g.eligibleForLongPress(8),"recycled row cannot be selected");
        check(!g.up(3,8),"recycled row cannot navigate");
        g.down(3,7,30,40);g.cancel();check(!g.up(3,7),"secondary touch cancels tap");
        System.out.println("PASS: touch policy separates tap, long press, scroll, recycled row and multi-touch");
    }
}
