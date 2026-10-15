package ru.reader.rsvp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Search, series and audiobook metadata from public knigavuhe HTML. No access-control bypass. */
public final class KvuheSearchParser {
    private KvuheSearchParser() {}
    private static final String HOST="knigavuhe.org";
    private static final String UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";


    public static final class Entry {
        public String url="",title="",cover="",author="",reader="",genre="",description="",duration="";
    }
    public static final class Page {
        public final ArrayList<Entry> entries=new ArrayList<>();
        public final ArrayList<Entry> series=new ArrayList<>();
        public String nextUrl="";
        public int totalBooks=-1,totalSeries=-1;
    }
    public static String searchUrl(String query){
        try{return "https://"+HOST+"/search/?q="+URLEncoder.encode(query==null?"":query.trim(),"UTF-8");}
        catch(java.io.UnsupportedEncodingException impossible){return "https://"+HOST+"/search/";}
    }
    private static boolean trusted(URI uri,String path){
        if(uri==null||!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null)return false;
        String host=uri.getHost();
        return host!=null&&(host.equalsIgnoreCase(HOST)||host.equalsIgnoreCase("www."+HOST)||host.equalsIgnoreCase("m."+HOST))
                &&uri.getPath()!=null&&uri.getPath().startsWith(path);
    }
    static boolean bookUrl(String url){try{return trusted(new URI(url),"/book/");}catch(Exception e){return false;}}
    static boolean seriesUrl(String url){try{return trusted(new URI(url),"/series/");}catch(Exception e){return false;}}
    static boolean searchPageUrl(String url){try{return trusted(new URI(url),"/search/");}catch(Exception e){return false;}}
    private static String text(Element root,String selector){Element e=root.selectFirst(selector);return e==null?"":e.text().trim();}
    private static String canonical(String candidate,String prefix){
        try{
            URI raw=new URI(candidate);if(!trusted(raw,prefix))return "";
            String path=raw.getPath();if(path.isEmpty())return "";
            if(!path.endsWith("/"))path+="/";
            return new URI("https",null,HOST,-1,path,null,null).toString();
        }catch(Exception e){return "";}
    }
    private static String imageUrl(Element img){
        if(img==null)return "";
        for(String key:new String[]{"data-original","data-src","data-lazy-src","data-srcset","srcset","src"}){
            String v=img.attr(key).trim();if(v.isEmpty())continue;
            if(v.contains(","))v=v.split(",")[0].trim();
            if(v.contains(" "))v=v.split(" ")[0].trim();
            if(v.startsWith("data:"))continue;
            String url=v;
            if(!img.baseUri().isEmpty())try{url=URI.create(img.baseUri()).resolve(v).toString();}catch(Exception ignored){}
            if(url.startsWith("https://")&&!url.toLowerCase(Locale.ROOT).contains("blank.gif"))return url;
        }
        return "";
    }
    private static Entry fromCard(Element card,Element anchor,String href){
        Entry e=new Entry();e.url=href;
        e.title=text(card,".bookkitem_name a[href*=/book/], .bookkitem_title a[href*=/book/], .book_title a[href*=/book/], h2 a[href*=/book/], h3 a[href*=/book/]");
        if(e.title.isEmpty())e.title=anchor.text().trim();
        if(e.title.isEmpty())e.title=text(card,".bookkitem_name, .bookkitem_title, .book_title, h2, h3");
        // The cover image can be wrapped in the same title link.
        e.cover=imageUrl(card.selectFirst(".bookkitem_cover img, .bookkitem_img img, .bookkitem_image img, picture img, img"));
        e.author=text(card,".bookkitem_author, .book_author, [itemprop=author], .bookkitem_autor");
        e.reader=text(card,".bookkitem_reader, .bookkitem_executor, .book_reader, .reader, .bookkitem_speaker");
        e.genre=text(card,".bookkitem_genre, .book_genre, .genre");
        e.description=text(card,".bookkitem_desc, .bookkitem_description, .book_description, .bookkitem_text, .bookkitem_annotation, p");
        e.duration=text(card,".bookkitem_duration, .book_duration, .duration, .bookkitem_time");
        if(e.description.length()>360)e.description=e.description.substring(0,360).trim()+"…";
        return e;
    }
    private static void books(Document doc,Page output,boolean includeLooseLinks){
        Set<String> seen=new HashSet<>();
        Elements cards=doc.select("div.bookkitem, article.bookkitem, .bookkitem, div.book_item, article.book-item, .book-list-item");
        for(Element card:cards){
            Element a=card.selectFirst(".bookkitem_name a[href*=/book/], .bookkitem_title a[href*=/book/], a[href*=/book/]");
            if(a==null)continue;
            String url=canonical(a.absUrl("href"),"/book/");
            if(url.isEmpty()||!seen.add(url))continue;
            Entry e=fromCard(card,a,url);if(!e.title.isEmpty())output.entries.add(e);
        }
        // Some versions of series and search results use another container structure.
        if(includeLooseLinks&&output.entries.isEmpty()){
            for(Element a:doc.select("main a[href*=/book/], .content a[href*=/book/], .books a[href*=/book/]")){
                String url=canonical(a.absUrl("href"),"/book/");if(url.isEmpty()||!seen.add(url))continue;
                Element card=a.closest("article, li, .book, .book_item, .book-card, .book_block, .item");
                if(card==null)card=a.parent();
                if(card==null)continue;
                Entry e=fromCard(card,a,url);if(!e.title.isEmpty())output.entries.add(e);
            }
        }
    }
    private static void series(Document doc,Page output){
        Set<String> seen=new HashSet<>();
        for(Element a:doc.select("a[href*=/series/]")){
            if(a.closest("header, footer, nav, .bookkitem, .book_item, .book-list-item, .book-card")!=null)continue;
            String url=canonical(a.absUrl("href"),"/series/");if(url.isEmpty()||!seen.add(url))continue;
            String name=a.text().trim();if(name.isEmpty())continue;
            Entry e=new Entry();e.url=url;e.title=name;
            Element card=a.closest(".search_series_item, .series_item, .series-item, .search-series, li");
            if(card!=null){e.cover=imageUrl(card.selectFirst("img"));e.author=text(card,".author, .series_author, .search_series_author");}
            output.series.add(e);
        }
        // Search may have unrelated sidebar series. Use site's stated count as a cap.
        if(output.totalSeries>=0&&output.series.size()>output.totalSeries)
            output.series.subList(output.totalSeries,output.series.size()).clear();
    }
    private static void pagination(Document doc,Page output,String base){
        int current=KvuheSearchPaging.pageNumber(base);if(current>100)return; // avoid unbounded catalog crawling
        String candidate="";
        for(Element a:doc.select("a[rel=next], .pagination a[href], .pages a[href], .paginator a[href], a.page-next[href], a.next[href]")){
            String url=a.absUrl("href"),label=a.text().trim();
            if(!searchPageUrl(url))continue;
            if(a.hasAttr("rel")&&a.attr("rel").contains("next")||a.hasClass("next")||a.hasClass("page-next")
                    ||label.equals("»")||label.equals("›")||label.equals("→")||label.equalsIgnoreCase("далее")
                    ||label.equalsIgnoreCase("показать ещё")||label.equals(Integer.toString(current+1))){candidate=url;break;}
        }
        if(candidate.isEmpty()){
            for(Element el:doc.select("[data-next], [data-page], [data-url]")){
                if(!el.className().matches("(?i).*(?:more|next|pagination|pager|page|load).*"))continue;
                String value=el.hasAttr("data-next")?el.attr("data-next"):el.hasAttr("data-url")?el.attr("data-url"):el.attr("data-page");
                if(value.equals(Integer.toString(current+1))){candidate=KvuheSearchPaging.following(base);break;}
                try{String resolved=el.absUrl(el.hasAttr("data-next")?"data-next":"data-url");
                    if(searchPageUrl(resolved)&&KvuheSearchPaging.pageNumber(resolved)==current+1){candidate=resolved;break;}}
                catch(Exception ignored){}
            }
        }
        // The HTML may render only a JS "Показать ещё" control, but exposes a total.
        // The public /search/?q=...&page=N URLs allow controlled, explicit pagination.
        if(candidate.isEmpty()&&output.totalBooks>output.entries.size()&&!output.entries.isEmpty())
            candidate=KvuheSearchPaging.following(base);
        if(searchPageUrl(candidate)&&KvuheSearchPaging.pageNumber(candidate)>current)output.nextUrl=candidate;
    }
    static Page parse(String html,String baseUrl){
        Document doc=Jsoup.parse(html,baseUrl);Page result=new Page();
        String all=doc.body()==null?"":doc.body().text();
        result.totalBooks=KvuheSearchPaging.countBooks(all);result.totalSeries=KvuheSearchPaging.countSeries(all);
        books(doc,result,true);series(doc,result);pagination(doc,result,baseUrl);
        return result;
    }
    static Page parseSeries(String html,String baseUrl){
        Document doc=Jsoup.parse(html,baseUrl);Page result=new Page();books(doc,result,true);return result;
    }
    private static String getHtml(String url,String path)throws Exception{
        URI current=new URI(url);if(!trusted(current,path))throw new IllegalArgumentException("Разрешены только публичные HTTPS-страницы knigavuhe.org"+path);
        Set<String> redirectsSeen=new HashSet<>();
        for(int n=0;n<6;n++){
            if(!trusted(current,path)||!redirectsSeen.add(current.toString()))throw new IOException("Недопустимое перенаправление сайта");
            HttpURLConnection c=(HttpURLConnection)current.toURL().openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(14000);c.setReadTimeout(18000);
            c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Accept","text/html,application/xhtml+xml;q=0.9,*/*;q=0.7");
            c.setRequestProperty("Accept-Language","ru-RU,ru;q=0.9,en;q=0.5");
            int code=c.getResponseCode();
            try{
                if(code==200){String mime=c.getContentType();
                    if(mime==null||!mime.toLowerCase(Locale.ROOT).contains("html"))throw new IOException("Сайт не вернул HTML");
                    return Jsoup.parse(c.getInputStream(),"UTF-8",current.toString()).outerHtml();
                }
                if(code==301||code==302||code==303||code==307||code==308){
                    String loc=c.getHeaderField("Location");if(loc==null)throw new IOException("Перенаправление без адреса");
                    current=current.resolve(loc).normalize();continue;
                }
                if(code==403||code==429)throw new IOException("Сайт ограничил запросы (HTTP "+code+"). Повторите позже.");
                throw new IOException("Сайт недоступен: HTTP "+code);
            }finally{c.disconnect();}
        }
        throw new IOException("Слишком много перенаправлений");
    }
    public static Page fetch(String url)throws Exception{return parse(getHtml(url,"/search/"),url);}
    public static Page fetchSeries(String url)throws Exception{return parseSeries(getHtml(url,"/series/"),url);}
}
