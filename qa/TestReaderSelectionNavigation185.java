package ru.reader.rsvp;

/** Java-only regression suite: deterministic without Android SDK or emulator. */
public final class TestReaderSelectionNavigation185 {
    private static void require(boolean v,String why){if(!v)throw new AssertionError(why);}
    public static void main(String[] args){
        ReaderSelectionRange r=ReaderSelectionRange.of(10,25,100);
        require(r.valid()&&r.start==10&&r.end==25,"initial selection");
        r=r.drag(true,3,100).range;
        require(r.start==3&&r.end==25,"expand selection left across a row");
        ReaderSelectionRange.Move crossed=r.drag(true,35,100);
        require(!crossed.movingStart&&crossed.range.start==25&&crossed.range.end==35,
            "start handle crossing end becomes end handle");
        ReaderSelectionRange.Move backward=crossed.range.drag(false,15,100);
        require(backward.movingStart&&backward.range.start==15&&backward.range.end==25,
            "end handle crossing start becomes start handle");
        require(ReaderSelectionRange.of(20,8,100).start==8,"reversed native selection normalized");
        require(ReaderSelectionRange.of(-10,150,100).start==0&&
            ReaderSelectionRange.of(-10,150,100).end==100,"selection clamped to book");
        // Java UTF-16 range, not a word index; spaces and emojis remain in copied text.
        String source="Одна фраза. \uD83D\uDE00 Вторая фраза!";
        ReaderSelectionRange unicode=ReaderSelectionRange.of(5,source.length(),source.length());
        require(unicode.end==source.length()&&source.substring(unicode.start,unicode.end).contains("\uD83D\uDE00"),
            "UTF-16 selection preserves supplementary characters");

        // Chunk selection must use actual characters, not nearest-word rounding.
        StringBuilder longText=new StringBuilder();
        for(int i=0;i<240;i++)longText.append("слово").append(i).append(i%80==79?'\n':' ');
        String book=longText.toString();
        ReadingWordMap words=ReadingWordMap.parse(book);
        BookChunkIndex chunks=new BookChunkIndex(book,words,80);
        require(chunks.size()>=3,"large book is chunked");
        int boundary=chunks.get(1).startChar;
        require(chunks.chunkForCharacter(boundary-1)==0,"end caret stays in old chunk");
        require(chunks.chunkForCharacter(boundary)==1,"start caret moves to next chunk");
        require(chunks.chunkForCharacter(book.length())==chunks.size()-1,"book end is clamped");

        ReaderSpeechPositionGuard guard=new ReaderSpeechPositionGuard();
        require(guard.accept("clip:100",100,0,100,true)==100,"first clip");
        require(guard.accept("clip:100",100,1800,115,true)==115,"word progression");
        require(guard.withoutTimings("clip:100",100)==115,"empty timing cannot rewind");
        require(guard.accept("clip:100",100,1900,100,true)==115,"stale earlier word rejected");
        require(guard.accept("clip:100",100,2200,118,true)==118,"next sentence accepted");
        require(guard.accept("delayed-old-track",80,0,90,true)==-1,
            "late previous Media3 item cannot roll position backwards");
        guard.seek(220,240);
        require(guard.accept("clip:100",100,2500,119,true)==-1,"old clip rejected during manual seek");
        require(guard.pending(),"seek must still be pending");
        require(guard.accept("clip:220",220,0,220,true)==220,"selected sentence confirmed");
        require(!guard.pending(),"selection confirmation releases guard");
        require(guard.accept("clip:220",220,700,225,true)==225,"new track advances");
        require(guard.accept("clip:220",220,0,222,true)==222,"intentional Media3 rewind allowed");
        guard.seek(300,310);
        require(guard.accept("clip:old",250,5000,275,true)==-1,"elapsed time is never confirmation");
        require(guard.accept("clip:next",310,0,310,true)==310,"ignored target may progress to next track");
        require(guard.accept("clip:next",310,500,311,false)==-1,"paused playback cannot jump");
        System.out.println("PASS: v185 selection geometry, crossing, UTF-16, manual TTS seek and stale callbacks");
    }
}
