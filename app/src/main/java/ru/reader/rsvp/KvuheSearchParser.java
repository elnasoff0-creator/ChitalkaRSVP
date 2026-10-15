package ru.reader.rsvp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Public HTML search only. No browser, authentication, challenge or DRM bypass. */
public final class KvuheSearchParser {
    private KvuheSearchParser() {}
    private static final String HOST="knigavuhe.org";
    private static final String UA="Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36";

    public static final class Entry {
        public String url="",title="",cover="",author="",reader="",genre="",description="",duration="";
    }
    public static final class Page {
        public final ArrayList<Entry> entries=new ArrayList<>();
        public String nextUrl="";
        public String count="";
    }
    public static String searchUrl(String query){
        String q=query==null?"":query.trim();
        try{return "https://"+HOST+"/search/?q="+URLEncoder.encode(q,"UTF-8");}
        catch(java.io.UnsupportedEncodingException impossible){return "https://"+HOST+"/search/";}
    }
    private static boolean trusted(URI uri,String path){
        if(uri==null||!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null)return false;
        String host=uri.getHost();
        if(host==null||!(host.equalsIgnoreCase(HOST)||host.equalsIgnoreCase("www."+HOST)||host.equalsIgnoreCase("m."+HOST)))return false;
        return uri.getPath()!=null&&uri.getPath().startsWith(path);
    }
    static boolean bookUrl(String url){try{return trusted(new URI(url),"/book/");}catch(Exception e){return false;}}
    static boolean searchPageUrl(String url){try{return trusted(new URI(url),"/search/");}catch(Exception e){return false;}}
    private static String text(Element root,String selectors){Element e=root.selectFirst(selectors);return e==null?"":e.text().trim();}
    private static String imageUrl(Element img){
        if(img==null)return "";
        // The site can lazy-load original book covers. Avoid placeholder GIF and data URIs.
        for(String key:new String[]{"data-original","data-src","data-lazy-src","data-srcset","srcset","src"}){
            String value=img.attr(key).trim();
            if(value.isEmpty())continue;
            if(value.contains(","))value=value.split(",")[0].trim();
            if(value.contains(" "))value=value.split(" ")[0].trim();
            if(value.startsWith("data:"))continue;
            String resolved=img.absUrl(key);
            if(resolved.isEmpty())resolved=img.baseUri().isEmpty()?value:URI.create(img.baseUri()).resolve(value).toString();
            if(resolved.startsWith("https://")&& !resolved.endsWith("blank.gif"))return resolved;
        }
        return "";
    }
    static Page parse(String html,String baseUrl){
        Document doc=Jsoup.parse(html,baseUrl);
        Page output=new Page();
        output.count=text(doc,".search-results-count, .search-result-count, .found_books, .books_found");
        Set<String> seen=new HashSet<>();
        for(Element card:doc.select("div.bookkitem")){
            Element link=card.selectFirst(".bookkitem_name a[href*=/book/], .bookkitem_title a[href*=/book/], .bookkitem_name[href*=/book/], a[href*=/book/]");
            if(link==null)continue;String href=link.absUrl("href");
            if(!bookUrl(href))continue;
            href=href.split("#")[0];
            if(!seen.add(href))continue;
            Entry result=new Entry();result.url=href;
            result.title=text(card,".bookkitem_name, .bookkitem_title, .book_title, .bookname, h2, h3");
            if(result.title.isEmpty())result.title=link.text().trim();
            if(result.title.isEmpty())continue;
            Element picture=card.selectFirst(".bookkitem_cover img, .bookkitem_img img, .bookkitem_image img, img");
            result.cover=imageUrl(picture);
            result.author=text(card,".bookkitem_author, .book_author, [itemprop=author]");
            result.reader=text(card,".bookkitem_reader, .bookkitem_executor, .book_reader, .reader");
            result.genre=text(card,".bookkitem_genre, .book_genre, .genre");
            result.description=text(card,".bookkitem_desc, .bookkitem_description, .book_description, .bookkitem_text, p");
            result.duration=text(card,".bookkitem_duration, .book_duration, .duration, .bookkitem_time");
            if(result.description.length()>450)result.description=result.description.substring(0,450).trim()+"…";
            output.entries.add(result);
        }
        // Only pagination links inside the search area, never unrelated book links.
        for(Element a:doc.select(".pagination a[href], .pages a[href], a[rel=next], a.next[href], a.page-next[href]")){
            String label=a.text().trim();String next=a.absUrl("href");
            if(searchPageUrl(next)&&(a.hasAttr("rel")||a.hasClass("next")||a.hasClass("page-next")||label.equals("»")||label.equals("›")||label.equalsIgnoreCase("Следующая")||label.equalsIgnoreCase("Далее"))){output.nextUrl=next;break;}
        }
        if(output.nextUrl.isEmpty()){
            Element active=doc.selectFirst(".pagination .active, .pages .active, .pagination .current, .pages .current");
            if(active!=null){Element next=active.nextElementSibling();if(next!=null){Element a=next.tagName().equals("a")?next:next.selectFirst("a[href]");if(a!=null&&searchPageUrl(a.absUrl("href")))output.nextUrl=a.absUrl("href");}}
        }
        return output;
    }
    public static Page fetch(String url)throws Exception{
        URI requested=new URI(url);
        if(!trusted(requested,"/search/"))throw new IllegalArgumentException("Допустим только HTTPS-поиск knigavuhe.org");
        HttpURLConnection connection=null;
        URI current=requested;
        for(int redirects=0;redirects<6;redirects++){
            if(!trusted(current,"/search/"))throw new IOException("Недопустимое перенаправление сайта");
            connection=(HttpURLConnection)current.toURL().openConnection();connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(14000);connection.setReadTimeout(18000);
            connection.setRequestProperty("User-Agent",UA);
            connection.setRequestProperty("Accept","text/html,application/xhtml+xml;q=0.9,*/*;q=0.7");
            connection.setRequestProperty("Accept-Language","ru-RU,ru;q=0.9,en;q=0.5");
            int status=connection.getResponseCode();
            if(status==200){
                try{
                    String content=connection.getContentType();if(content==null||!content.toLowerCase(Locale.ROOT).contains("html"))throw new IOException("Сайт не вернул HTML");
                    Document document=Jsoup.parse(connection.getInputStream(),"UTF-8",current.toString());
                    return parse(document.outerHtml(),current.toString());
                }finally{connection.disconnect();}
            }
            if(status==301||status==302||status==303||status==307||status==308){
                String target=connection.getHeaderField("Location");connection.disconnect();
                if(target==null)throw new IOException("Нет адреса перенаправления");current=current.resolve(target).normalize();continue;
            }
            connection.disconnect();
            if(status==403||status==429)throw new IOException("Сайт ограничил запросы (HTTP "+status+"). Повторите позже.");
            throw new IOException("Поиск недоступен: HTTP "+status);
        }
        throw new IOException("Слишком много перенаправлений поиска");
    }
}
