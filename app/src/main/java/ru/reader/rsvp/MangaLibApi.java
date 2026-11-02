package ru.reader.rsvp;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import java.io.IOException;
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
    static final class Page { String url;int number;Page(String url,int number){this.url=url;this.number=number;} }
    static final class AccessException extends IOException {AccessException(String msg){super(msg);} }
    private final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(35,TimeUnit.SECONDS).followRedirects(false).build();

    static String validSlug(String slug){
        if(slug==null||!slug.matches("[0-9]{1,12}--[a-zA-Z0-9_-]{1,180}"))throw new IllegalArgumentException("Некорректный идентификатор MangaLib");
        return slug;
    }
    static String slugFromLink(String entered){
        if(entered==null)return "";
        String q=entered.trim();
        if(q.matches("[0-9]{1,12}--[a-zA-Z0-9_-]{1,180}"))return q;
        try{Uri u=Uri.parse(q);if(!"https".equalsIgnoreCase(u.getScheme())||!"mangalib.me".equalsIgnoreCase(u.getHost()))return "";
            for(String part:u.getPathSegments())if(part.matches("[0-9]{1,12}--[a-zA-Z0-9_-]{1,180}"))return part;
        }catch(Exception ignored){}
        return "";
    }
    static JSONObject objectData(JSONObject root){JSONObject d=root.optJSONObject("data");return d!=null?d:root;}
    static JSONArray listData(JSONObject root){
        JSONArray a=root.optJSONArray("data");if(a!=null)return a;
        JSONObject d=root.optJSONObject("data");if(d!=null){a=d.optJSONArray("items");if(a==null)a=d.optJSONArray("data");}
        return a==null?new JSONArray():a;
    }
    private JSONObject getJson(HttpUrl url) throws IOException {
        if(url==null||!"https".equals(url.scheme())||!"api.cdnlibs.org".equals(url.host()))throw new IOException("Недопустимый адрес API");
        Request req=new Request.Builder().url(url).header("Accept","application/json").header("Site-Id","1").header("User-Agent","PROChitalka/1.11.125 (Android; MangaLib public client)").build();
        try(Response r=client.newCall(req).execute()){
            if(r.code()==401||r.code()==403)throw new AccessException("Глава недоступна без авторизации или разрешения");
            if(r.code()==429)throw new IOException("MangaLib: слишком много запросов (429)");
            if(!r.isSuccessful())throw new IOException("MangaLib: HTTP "+r.code());
            ResponseBody b=r.body();if(b==null)throw new IOException("Пустой ответ MangaLib");
            String s=b.string();if(s.length()>8_000_000)throw new IOException("Слишком большой ответ API");
            try{return new JSONObject(s);}catch(Exception e){throw new IOException("Некорректный JSON MangaLib",e);}
        }
    }
    List<Title> search(String q) throws IOException {
        HttpUrl url=HttpUrl.parse(BASE+"/manga").newBuilder().addQueryParameter("q",q).addQueryParameter("site_id[]","1").build();
        JSONArray raw=listData(getJson(url));ArrayList<Title> result=new ArrayList<>();
        for(int i=0;i<raw.length()&&result.size()<70;i++){
            JSONObject o=raw.optJSONObject(i);if(o==null)continue;
            String slug=o.optString("slug_url","");
            try{validSlug(slug);}catch(Exception ignored){continue;}
            String title=o.optString("rus_name","");if(title.isEmpty())title=o.optString("name",slug);
            JSONObject cover=o.optJSONObject("cover");String image=cover==null?"":cover.optString("thumbnail","");
            result.add(new Title(slug,title,image));
        }
        return result;
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
            if(servers==null)servers=path.startsWith("https://")?new ArrayList<>():imageServers();
            String absolute=imageUrl(path,servers);
            if(absolute.isEmpty())throw new IOException("Не удалось определить адрес страницы "+(i+1));
            pages.add(new Page(absolute,i+1));
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
    private static String imageUrl(String path,List<String> servers){
        if(path.startsWith("https://"))return allowedImage(path)?path:"";
        String p=path.startsWith("/")?path:"/"+path;
        for(String server:servers){String combined=server.replaceAll("/+$","")+p;if(allowedImage(combined))return combined;}
        return "";
    }
    byte[] image(String url) throws IOException {
        if(!allowedImage(url))throw new IOException("Недопустимый сервер изображения");
        HttpUrl u=HttpUrl.parse(url);Request req=new Request.Builder().url(u)
            .header("User-Agent","PROChitalka/1.11.125 (Android)").build();
        try(Response r=client.newCall(req).execute()){
            if(r.code()==401||r.code()==403)throw new AccessException("Нет разрешения скачивать эту страницу");
            if(!r.isSuccessful())throw new IOException("Страница: HTTP "+r.code());
            String mime=r.header("Content-Type","").toLowerCase(Locale.ROOT);
            if(!mime.startsWith("image/"))throw new IOException("Сервер вернул не изображение");
            ResponseBody body=r.body();if(body==null||body.contentLength()>25_000_000L)throw new IOException("Слишком большая страница");
            byte[] data=body.bytes();if(data.length<128||data.length>25_000_000)throw new IOException("Некорректный размер страницы");
            // Check a recognizable image signature, not only the response MIME.
            boolean jpeg=(data[0]&255)==255&&(data[1]&255)==216;
            boolean png=data[0]==(byte)137&&data[1]=='P'&&data[2]=='N'&&data[3]=='G';
            boolean webp=data[0]=='R'&&data[1]=='I'&&data[2]=='F'&&data[3]=='F'&&data[8]=='W';
            boolean gif=data[0]=='G'&&data[1]=='I'&&data[2]=='F';
            if(!jpeg&&!png&&!webp&&!gif)throw new IOException("Недопустимые данные изображения");
            return data;
        }
    }
    static String extension(byte[] b){return b[0]==(byte)137?"png":b[0]=='R'?"webp":b[0]=='G'?"gif":"jpg";}
}
