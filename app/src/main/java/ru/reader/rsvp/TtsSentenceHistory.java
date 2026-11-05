package ru.reader.rsvp;

/** Captures the last confirmed spoken sentence when PAUSE is received.
 * Is owned by a playback service, never by a disposable Activity or a cached MP3. */
final class TtsSentenceHistory {
    private SentenceIndex index=SentenceIndex.fromText("");
    private int current=-1,previous=-1,pausedCurrent=-1,pausedPrevious=-1;
    void bind(SentenceIndex sentences){index=sentences;current=-1;previous=-1;clearPause();}
    void observe(int word){
        int id=index.atWord(word);
        if(id<0)return;
        if(current>=0&&current!=id)previous=current;
        current=id;
    }
    void capturePause(int word){
        observe(word);pausedCurrent=current;
        // A jump may have skipped sentences; the previous *text* sentence wins.
        pausedPrevious=current<0?-1:Math.max(0,current-1);
    }
    int previousOnResume(int fallbackWord){
        int id=pausedPrevious>=0?pausedPrevious:index.atWord(fallbackWord)-1;
        return index.startAt(Math.max(0,id));
    }
    int currentOnResume(int fallbackWord){
        int id=pausedCurrent>=0?pausedCurrent:index.atWord(fallbackWord);
        return index.startAt(Math.max(0,id));
    }
    void clearPause(){pausedCurrent=-1;pausedPrevious=-1;}
    void manuallySelect(int word){current=index.atWord(word);previous=current<0?-1:Math.max(0,current-1);clearPause();}
    int currentSentence(){return current;}
    int previousSentence(){return previous;}
}
