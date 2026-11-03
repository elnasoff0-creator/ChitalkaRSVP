package ru.reader.rsvp;

import java.util.Arrays;

public final class TestComicProgressMath {
    private static void eq(int expected,int found,String label){
        if(expected!=found)throw new AssertionError(label+": "+found+" instead of "+expected);
    }
    public static void main(String[] args){
        eq(13,ComicProgressMath.percent(Arrays.asList(47,48,47,46,47,47,47,47,47,47,47,47,47,47,47,47,47,47),2,11,18),"global progress");
        eq(-1,ComicProgressMath.percent(Arrays.asList(47,-1,47),2,11,3),"unknown denominator");
        eq(-1,ComicProgressMath.percent(Arrays.asList(47,48),2,11,3),"incomplete index");
        eq(100,ComicProgressMath.percent(Arrays.asList(2,3,5),2,4,3),"last image");
        eq(10,ComicProgressMath.percent(Arrays.asList(2,3,5),0,0,3),"first image");
        eq(-1,ComicProgressMath.percent(Arrays.asList(2,3,5),3,0,3),"invalid chapter");
        System.out.println("PASS: 6 exact comic position checks");
    }
}
