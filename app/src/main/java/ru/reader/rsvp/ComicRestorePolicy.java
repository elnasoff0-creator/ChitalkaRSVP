package ru.reader.rsvp;

import java.util.List;
/** Stable, pure resolution of persisted comic positions across chapter reorderings. */
final class ComicRestorePolicy {
    private ComicRestorePolicy(){}
    static int resolveChapter(String stableId, List<String> currentIds, int legacyIndex){
        if(currentIds==null||currentIds.isEmpty())return -1;
        if(stableId!=null&&!stableId.isEmpty()){
            for(int i=0;i<currentIds.size();i++)if(stableId.equals(currentIds.get(i)))return i;
        }
        return Math.max(0,Math.min(legacyIndex,currentIds.size()-1));
    }
    static double clampFraction(double value){
        return Double.isFinite(value)?Math.max(0.0,Math.min(1.0,value)):-1.0;
    }
}
