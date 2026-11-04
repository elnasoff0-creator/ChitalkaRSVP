package ru.reader.rsvp;

import java.util.ArrayList;
import java.util.List;

/** Stable offsets for TTS sentence navigation, independent of audio file boundaries.
 * Very long source sentences follow the 800-character logical-block fallback. */
final class SentenceIndex {
    private final ArrayList<SentenceChunks.Chunk> sentences;
    private SentenceIndex(ArrayList<SentenceChunks.Chunk> parts){sentences=parts;}
    static SentenceIndex fromChunks(List<SentenceChunks.Chunk> source){return new SentenceIndex(new ArrayList<>(source));}
    static SentenceIndex fromText(String text){return fromChunks(SentenceChunks.split(text));}
    int size(){return sentences.size();}
    int atWord(int word){
        int low=0,high=sentences.size()-1;
        while(low<=high){int middle=(low+high)>>>1;SentenceChunks.Chunk s=sentences.get(middle);
            if(word<s.fromWord)high=middle-1;
            else if(word>=s.toWord)low=middle+1;
            else return middle;
        }
        return Math.max(0,Math.min(sentences.size()-1,low));
    }
    int beginning(int word){return sentences.isEmpty()?Math.max(0,word):sentences.get(atWord(word)).fromWord;}
    int previous(int word){return sentences.isEmpty()?0:sentences.get(Math.max(0,atWord(word)-1)).fromWord;}
    int next(int word){return sentences.isEmpty()?0:sentences.get(Math.min(sentences.size()-1,atWord(word)+1)).fromWord;}
}
