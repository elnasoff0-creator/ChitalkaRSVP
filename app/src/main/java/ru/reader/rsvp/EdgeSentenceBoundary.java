package ru.reader.rsvp;

import java.util.ArrayList;
import java.util.Locale;

/** Complete natural sentences for Edge WebSocket synthesis. Unlike the Android
 * TTS transport chunks, these boundaries NEVER use a character or word cap.
 * Long sentences are kept intact; EdgeSpeechEngine reports any actual service
 * byte-limit failure rather than inserting unnatural mid-sentence pauses.
 */
final class EdgeSentenceBoundary {
    private EdgeSentenceBoundary(){}
    private static boolean mark(char ch){return ch=='.'||ch=='!'||ch=='?'||ch=='…';}
    private static boolean closing(char ch){return ch=='»'||ch=='”'||ch=='"'||ch=='\''||ch==')'||ch==']'||ch=='’';}
    private static boolean abbreviation(String text,int position){
        if(text.charAt(position)!='.')return false;
        int start=position;
        while(start>0&&!Character.isWhitespace(text.charAt(start-1)))start--;
        String word=text.substring(start,position+1).toLowerCase(Locale.ROOT)
            .replaceAll("^[\\(\\[\\{«\"]+","");
        return word.matches("(?:т|д|п|е|г|ул|им|рис|стр|др|пр|см|ст|вв|гг|mr|mrs|ms|dr|prof)\\.")
            ||word.matches("[а-яёa-z]\\.");
    }
    private static void add(ArrayList<SentenceChunks.Chunk> out,String text,
            ArrayList<Integer> words,int from,int to){
        while(from<to&&Character.isWhitespace(text.charAt(from)))from++;
        while(to>from&&Character.isWhitespace(text.charAt(to-1)))to--;
        int first=SentenceChunks.lowerBound(words,from);
        int last=SentenceChunks.lowerBound(words,to);
        if(first<last)out.add(new SentenceChunks.Chunk(from,to,first,last,text.substring(from,to)));
    }
    static ArrayList<SentenceChunks.Chunk> split(String input){
        String text=input==null?"":input;
        ArrayList<SentenceChunks.Chunk> result=new ArrayList<>();
        ArrayList<Integer> words=SentenceChunks.wordPositions(text);
        int start=0,n=text.length();
        for(int i=0;i<n;i++){
            char ch=text.charAt(i);
            if(!mark(ch))continue;
            if(ch=='.'&&i>0&&i+1<n&&Character.isDigit(text.charAt(i-1))
                    &&Character.isDigit(text.charAt(i+1)))continue;
            int after=i+1;
            while(after<n&&mark(text.charAt(after)))after++;
            while(after<n&&closing(text.charAt(after)))after++;
            if(after<n&&!Character.isWhitespace(text.charAt(after)))continue;
            if(ch=='.'&&abbreviation(text,i))continue;
            add(result,text,words,start,after);
            while(after<n&&Character.isWhitespace(text.charAt(after)))after++;
            start=after;
            i=after-1;
        }
        add(result,text,words,start,n);
        return result;
    }
}
