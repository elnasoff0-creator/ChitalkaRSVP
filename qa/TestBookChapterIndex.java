package ru.reader.rsvp;
import java.util.Arrays;
public class TestBookChapterIndex {
    static void check(boolean v){if(!v)throw new AssertionError();}
    public static void main(String[] args){
        BookChapterIndex index=BookChapterIndex.build(Arrays.asList(
            new BookChapterIndex.Chapter("Глава 3",500,0,1),
            new BookChapterIndex.Chapter("Глава 1",0,0,1),
            new BookChapterIndex.Chapter("Глава 2",250,0,1),
            new BookChapterIndex.Chapter("Дубль главы 2",250,0,2)),800);
        check(index.size()==3);
        check(index.get(0).endChar==250);
        check(index.get(1).endChar==500);
        check(index.get(2).endChar==800);
        check(index.atChar(310).title.equals("Глава 2"));
        System.out.println("PASS structural chapter offsets and boundaries");
    }
}
