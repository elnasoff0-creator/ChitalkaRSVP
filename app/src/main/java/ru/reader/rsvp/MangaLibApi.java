package ru.reader.rsvp;

import android.net.Uri;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Public, unauthenticated MangaLib JSON adapter. Never bypasses access restrictions. */
final class MangaLibApi {
    static final String BASE="https://api.cdnlibs.org/api";
    static final class Title {
        String slug,title,cover;
        Title(String slug,String title,String cover){this.slug=slug;this.title=title;this.cover=cover;}
    }
    static final class Chapter {
        String volume,number,title;long branch;
        Chapter(String volume,String number,String title,long branch){this.volume=volume;this.number=number;this.title=title;this.branch=branch;}
        String display(){return "Том "+volume+" · Глава "+number+(title.isEmpty()?"":" — "+title);}
        String key(){return volume+":"+number+":"+branch;}
    }
    static final class Page {
        final String url;
        final int number;
        final List<String> sources;
        Page(String url,int number){this(java.util.Collections.singletonList(url),number);}
        Page(List<String> urls,int number){
            if(urls==null||urls.isEmpty())throw new IllegalArgumentException("No image server");
            this.url=urls.get(0);this.sources=new ArrayList<>(urls);this.number=number;
        }
    }
    static final class AccessException extends IOException {AccessException(String msg){super(msg);} }
    static final class RateLimitException extends IOException {RateLimitException(String msg){super(msg);} }
    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(18,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS)
        .followRedirects(true).build();
    private static final String USER_AGENT="Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    static String validSlug(String slug){
        if(slug==null||!slug.matches("[0-9]{1,12}--[a-zA-Z0-9_-]{1,180}"))throw new IllegalArgumentException("Некорректный идентификатор MangaLib");
        return slug;
    }
    static String slugFromLink(String entered){
        if(entered==null)return "";
        String q=entered.trim();
        if(q.matches("[0-9]{1,12}--[a-zA-Z0-9_-]{1,180}"))return q;
        if(q.matches("(?i)^(www\\.)?mangalib\\.(me|org)/.*"))q="https://"+q;
        try{Uri u=Uri.parse(q);if(!"https".equalsIgnoreCase(u.getScheme())||!isMangaLibHost(u.getHost()))return "";
            for(String part:u.getPathSegments())if(part.matches("[0-9]{1,12}--[a-zA-Z0-9_-]{1,180}"))return part;
        }catch(Exception ignored){}
        return "";
    }
    private static boolean isMangaLibHost(String host){
        return host!=null&&(host.equalsIgnoreCase("mangalib.me")||host.equalsIgnoreCase("www.mangalib.me")
            ||host.equalsIgnoreCase("mangalib.org")||host.equalsIgnoreCase("www.mangalib.org"));
    }
    static boolean isTitleLink(String entered){
        if(entered==null)return false;
        String value=entered.trim();
        if(value.matches("(?i)^(www\\.)?mangalib\\.(me|org)/.*"))value="https://"+value;
        try{Uri u=Uri.parse(value);
            if(!"https".equalsIgnoreCase(u.getScheme())||!isMangaLibHost(u.getHost()))return false;
            java.util.List<String> parts=u.getPathSegments();
            return parts.size()>=2&&parts.contains("manga");
        }catch(Exception ignored){return false;}
    }
    /** Older MangaLib links contain a text slug without the numeric identifier. Search by its title words. */
    static String queryFromLink(String entered){
        if(entered==null)return "";
        String value=entered.trim();
        if(value.matches("(?i)^(www\\.)?mangalib\\.(me|org)/.*"))value="https://"+value;
        try{Uri u=Uri.parse(value);
            if(!"https".equalsIgnoreCase(u.getScheme())||!isMangaLibHost(u.getHost()))return value;
            java.util.List<String> parts=u.getPathSegments();
            if(parts.isEmpty())return "";
            String last=parts.get(parts.size()-1);
            if("manga".equals(last)||"ru".equals(last))return "";
            if(last.matches("[0-9]{1,12}--[a-zA-Z0-9_-]{1,180}"))return "";
            return last.matches("[a-zA-Z0-9_-]{2,180}")?last.replace('-', ' ').replace('_',' '):"";
        }catch(Exception ignored){return value;}
    }
    static JSONObject objectData(JSONObject root){JSONObject d=root.optJSONObject("data");return d!=null?d:root;}
    static JSONArray listData(JSONObject root){
        JSONArray a=root.optJSONArray("data");if(a!=null)return a;
        JSONObject d=root.optJSONObject("data");if(d!=null){a=d.optJSONArray("items");if(a==null)a=d.optJSONArray("data");if(a==null)a=d.optJSONArray("list");}
        return a==null?new JSONArray():a;
    }
    private JSONObject getJson(HttpUrl url) throws IOException {
        if(url==null||!"https".equals(url.scheme())||!"api.cdnlibs.org".equals(url.host()))throw new IOException("Недопустимый адрес API");
        Request req=new Request.Builder().url(url)
            .header("Accept","application/json")
            .header("Site-Id","1")
            .header("Referer","https://mangalib.me/")
            .header("User-Agent",USER_AGENT).build();
        try(Response r=client.newCall(req).execute()){
            if(!"api.cdnlibs.org".equals(r.request().url().host()))throw new IOException("API перенаправил запрос на другой сервер");
            if(r.code()==401||r.code()==403)throw new AccessException("MangaLib: доступ к API ограничен (HTTP "+r.code()+"). Проверьте доступность сайта и разрешения");
            if(r.code()==429)throw new IOException("MangaLib: ограничение частоты запросов (429), повторите позже");
            if(!r.isSuccessful())throw new IOException("MangaLib: HTTP "+r.code()+" ("+url.encodedPath()+")");
            ResponseBody b=r.body();if(b==null)throw new IOException("Пустой ответ MangaLib");
            if(b.contentLength()>32_000_000L)throw new IOException("Слишком большой ответ API");
            String s=b.string();if(s.length()>32_000_000)throw new IOException("Слишком большой ответ API");
            try{
                JSONObject json=new JSONObject(s);
                if(json.optBoolean("error",false))throw new IOException("MangaLib: "+json.optString("message","ошибка API"));
                return json;
            }catch(IOException e){throw e;}
            catch(Exception e){throw new IOException("MangaLib вернул не JSON: проверьте сеть или доступ к сайту",e);}
        }
    }
    List<Title> search(String q) throws IOException {
        HttpUrl url=HttpUrl.parse(BASE+"/manga").newBuilder().addQueryParameter("q",q).addQueryParameter("site_id[]","1").build();
        JSONArray raw=listData(getJson(url));ArrayList<Title> result=new ArrayList<>();
        for(int i=0;i<raw.length()&&result.size()<70;i++){
            JSONObject o=raw.optJSONObject(i);if(o==null)continue;
            String slug=o.optString("slug_url","");
            if(slug.isEmpty()&&o.optLong("id",0)>0&&!o.optString("slug","").isEmpty())
                slug=o.optLong("id")+"--"+o.optString("slug");
            try{validSlug(slug);}catch(Exception ignored){continue;}
            String title=o.optString("rus_name","");if(title.isEmpty())title=o.optString("name",slug);
            JSONObject cover=o.optJSONObject("cover");String image=cover==null?"":cover.optString("thumbnail",cover.optString("default",""));
            result.add(new Title(slug,title,image));
        }
        return result;
    }
    Title titleBySlug(String slug) throws IOException {
        JSONObject meta=objectData(getJson(HttpUrl.parse(BASE+"/manga/"+validSlug(slug))));
        String name=meta.optString("rus_name","");
        if(name.isEmpty())name=meta.optString("name",slug);
        JSONObject cover=meta.optJSONObject("cover");
        String image=cover==null?"":cover.optString("thumbnail",cover.optString("default",""));
        return new Title(slug,name,image);
    }
    List<Chapter> chapters(String slug) throws IOException {
        HttpUrl url=HttpUrl.parse(BASE+"/manga/"+validSlug(slug)+"/chapters");
        JSONArray raw=listData(getJson(url));ArrayList<Chapter> chapters=new ArrayList<>();
        for(int i=0;i<raw.length();i++){
            JSONObject o=raw.optJSONObject(i);if(o==null)continue;
            String volume=o.optString("volume","1"),num=o.optString("number","0"),title=o.optString("name","");
            if(volume.isEmpty())volume="1";if(num.isEmpty())num="0";
            JSONArray branches=o.optJSONArray("branches");
            if(branches!=null&&branches.length()>0){
                JSONObject first=branches.optJSONObject(0);
                // Multiple translations are different chapters; first public branch by default.
                long b=first==null?0:first.optLong("branch_id",0);
                chapters.add(new Chapter(volume,num,title,b));
            }else chapters.add(new Chapter(volume,num,title,o.optLong("branch_id",0)));
        }
        chapters.sort((a,b)->{
            int r=Double.compare(number(a.volume),number(b.volume));if(r!=0)return r;
            r=Double.compare(number(a.number),number(b.number));return r!=0?r:a.title.compareToIgnoreCase(b.title);
        });
        return chapters;
    }
    private static double number(String s){try{return Double.parseDouble(s.replace(',','.'));}catch(Exception ignored){return 0;}}
    List<Page> pages(String slug,Chapter ch) throws IOException {
        HttpUrl.Builder u=HttpUrl.parse(BASE+"/manga/"+validSlug(slug)+"/chapter").newBuilder()
            .addQueryParameter("number",ch.number).addQueryParameter("volume",ch.volume);
        if(ch.branch>0)u.addQueryParameter("branch_id",String.valueOf(ch.branch));
        JSONObject data=objectData(getJson(u.build()));
        JSONArray raw=data.optJSONArray("pages");if(raw==null||raw.length()==0)throw new AccessException("Для главы нет доступных страниц");
        List<String> servers=null;ArrayList<Page> pages=new ArrayList<>();
        for(int i=0;i<raw.length();i++){
            JSONObject o=raw.optJSONObject(i);if(o==null)continue;
            String path=o.optString("url","");if(path.isEmpty())path=o.optString("image","");
            if(servers==null){
                // Absolute URLs also get alternative CDNs when MangaLib exposes them.
                // If constants is temporarily unavailable, a valid absolute URL still works.
                if(path.startsWith("https://")){
                    try{servers=imageServers();}catch(IOException ignored){servers=new ArrayList<>();}
                }else servers=imageServers();
            }
            List<String> candidates=imageUrls(path,servers);
            if(candidates.isEmpty())throw new IOException("Не удалось определить адрес страницы "+(i+1));
            pages.add(new Page(candidates,i+1));
        }
        if(pages.isEmpty())throw new IOException("Нет изображений главы");
        return pages;
    }
    private List<String> imageServers() throws IOException {
        HttpUrl u=HttpUrl.parse(BASE+"/constants").newBuilder().addQueryParameter("fields[]","imageServers").build();
        JSONObject data=objectData(getJson(u));JSONArray a=data.optJSONArray("imageServers");
        ArrayList<String> out=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);String host=o==null?a.optString(i,""):o.optString("url","");
            if(allowedImage(host))out.add(host);
        }
        return out;
    }
    private static boolean allowedImage(String value){
        try{HttpUrl u=HttpUrl.parse(value);if(u==null||!"https".equals(u.scheme()))return false;
            String h=u.host().toLowerCase(Locale.ROOT);
            return h.equals("mixlib.me")||h.endsWith(".mixlib.me")||h.equals("imglib.info")||h.endsWith(".imglib.info")||h.equals("cdnlibs.org")||h.endsWith(".cdnlibs.org");
        }catch(Exception ignored){return false;}
    }
    private static List<String> imageUrls(String path,List<String> servers){
        LinkedHashSet<String> candidates=new LinkedHashSet<>();
        if(path==null||path.isEmpty())return new ArrayList<>();
        HttpUrl original=HttpUrl.parse(path);
        if(original!=null){
            if(!allowedImage(original.toString()))return new ArrayList<>();
            candidates.add(original.toString());
            // The official server list can expose mirrors with the same image path.
            path=original.encodedPath(); // Do not copy URL query tokens to another CDN.
        }else if(path.startsWith("//")||path.startsWith("http:")){
            return new ArrayList<>();
        }
        String relative=path.startsWith("/")?path:"/"+path;
        if(servers!=null)for(String server:servers){
            HttpUrl base=HttpUrl.parse(server);
            if(base==null||!allowedImage(server))continue;
            String joined=base.scheme()+"://"+base.host()+(base.port()==443?"":":"+base.port())
                +base.encodedPath().replaceAll("/+$","")+relative;
            if(allowedImage(joined))candidates.add(joined);
        }
        return new ArrayList<>(candidates);
    }

    private static final long MAX_IMAGE_BYTES=25_000_000L;
    private static byte[] readImageBytes(ResponseBody body) throws IOException {
        if(body==null)throw new IOException("Пустой ответ CDN");
        if(body.contentLength()>MAX_IMAGE_BYTES)throw new IOException("Изображение превышает 25 МБ");
        try(InputStream input=body.byteStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;long total=0;
            while((n=input.read(buffer))!=-1){
                total+=n;
                if(total>MAX_IMAGE_BYTES)throw new IOException("Изображение превышает 25 МБ");
                output.write(buffer,0,n);
            }
            return output.toByteArray();
        }
    }
    private static String imageFormat(byte[] b){
        if(b==null||b.length<16)return "unknown";
        if((b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return "jpeg";
        if((b[0]&255)==137&&b[1]=='P'&&b[2]=='N'&&b[3]=='G'
                &&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10)return "png";
        if(b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'
                &&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P')return "webp";
        if(b[0]=='G'&&b[1]=='I'&&b[2]=='F'&&(b[3]=='8'))return "gif";
        if(b[4]=='f'&&b[5]=='t'&&b[6]=='y'&&b[7]=='p'&&b[8]=='a'&&b[9]=='v'&&b[10]=='i'&&b[11]=='f')return "avif";
        int i=0;while(i<b.length&&i<64&&(b[i]==' '||b[i]=='\n'||b[i]=='\r'||b[i]=='\t'))i++;
        if(i<b.length&&(b[i]=='<'||b[i]=='{'||b[i]=='['))return "html/json";
        return "unknown";
    }
    private static String safeMime(Response response){
        String type=response.header("Content-Type", "");
        if(type.length()>72)type=type.substring(0,72);
        return type.replace('\n',' ').replace('\r',' ');
    }
    private byte[] fetchImage(String url) throws IOException {
        if(!allowedImage(url))throw new IOException("Недопустимый сервер изображения");
        Request req=new Request.Builder().url(url)
            .header("Referer","https://mangalib.me/")
            // Do not negotiate AVIF: ComicReader supports JPEG/PNG/WebP/GIF on API 24+.
            .header("Accept","image/jpeg,image/png,image/webp,image/gif;q=0.8")
            .header("User-Agent",USER_AGENT).build();
        try(Response r=client.newCall(req).execute()){
            int code=r.code();
            if(code==401||code==403)throw new AccessException("MangaLib: доступ к странице ограничен (HTTP "+code+")");
            if(code==429)throw new RateLimitException("MangaLib: ограничение запросов (HTTP 429), повторить позже");
            if(!allowedImage(r.request().url().toString()))throw new IOException("CDN перенаправил на неизвестный сервер");
            if(!r.isSuccessful())throw new IOException("HTTP "+code+", Content-Type="+safeMime(r));
            byte[] data=readImageBytes(r.body());
            String format=imageFormat(data);
            if("avif".equals(format))throw new IOException("CDN вернул AVIF, не поддерживаемый этой версией; Content-Type="+safeMime(r));
            if("unknown".equals(format)||"html/json".equals(format))
                throw new IOException("Вместо изображения получены "+format+" (HTTP "+code+", Content-Type="+safeMime(r)+")");
            BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(data,0,data.length,opts);
            if(opts.outWidth<=0||opts.outHeight<=0)
                throw new IOException("Повреждено изображение "+format+" (HTTP "+code+", Content-Type="+safeMime(r)+")");
            return data;
        }
    }
    byte[] image(Page page) throws IOException {
        IOException last=null;
        // No more than four official, allowlisted endpoints for a single public page.
        for(int i=0;i<page.sources.size()&&i<4;i++){
            String url=page.sources.get(i);
            try{return fetchImage(url);}
            catch(AccessException denied){throw denied;} // Never bypass 401/403 via mirrors.
            catch(RateLimitException throttled){throw throttled;} // Do not bypass 429; retry later.
            catch(IOException ex){
                String host=HttpUrl.parse(url)==null?"неизвестный CDN":HttpUrl.parse(url).host();
                last=new IOException("CDN "+host+": "+ex.getMessage(),ex);
            }
        }
        throw new IOException("Страница "+page.number+": все доступные CDN отклонили изображение; "
            +(last==null?"нет серверов":last.getMessage()),last);
    }
    byte[] image(String url) throws IOException {return image(new Page(url,1));}
    static String extension(byte[] b){
        String format=imageFormat(b);
        if("jpeg".equals(format))return "jpg";
        if("png".equals(format)||"webp".equals(format)||"gif".equals(format))return format;
        throw new IllegalArgumentException("Неизвестный формат изображения");
    }
}
