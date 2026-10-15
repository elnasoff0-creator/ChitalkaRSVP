package ru.reader.rsvp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One segmentation algorithm for the playback cache and full-book synthesis. */
final class EdgeTextSegments {
    static final class Part {
        final int start,end;
        final String text;
        Part(int start,int end,String text){this.start=start;this.end=end;this.text=text;}
    }
    private EdgeTextSegments() {}
    static ArrayList<Integer> positions(String raw){
        ArrayList<Integer> result=new ArrayList<>();
        Matcher m=Pattern.compile("\\S+").matcher(raw);
        while(m.find())result.add(m.start());
        return result;
    }
    static Part at(String raw,ArrayList<Integer> positions,int start,int characters){
        if(start<0||start>=positions.size())return null;
        int begin=positions.get(start),end=Math.min(raw.length(),begin+characters);
        if(end<raw.length()){
            int sentence=-1;
            for(char ch:new char[]{'.','!','?','\n'})sentence=Math.max(sentence,raw.lastIndexOf(ch,end-1));
            if(sentence>=begin+characters/2)end=sentence+1;
            else {int space=raw.lastIndexOf(' ',end-1);if(space>begin+characters/2)end=space+1;}
        }
        int after=Collections.binarySearch(positions,end);
        if(after<0)after=-after-1;
        after=Math.max(start+1,Math.min(positions.size(),after));
        String text=raw.substring(begin,end).trim();
        return text.isEmpty()?null:new Part(start,after,text);
    }
    static ArrayList<Part> all(String raw,int characters){
        ArrayList<Integer> positions=positions(raw);
        ArrayList<Part> parts=new ArrayList<>();
        for(int i=0;i<positions.size();){
            Part part=at(raw,positions,i,characters);
            if(part==null||part.end<=i)break;
            parts.add(part);i=part.end;
        }
        return parts;
    }
}
