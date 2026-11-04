package ru.reader.rsvp;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Send-only speech normalization. Never mutates book text or original word offsets.
 * A mapping of every output UTF-16 character back to original source spans lets
 * Edge WordBoundary continue to highlight the unmodified book text.
 */
final class EdgeSpeechTextTransform {
    static final String MODE="edge_dot_mode_v108", REPLACEMENT="edge_dot_replace_v108";
    static final String DICTIONARY="edge_pronunciation_dictionary_v108";
    /** Literal lines/symbols to omit from speech; never sent to Edge WebSocket. */
    static final String IGNORED="edge_ignored_text_v118";
    static final String[] MODES={"off","v1","v2","v3"};
    static final String[] SYMBOLS={",",";",":","-","\n\n\n"};
    // Domains/URLs, decimal numbers and common abbreviations cannot be modified.
    private static final Pattern PROTECTED=Pattern.compile(
        "(?iu)(?:https?://|www\\.)[^\\s]+|(?<![\\p{L}\\p{N}_])[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+(?:/[^\\s]*)?|[0-9]+\\.[0-9]+|(?:[\\p{L}]\\.){2,}|\\b(?:г|ул|им|стр|т|д|др|тп|пр)\\.");
    private static final Pattern RULE=Pattern.compile("(?m)^([^=\\r\\n]+)=(.{1,120})$");
    /** An immutable compiled rule; the engine never opens SQLite during playback. */
    static final class Entry {
        final int kind;
        final String key,context,replacement;
        final boolean regex;
        final Pattern compiled;
        Entry(int kind,String key,String context,String replacement,boolean regex){
            if(regex)validateRegexPattern(key);
            this.kind=kind;this.key=key;this.context=context==null?"":context;
            this.replacement=replacement==null?"":replacement;this.regex=regex;
            if(regex){compiled=compileUserRegex(key);}
            else{
                boolean wordStart=key.length()>0 && (Character.isLetterOrDigit(key.charAt(0))||key.charAt(0)=='_');
                boolean wordEnd=key.length()>0 && (Character.isLetterOrDigit(key.charAt(key.length()-1))||key.charAt(key.length()-1)=='_');
                compiled=Pattern.compile("(?iu)"+(wordStart?"(?<![\\p{L}\\p{N}_])":"")+
                    Pattern.quote(key)+(wordEnd?"(?![\\p{L}\\p{N}_])":""));
            }
        }
    }
    static final class Options {
        final String mode,replacement,dictionary,ignored;
        final List<Entry> entries;
        final List<Entry> ignoredEntries,replacementEntries;
        final int revision;
        Options(String mode,String replacement,String dictionary){this(mode,replacement,dictionary,"");}
        Options(String mode,String replacement,String dictionary,String ignored){
            this.ignored=ignored==null?"":ignored.substring(0,Math.min(ignored.length(),8192));
            this.mode=Arrays.asList(MODES).contains(mode)?mode:"off";
            this.replacement=Arrays.asList(SYMBOLS).contains(replacement)?replacement:",";
            this.dictionary=dictionary==null?"":dictionary.substring(0,Math.min(dictionary.length(),8192));
            this.entries=Collections.emptyList();
            this.ignoredEntries=Collections.emptyList();
            this.replacementEntries=Collections.emptyList();
            this.revision=-1;
        }
        Options(String mode,String replacement,List<Entry> entries,int revision){
            this.mode=Arrays.asList(MODES).contains(mode)?mode:"off";
            this.replacement=Arrays.asList(SYMBOLS).contains(replacement)?replacement:",";
            this.dictionary="";this.ignored="";
            this.entries=Collections.unmodifiableList(new ArrayList<>(entries));
            ArrayList<Entry> ignoring=new ArrayList<>(),replacing=new ArrayList<>();
            for(Entry entry:entries)(entry.kind==1?ignoring:replacing).add(entry);
            java.util.Comparator<Entry> priority=(a,b)->{
                int c=Boolean.compare(!a.context.isEmpty(),!b.context.isEmpty());
                if(c!=0)return -c;
                if(a.regex!=b.regex)return a.regex?1:-1;
                return Integer.compare(b.key.length(),a.key.length());
            };
            ignoring.sort(priority);replacing.sort(priority);
            this.ignoredEntries=Collections.unmodifiableList(ignoring);
            this.replacementEntries=Collections.unmodifiableList(replacing);
            this.revision=revision;
        }
        static Options read(SharedPreferences preferences){
            String dictionary=preferences.getString(DICTIONARY,"");
            String ignored=preferences.getString(IGNORED,"");
            // v1.11.117 shipped ***=. as an example symbol replacement.
            // Treat an existing exact rule as ignored immediately, even before
            // the user opens the new dictionary editor to migrate its storage.
            if(dictionary.contains("***=.")){
                StringBuilder cleaned=new StringBuilder();
                for(String row:dictionary.split("\\r?\\n")){
                    if("***=.".equals(row.trim())){
                        if(!java.util.Arrays.asList(ignored.split("\\r?\\n")).contains("***"))
                            ignored=ignored.isEmpty()?"***":ignored+"\n***";
                    }else if(!row.isEmpty()){
                        if(cleaned.length()>0)cleaned.append('\n');cleaned.append(row);
                    }
                }
                dictionary=cleaned.toString();
            }
            return new Options(preferences.getString(MODE,"off"),
                preferences.getString(REPLACEMENT,","),dictionary,ignored);
        }
        Result apply(String original){return EdgeSpeechTextTransform.apply(original,this);}
        String fingerprint(){return revision>=0?mode+"|"+replacement+"|db:"+revision:
            mode+"|"+replacement+"|"+dictionary+"|ignore:"+ignored;}
    }
    static final class Result {
        final String original,spoken;
        final int[] from,to;
        final boolean ignoredAny;
        Result(String original,String spoken,int[] from,int[] to){this(original,spoken,from,to,false);}
        Result(String original,String spoken,int[] from,int[] to,boolean ignoredAny){
            this.original=original;this.spoken=spoken;this.from=from;this.to=to;
            this.ignoredAny=ignoredAny;
        }
        /** Entire segment was intentionally ignored: synthesize no MP3 and open no socket.
         * Punctuation left around removed strings must not cause a request. */
        boolean skipSynthesis(){
            if(!ignoredAny)return false;
            for(int i=0;i<spoken.length();i++)if(Character.isLetterOrDigit(spoken.charAt(i)))return false;
            return true;
        }
        String cacheKey(String voice,String rate){
            // Unmodified speech retains every previously saved MP3 key.
            return EdgeAudioManager.key(original.equals(spoken)?original:original+"\u0000"+spoken,voice,rate);
        }
        java.util.List<EdgeWordTimings.Word> aligned(java.util.List<EdgeWordTimings.Boundary> boundaries){
            java.util.List<EdgeWordTimings.Word> spokenWords=EdgeWordTimings.align(spoken,boundaries);
            ArrayList<EdgeWordTimings.Word> mapped=new ArrayList<>();
            int previousEnd=0;
            for(EdgeWordTimings.Word word:spokenWords){
                if(word.from<0||word.to>from.length||word.from>=word.to)continue;
                int sourceFrom=from[word.from],sourceTo=to[word.to-1];
                if(sourceFrom<previousEnd||sourceTo<=sourceFrom||sourceTo>original.length())continue;
                mapped.add(new EdgeWordTimings.Word(word.startMs,word.durationMs,sourceFrom,sourceTo));
                previousEnd=sourceTo;
            }
            return mapped;
        }
    }
    private static final class Buffer {
        final StringBuilder speech=new StringBuilder();
        final ArrayList<Integer> from=new ArrayList<>(),to=new ArrayList<>();
        void append(String str,int sourceFrom,int sourceTo){
            for(int i=0;i<str.length();i++){
                speech.append(str.charAt(i));from.add(sourceFrom);to.add(sourceTo);
            }
        }
        void copy(String original,int begin,int end){
            for(int i=begin;i<end;i++)append(original.substring(i,i+1),i,i+1);
        }
        Result result(String source){return result(source,false);}
        Result result(String source,boolean ignoredAny){
            int[] a=new int[from.size()],b=new int[to.size()];
            for(int i=0;i<a.length;i++){a[i]=from.get(i);b[i]=to.get(i);}
            return new Result(source,speech.toString(),a,b,ignoredAny);
        }
    }
    private EdgeSpeechTextTransform(){}
    static Result apply(String text,Options options){
        String source=text==null?"":text;
        Buffer original=new Buffer();original.copy(source,0,source.length());
        Result result=original.result(source);
        // Ignore only user-supplied literal sequences. A complete matching
        // segment is silently skipped; inside a real sentence only the matched
        // span disappears, preserving the pronunciation of its remaining words.
        result=ignoreLiterals(result,options.ignored);
        if(!options.entries.isEmpty()){
            result=applyEntries(result,options.ignoredEntries,true);
            if(result.skipSynthesis())return result;
            result=applyEntries(result,options.replacementEntries,false);
        }
        if(result.skipSynthesis())return result;
        if(!options.dictionary.trim().isEmpty()){
            int rules=0;
            java.util.ArrayList<String[]> rulesOrdered=new java.util.ArrayList<>();
            Matcher rows=RULE.matcher(options.dictionary);
            while(rows.find()&&rulesOrdered.size()<100)
                rulesOrdered.add(new String[]{rows.group(1).trim(),rows.group(2).trim()});
            // Specific contextual rules precede generic spelling rules, regardless
            // of the order in which the user entered them in the dictionary.
            rulesOrdered.sort((a,b)->{
                // A contextual rule wins; within equal priority, longer symbol
                // sequences win over shorter ones (*** before *).
                int contextual=Boolean.compare(b[0].contains("|"),a[0].contains("|"));
                if(contextual!=0)return contextual;
                return Integer.compare(b[0].split("\\|",2)[0].length(),a[0].split("\\|",2)[0].length());
            });
            for(String[] entry:rulesOrdered){
                String rule=entry[0],replacement=entry[1];
                int bar=rule.indexOf('|');
                String originalWord=(bar<0?rule:rule.substring(0,bar)).trim();
                String context=bar<0?"":rule.substring(bar+1).trim();
                if(originalWord.length()<1||originalWord.length()>60||replacement.isEmpty())continue;
                String escaped=Pattern.quote(originalWord);
                // Restrict only edges that are letters/digits/underscores.
                // Pure punctuation (***, ---, !!!) must also match beside text.
                boolean beginsWord=Character.isLetterOrDigit(originalWord.charAt(0))||originalWord.charAt(0)=='_';
                char finalChar=originalWord.charAt(originalWord.length()-1);
                boolean endsWord=Character.isLetterOrDigit(finalChar)||finalChar=='_';
                Pattern literal=Pattern.compile("(?iu)"+(beginsWord?"(?<![\\p{L}\\p{N}_])":"")+
                    escaped+(endsWord?"(?![\\p{L}\\p{N}_])":""));
                Matcher occurrences=literal.matcher(result.spoken);
                Buffer updated=new Buffer();int cursor=0,matches=0;
                while(occurrences.find()&&matches++<100){
                    if(!context.isEmpty()){
                        String local=result.spoken.substring(Math.max(0,occurrences.start()-90),
                            Math.min(result.spoken.length(),occurrences.end()+90)).toLowerCase(Locale.ROOT);
                        if(!local.contains(context.toLowerCase(Locale.ROOT)))continue;
                    }
                    // Never reprocess text created by another rule. Otherwise
                    // a second symbol rule can unexpectedly change the first.
                    int sourceStart=result.from[occurrences.start()];
                    int sourceEnd=result.to[occurrences.end()-1];
                    if(sourceStart<0||sourceEnd>source.length()||
                       !source.substring(sourceStart,sourceEnd).equalsIgnoreCase(originalWord))continue;
                    copyRange(result,updated,cursor,occurrences.start());
                    updated.append(replacement,sourceStart,sourceEnd);
                    cursor=occurrences.end();
                }
                copyRange(result,updated,cursor,result.spoken.length());
                result=updated.result(source,result.ignoredAny);rules++;
            }
        }
        if("off".equals(options.mode))return result;
        // Do not touch punctuation belonging to URLs, decimals or abbreviations.
        boolean[] shield=new boolean[result.spoken.length()];
        Matcher excluded=PROTECTED.matcher(result.spoken);
        while(excluded.find())Arrays.fill(shield,excluded.start(),excluded.end(),true);
        Buffer output=new Buffer();
        for(int i=0;i<result.spoken.length();i++){
            char c=result.spoken.charAt(i);
            boolean atLineEnd=i+1==result.spoken.length()||result.spoken.charAt(i+1)=='\n'||result.spoken.charAt(i+1)=='\r';
            boolean whitespaceNext=i+1<result.spoken.length()&&(result.spoken.charAt(i+1)==' '||result.spoken.charAt(i+1)=='\t');
            // An explicit dictionary rule such as ***=. overrides the
            // generic dot-replacement preference. Only source dots qualify.
            boolean sourceDot=result.to[i]==result.from[i]+1 &&
                result.from[i]>=0 && result.from[i]<source.length() &&
                source.charAt(result.from[i])=='.';
            boolean eligible=c=='.'&&sourceDot&&!shield[i]&&
                ("v1".equals(options.mode)||("v2".equals(options.mode)&&!atLineEnd)||
                 ("v3".equals(options.mode)&&whitespaceNext));
            output.append(eligible?options.replacement:String.valueOf(c),result.from[i],result.to[i]);
        }
        return output.result(source,result.ignoredAny);
    }
    /** Android's java.util.regex.Pattern rejects UNICODE_CHARACTER_CLASS, and already
     * uses Unicode character classes by default. Desktop Java needs that flag to
     * interpret \b consistently for Cyrillic words. Detect support once so no
     * unsupported flag is ever passed to user Regex compilation on Android.
     */
    private static final int SUPPORTED_UNICODE_FLAGS=detectSupportedUnicodeFlags();
    private static int detectSupportedUnicodeFlags(){
        try{
            Pattern.compile("",Pattern.UNICODE_CHARACTER_CLASS);
            return Pattern.UNICODE_CHARACTER_CLASS;
        }catch(IllegalArgumentException androidUnsupported){
            return 0;
        }
    }
    static Pattern compileUserRegex(String expression){
        validateRegexPattern(expression);
        return Pattern.compile(expression,SUPPORTED_UNICODE_FLAGS);
    }
    /** Basic guard for obviously explosive nested quantifiers (not a timeout). */
    static void validateRegexPattern(String expression){
        if(expression==null||expression.isEmpty()||expression.length()>400)
            throw new IllegalArgumentException("Regex должен содержать от 1 до 400 символов");
        // (.+)+, (.*)* and similar nested quantifiers can hang Edge playback.
        if(Pattern.compile("\\([^)]*[+*][^)]*\\)[+*{]").matcher(expression).find()||
            expression.contains(".*.*")||expression.contains(".+.+"))
            throw new IllegalArgumentException("Слишком тяжёлое Regex: вложенные повторения");
    }
    /** Validate Java replacement syntax without requiring the expression to match a sample. */
    static void validateReplacement(Pattern compiled,String value){
        int groups=compiled.matcher("").groupCount();
        for(int i=0;i<value.length();i++){
            char ch=value.charAt(i);
            if(ch=='\\'){if(++i>=value.length())throw new IllegalArgumentException("Незавершённое экранирование в замене");continue;}
            if(ch!='$')continue;
            if(++i>=value.length())throw new IllegalArgumentException("После $ нужна группа");
            if(value.charAt(i)=='{'){
                int end=value.indexOf('}',i+1);
                if(end<0||end==i+1)throw new IllegalArgumentException("Неверное имя группы");
                i=end;continue;
            }
            if(!Character.isDigit(value.charAt(i)))throw new IllegalArgumentException("После $ нужен номер группы");
            int n=value.charAt(i)-'0';
            while(i+1<value.length()&&Character.isDigit(value.charAt(i+1))&&
                n*10+value.charAt(i+1)-'0'<=groups){n=n*10+value.charAt(++i)-'0';}
            if(n>groups)throw new IllegalArgumentException("Группа $"+n+" отсутствует");
        }
    }
    /** Expand Java-style $1 and ${name}. Each captured token maps to its own original span.
     * Literal text maps to the surrounding part of the match; this keeps a typical
     * 'гл. 12' -> 'глава 12' aligned with the original number for WordBoundary. */
    private static void appendExpanded(Buffer out,Result previous,Matcher match,String template){
        int mFrom=match.start(),mTo=match.end();
        int prevSource=previous.from[mFrom];
        int lastSource=previous.to[mTo-1];
        int lastGroupSource=prevSource;
        StringBuilder literal=new StringBuilder();
        for(int i=0;i<template.length();i++){
            char c=template.charAt(i);
            if(c=='\\'&&i+1<template.length()){literal.append(template.charAt(++i));continue;}
            if(c=='$'&&i+1<template.length()){
                int begin=i+1,end=begin;String content;
                if(template.charAt(begin)=='{'){
                    end=template.indexOf('}',begin+1);
                    if(end<0)throw new IllegalArgumentException("Bad named group");
                    content=template.substring(begin+1,end);
                }else{
                    if(!Character.isDigit(template.charAt(begin)))throw new IllegalArgumentException("Bad group");
                    int max=match.groupCount(),number=template.charAt(begin)-'0';
                    while(end+1<template.length()&&Character.isDigit(template.charAt(end+1))&&
                        number*10+template.charAt(end+1)-'0'<=max)
                        number=number*10+template.charAt(++end)-'0';
                    content=String.valueOf(number);
                }
                int groupBegin,groupEnd;String value;
                if(template.charAt(begin)=='{'){
                    value=match.group(content);groupBegin=match.start(content);groupEnd=match.end(content);
                }else{
                    int group=Integer.parseInt(content);
                    value=match.group(group);groupBegin=match.start(group);groupEnd=match.end(group);
                }
                if(groupBegin<0){i=end;continue;}
                int sourceStart=previous.from[groupBegin],sourceEnd=previous.to[groupEnd-1];
                if(literal.length()>0){
                    out.append(literal.toString(),prevSource,Math.max(prevSource+1,sourceStart));
                    literal.setLength(0);
                }
                if(value!=null&&!value.isEmpty())out.append(value,sourceStart,sourceEnd);
                prevSource=sourceEnd;lastGroupSource=sourceEnd;i=end;continue;
            }
            literal.append(c);
        }
        if(literal.length()>0)out.append(literal.toString(),
            Math.min(prevSource,lastSource-1),lastSource);
    }
    /** Keep context rules first and long exact symbols before short symbols. Regex rules
     * are already compiled once per revision; avoid regex evaluation for absent literals. */
    private static Result applyEntries(Result initial,List<Entry> entries,boolean ignored){
        if(entries.isEmpty())return initial;
        Result result=initial;
        String lowered=result.spoken.toLowerCase(Locale.ROOT);
        for(Entry entry:entries){
            if(result.spoken.isEmpty())break;
            if(!entry.regex&&!lowered.contains(entry.key.toLowerCase(Locale.ROOT)))continue;
            Matcher matcher=entry.compiled.matcher(result.spoken);
            Buffer output=new Buffer();int cursor=0,matches=0;boolean changed=false;
            while(matcher.find() && matches++<250){
                int start=matcher.start(),end=matcher.end();
                if(start==end)continue; // An insertion at zero width has no source span.
                if(!entry.context.isEmpty()){
                    String local=result.spoken.substring(Math.max(0,start-90),
                        Math.min(result.spoken.length(),end+90)).toLowerCase(Locale.ROOT);
                    if(!local.contains(entry.context.toLowerCase(Locale.ROOT)))continue;
                }
                int from=result.from[start],to=result.to[end-1];
                if(from<0||to>result.original.length()||from>=to)continue;
                // Never mutate a previous rule's output twice; offset mapping remains stable.
                if(!result.spoken.substring(start,end).equals(result.original.substring(from,to)))continue;
                try{
                    Buffer piece=new Buffer();
                    if(!ignored&&!entry.replacement.isEmpty()){
                        if(entry.regex)appendExpanded(piece,result,matcher,entry.replacement);
                        else piece.append(entry.replacement,from,to);
                    }
                    // Never let a single rule inflate an audio fragment without bound.
                    int maxOutput=Math.max(4000,initial.spoken.length()*4);
                    if(output.speech.length()+piece.speech.length()+
                        (result.spoken.length()-end)>maxOutput)continue;
                    copyRange(result,output,cursor,start);
                    if(ignored||entry.replacement.isEmpty()){
                        boolean between=start>0&&end<result.spoken.length()&&
                            Character.isLetterOrDigit(result.spoken.charAt(start-1))&&
                            Character.isLetterOrDigit(result.spoken.charAt(end));
                        if(between)output.append(" ",from,to);
                    }else for(int j=0;j<piece.speech.length();j++)
                        output.append(piece.speech.substring(j,j+1),piece.from.get(j),piece.to.get(j));
                    cursor=end;changed=true;
                }catch(IllegalArgumentException invalid){
                    // Malformed group reference: skip this match, never fail the audio job.
                }
            }
            if(changed){
                copyRange(result,output,cursor,result.spoken.length());
                result=output.result(initial.original,initial.ignoredAny||ignored||entry.replacement.isEmpty());
                lowered=result.spoken.toLowerCase(Locale.ROOT);
            }
        }
        return result;
    }
    /** Literal (not regex) ignore rules; longest patterns take precedence. */
    private static Result ignoreLiterals(Result input,String patterns){
        if(patterns==null||patterns.isEmpty()||input.spoken.isEmpty())return input;
        ArrayList<String> items=new ArrayList<>();
        for(String row:patterns.split("\\r?\\n")){
            String key=row.trim();
            if(!key.isEmpty()&&key.length()<=400&&items.size()<100&&!items.contains(key))items.add(key);
        }
        if(items.isEmpty())return input;
        items.sort((a,b)->Integer.compare(b.length(),a.length()));
        boolean[] omitted=new boolean[input.spoken.length()];boolean found=false;
        for(String item:items){
            boolean beginsWord=Character.isLetterOrDigit(item.charAt(0))||item.charAt(0)=='_';
            char end=item.charAt(item.length()-1);
            boolean endsWord=Character.isLetterOrDigit(end)||end=='_';
            Pattern literal=Pattern.compile("(?iu)"+(beginsWord?"(?<![\\p{L}\\p{N}_])":"")+
                Pattern.quote(item)+(endsWord?"(?![\\p{L}\\p{N}_])":""));
            Matcher m=literal.matcher(input.spoken);
            while(m.find()){
                boolean free=true;
                for(int i=m.start();i<m.end();i++)if(omitted[i]){free=false;break;}
                if(!free)continue;
                Arrays.fill(omitted,m.start(),m.end(),true);found=true;
            }
        }
        if(!found)return input;
        Buffer filtered=new Buffer();
        int i=0;
        while(i<input.spoken.length()){
            if(!omitted[i]){copyRange(input,filtered,i,i+1);i++;continue;}
            int start=i;while(i<input.spoken.length()&&omitted[i])i++;
            // Avoid joining two spoken words: e.g. word***word -> word word.
            boolean betweenWords=start>0&&i<input.spoken.length() &&
                Character.isLetterOrDigit(input.spoken.charAt(start-1))&&
                Character.isLetterOrDigit(input.spoken.charAt(i));
            if(betweenWords)filtered.append(" ",input.from[start],input.to[i-1]);
        }
        return filtered.result(input.original,true);
    }
    private static void copyRange(Result input,Buffer target,int begin,int end){
        for(int i=begin;i<end;i++)target.append(input.spoken.substring(i,i+1),input.from[i],input.to[i]);
    }
}
