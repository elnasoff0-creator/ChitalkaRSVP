package ru.reader.rsvp;

import java.util.ArrayList;
import java.util.Arrays;

/** Tests actual live/offline Edge grouping and manual jump boundaries. */
public final class TestEdgeNaturalSentences160 {
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static void checkAllWords(String source,ArrayList<SentenceChunks.Chunk> groups){
        int at=0;
        for(SentenceChunks.Chunk group:groups){
            check(group.fromWord==at,"missing or repeated words at group "+at);
            check(group.toWord>at,"empty group");
            check(group.text.equals(source.substring(group.fromChar,group.toChar)),"wrong offsets");
            at=group.toWord;
        }
        check(at==SentenceChunks.wordPositions(source).size(),"final word count mismatch");
    }
    public static void main(String[] unused){
        String longSentence="Очень длинная связная фраза продолжает развиваться и ни разу не заканчивается ".repeat(19)
            +"на этих словах.";
        check(longSentence.length()>800,"test needs a sentence above soft cap");
        String entire=longSentence+" Небольшой финал. Ещё один небольшой финал!";
        ArrayList<SentenceChunks.Chunk> raw=EdgeSentenceBoundary.split(entire);
        check(raw.size()==3,"complete sentence splitting with soft maximum");
        check(raw.get(0).text.equals(longSentence),"giant sentence was cut");
        ArrayList<SentenceChunks.Chunk> groups=EdgeSpeechSegments.split(entire);
        check(groups.get(0).text.equals(longSentence),"Edge must not cut long sentence mid-way");
        checkAllWords(entire,groups);
        String excerpt="Она спросила: «Ты идёшь?» Он ответил: «Да!» После было тихо. ";
        ArrayList<SentenceChunks.Chunk> quotes=EdgeSentenceBoundary.split(excerpt);
        check(quotes.size()==3,"quotes should end with the sentence");
        check(quotes.get(0).text.endsWith("?»"),"closing quote belongs to previous sentence");
        String decimal="Цена 12.5 рубля. В г. Москве продолжение истории. Конец.";
        ArrayList<SentenceChunks.Chunk> decimals=EdgeSentenceBoundary.split(decimal);
        check(decimals.size()==3,"decimals and abbreviations must not force breaks");
        String little="Первое. Второе? Третье! Четвёртое. ".repeat(30);
        ArrayList<SentenceChunks.Chunk> packed=EdgeSpeechSegments.split(little);
        check(packed.size()<EdgeSentenceBoundary.split(little).size()/2,"should group short sentences to avoid pauses");
        checkAllWords(little,packed);
        for(SentenceChunks.Chunk group:packed){
            char last=little.charAt(group.toChar-1);
            check(last=='.'||last=='?'||last=='!'||last=='…'||last=='»',"group ends in the middle of a sentence");
        }
        ArrayList<Integer> words=SentenceChunks.wordPositions(little);
        SentenceChunks.Chunk second=packed.get(1);
        int tapped=second.fromWord+2;
        ArrayList<SentenceChunks.Chunk> priority=EdgeSpeechSegments.priorityPieces(
            little,words,tapped,second.toWord,second.toChar);
        check(priority.size()==1,"manual seek should not create 500-character gaps");
        check(priority.get(0).fromWord==tapped&&priority.get(0).toWord==second.toWord,
            "manual seek replays or skips words");
        check(priority.get(0).text.endsWith("."),"manual clip cuts a sentence");
        check(priority.get(0).text.equals(little.substring(priority.get(0).fromChar,second.toChar)),
            "manual clip does not match original text");
        int secondChapter=little.indexOf("Первое.",500);
        BookChapterIndex chapters=BookChapterIndex.build(Arrays.asList(
            new BookChapterIndex.Chapter("Том 1",0,0,1),
            new BookChapterIndex.Chapter("Том 2",secondChapter,0,1)),little.length());
        for(SentenceChunks.Chunk group:EdgeSpeechSegments.split(little,chapters))
            check(!(group.fromChar<secondChapter&&group.toChar>secondChapter),"chapter boundary crossed");
        System.out.println("PASS: natural sentence endpoints, 800+ chars, quotes, decimals, abbreviations, manual seek, TOC, word offsets");
    }
}
