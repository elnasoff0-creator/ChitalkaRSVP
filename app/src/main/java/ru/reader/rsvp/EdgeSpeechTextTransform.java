package ru.reader.rsvp;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Send-only speech normalization. Never mutates book text or original word offsets.
 * A mapping of every output UTF-16 character back to original source spans lets
 * Edge WordBoundary continue to highlight the unmodified book text.
 */
final class EdgeSpeechTextTransform {
    static final String MODE="edge_dot_mode_v108", REPLACEMENT="edge_dot_replace_v108";
    static final String DICTIONARY="edge_pronunciation_dictionary_v108";
    static final String[] MODES={"off","v1","v2","v3"};
    static final String[] SYMBOLS={",",";",":","-","\n\n\n"};
    // Domains/URLs, decimal numbers and common abbreviations cannot be modified.
    private static final Pattern PROTECTED=Pattern.compile(
        "(?iu)(?:https?://|www\\.)[^\\s]+|(?<![\\p{L}\\p{N}_])[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+(?:/[^\\s]*)?|[0-9]+\\.[0-9]+|(?:[\\p{L}]\\.){2,}|\\b(?:г|ул|им|стр|т|д|др|тп|пр)\\.");
    private static final Pattern RULE=Pattern.compile("(?m)^([^=\\r\\n]+)=(.{1,120})$");
    static final class Options {
        final String mode,replacement,dictionary;
        Options(String mode,String replacement,String dictionary){
            this.mode=Arrays.asList(MODES).contains(mode)?mode:"off";
            this.replacement=Arrays.asList(SYMBOLS).contains(replacement)?replacement:",";
            this.dictionary=dictionary==null?"":dictionary.substring(0,Math.min(dictionary.length(),8192));
        }
        static Options read(SharedPreferences preferences){return new Options(
            preferences.getString(MODE,"off"), preferences.getString(REPLACEMENT,","),
            preferences.getString(DICTIONARY,""));}
        Result apply(String original){return EdgeSpeechTextTransform.apply(original,this);}
        String fingerprint(){return mode+"|"+replacement+"|"+dictionary;}
    }
    static final class Result {
        final String original,spoken;
        final int[] from,to;
        Result(String original,String spoken,int[] from,int[] to){
            this.original=original;this.spoken=spoken;this.from=from;this.to=to;
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
        Result result(String source){
            int[] a=new int[from.size()],b=new int[to.size()];
            for(int i=0;i<a.length;i++){a[i]=from.get(i);b[i]=to.get(i);}
            return new Result(source,speech.toString(),a,b);
        }
    }
    private EdgeSpeechTextTransform(){}
    static Result apply(String text,Options options){
        String source=text==null?"":text;
        Buffer original=new Buffer();original.copy(source,0,source.length());
        Result result=original.result(source);
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
                result=updated.result(source);rules++;
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
        return output.result(source);
    }
    private static void copyRange(Result input,Buffer target,int begin,int end){
        for(int i=begin;i<end;i++)target.append(input.spoken.substring(i,i+1),input.from[i],input.to[i]);
    }
}
