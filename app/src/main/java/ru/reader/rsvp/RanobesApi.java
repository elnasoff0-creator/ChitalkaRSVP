package ru.reader.rsvp;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Public HTML adapter for Ranobes.net. Does not attempt to defeat antibot gates. */
final class RanobesApi {
    static final String DOMAIN="https://ranobes.net";
    private static final Pattern BOOK_LINK=Pattern.compile("^/novels/(\\d{3,12})-[a-z0-9-]+(?:-v\\d+)?\\.html$",Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAPTER_LINK=Pattern.compile("^/[^/?#]+-\\d{3,12}/(\\d{3,12})\\.html$",Pattern.CASE_INSENSITIVE);
    private static final Pattern VOL=Pattern.compile("(?:book|volume|vol\\.?|том|книга)\\s*(\\d+(?:[.,]\\d+)?)",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final String UA="Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(14,TimeUnit.SECONDS)
        .readTimeout(28,TimeUnit.SECONDS).followRedirects(false).build();
    static final class Title {
        String id="",url="",title="",author="",description="",cover="",status="";
        int estimatedChapters;
        Title(String id,String url,String title){this.id=id;this.url=url;this.title=title;}
    }
    static final class Chapter {
        String id="",url="",title="",volume="1";int index;
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
    private Document get(String url)throws IOException {
        HttpUrl parsed=HttpUrl.parse(url);
        if(parsed==null||!"https".equals(parsed.scheme())||!"ranobes.net".equals(parsed.host()))throw new IOException("Недопустимый адрес Ranobes.net");
        Request req=new Request.Builder().url(parsed).header("User-Agent",UA).header("Accept","text/html,application/xhtml+xml")
            .header("Accept-Language","en-US,en;q=0.8").build();
        try(Response response=client.newCall(req).execute()){
            int status=response.code();
            if(status==401||status==403||status==429||status==503)
                throw new IOException("Ranobes.net: HTTP "+status+". Сервер ограничил доступ; откройте сайт в браузере и повторите позже. Обход защиты не выполняется.");
            if(!response.isSuccessful())throw new IOException("Ranobes.net: HTTP "+status);
            ResponseBody body=response.body();if(body==null||body.contentLength()>6_000_000L)throw new IOException("Ответ Ranobes пуст или слишком велик");
            String markup=body.string();if(markup.length()>6_000_000)throw new IOException("HTML превышает предел");
            Document doc=Jsoup.parse(markup,url);
            String label=doc.title().toLowerCase(Locale.ROOT);
            if((label.contains("just a moment")||label.contains("ddos")||label.contains("antibot")||label.contains("антибот"))
                ||(doc.select("form[action*=captcha],#challenge-form,.cf-challenge").size()>0))
                throw new IOException("Ranobes запросил подтверждение в браузере. Автоматическое скачивание ограничено.");
            return doc;
        }
    }
    private static String pageTitle(Document page){Element meta=page.selectFirst("meta[property=og:title]");
        String text=meta==null?"":meta.attr("content");if(text.isEmpty()){Element h=page.selectFirst("h1");if(h!=null)text=h.text();}
        if(text.isEmpty())text=page.title();return text.replaceAll("\\s*[-|]\\s*(?:Ranobes.*|RANOBES.*)$","").trim();}
    private static String cover(Document page){for(String select:new String[]{"meta[property=og:image]","meta[name=twitter:image]",".cover img",".story-image img",".poster img",".fullimg img"}){
        Element e=page.selectFirst(select);if(e==null)continue;
        for(String attr:new String[]{"content","data-src","src"}){String url=e.absUrl(attr);if(url.startsWith("https://"))return url;}}
        return "";
    }
    private static String extractAuthor(Document page){Element a=page.selectFirst("[itemprop=author],.author,.book-author,.story_author,.story-author");
        return a==null?"":a.text().replaceFirst("(?i)^Author\\s*[:：]\\s*","").trim();}
    static List<Title> parseSearch(String markup,String base){Document page=Jsoup.parse(markup,base);LinkedHashMap<String,Title> found=new LinkedHashMap<>();
        for(Element a:page.select("a[href*=/novels/],a[href*=/novel/],a[href*=/books/]")){
            String url=validBookUrl(a.absUrl("href"));if(url.isEmpty())continue;
            String id=novelId(url);if(id.isEmpty()||found.containsKey(id))continue;
            Element parent=a.closest("article,.shortstory,.short-story,.story,.search-item,.item,.shortstory-body");
            String name=a.text().trim();if(name.length()<3&&parent!=null){Element heading=parent.selectFirst("h2,h3,.title");if(heading!=null)name=heading.text();}
            if(name.length()<3||name.length()>240)continue;
            Title title=new Title(id,url,name);
            if(parent!=null){title.cover=cover(parent.ownerDocument()); // refined below from the card itself
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
        HttpUrl address=HttpUrl.parse(DOMAIN+"/index.php").newBuilder().addQueryParameter("do","search")
            .addQueryParameter("subaction","search").addQueryParameter("story",query.trim())
            .addQueryParameter("page",Integer.toString(Math.max(1,page))).build();
        Document response=get(address.toString());return parseSearch(response.outerHtml(),DOMAIN);
    }
    Title title(String url)throws IOException {String canonical=validBookUrl(url);if(canonical.isEmpty())throw new IOException("Укажите ссылку https://ranobes.net/novels/…html");
        Document page=get(canonical);Title title=new Title(novelId(canonical),canonical,pageTitle(page));
        title.cover=cover(page);title.author=extractAuthor(page);
        Element info=page.selectFirst(".description,.full-story,.fullstory,.story-description,[itemprop=description],meta[name=description]");
        if(info!=null)title.description=info.tagName().equals("meta")?info.attr("content"):info.text();
        if(title.description.length()>2500)title.description=title.description.substring(0,2500);
        return title;
    }
    static List<Chapter> parseChapters(String markup,String base,String id){
        Document page=Jsoup.parse(markup,base);LinkedHashMap<String,Chapter> chapters=new LinkedHashMap<>();
        for(Element a:page.select("a[href]")){
            String url=validChapterUrl(a.absUrl("href"),id);if(url.isEmpty())continue;
            String chapterId=chapterId(url);if(chapters.containsKey(chapterId))continue;
            String text=a.text().trim();if(text.length()<2)text=a.attr("title").trim();
            if(text.length()<2)continue; // omit navigation-only numeric links
            String volume="1";Matcher vol=VOL.matcher(text);if(vol.find())volume=vol.group(1).replace(',','.');
            chapters.put(chapterId,new Chapter(chapterId,url,text,volume,chapters.size()+1));
        }
        return new ArrayList<>(chapters.values());
    }
    List<Chapter> chapters(String bookUrl)throws IOException {
        String canonical=validBookUrl(bookUrl);if(canonical.isEmpty())throw new IOException("Некорректная ссылка на Ranobes");
        String id=novelId(canonical);Document page=get(canonical);
        LinkedHashMap<String,Chapter> chapters=new LinkedHashMap<>();
        ArrayList<String> pending=new ArrayList<>();pending.add(canonical);Set<String> visited=new HashSet<>();
        for(int index=0;index<pending.size()&&index<30;index++){
            String address=pending.get(index);if(!visited.add(address))continue;
            Document current=index==0?page:get(address);
            for(Chapter ch:parseChapters(current.outerHtml(),address,id))chapters.putIfAbsent(ch.id,ch);
            // Crawl only same-book, explicitly linked pagination pages.
            for(Element link:current.select(".pagination a[href],.pages a[href],a[rel=next][href],a[href*=page]")){
                String url=link.absUrl("href");HttpUrl parsed=HttpUrl.parse(url);
                if(parsed==null||!"https".equals(parsed.scheme())||!"ranobes.net".equals(parsed.host()))continue;
                String path=parsed.encodedPath();
                if(!path.equals(HttpUrl.parse(canonical).encodedPath()))continue;
                if((parsed.queryParameter("page")!=null||parsed.queryParameter("start")!=null)&&!pending.contains(url))pending.add(url);
            }
        }
        if(chapters.isEmpty())throw new IOException("Оглавление Ranobes не найдено в публичном HTML. Сайт может загружать главы динамически или ограничивать доступ.");
        return new ArrayList<>(chapters.values());
    }
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
