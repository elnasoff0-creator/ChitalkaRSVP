package ru.reader.rsvp;

import java.util.*;

public final class TestHtmlChapterOrder {
    public static void main(String[] args){
        List<String> names=new ArrayList<>(Arrays.asList(
            "v3/chapter_10.html", "v2/chapter_2.html", "v3/chapter_1.5.html",
            "v3/chapter_2.html", "v3/chapter_1.html", "v10/chapter_1.html",
            "v2/chapter_11.html"));
        names.sort(HtmlChapterOrder::compare);
        List<String> expected=Arrays.asList("v2/chapter_2.html", "v2/chapter_11.html",
            "v3/chapter_1.html", "v3/chapter_1.5.html", "v3/chapter_2.html",
            "v3/chapter_10.html", "v10/chapter_1.html");
        if(!expected.equals(names))throw new AssertionError("Wrong natural order: "+names);
        if(HtmlChapterOrder.compare("chapter_001.html","chapter_1.html")!=0)
            throw new AssertionError("Zero-padded chapter is not equivalent");
        System.out.println("PASS: chapter order with volumes, fractional numbers and zero padding");
    }
}
