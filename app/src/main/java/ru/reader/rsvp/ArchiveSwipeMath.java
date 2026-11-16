package ru.reader.rsvp;

/** Pure gesture math for the downloads archive action reveal. No Android state. */
final class ArchiveSwipeMath {
    private ArchiveSwipeMath(){}
    static boolean horizontal(float dx,float dy,float slop,float oldOffset){
        return Math.abs(dx)>slop && Math.abs(dx)>Math.abs(dy)*1.2f &&
            (dx>0f || oldOffset>0f);
    }
    static float offset(float oldOffset,float delta,float width){
        return Math.max(0f,Math.min(Math.max(1f,width),oldOffset+delta));
    }
    static boolean reveal(float offset,float width,boolean released){
        return released && offset>=Math.max(1f,width)*.42f;
    }
}
