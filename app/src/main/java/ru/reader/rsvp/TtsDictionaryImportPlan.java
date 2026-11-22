package ru.reader.rsvp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Side-effect-free plan. Preview and commit both use exactly the same validation. */
final class TtsDictionaryImportPlan {
    enum Mode {MERGE_KEEP,MERGE_OVERWRITE,REPLACE_ALL}
    final List<TtsDictionaryCsv.Row> result;
    final int added,unchanged,conflicts,updated,deleted;
    final boolean changed;
    private TtsDictionaryImportPlan(List<TtsDictionaryCsv.Row> result,int added,int unchanged,
        int conflicts,int updated,int deleted,boolean changed){
        this.result=result;this.added=added;this.unchanged=unchanged;
        this.conflicts=conflicts;this.updated=updated;this.deleted=deleted;this.changed=changed;
    }
    static TtsDictionaryImportPlan build(List<TtsDictionaryCsv.Row> current,
          List<TtsDictionaryCsv.Row> input,Mode mode)throws TtsDictionaryCsv.CsvException {
        LinkedHashMap<String,TtsDictionaryCsv.Row> seen=new LinkedHashMap<>();
        for(TtsDictionaryCsv.Row item:input){
            String key=item.identity();TtsDictionaryCsv.Row last=seen.putIfAbsent(key,item);
            if(last!=null){
                if(!item.sameValue(last))throw new TtsDictionaryCsv.CsvException(item.line,
                    "Конфликт двух строк CSV для одного правила (первая — строка "+last.line+")");
            }
        }
        LinkedHashMap<String,TtsDictionaryCsv.Row> existing=new LinkedHashMap<>();
        LinkedHashMap<String,Integer> existingAt=new LinkedHashMap<>();
        for(int i=0;i<current.size();i++){
            TtsDictionaryCsv.Row item=current.get(i);
            existing.putIfAbsent(item.identity(),item);
            existingAt.putIfAbsent(item.identity(),i);
        }
        // Retain historical duplicate database rows when merging: importing a
        // CSV must never silently delete data from the existing dictionary.
        ArrayList<TtsDictionaryCsv.Row> output=new ArrayList<>();
        if(mode!=Mode.REPLACE_ALL)output.addAll(current);
        int added=0,unchanged=0,conflicts=0,updated=0;
        for(Map.Entry<String,TtsDictionaryCsv.Row> e:seen.entrySet()){
            TtsDictionaryCsv.Row old=existing.get(e.getKey()),incoming=e.getValue();
            if(old==null){output.add(incoming);added++;}
            else if(old.sameValue(incoming)){unchanged++;if(mode==Mode.REPLACE_ALL)output.add(incoming);}
            else{
                conflicts++;
                if(mode==Mode.REPLACE_ALL)output.add(incoming);
                else if(mode==Mode.MERGE_OVERWRITE){updated++;output.set(existingAt.get(e.getKey()),incoming);}
                else if(mode==Mode.MERGE_KEEP){/* leave original row, including duplicates */}
                if(mode==Mode.REPLACE_ALL)updated++;
            }
        }
        int deleted=0;
        if(mode==Mode.REPLACE_ALL)for(TtsDictionaryCsv.Row item:current)
            if(!seen.containsKey(item.identity()))deleted++;
        int regex=0,literalReplace=0,literalIgnore=0;
        for(TtsDictionaryCsv.Row r:output){
            if(r.regex)regex++;
            else if(r.kind==0)literalReplace++;
            else literalIgnore++;
        }
        if(regex>1000||literalReplace>10000||literalIgnore>5000)
            throw new TtsDictionaryCsv.CsvException(1,"Превышены лимиты: Regex 1000, замены 10000, игнорирование 5000");
        List<TtsDictionaryCsv.Row> result=output;
        boolean changed=!sameOrdered(current,result);
        return new TtsDictionaryImportPlan(result,added,unchanged,conflicts,updated,deleted,changed);
    }
    static boolean sameOrdered(List<TtsDictionaryCsv.Row> a,List<TtsDictionaryCsv.Row> b){
        if(a.size()!=b.size())return false;
        for(int i=0;i<a.size();i++)if(!a.get(i).sameValue(b.get(i)))return false;
        return true;
    }
}
