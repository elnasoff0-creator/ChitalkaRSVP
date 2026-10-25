package ru.reader.rsvp;

import java.util.ArrayList;
import java.util.List;

/** Immutable range index; RecyclerView renders one short chunk instead of an entire book. */
public final class BookChunkIndex {
    public static final class Chunk {
        public final int startWord, endWord, startChar, endChar;
        Chunk(int startWord,int endWord,int startChar,int endChar){
            this.startWord=startWord;this.endWord=endWord;this.startChar=startChar;this.endChar=endChar;
        }
    }
    private final Chunk[] chunks;
    private final int[] wordToChunk;
    public BookChunkIndex(String text,ReadingWordMap map,int targetWords){
        if(text==null)text="";
        int count=map.size();
        wordToChunk=new int[count];
        List<Chunk> result=new ArrayList<>();
        int word=0;
        int target=Math.max(80,Math.min(1200,targetWords));
        while(word<count){
            int end=Math.min(count,word+target);
            // Prefer paragraph boundaries nearby, but enforce a hard size cap.
            if(end<count){
                int min=Math.max(word+target/2,end-target/3);
                for(int i=end;i>=min;i--){
                    if(text.substring(map.ends[i-1],map.starts[i]).contains("\n")){
                        end=i;break;
                    }
                }
            }
            if(end<=word)end=Math.min(count,word+target);
            int first=map.starts[word];
            int last=end<count?map.starts[end]:text.length();
            int chunkId=result.size();
            for(int i=word;i<end;i++)wordToChunk[i]=chunkId;
            result.add(new Chunk(word,end,first,last));
            word=end;
        }
        chunks=result.toArray(new Chunk[0]);
    }
    /** Build the same UI blocks from existing word offsets without duplicating
     * every word as a String. The TTS screen already owns these global offsets. */
    public BookChunkIndex(String text,java.util.List<Integer> positions,int targetWords){
        if(text==null)text="";
        int count=positions==null?0:positions.size();
        wordToChunk=new int[count];
        List<Chunk> result=new ArrayList<>();
        int word=0;
        int target=Math.max(80,Math.min(1200,targetWords));
        while(word<count){
            int end=Math.min(count,word+target);
            if(end<count){
                int min=Math.max(word+target/2,end-target/3);
                for(int i=end;i>=min;i--){
                    // A paragraph boundary between two word starts is sufficient.
                    int left=positions.get(i-1),right=positions.get(i);
                    if(text.substring(left,right).indexOf('\n')>=0){
                        end=i;break;
                    }
                }
            }
            if(end<=word)end=Math.min(count,word+target);
            int first=positions.get(word);
            int last=end<count?positions.get(end):text.length();
            int chunkId=result.size();
            for(int i=word;i<end;i++)wordToChunk[i]=chunkId;
            result.add(new Chunk(word,end,first,last));
            word=end;
        }
        chunks=result.toArray(new Chunk[0]);
    }
    public int size(){return chunks.length;}
    public Chunk get(int chunk){return chunks[Math.max(0,Math.min(chunks.length-1,chunk))];}
    public int chunkForWord(int word){return wordToChunk.length==0?0:wordToChunk[Math.max(0,Math.min(wordToChunk.length-1,word))];}
}
