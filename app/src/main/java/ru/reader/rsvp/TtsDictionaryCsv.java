package ru.reader.rsvp;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Stable, human-editable UTF-8 CSV dictionary format. No Android dependencies.
 * Semicolon columns keep Russian Excel's default import practical. All columns
 * are text; embedded delimiters, double-quotes and newlines are RFC-4180 escaped.
 * No JSON file is ever emitted.
 */
final class TtsDictionaryCsv {
    static final String HEADER="Тип;Шаблон;Произношение;Контекст;Regex;Включено;Комментарий";
    static final int MAX_ROWS=17000,MAX_CHARS=32_000_000;
    static final class Row {
        final int kind,line;
        final String pattern,replacement,context,note;
        final boolean regex,enabled;
        Row(int kind,String pattern,String replacement,String context,
            boolean regex,boolean enabled,String note,int line){
            this.kind=kind;this.pattern=pattern;this.replacement=replacement;
            this.context=context;this.regex=regex;this.enabled=enabled;
            this.note=note;this.line=line;
        }
        String identity(){return kind+"|"+(regex?"R":"L")+"|"+
            (regex?pattern:pattern.toLowerCase(Locale.ROOT))+"|"+context.toLowerCase(Locale.ROOT);}
        boolean sameValue(Row that){return that!=null &&
            replacement.equals(that.replacement)&&enabled==that.enabled&&
            note.equals(that.note)&&pattern.equals(that.pattern)&&context.equals(that.context)&&
            kind==that.kind&&regex==that.regex;}
    }
    static final class CsvException extends Exception {
        final int line;
        CsvException(int line,String description){super("Строка "+line+": "+description);this.line=line;}
    }
    private TtsDictionaryCsv(){}
    /** Exporter adds UTF-8 BOM so Excel detects Cyrillic without manual setup. */
    static void write(Writer output,List<Row> rows)throws IOException {
        output.write('\ufeff');output.write(HEADER);output.write("\r\n");
        for(Row row:rows){
            String[] columns={row.kind==0?"Замена":"Игнорировать",row.pattern,row.replacement,
                row.context,row.regex?"Да":"Нет",row.enabled?"Да":"Нет",row.note};
            for(int i=0;i<columns.length;i++){
                if(i>0)output.write(';');
                output.write('"');String value=protectSpreadsheet(columns[i]);
                for(int j=0;j<value.length();j++){
                    char ch=value.charAt(j);if(ch=='"')output.write('"');output.write(ch);
                }
                output.write('"');
            }
            output.write("\r\n");
        }
        output.flush();
    }
    /** Preserve formula-looking cells EXACTLY on round-trip, but prevent CSV formula execution.
     * Extra tab is removed only for a recognizable escaped leading character.
     * Excel users must still import all seven columns as text to avoid date coercion.
     */
    private static String protectSpreadsheet(String s){
        if(s.isEmpty())return s;
        return dangerousStart(s.charAt(0))?"\t"+s:s;
    }
    private static boolean dangerousStart(char c){return c=='='||c=='+'||c=='-'||c=='@'||c=='\t'||c=='\r'||c=='\n';}
    private static String restoreSpreadsheet(String s){
        return s.length()>1&&s.charAt(0)=='\t'&&dangerousStart(s.charAt(1))?s.substring(1):s;
    }
    static List<Row> read(Reader input)throws IOException,CsvException {
        List<String[]> records=new ArrayList<>();
        List<Integer> locations=new ArrayList<>();
        PushbackReader reader=new PushbackReader(input,2);
        StringBuilder value=new StringBuilder();ArrayList<String> fields=new ArrayList<>();
        final int START=0,RAW=1,QUOTED=2,AFTER=3;
        int mode=START,physicalLine=1,rowStart=1,count=0;
        boolean any=false;
        for(int n;(n=reader.read())!=-1;){
            if(++count>MAX_CHARS)throw new CsvException(physicalLine,"Файл превышает 32 млн символов");
            char ch=(char)n;
            if(ch=='\ufeff'&&count==1)continue;
            if(mode==QUOTED){
                if(ch=='"'){mode=AFTER;continue;}
                if(ch=='\r'){
                    int next=reader.read();if(next!='\n'&&next!=-1)reader.unread(next);
                    ch='\n';physicalLine++;
                }else if(ch=='\n')physicalLine++;
                value.append(ch);if(value.length()>16000)throw new CsvException(rowStart,"Слишком длинное значение");
                continue;
            }
            if(mode==AFTER&&ch=='"'){value.append('"');mode=QUOTED;continue;}
            if(ch==';'){
                if(mode==AFTER||mode==START||mode==RAW){fields.add(value.toString());value.setLength(0);mode=START;any=true;continue;}
            }
            if(ch=='\r'||ch=='\n'){
                if(ch=='\r'){
                    int next=reader.read();if(next!='\n'&&next!=-1)reader.unread(next);
                }
                if(any||value.length()>0||!fields.isEmpty()){
                    fields.add(value.toString());records.add(fields.toArray(new String[0]));
                    locations.add(rowStart);
                    if(records.size()>MAX_ROWS+1)throw new CsvException(rowStart,"Превышен лимит строк");
                }
                fields=new ArrayList<>();value.setLength(0);mode=START;any=false;
                physicalLine++;rowStart=physicalLine;continue;
            }
            if(mode==AFTER)throw new CsvException(physicalLine,"После закрывающей кавычки ожидается ; или конец строки");
            if(ch=='"'){
                if(mode==START){mode=QUOTED;any=true;continue;}
                throw new CsvException(physicalLine,"Кавычки внутри значения нужно удваивать");
            }
            mode=RAW;value.append(ch);any=true;
            if(value.length()>16000)throw new CsvException(rowStart,"Слишком длинное значение");
        }
        if(mode==QUOTED)throw new CsvException(rowStart,"Не закрыта кавычка");
        if(any||value.length()>0||!fields.isEmpty()){
            fields.add(value.toString());records.add(fields.toArray(new String[0]));locations.add(rowStart);
        }
        if(records.isEmpty())throw new CsvException(1,"Пустой CSV");
        String[] header=records.get(0);
        String[] expected=HEADER.split(";",-1);
        if(header.length!=expected.length)throw new CsvException(1,"Ожидаются семь колонок: "+HEADER);
        for(int c=0;c<expected.length;c++){
            if(!expected[c].equals(header[c].trim()))
                throw new CsvException(1,"Неверная колонка "+(c+1)+": "+expected[c]);
        }
        ArrayList<Row> rows=new ArrayList<>();
        for(int i=1;i<records.size();i++){
            String[] cols=records.get(i);int line=locations.get(i);
            if(cols.length!=7)throw new CsvException(line,"Нужно семь колонок (получено "+cols.length+")");
            for(int c=0;c<cols.length;c++)cols[c]=restoreSpreadsheet(cols[c]);
            String action=cols[0].trim().toLowerCase(Locale.ROOT);
            int kind;
            if(action.equals("замена")||action.equals("replace"))kind=0;
            else if(action.equals("игнорировать")||action.equals("ignore"))kind=1;
            else throw new CsvException(line,"Тип: Замена или Игнорировать");
            boolean regex=booleanValue(cols[4],"Regex",line);
            boolean enabled=booleanValue(cols[5],"Включено",line);
            String pattern=cols[1],replacement=cols[2],context=cols[3],note=cols[6];
            if(pattern.trim().isEmpty()||pattern.length()>400)
                throw new CsvException(line,"Шаблон должен содержать от 1 до 400 символов");
            if(replacement.length()>1000||context.length()>240||note.length()>500)
                throw new CsvException(line,"Превышена длина замены, контекста или комментария");
            if((regex||kind==1)&&!context.isEmpty())
                throw new CsvException(line,"Контекст доступен только для обычной замены");
            if(kind==1&&!replacement.isEmpty())
                throw new CsvException(line,"У игнорирования поле Произношение должно быть пустым");
            rows.add(new Row(kind,pattern,replacement,context,regex,enabled,note,line));
        }
        return rows;
    }
    private static boolean booleanValue(String value,String label,int line)throws CsvException {
        switch(value.trim().toLowerCase(Locale.ROOT)){
            case "да":case "true":case "1":return true;
            case "нет":case "false":case "0":return false;
            default:throw new CsvException(line,label+": допускается Да или Нет");
        }
    }
}
