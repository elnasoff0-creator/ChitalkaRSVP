package ru.reader.rsvp;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Public HTML adapter for Ranobes.net. Does not attempt to defeat antibot gates. */
final class RanobesApi {
    static final String DOMAIN="https://ranobes.net";
    private static final Pattern BOOK_LINK=Pattern.compile("^/novels/(\\d{3,12})-[a-z0-9-]+(?:-v\\d+)?\\.html$",Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAPTER_LINK=Pattern.compile("^/[^/?#]+-\\d{3,12}/(\\d{3,12})\\.html$",Pattern.CASE_INSENSITIVE);
    private static final Pattern VOL=Pattern.compile("(?:book|volume|vol\\.?|том|книга)\\s*(\\d+(?:[.,]\\d+)?)",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final String UA="Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    // Site-owned session cookies are scoped to ranobes.net; no login or bypass handling.
    private final Map<String,List<Cookie>> sessionCookies=new HashMap<>();
    private final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(14,TimeUnit.SECONDS)
        .readTimeout(28,TimeUnit.SECONDS).followRedirects(false).cookieJar(new CookieJar(){
            @Override public synchronized void saveFromResponse(HttpUrl url,List<Cookie> cookies){
                if(!"ranobes.net".equalsIgnoreCase(url.host()))return;
                List<Cookie> saved=sessionCookies.computeIfAbsent(url.host(),k->new ArrayList<>());
                long now=System.currentTimeMillis();saved.removeIf(c->c.expiresAt()<=now);
                for(Cookie c:cookies){saved.removeIf(old->old.name().equals(c.name())&&old.path().equals(c.path()));
                    if(c.expiresAt()>now)saved.add(c);}
            }
            @Override public synchronized List<Cookie> loadForRequest(HttpUrl url){
                List<Cookie> saved=sessionCookies.get(url.host());if(saved==null)return Collections.emptyList();
                List<Cookie> valid=new ArrayList<>();long now=System.currentTimeMillis();
                for(Cookie c:saved)if(c.expiresAt()>now&&c.matches(url))valid.add(c);return valid;
            }
        }).build();
    static final class Title {
        String id="",url="",title="",author="",description="",cover="",status="";
        int estimatedChapters;
        Title(String id,String url,String title){this.id=id;this.url=url;this.title=title;}
    }
    static final class Chapter {
        String id="",url="",title="",volume="1";int index,page=1;
        Chapter(String id,String url,String title,String volume,int index){this.id=id;this.url=url;this.title=title;this.volume=volume;this.index=index;}
    }
    static String validBookUrl(String raw){
        if(raw==null)return "";String input=raw.trim();
        if(input.matches("(?i)^(www\\.)?ranobes\\.net/.*"))input="https://"+input;
        try{HttpUrl url=HttpUrl.parse(input);if(url==null||!"https".equals(url.scheme())||!"ranobes.net".equalsIgnoreCase(url.host()))return "";
            Matcher m=BOOK_LINK.matcher(url.encodedPath());return m.matches()?DOMAIN+url.encodedPath():"";
        }catch(Exception ex){return "";}
    }
    static String novelId(String bookUrl){String url=validBookUrl(bookUrl);if(url.isEmpty())return "";
        Matcher m=BOOK_LINK.matcher(HttpUrl.parse(url).encodedPath());return m.matches()?m.group(1):"";
    }
    private static String validChapterUrl(String url,String bookId){
        try{HttpUrl parsed=HttpUrl.parse(url);if(parsed==null||!"https".equals(parsed.scheme())||!"ranobes.net".equalsIgnoreCase(parsed.host()))return "";
            String path=parsed.encodedPath();Matcher m=CHAPTER_LINK.matcher(path);
            if(m.matches()&&path.contains("-"+bookId+"/"))return DOMAIN+path;
        }catch(Exception ignored){}return "";
    }
    static String chapterId(String url){try{HttpUrl u=HttpUrl.parse(url);if(u==null)return "";Matcher m=CHAPTER_LINK.matcher(u.encodedPath());return m.matches()?m.group(1):"";}catch(Exception ex){return "";}}
    private static boolean allowed(HttpUrl url){
        return url!=null&&"https".equals(url.scheme())&&"ranobes.net".equalsIgnoreCase(url.host());
    }
    private Document get(String url)throws IOException {
        HttpUrl parsed=HttpUrl.parse(url);
        if(!allowed(parsed))throw new IOException("Недопустимый адрес Ranobes.net");
        return fetchDocument(new Request.Builder().url(parsed).get().build());
    }
    private Document fetchDocument(Request original)throws IOException {
        Request current=original;
        HashSet<String> visited=new HashSet<>();
        for(int hop=0;hop<7;hop++){
            if(!allowed(current.url()))throw new IOException("Запрещённое перенаправление Ranobes.net");
            String key=current.method()+" "+current.url();
            if(!visited.add(key))throw new IOException("Ranobes.net: циклическое перенаправление HTTP");
            Request req=current.newBuilder().header("User-Agent",UA)
                .header("Accept","text/html,application/xhtml+xml")
                .header("Accept-Language","en-US,en;q=0.8").build();
            try(Response response=client.newCall(req).execute()){
                int status=response.code();
                if(status==301||status==302||status==303||status==307||status==308){
                    String loc=response.header("Location","");
                    HttpUrl destination=loc.isEmpty()?null:response.request().url().resolve(loc);
                    if(!allowed(destination))throw new IOException("Ranobes.net: HTTP "+status+" — перенаправление на внешний или небезопасный адрес");
                    Request.Builder next=req.newBuilder().url(destination).header("Referer",req.url().toString());
                    // HTTP 307/308 MUST preserve the POST body; 303 changes to GET.
                    if(status==303 || ((status==301||status==302)&&"POST".equals(req.method())))next.get();
                    current=next.build();continue;
                }
                if(status==401||status==403||status==429||status==503)
                    throw new IOException("Ranobes.net: HTTP "+status+". Сервер ограничил доступ; откройте сайт в браузере и повторите позже. Обход защиты не выполняется.");
                if(!response.isSuccessful())throw new IOException("Ranobes.net: HTTP "+status);
                ResponseBody body=response.body();if(body==null||body.contentLength()>6_000_000L)
                    throw new IOException("Ответ Ranobes пуст или слишком велик");
                String markup=body.string();if(markup.length()>6_000_000)throw new IOException("HTML превышает предел");
                Document doc=Jsoup.parse(markup,response.request().url().toString());
                String label=doc.title().toLowerCase(Locale.ROOT);
                if(label.contains("just a moment")||label.contains("ddos")||label.contains("antibot")
                    ||label.contains("антибот")||doc.select("form[action*=captcha],#challenge-form,.cf-challenge").size()>0)
                    throw new IOException("Ranobes запросил подтверждение в браузере. Автоматическое скачивание ограничено.");
                return doc;
            }
        }
        throw new IOException("Ranobes.net: слишком много HTTP-перенаправлений");
    }
    private static String pageTitle(Document page){Element meta=page.selectFirst("meta[property=og:title]");
        String text=meta==null?"":meta.attr("content");if(text.isEmpty()){Element h=page.selectFirst("h1");if(h!=null)text=h.text();}
        if(text.isEmpty())text=page.title();return text.replaceAll("\\s*[-|]\\s*(?:Ranobes.*|RANOBES.*)$","").trim();}
    private static String cover(Document page){for(String select:new String[]{"meta[property=og:image]","meta[name=twitter:image]",".cover img",".story-image img",".poster img",".fullimg img"}){
        Element e=page.selectFirst(select);if(e==null)continue;
        for(String attr:new String[]{"content","data-src","src"}){String url=e.absUrl(attr);if(url.startsWith("https://"))return url;}}
        return "";
    }
    private static String extractAuthor(Document page){
        Element subtitle=page.selectFirst("h1.title .subtitle");
        if(subtitle!=null)return subtitle.text().replaceFirst("(?i)^by\\s+","").trim();
        for(Element li:page.select(".r-fullstory-spec li"))if(li.text().matches("(?is)^Authors?:.*")){
            Element link=li.selectFirst("a");
            return link==null?li.text().replaceFirst("(?i)^Authors?:\\s*","").trim():link.text().trim();
        }
        Element author=page.selectFirst("[itemprop=author],.book-author,.story_author,.story-author");
        return author==null?"":author.text().trim();
    }
    static List<Title> parseSearch(String markup,String base){Document page=Jsoup.parse(markup,base);LinkedHashMap<String,Title> found=new LinkedHashMap<>();
        for(Element a:page.select("a[href*=/novels/],a[href*=/novel/],a[href*=/books/]")){
            String url=validBookUrl(a.absUrl("href"));if(url.isEmpty())continue;
            String id=novelId(url);if(id.isEmpty()||found.containsKey(id))continue;
            Element parent=a.closest("article,.shortstory,.short-story,.story,.search-item,.item,.shortstory-body");
            String name=a.text().trim();if(name.length()<3&&parent!=null){Element heading=parent.selectFirst("h2,h3,.title");if(heading!=null)name=heading.text();}
            if(name.length()<3||name.length()>240)continue;
            Title title=new Title(id,url,name);
            if(parent!=null){ // Search cards have their own cover; do not use the page-wide og:image.
                Element img=parent.selectFirst("img[data-src],img[src]");if(img!=null){String link=img.absUrl("data-src");if(link.isEmpty())link=img.absUrl("src");if(link.startsWith("https://"))title.cover=link;}
                Element author=parent.selectFirst(".author,[itemprop=author]");if(author!=null)title.author=author.text();
                Element desc=parent.selectFirst(".description,.shortstory__text,.story__text");if(desc!=null)title.description=desc.text();
            }
            found.put(id,title);
        }
        return new ArrayList<>(found.values());
    }
    List<Title> search(String query,int page)throws IOException {
        if(query==null||query.trim().isEmpty())return new ArrayList<>();
        // The public DLE search form submits POST (not query parameters in a GET).
        // Follow ordinary redirects within the same HTTPS origin only.
        FormBody form=new FormBody.Builder()
            .add("catlist[]","1").add("story",query.trim())
            .add("do","search").add("subaction","search").build();
        HttpUrl address=HttpUrl.parse(DOMAIN+"/index.php");
        Document response=fetchDocument(new Request.Builder().url(address).post(form).build());
        return parseSearch(response.outerHtml(),response.baseUri());
    }
    Title title(String url)throws IOException {String canonical=validBookUrl(url);if(canonical.isEmpty())throw new IOException("Укажите ссылку https://ranobes.net/novels/…html");
        Document page=get(canonical);Title title=new Title(novelId(canonical),canonical,pageTitle(page));
        title.cover=cover(page);title.author=extractAuthor(page);
        Element info=page.selectFirst(".description,.full-story,.fullstory,.story-description,[itemprop=description],meta[name=description]");
        if(info!=null)title.description=info.tagName().equals("meta")?info.attr("content"):info.text();
        if(title.description.length()>2500)title.description=title.description.substring(0,2500);
        return title;
    }
    static final class TocPage {
        final int number,pagesCount,totalCount;
        final ArrayList<Chapter> chapters;
        TocPage(int page,int pages,int total,ArrayList<Chapter> entries){
            number=page;pagesCount=Math.max(1,pages);totalCount=Math.max(0,total);chapters=entries;
        }
    }
    static final class TocResult {
        final ArrayList<Chapter> chapters;
        final int pagesCount,totalCount,loadedPages;
        final boolean partial;
        TocResult(ArrayList<Chapter> chapters,int pages,int total,int loaded,boolean incomplete){
            this.chapters=chapters;pagesCount=pages;totalCount=total;loadedPages=loaded;partial=incomplete;
        }
    }
    private static final Pattern TOC_PAGINATION=Pattern.compile("/chapters/(\\d{3,12})/page/(\\d+)/?",Pattern.CASE_INSENSITIVE);
    static String tocUrl(String id,int page){
        if(id==null||!id.matches("\\d{3,12}"))return "";
        return DOMAIN+"/chapters/"+id+"/"+(page<=1?"":"page/"+page+"/");
    }
    private static Chapter fromLink(String url,String title,String id,int page,int index){
        String canonical=validChapterUrl(url,id);if(canonical.isEmpty()||title==null||title.trim().length()<2)return null;
        String volume="1";Matcher matcher=VOL.matcher(title);if(matcher.find())volume=matcher.group(1).replace(',','.');
        Chapter result=new Chapter(chapterId(canonical),canonical,title.trim(),volume,index);result.page=page;
        return result;
    }
    /** The chapters page exposes Vue state as window.__DATA__, alongside HTML fallback. */
    static TocPage parseTocPage(String markup,String base,String bookId,int page){
        Document doc=Jsoup.parse(markup,base);
        LinkedHashMap<String,Chapter> collected=new LinkedHashMap<>();int total=0,pages=1;
        for(Element script:doc.select("script")){
            String data=script.data();int offset=data.indexOf("window.__DATA__");
            if(offset<0)continue;
            int equal=data.indexOf('=',offset);if(equal<0)continue;
            String json=data.substring(equal+1).trim();if(json.endsWith(";"))json=json.substring(0,json.length()-1).trim();
            try{
                JSONObject state=new JSONObject(json);
                if(!bookId.equals(state.optString("book_id")))continue;
                pages=Math.min(500,Math.max(1,state.optInt("pages_count",1)));
                total=Math.max(0,state.optInt("count_all",0));
                JSONArray entries=state.optJSONArray("chapters");
                if(entries==null)continue;
                for(int i=0;i<entries.length();i++){
                    JSONObject entry=entries.optJSONObject(i);if(entry==null)continue;
                    Chapter chapter=fromLink(entry.optString("link"),entry.optString("title"),bookId,page,collected.size()+1);
                    if(chapter!=null)collected.putIfAbsent(chapter.id,chapter);
                }
                break;
            }catch(Exception ignored){/* HTML fallback below */}
        }
        if(collected.isEmpty()){
            for(Element link:doc.select(".cat_block.cat_line a[href],.chapters-scroll-list a[rel=chapter],a.chapter-item[rel=chapter]")){
                String text=link.attr("title");if(text.isEmpty())text=link.text();
                Chapter chapter=fromLink(link.absUrl("href"),text,bookId,page,collected.size()+1);
                if(chapter!=null)collected.putIfAbsent(chapter.id,chapter);
            }
        }
        for(Element link:doc.select(".pages a[href],.ch-pages option")){
            Matcher m=TOC_PAGINATION.matcher(link.absUrl("href"));
            if(m.find()&&bookId.equals(m.group(1)))try{pages=Math.max(pages,Math.min(500,Integer.parseInt(m.group(2))));}catch(Exception ignored){}
        }
        for(Element opt:doc.select("select.ch-pages option")){
            try{pages=Math.max(pages,Math.min(500,Integer.parseInt(opt.attr("value"))));}catch(Exception ignored){}
        }
        return new TocPage(page,pages,total,new ArrayList<>(collected.values()));
    }
    static List<Chapter> parseChapters(String markup,String base,String id){
        return parseTocPage(markup,base,id,1).chapters;
    }
    TocPage tocPage(String id,int number)throws IOException {
        String target=tocUrl(id,number);
        if(target.isEmpty()||number<1||number>500)throw new IOException("Неверный адрес страницы оглавления");
        TocPage parsed=parseTocPage(get(target).outerHtml(),target,id,number);
        if(parsed.chapters.isEmpty())throw new IOException("Страница оглавления Ranobes не содержит доступных глав: "+number);
        return parsed;
    }
    TocResult loadToc(String bookUrl,IntConsumer progress)throws IOException {
        String canonical=validBookUrl(bookUrl);if(canonical.isEmpty())throw new IOException("Некорректная ссылка на Ranobes");
        String bookId=novelId(canonical);
        TocPage first=tocPage(bookId,1);
        LinkedHashMap<String,Chapter> all=new LinkedHashMap<>();
        for(Chapter chapter:first.chapters)all.putIfAbsent(chapter.id,chapter);
        int completed=1;boolean partial=false;
        if(progress!=null)progress.accept(completed);
        int pages=Math.min(500,first.pagesCount);
        for(int i=2;i<=pages;i++){
            if(Thread.currentThread().isInterrupted()){partial=true;break;}
            try{
                TocPage next=tocPage(bookId,i);
                for(Chapter chapter:next.chapters)all.putIfAbsent(chapter.id,chapter);
                completed=i;if(progress!=null)progress.accept(i);
            }catch(IOException problem){partial=true;break;}
        }
        ArrayList<Chapter> ordered=new ArrayList<>(all.values());
        // Ranobes lists newest chapters first. Stored chapter indices must follow reading order.
        Collections.reverse(ordered);
        for(int i=0;i<ordered.size();i++)ordered.get(i).index=i+1;
        return new TocResult(ordered,pages,first.totalCount,completed,partial);
    }
    List<Chapter> chapters(String bookUrl)throws IOException{return loadToc(bookUrl,null).chapters;}
    static String extractChapterHtml(String markup,String url)throws IOException {
        Document page=Jsoup.parse(markup,url);
        String[] targets={"#arrticle","#article","#chapter-content",".chapter-content",".chapter_content",".text-content",".reader-content",".reader-content-body",".chapter_text","#text",".reading-content","article[itemprop=articleBody]"};
        Element content=null;for(String selector:targets){Element test=page.selectFirst(selector);if(test!=null&&test.text().length()>90){content=test;break;}}
        if(content==null)throw new IOException("Текст главы не найден. Не сохраняем меню сайта или комментарии вместо книги.");
        content=content.clone();
        content.select("script,style,form,nav,.navigation,.comments,.comment,.advert,.advertisement,.ads,.chapter-navigation,.nextprev,.share,.social,.notice").remove();
        for(Element a:content.select("a[href]"))a.unwrap();
        for(Element img:content.select("img")){String src=img.absUrl("src");if(!src.startsWith("https://"))img.remove();else img.attr("src",src).removeAttr("srcset").removeAttr("onload");}
        for(Element node:content.select("*")){
            node.removeAttr("onclick").removeAttr("style").removeAttr("class").removeAttr("id").removeAttr("onerror");
            if(!node.tagName().matches("(?i)p|h2|h3|h4|b|strong|em|i|u|blockquote|br|hr|ul|ol|li|img|div|span"))node.unwrap();
        }
        if(content.text().length()<90)throw new IOException("Глава слишком коротка или защищена");
        return content.html();
    }
    String chapterHtml(Chapter chapter)throws IOException {String canonical=validChapterUrl(chapter.url,chapter.url.replaceAll("^.*-(\\d+)/\\d+\\.html$","$1"));
        // Validate source chapter identity even if the book's URL is redirected.
        if(canonical.isEmpty())throw new IOException("Некорректная ссылка главы Ranobes");
        return extractChapterHtml(get(canonical).outerHtml(),canonical);
    }
    byte[] imageBytes(String source)throws IOException {HttpUrl url=HttpUrl.parse(source);if(url==null||!"https".equals(url.scheme())||url.host().isEmpty())throw new IOException("Недопустимая обложка");
        Request request=new Request.Builder().url(url).header("User-Agent",UA).header("Referer",DOMAIN+"/").build();
        try(Response response=client.newCall(request).execute()){
            if(!response.isSuccessful()||response.body()==null||response.body().contentLength()>4_000_000L)throw new IOException("Обложка недоступна");
            String type=response.header("Content-Type","");if(!type.startsWith("image/"))throw new IOException("Ссылка не содержит изображение");
            byte[] bytes=response.body().bytes();if(bytes.length>4_000_000||bytes.length<40)throw new IOException("Некорректный размер обложки");return bytes;
        }
    }
}
