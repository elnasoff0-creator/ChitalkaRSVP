package ru.reader.rsvp;
import java.util.Arrays;
public final class TestComicRestore191 {
    private static void eq(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args){
        java.util.List<String> original=Arrays.asList("manga:ch5","manga:ch6","manga:ch7");
        java.util.List<String> reordered=Arrays.asList("manga:ch4","manga:ch5","manga:ch6","manga:ch7");
        eq(ComicRestorePolicy.resolveChapter("manga:ch6",original,1)==1,"original chapter");
        eq(ComicRestorePolicy.resolveChapter("manga:ch6",reordered,1)==2,"downloaded earlier chapter must not change read chapter");
        eq(ComicRestorePolicy.resolveChapter("",reordered,2)==2,"legacy chapter migration");
        eq(ComicRestorePolicy.resolveChapter("missing",reordered,99)==3,"missing id bounded fallback");
        eq(ComicRestorePolicy.resolveChapter("missing",java.util.Collections.emptyList(),2)==-1,"empty catalogue");
        eq(ComicRestorePolicy.clampFraction(.37)==.37,"relative image position");
        eq(ComicRestorePolicy.clampFraction(-.5)==0&&ComicRestorePolicy.clampFraction(1.5)==1,"fraction clamp");
        eq(ComicRestorePolicy.clampFraction(Double.NaN)<0,"corrupted fraction fallback");
        eq(ReaderTtsScrollPolicy.needsFastReturn(3200,800),"distant return is fast");
        eq(!ReaderTtsScrollPolicy.needsFastReturn(250,800),"near return remains smooth");
        eq(ReaderTtsScrollPolicy.needsFastReturn(-1800,800),"backward distant return is fast");
        System.out.println("PASS stable chapter IDs, fraction and fast-return routing");
    }
}
