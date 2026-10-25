package ru.reader.rsvp;

import java.util.ArrayList;

/** Tests word offsets/UI blocks and estimated time without Android dependencies. */
public final class TestTtsNavigation {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        StringBuilder source=new StringBuilder();
        for(int i=0;i<2200;i++){
            if(i>0)source.append(i%127==0?"\n\n":" ");
            source.append("word").append(i);
        }
        String text=source.toString();
        ReadingWordMap map=ReadingWordMap.parse(text);
        ArrayList<Integer> offsets=SentenceChunks.wordPositions(text);
        BookChunkIndex reader=new BookChunkIndex(text,map,560);
        BookChunkIndex tts=new BookChunkIndex(text,offsets,560);
        check(reader.size()==tts.size(),"reader and TTS chunk counts");
        for(int i=0;i<reader.size();i++){
            BookChunkIndex.Chunk a=reader.get(i),b=tts.get(i);
            check(a.startChar==b.startChar && a.endChar==b.endChar,"UI chunk ranges "+i);
            check(a.startWord==b.startWord && a.endWord==b.endWord,"UI chunk word indexes "+i);
            check(a.endChar<=text.length(),"chunk stays within text");
        }
        for(int i=0;i<offsets.size();i++){
            int chunk=tts.chunkForWord(i);
            BookChunkIndex.Chunk c=tts.get(chunk);
            check(c.startWord<=i&&c.endWord>i,"every word belongs to a UI block");
        }
        check(TtsPositionTimeline.label(131100,0,300).equals("0 ч 0 мин / ~7 ч 17 мин"),"book start time");
        check(TtsPositionTimeline.label(131100,65550,300).equals("3 ч 38 мин / ~7 ч 17 мин"),"middle position");
        check(TtsPositionTimeline.label(131100,104880,300).equals("5 ч 49 мин / ~7 ч 17 мин"),"jump to later chapter");
        check(TtsPositionTimeline.label(131100,131100,300).equals("7 ч 17 мин / ~7 ч 17 мин"),"end time");
        check(TtsPositionTimeline.positionMinutes(100,700,0)==100,"clamp invalid WPM and word index");
        System.out.println("PASS UI block parity="+reader.size()+", word offsets="+offsets.size()+", position timeline");
    }
}
