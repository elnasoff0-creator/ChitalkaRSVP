package ru.reader.rsvp;
public final class TestReaderTextReanchor197 {
    private static void check(boolean yes,String what){if(!yes)throw new AssertionError(what);}
    public static void main(String[] args){
        ReadingWordMap old=ReadingWordMap.parse("Contents Chapter One Chapter Two alpha beta gamma delta epsilon zeta eta theta iota kappa");
        ReadingWordMap clean=ReadingWordMap.parse("alpha beta gamma delta epsilon zeta eta theta iota kappa");
        ReaderTextReanchor map=new ReaderTextReanchor(old,clean);
        check(map.word(8)==3,"saved word after removed TOC");
        check(map.word(1)==0,"position inside removed TOC");
        check(map.word(14)==9,"last word");
        check(map.startChar(old.starts[8]+2,8,3)==clean.starts[3]+2,"char within word");
        check(map.word(-1)==-1,"unset viewport");
        ReadingWordMap repeated=ReadingWordMap.parse("alpha beta gamma delta epsilon zeta eta theta iota kappa alpha beta gamma delta epsilon zeta eta theta iota kappa");
        ReaderTextReanchor same=new ReaderTextReanchor(repeated,repeated);
        check(same.word(15)==15,"repeated paragraph identity");
        System.out.println("PASS: word and character anchors after removal, nearest surviving passage, repeated text");
    }
}
