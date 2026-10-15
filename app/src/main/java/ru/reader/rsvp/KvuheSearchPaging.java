package ru.reader.rsvp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java search pagination/count helpers; independently testable without Android. */
final class KvuheSearchPaging {
    private KvuheSearchPaging() {}
    private static final Pattern BOOK_COUNT=Pattern.compile("(?:найдено\\s+([\\d\\s\\u00a0]+)\\s+книг[аи]?|книги\\s+([\\d\\s\\u00a0]+))",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern SERIES_COUNT=Pattern.compile("([\\d\\s\\u00a0]+)\\s+цикл(?:а|ов)?",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    static int countBooks(String body){return count(BOOK_COUNT,body);}
    static int countSeries(String body){return count(SERIES_COUNT,body);}
    private static int count(Pattern pattern,String text){
        Matcher m=pattern.matcher(text==null?"":text);if(!m.find())return -1;
        try{return Integer.parseInt((m.group(1)==null?m.group(2):m.group(1)).replaceAll("[^0-9]",""));}
        catch(Exception e){return -1;}
    }
    static int pageNumber(String url){
        Matcher m=Pattern.compile("(?:[?&])page=(\\d+)").matcher(url==null?"":url);
        if(m.find())try{return Integer.parseInt(m.group(1));}catch(Exception ignored){}
        return 1;
    }
    static String following(String url){
        if(url==null||url.isEmpty())return "";
        int target=pageNumber(url)+1;
        if(target>100)return "";
        String stripped=url.replaceAll("\\?page=\\d+&", "?").replaceAll("&page=\\d+", "").replaceAll("\\?page=\\d+$", "");
        return stripped+(stripped.contains("?")?"&":"?")+"page="+target;
    }
}
