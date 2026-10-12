package ru.reader.rsvp;

import android.net.Uri;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.TextNode;
import org.json.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Parses only explicit, unambiguous, publicly exposed audio URLs. Never bypasses access controls. */
public final class KvuheParser {
    public static final class Chapter {
        public String name="",time="",url=""; public long seconds;
        JSONObject json() throws JSONException {JSONObject o=new JSONObject();o.put("name",name);o.put("time",time);o.put("seconds",seconds);o.put("url",url);return o;}
        static Chapter parse(JSONObject o){Chapter c=new Chapter();c.name=o.optString("name");c.time=o.optString("time");c.url=o.optString("url");c.seconds=o.optLong("seconds");return c;}
    }
    public static final class Book {
        public String source="",title="",cover="",warning="";public ArrayList<Chapter> chapters=new ArrayList<>();
        JSONObject json()throws JSONException{JSONObject j=new JSONObject();j.put("source",source);j.put("title",title);j.put("cover",cover);j.put("warning",warning);JSONArray a=new JSONArray();for(Chapter c:chapters)a.put(c.json());j.put("chapters",a);return j;}
        static Book parse(JSONObject j){Book b=new Book();b.source=j.optString("source");b.title=j.optString("title");b.cover=j.optString("cover");b.warning=j.optString("warning");JSONArray a=j.optJSONArray("chapters");if(a!=null)for(int i=0;i<a.length();i++)if(a.optJSONObject(i)!=null)b.chapters.add(Chapter.parse(a.optJSONObject(i)));return b;}
    }
    public static Book fetch(String address)throws Exception{
        URI uri=new URI(address.trim());if(!"https".equalsIgnoreCase(uri.getScheme())||!"knigavuhe.org".equalsIgnoreCase(uri.getHost())||!uri.getPath().startsWith("/book/"))throw new IllegalArgumentException("Требуется https-ссылка на книгу knigavuhe.org/book/...");
        HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();c.setConnectTimeout(14000);c.setReadTimeout(18000);c.setRequestProperty("User-Agent","ChitalkaRSVP/1.11 (Android; public HTML importer)");c.setInstanceFollowRedirects(false);
        try{if(c.getResponseCode()!=200)throw new java.io.IOException("Сайт вернул HTTP "+c.getResponseCode());String ct=c.getContentType();if(ct==null||!ct.toLowerCase(Locale.ROOT).contains("text/html"))throw new java.io.IOException("Ответ сайта не HTML");
            Document d=Jsoup.parse(c.getInputStream(),"UTF-8",uri.toString()); Book b=new Book();b.source=uri.toString();Element title=d.selectFirst("span.book_title_elem.book_title_name");if(title==null)title=d.selectFirst("h1");b.title=title==null?"Аудиокнига":title.text().trim();Element cover=d.selectFirst("meta[property=og:image]");if(cover!=null)b.cover=cover.attr("abs:content");
            Elements names=d.select("div.book_playlist_item_name");Elements times=d.select("div.book_playlist_item_time");for(int i=0;i<names.size();i++){Chapter ch=new Chapter();ch.name=names.get(i).text().trim();ch.time=i<times.size()?times.get(i).text().trim():"";ch.seconds=parseTime(ch.time);b.chapters.add(ch);}
            // Direct chapter-bound URLs only. Global JS URLs cannot safely be mapped by order.
            Elements items=d.select(".book_playlist_item");if(items.size()==b.chapters.size())for(int i=0;i<items.size();i++){
                Element item=items.get(i);String link="";for(String attr:new String[]{"data-src","data-url","data-file","data-audio"}){String val=item.attr(attr);if(isMp3(val)){link=val;break;}}
                if(link.isEmpty()){Element audio=item.selectFirst("audio[src],audio source[src],a[href$=.mp3]");if(audio!=null)link=audio.hasAttr("src")?audio.attr("src"):audio.attr("href");}
                if(isMp3(link)) {String full=item.absUrl("data-src");if(!link.startsWith("https://"))full=URI.create(b.source).resolve(link).toString();else full=link;if(isSafeMedia(full))b.chapters.get(i).url=full;}
            }
            if(b.chapters.isEmpty())throw new java.io.IOException("Не удалось найти список глав: возможно, структура страницы изменилась");
            // The Python importer finds quoted MP3 URLs in JavaScript text nodes.
            // Collect URLs from each script in DOM order, never from unrelated page assets.
            // If there is exactly one playlist-sized, unambiguous sequence, align it to
            // the playlist's DOM order; mismatched counts are NEVER zipped/truncated.
            int explicit=0;for(Chapter x:b.chapters)if(!x.url.isEmpty())explicit++;
            if(explicit<b.chapters.size()){
                LinkedHashSet<String> found=new LinkedHashSet<>();
                for(Element script:d.select("script")){
                    String scriptText=script.data();
                    if(scriptText==null||scriptText.isEmpty())scriptText=script.html();
                    found.addAll(extractMp3Urls(scriptText));
                }
                // BeautifulSoup string nodes can include JSON snippets outside <script>.
                // Look at non-script HTML text nodes too, without counting HTML attributes.
                if(found.isEmpty())for(TextNode node:d.textNodes())found.addAll(extractMp3Urls(node.getWholeText()));
                if(found.size()==b.chapters.size()){
                    int i=0;boolean conflict=false;
                    for(String candidate:found){
                        if(!b.chapters.get(i).url.isEmpty()&&!b.chapters.get(i).url.equals(candidate))conflict=true;
                        i++;
                    }
                    if(!conflict){i=0;for(String candidate:found)b.chapters.get(i++).url=candidate;b.warning="MP3 найдены в JavaScript и сопоставлены по порядку. Проверьте названия и длительности глав перед скачиванием.";}
                    else b.warning="Найдены MP3 в JavaScript, но их порядок противоречит ссылкам глав. Автоматическое сопоставление отключено.";
                }else if(found.size()>0){
                    b.warning="Найдено MP3-ссылок: "+found.size()+", глав: "+b.chapters.size()+". Порядок не подтверждён, автоматическое сопоставление отключено.";
                }
            }
            int valid=0;for(Chapter x:b.chapters)if(!x.url.isEmpty())valid++;
            if(valid!=b.chapters.size()&&b.warning.isEmpty())b.warning="Список глав найден, но прямые MP3-ссылки не подтверждены для "+(b.chapters.size()-valid)+" глав. Скачивание недоступно: сайт может загружать адреса динамически либо ограничивать загрузку.";
            return b;
        }finally{c.disconnect();}
    }
    /** Equivalent to Python BeautifulSoup string-regex extraction, with de-escaping. */
    static LinkedHashSet<String> extractMp3Urls(String input){
        LinkedHashSet<String> output=new LinkedHashSet<>();
        if(input==null||input.isEmpty())return output;
        String normalized=input.replace("\\/","/")
                .replace("\\u002F","/").replace("\\u002f","/")
                .replace("\\u003A",":").replace("\\u003a",":")
                .replace("&amp;","&");
        Pattern matcher=Pattern.compile("https?://[^\\s\"'<>\\\\]+", Pattern.CASE_INSENSITIVE);
        Matcher found=matcher.matcher(normalized);
        while(found.find()){
            String candidate=found.group();
            // Strip trailing JavaScript or JSON punctuation, but preserve URL queries.
            while(candidate.endsWith(",")||candidate.endsWith(";")||candidate.endsWith(")"))candidate=candidate.substring(0,candidate.length()-1);
            if(isMp3(candidate)&&isSafeMedia(candidate))output.add(candidate);
        }
        return output;
    }
    static boolean isMp3(String u){return u!=null&&u.toLowerCase(Locale.ROOT).matches("^.+\\.mp3(?:[?#].*)?$");}
    static boolean isSafeMedia(String u){try{URI a=new URI(u);String host=a.getHost();return "https".equalsIgnoreCase(a.getScheme())&&host!=null&&!host.equals("localhost")&&!host.matches("(?i)(127\\..*|10\\..*|192\\.168\\..*|172\\.(1[6-9]|2[0-9]|3[01])\\..*|169\\.254\\..*)");}catch(Exception e){return false;}}
    static long parseTime(String str){try{String[] a=str.trim().split(":");long t=0;for(String part:a)t=t*60+Long.parseLong(part.trim());return t;}catch(Exception ex){return 0;}}
}
