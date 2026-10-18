package ru.reader.rsvp;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One immutable word/character index shared by ordinary, RSVP and Cursor reading. */
public final class ReadingWordMap {
    public final String[] words;
    public final int[] starts;
    public final int[] ends;
    private ReadingWordMap(ArrayList<String> w,ArrayList<Integer> a,ArrayList<Integer> b){
        words=w.toArray(new String[0]);starts=new int[a.size()];ends=new int[b.size()];
        for(int i=0;i<a.size();i++){starts[i]=a.get(i);ends[i]=b.get(i);}
    }
    public static ReadingWordMap parse(String content){
        if(content==null)content="";
        ArrayList<String> w=new ArrayList<>();ArrayList<Integer> a=new ArrayList<>(),b=new ArrayList<>();
        Matcher m=Pattern.compile("\\S+").matcher(content);
        while(m.find()){w.add(m.group());a.add(m.start());b.add(m.end());}
        return new ReadingWordMap(w,a,b);
    }
    public int size(){return words.length;}
    public int clamp(int word){return Math.max(0,Math.min(words.length-1,word));}
    /** Nearest word for a tap/caret, including taps in whitespace. */
    public int atCharacter(int offset){
        if(words.length==0)return 0;
        int lo=0,hi=starts.length-1;
        while(lo<=hi){int mid=(lo+hi)>>>1;if(ends[mid]<=offset)lo=mid+1;else hi=mid-1;}
        if(lo>=starts.length)return starts.length-1;
        if(lo>0&&offset<starts[lo]&&offset-ends[lo-1]<starts[lo]-offset)return lo-1;
        return lo;
    }
    public static int portion(int v){return Math.max(1,Math.min(4,v));}
    public static int advance(int index,int amount,int count){return count<=0?0:Math.min(count-1,Math.max(0,index+Math.max(1,amount)));}
    public static long delayMillis(int wpm,int shown){return Math.max(65,(60000L*Math.max(1,shown))/Math.max(100,wpm));}
    public static int orp(int length){return Math.min(Math.max(0,length-1),Math.max(0,(int)Math.floor(length*0.35)));}
}
