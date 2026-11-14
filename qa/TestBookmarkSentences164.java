package ru.reader.rsvp;

/** Plain JVM regression coverage for sentence boundaries and preserved manual selections. */
public final class TestBookmarkSentences164 {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        String body="Артур не удивился, когда с неба спустился дракон. Он всё ещё смотрел наверх!\n\nСледующее предложение здесь.";
        ReadingWordMap map=ReadingWordMap.parse(body);
        SentenceIndex index=SentenceIndex.fromText(body);
        ReaderBookmarkSentences.Range single=ReaderBookmarkSentences.choose(2,2,map.size(),index);
        check(single.from==0,"Single-word bookmark must begin at sentence start");
        check(ReaderBookmarkSentences.excerpt(body,map,single).equals("Артур не удивился, когда с неба спустился дракон."),"Complete sentence excerpt");
        int second=index.next(0);
        ReaderBookmarkSentences.Range secondSentence=ReaderBookmarkSentences.choose(second,second,map.size(),index);
        check(ReaderBookmarkSentences.excerpt(body,map,secondSentence).equals("Он всё ещё смотрел наверх!"),"Russian ё and punctuation");
        ReaderBookmarkSentences.Range expanded=ReaderBookmarkSentences.choose(1,second+1,map.size(),index);
        check(expanded.from==1&&expanded.to==second+1,"A manually expanded selection must not be changed");
        check(ReaderBookmarkSentences.choose(0,0,0,index).from==0,"Empty map safe");
        System.out.println("PASS: sentence bookmarks, manual ranges, UTF-8 text");
    }
}
