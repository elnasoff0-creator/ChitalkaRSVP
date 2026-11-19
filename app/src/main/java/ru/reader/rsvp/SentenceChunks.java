package ru.reader.rsvp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared sentence-first segmentation for Android and Edge TTS. Source offsets stay stable. */
final class SentenceChunks {
    static final int LIMIT = 800; // preferred grouping cap, NOT a sentence-splitting limit
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
    /** Each returned chunk is one entire grammatical sentence, even when it
     * exceeds the preferred transport budget or a single word is extremely long.
     * Edge and installed Android TTS use identical immutable source boundaries.
     */
    static ArrayList<Chunk> split(String raw) {
        return EdgeSentenceBoundary.split(raw);
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
