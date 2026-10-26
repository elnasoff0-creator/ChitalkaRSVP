package ru.reader.rsvp;

import java.util.List;

/** Locate an exact first spoken word at/after a manually selected sentence.
 * Returns -1 when WordBoundary metadata cannot safely identify that sentence.
 * Calling code then generates a new clip starting exactly at the selection.
 */
final class EdgeSentenceSeek {
    private EdgeSentenceSeek() {}
    static long at(int relativeChar,List<EdgeWordTimings.Word> words) {
        if(relativeChar<=0)return 0L;
        if(words==null||words.isEmpty())return -1L;
        for(EdgeWordTimings.Word word:words){
            if(word.from<=relativeChar && relativeChar<word.to)return word.startMs;
            // Skip punctuation/whitespace preceding the selected word, but
            // never jump dozens of characters into another sentence.
            if(word.from>relativeChar){
                return word.from-relativeChar<=12?word.startMs:-1L;
            }
        }
        return -1L;
    }
}
