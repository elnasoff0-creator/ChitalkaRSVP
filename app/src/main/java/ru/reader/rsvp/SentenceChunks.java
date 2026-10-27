package ru.reader.rsvp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared sentence-first segmentation for Android and Edge TTS. Source offsets stay stable. */
final class SentenceChunks {
    static final int LIMIT = 900;
    static final class Chunk {
        final int fromChar, toChar, fromWord, toWord;
        final String text;
        Chunk(int a,int b,int c,int d,String value) {
            fromChar=a;toChar=b;fromWord=c;toWord=d;text=value;
        }
    }
    private SentenceChunks() {}
    static ArrayList<Integer> wordPositions(String raw) {
        ArrayList<Integer> out=new ArrayList<>();
        Matcher m=Pattern.compile("\\S+").matcher(raw==null?"":raw);
        while(m.find())out.add(m.start());
        return out;
    }
    static int lowerBound(ArrayList<Integer> offsets,int value) {
        int at=Collections.binarySearch(offsets,value);
        return at<0?-at-1:at;
    }
    static ArrayList<Chunk> split(String raw) {
        if(raw==null)raw="";
        ArrayList<Chunk> out=new ArrayList<>();
        ArrayList<Integer> offsets=wordPositions(raw);
        Matcher breaks=Pattern.compile("(?<=[.!?…])\\s+|\\n{2,}").matcher(raw);
        int sentenceBegin=0;
        while(sentenceBegin<raw.length()) {
            boolean found=breaks.find(sentenceBegin);
            int sentenceEnd=found?breaks.start():raw.length();
            int cursor=sentenceBegin;
            while(cursor<sentenceEnd) {
                while(cursor<sentenceEnd&&Character.isWhitespace(raw.charAt(cursor)))cursor++;
                if(cursor>=sentenceEnd)break;
                int end=Math.min(sentenceEnd,cursor+LIMIT);
                if(end<sentenceEnd && !Character.isWhitespace(raw.charAt(end-1)) && !Character.isWhitespace(raw.charAt(end))) {
                    int boundary=end;
                    while(boundary>cursor && !Character.isWhitespace(raw.charAt(boundary-1)))boundary--;
                    if(boundary>cursor)end=boundary; // avoid cutting an ordinary word
                    // A pathological word longer than LIMIT must be split to obey the hard cap.
                }
                while(end>cursor&&Character.isWhitespace(raw.charAt(end-1)))end--;
                if(end<=cursor){end=Math.min(sentenceEnd,cursor+LIMIT);}
                int firstWord=lowerBound(offsets,cursor);
                int lastWord=lowerBound(offsets,end);
                if(firstWord<lastWord)out.add(new Chunk(cursor,end,firstWord,lastWord,raw.substring(cursor,end)));
                cursor=end;
            }
            if(!found)break;
            sentenceBegin=breaks.end();
        }
        return out;
    }
    static Chunk forWord(ArrayList<Chunk> parts,int word) {
        if(parts.isEmpty())return null;
        int lo=0,hi=parts.size()-1;
        while(lo<=hi){int mid=(lo+hi)>>>1;Chunk c=parts.get(mid);
            if(word<c.fromWord)hi=mid-1;
            else if(word>=c.toWord)lo=mid+1;
            else return c;
        }
        return parts.get(Math.max(0,Math.min(parts.size()-1,lo)));
    }
}
