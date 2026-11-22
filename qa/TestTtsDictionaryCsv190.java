package ru.reader.rsvp;
import java.util.*;
import java.io.*;
public final class TestTtsDictionaryCsv190 {
    static void yes(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    static TtsDictionaryCsv.Row r(int kind,String pat,String replacement,String ctx,boolean regex,boolean enabled,String note){
        return new TtsDictionaryCsv.Row(kind,pat,replacement,ctx,regex,enabled,note,2);
    }
    public static void main(String[] args)throws Exception{
        List<TtsDictionaryCsv.Row> original=Arrays.asList(
            r(0,"замок","замо\u0301к","дверь",false,true,"с ударением"),
            r(0,"^(.*);(\\d+)$","$1 \"номер\" $2","",true,false,"Regex; кавычки \"\""),
            r(1,"***","","",false,true,"ignore\nnext line"),
            r(0,"=HYPERLINK(\"http://test\")","+24.01.2026","",false,true,"@evil"),
            r(0,"'leading","\t+formula","",false,true,""));
        StringWriter w=new StringWriter();TtsDictionaryCsv.write(w,original);
        String csv=w.toString();
        yes(csv.charAt(0)=='\ufeff',"BOM");
        yes(csv.contains("\"\t=HYPERLINK"),"spreadsheet formula safety");
        List<TtsDictionaryCsv.Row> imported=TtsDictionaryCsv.read(new StringReader(csv));
        yes(imported.size()==original.size(),"rows count");
        for(int i=0;i<original.size();i++)yes(original.get(i).sameValue(imported.get(i)),"lossless UTF-8 Excel roundtrip "+i);
        StringWriter clean=new StringWriter();TtsDictionaryCsv.write(clean,imported);
        yes(clean.toString().equals(csv),"stable CSV export");
        TtsDictionaryImportPlan add=TtsDictionaryImportPlan.build(Collections.emptyList(),imported,TtsDictionaryImportPlan.Mode.MERGE_KEEP);
        yes(add.added==original.size(),"adds all rules");
        TtsDictionaryImportPlan repeat=TtsDictionaryImportPlan.build(imported,imported,TtsDictionaryImportPlan.Mode.MERGE_KEEP);
        yes(!repeat.changed&&repeat.unchanged==original.size(),"duplicate-safe repeated import");
        List<TtsDictionaryCsv.Row> modified=Arrays.asList(r(0,"замок","за\u0301мок","дверь",false,true,"modified"));
        TtsDictionaryImportPlan keep=TtsDictionaryImportPlan.build(imported,modified,TtsDictionaryImportPlan.Mode.MERGE_KEEP);
        TtsDictionaryImportPlan overwrite=TtsDictionaryImportPlan.build(imported,modified,TtsDictionaryImportPlan.Mode.MERGE_OVERWRITE);
        yes(keep.conflicts==1&&!keep.changed,"keep existing conflict");
        yes(overwrite.conflicts==1&&overwrite.updated==1&&overwrite.changed,"overwrite conflict");
        yes(TtsDictionaryImportPlan.build(imported,modified,TtsDictionaryImportPlan.Mode.REPLACE_ALL).deleted==4,"replace removes old");
        try{TtsDictionaryCsv.read(new StringReader("wrong;header\n"));throw new AssertionError("header should fail");}
        catch(TtsDictionaryCsv.CsvException e){yes(e.line==1,"header line error");}
        try{TtsDictionaryCsv.read(new StringReader(TtsDictionaryCsv.HEADER+"\n\"Замена\";\"bad\n"));
            throw new AssertionError("quote should fail");}
        catch(TtsDictionaryCsv.CsvException e){yes(e.line==2,"quote line error");}
        try{TtsDictionaryImportPlan.build(imported,Arrays.asList(modified.get(0),
                r(0,"замок","другой","дверь",false,true,"")),TtsDictionaryImportPlan.Mode.MERGE_KEEP);
            throw new AssertionError("conflicting duplicates should fail");}
        catch(TtsDictionaryCsv.CsvException e){yes(e.line>0,"duplicate error line");}
        System.out.println("PASS: CSV roundtrip, BOM, literal/regex, accent, formulas, notes, import modes, duplicates, error lines");
    }
}
