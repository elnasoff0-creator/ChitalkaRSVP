package ru.reader.rsvp;

import java.util.ArrayList;
import java.util.List;

/** A bounded *candidate* window. Candidates are NOT WebSocket connections;
 * the reader checks the persistent cache before scheduling any network work.
 * Quickify-inspired thresholds, not a claim of identical obfuscated branches.
 */
final class EdgePrefetchPlanner {
    static final int MAX_CANDIDATES=6;
    private EdgePrefetchPlanner(){}
    static int budget(int currentChars,boolean idle){
        if(idle)return currentChars<120?100:currentChars<200?150:200;
        if(currentChars<200)return 400;
        if(currentChars<350)return 600;
        if(currentChars<500)return 800;
        if(currentChars<650)return 1000;
        return 1200;
    }
    static int countLimit(int budget){
        if(budget<=150)return 2;
        if(budget<=400)return 3;
        if(budget<=600)return 6;
        if(budget<=800)return 8;
        if(budget<=1000)return 10;
        return 12;
    }
    /** Return indexes from first candidate onward, constrained by both text
     * budget and count. Always allow the first (even if it exceeds the budget). */
    static List<Integer> select(List<Integer> lengths,int budget,int countLimit){
        ArrayList<Integer> chosen=new ArrayList<>();int total=0;
        if(lengths==null)return chosen;
        int max=Math.min(MAX_CANDIDATES,Math.max(0,countLimit));
        for(int i=0;i<lengths.size()&&chosen.size()<max;i++){
            int len=Math.max(0,lengths.get(i));
            if(len==0)continue;
            if(!chosen.isEmpty()&&total+len>budget)break;
            total+=len;chosen.add(i);
        }
        return chosen;
    }
}
