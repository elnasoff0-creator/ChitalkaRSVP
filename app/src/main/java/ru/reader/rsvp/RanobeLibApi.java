package ru.reader.rsvp;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Read-only public RanobeLIB adapter. No authentication or access-control bypass. */
final class RanobeLibApi {
    static final String BASE="https://api.cdnlibs.org/api";
    static final String AGENT="Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
    static final class Title {
        String slug="",title="",author="",description="",cover="",status="",genres="";
        int chapters=0;
        Title(String slug,String title){this.slug=slug;this.title=title;}
        JSONObject json(){JSONObject o=new JSONObject();try{o.put("slug",slug);o.put("title",title);o.put("author",author);o.put("description",description);o.put("cover",cover);o.put("status",status);o.put("genres",genres);o.put("chapters",chapters);}catch(Exception ignored){}return o;}
        static Title fromJson(JSONObject o){Title t=new Title(o.optString("slug"),o.optString("title"));t.author=o.optString("author");t.description=o.optString("description");t.cover=o.optString("cover");t.status=o.optString("status");t.genres=o.optString("genres");t.chapters=o.optInt("chapters");return t;}
    }
    static final class Chapter {
        final String volume,number,name;final long branch;
        Chapter(String volume,String number,String name,long branch){this.volume=volume;this.number=number;this.name=name;this.branch=branch;}
        String key(){return volume+"|"+number+"|"+branch;}
        String display(){return "Глава "+number+(name.isEmpty()?"":" · "+name)+(branch>0?" [перевод #"+branch+"]":"");}
        String fullTitle(){return "Том "+volume+" · "+display();}
    }
    static class AccessException extends IOException {AccessException(String message){super(message);} }
    static class RateException extends IOException {RateException(String message){super(message);} }
    private final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(18,TimeUnit.SECONDS)
        .readTimeout(50,TimeUnit.SECONDS).followRedirects(false).build();
    static String validSlug(String slug){if(slug==null||!slug.matches("[0-9]{1,12}--[A-Za-z0-9_-]{1,180}"))throw new IllegalArgumentException("Некорректная ссылка на RanobeLIB");return slug;}
    static String slugFromLink(String input){
        if(input==null)return "";
        String value=input.trim();if(value.matches("[0-9]{1,12}--[A-Za-z0-9_-]{1,180}"))return value;
        if(value.matches("(?i)^(www\\.)?ranobelib\\.(me|org)/.*"))value="https://"+value;
        try{Uri uri=Uri.parse(value);String host=uri.getHost();if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||!(host.equalsIgnoreCase("ranobelib.me")||host.equalsIgnoreCase("www.ranobelib.me")||host.equalsIgnoreCase("ranobelib.org")))return "";
            for(String part:uri.getPathSegments())if(part.matches("[0-9]{1,12}--[A-Za-z0-9_-]{1,180}"))return part;
        }catch(Exception ignored){}return "";
    }
    private static JSONObject objectData(JSONObject root){JSONObject data=root.optJSONObject("data");return data==null?root:data;}
    private static JSONArray listData(JSONObject root){JSONArray array=root.optJSONArray("data");if(array!=null)return array;JSONObject data=root.optJSONObject("data");if(data!=null){array=data.optJSONArray("items");if(array==null)array=data.optJSONArray("data");if(array==null)array=data.optJSONArray("list");}return array==null?new JSONArray():array;}
    private JSONObject getJson(HttpUrl url) throws IOException {
        if(url==null||!"https".equals(url.scheme())||!"api.cdnlibs.org".equals(url.host()))throw new IOException("Недопустимый адрес API");
        Request request=new Request.Builder().url(url).header("Accept","application/json")
            .header("Site-Id","3").header("Referer","https://ranobelib.me/")
            .header("User-Agent",AGENT).build();
        try(Response response=client.newCall(request).execute()){
            if(response.code()==401||response.code()==403)throw new AccessException("RanobeLIB ограничил доступ (HTTP "+response.code()+"). Доступны только публичные главы");
            if(response.code()==429)throw new RateException("HTTP 429: слишком много запросов; загрузка будет повторена с задержкой");
            if(!response.isSuccessful())throw new IOException("RanobeLIB: HTTP "+response.code()+" · "+url.encodedPath());
            ResponseBody body=response.body();if(body==null||body.contentLength()>25_000_000L)throw new IOException("Пустой или слишком большой ответ RanobeLIB");
            String data=body.string();if(data.length()>25_000_000)throw new IOException("Ответ API превышает предел");
            try{JSONObject json=new JSONObject(data);if(json.optBoolean("error"))throw new IOException(json.optString("message","Ошибка RanobeLIB"));return json;}
            catch(IOException ex){throw ex;}catch(Exception ex){throw new IOException("RanobeLIB вернул не JSON; сайт или защита недоступны",ex);}
        }
    }
    List<Title> search(String query,int page) throws IOException {
        HttpUrl url=HttpUrl.parse(BASE+"/manga").newBuilder().addQueryParameter("q",query)
            .addQueryParameter("site_id[]","3").addQueryParameter("page",String.valueOf(Math.max(1,page))).build();
        JSONArray rows=listData(getJson(url));ArrayList<Title> result=new ArrayList<>();
        for(int i=0;i<rows.length()&&result.size()<80;i++){
            JSONObject o=rows.optJSONObject(i);if(o==null)continue;
            Title title=parseTitle(o);try{validSlug(title.slug);}catch(Exception ignored){continue;}
            result.add(title);
        }
        return result;
    }
    Title title(String slug) throws IOException {
        HttpUrl url=HttpUrl.parse(BASE+"/manga/"+validSlug(slug)).newBuilder()
            .addQueryParameter("fields[]","summary").addQueryParameter("fields[]","authors")
            .addQueryParameter("fields[]","genres").addQueryParameter("fields[]","chap_count")
            .build();
        Title result=parseTitle(objectData(getJson(url)));
        if(result.slug.isEmpty())result.slug=slug;
        return result;
    }
    static Title parseTitle(JSONObject obj){
        String slug=obj.optString("slug_url","");if(slug.isEmpty())slug=obj.optString("slug","");
        if(!slug.matches("[0-9]{1,12}--.*")&&obj.optLong("id",0)>0)slug=obj.optLong("id")+"--"+slug;
        String name=obj.optString("rus_name","");if(name.isEmpty())name=obj.optString("name",slug);
        Title result=new Title(slug,name);
        result.chapters=obj.optInt("chap_count",obj.optInt("chapter_count",0));
        JSONObject items=obj.optJSONObject("items_count");
        if(items!=null)result.chapters=items.optInt("uploaded",result.chapters);
        JSONObject cover=obj.optJSONObject("cover");
        if(cover!=null){String[] variants={"default","md","thumbnail"};for(String v:variants){String c=cover.optString(v,"");if(!c.isEmpty()&&!"null".equals(c)){result.cover=coverUrl(c);if(!result.cover.isEmpty())break;}}}
        Object summary=obj.opt("summary");
        result.description=summary instanceof JSONObject?plainDocument((JSONObject)summary)
            :summary instanceof String?Jsoup.parse((String)summary).text():"";
        if(result.description.length()>2500)result.description=result.description.substring(0,2500);
        JSONArray authors=obj.optJSONArray("authors");
        if(authors!=null){StringBuilder b=new StringBuilder();for(int i=0;i<authors.length();i++){
            JSONObject a=authors.optJSONObject(i);String namePart=a==null?authors.optString(i,""):a.optString("name",a.optString("rus_name",""));
            if(!namePart.isEmpty()){if(b.length()>0)b.append(", ");b.append(namePart);}
        }result.author=b.toString();}
        Object status=obj.opt("status");if(status instanceof JSONObject){JSONObject s=(JSONObject)status;result.status=s.optString("label",s.optString("name",""));}
        else if(status!=null)result.status=status.toString();
        JSONArray genres=obj.optJSONArray("genres");if(genres!=null){StringBuilder b=new StringBuilder();for(int i=0;i<genres.length();i++){
            JSONObject g=genres.optJSONObject(i);String value=g==null?genres.optString(i,""):g.optString("name",g.optString("label",""));
            if(!value.isEmpty()){if(b.length()>0)b.append(", ");b.append(value);}
        }result.genres=b.toString();}
        return result;
    }
    static String coverUrl(String input){
        if(input==null||input.isEmpty())return "";
        String value=input.trim();if(value.startsWith("//"))value="https:"+value;
        if(value.startsWith("/"))value="https://cover.cdnlibs.org"+value;
        if(!value.startsWith("https://")&&!value.contains(":"))value="https://cover.cdnlibs.org/"+value;
        try{HttpUrl url=HttpUrl.parse(value);if(url==null||!"https".equals(url.scheme()))return "";
            String h=url.host();if(h.equals("cover.cdnlibs.org")||h.endsWith(".cdnlibs.org")||h.equals("ranobelib.me")||h.endsWith(".ranobelib.me")||h.endsWith(".mixlib.me")||h.endsWith(".imglib.info"))return value;
        }catch(Exception ignored){}return "";
    }
    List<Chapter> chapters(String slug) throws IOException {
        JSONObject root=getJson(HttpUrl.parse(BASE+"/manga/"+validSlug(slug)+"/chapters"));
        JSONArray rows=listData(root);ArrayList<Chapter> out=new ArrayList<>();HashSet<String> seen=new HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject o=rows.optJSONObject(i);if(o==null)continue;
            String vol=o.optString("volume","1"),num=o.optString("number","0"),name=o.optString("name","");
            if(vol.isEmpty())vol="1";if(num.isEmpty())num="0";
            JSONArray branches=o.optJSONArray("branches");
            if(branches!=null&&branches.length()>0){for(int j=0;j<branches.length();j++){
                JSONObject b=branches.optJSONObject(j);long id=b==null?0:b.optLong("branch_id",0);
                Chapter chapter=new Chapter(vol,num,name,id);if(seen.add(chapter.key()))out.add(chapter);
            }}else{Chapter chapter=new Chapter(vol,num,name,o.optLong("branch_id",0));if(seen.add(chapter.key()))out.add(chapter);}
        }
        out.sort((a,b)->{int d=Double.compare(numeric(a.volume),numeric(b.volume));if(d!=0)return d;
            d=Double.compare(numeric(a.number),numeric(b.number));return d!=0?d:Long.compare(a.branch,b.branch);});
        return out;
    }
    private static double numeric(String v){try{return Double.parseDouble(v.replace(',','.'));}catch(Exception ignored){return 0;}}
    String chapterHtml(String slug,Chapter chapter) throws IOException {
        HttpUrl.Builder b=HttpUrl.parse(BASE+"/manga/"+validSlug(slug)+"/chapter").newBuilder()
            .addQueryParameter("volume",chapter.volume).addQueryParameter("number",chapter.number);
        if(chapter.branch>0)b.addQueryParameter("branch_id",String.valueOf(chapter.branch));
        JSONObject data=objectData(getJson(b.build()));
        if(!data.has("content")){JSONObject nested=data.optJSONObject("chapter");if(nested!=null)data=nested;}
        Object raw=data.opt("content");
        if(raw==null||JSONObject.NULL.equals(raw))throw new AccessException("Текст главы отсутствует или недоступен");
        String source=raw instanceof JSONObject?renderDocument((JSONObject)raw,data.optJSONArray("attachments"))
            :raw instanceof String?(String)raw:"";
        if(source.trim().isEmpty())throw new AccessException("Текст главы отсутствует или недоступен");
        // Prevent active content and unsafe links from being written to a local HTML book.
        Safelist safe=Safelist.relaxed().addTags("h1","h2","h3","h4","hr","figure","figcaption","center")
            .addAttributes("img","src","alt","title");
        String clean=Jsoup.clean(source,"https://ranobelib.me/",safe);
        String visible=Jsoup.parse(clean).text();
        if(visible.length()<35)throw new AccessException("В главе недостаточно текста; не сохраняем страницу ошибки");
        return clean;
    }
    /** Convert the editor's ProseMirror JSON representation without losing paragraph boundaries. */
    private static String plainDocument(JSONObject doc){
        String rendered=renderDocument(doc,null);
        return Jsoup.parse(rendered).text().replaceAll("\\s+"," ").trim();
    }
    private static String renderDocument(JSONObject doc,JSONArray attachments){
        java.util.HashMap<String,String> imageUrls=new java.util.HashMap<>();
        if(attachments!=null)for(int i=0;i<attachments.length();i++){
            JSONObject a=attachments.optJSONObject(i);if(a==null)continue;
            String key=a.optString("name","");String path=a.optString("url","");
            if(!key.isEmpty()&&!path.isEmpty()){
                if(path.startsWith("/"))path="https://ranobelib.me"+path;
                imageUrls.put(key,coverUrl(path));
            }
        }
        StringBuilder out=new StringBuilder();renderNode(doc,imageUrls,out,0);return out.toString();
    }
    private static void renderNode(JSONObject node,java.util.Map<String,String> images,StringBuilder out,int depth){
        if(node==null||depth>48||out.length()>12_000_000)return;
        String type=node.optString("type","");
        if("text".equals(type)){
            String data=node.optString("text","");String escaped=escape(data);
            boolean bold=false,italic=false;JSONArray marks=node.optJSONArray("marks");
            if(marks!=null)for(int i=0;i<marks.length();i++){
                JSONObject m=marks.optJSONObject(i);String kind=m==null?"":m.optString("type","");
                if("bold".equals(kind)||"strong".equals(kind))bold=true;
                if("italic".equals(kind)||"em".equals(kind))italic=true;
            }
            if(bold)out.append("<strong>");if(italic)out.append("<em>");
            out.append(escaped);if(italic)out.append("</em>");if(bold)out.append("</strong>");return;
        }
        if("hardBreak".equals(type)){out.append("<br>");return;}
        if("horizontalRule".equals(type)){out.append("<hr>");return;}
        if("image".equals(type)){
            JSONObject attrs=node.optJSONObject("attrs");JSONArray refs=attrs==null?null:attrs.optJSONArray("images");
            if(refs!=null)for(int i=0;i<refs.length()&&i<5;i++){
                JSONObject ref=refs.optJSONObject(i);String key=ref==null?"":ref.optString("image","");
                String src=images.get(key);if(src!=null&&!src.isEmpty())out.append("<img src=\"").append(escape(src)).append("\" alt=\"Иллюстрация\">");
            }
            return;
        }
        boolean paragraph="paragraph".equals(type)||"heading".equals(type);
        if(paragraph)out.append("<p>");
        JSONArray children=node.optJSONArray("content");
        if(children!=null)for(int i=0;i<children.length()&&i<10000;i++)renderNode(children.optJSONObject(i),images,out,depth+1);
        if(paragraph)out.append("</p>");
    }
    private static String escape(String raw){return raw.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}

    byte[] coverBytes(String url) throws IOException {
        String safe=coverUrl(url);if(safe.isEmpty())throw new IOException("Недопустимая ссылка на обложку");
        Request req=new Request.Builder().url(safe).header("User-Agent",AGENT)
            .header("Referer","https://ranobelib.me/").build();
        try(Response response=client.newCall(req).execute()){
            if(!response.isSuccessful()||response.body()==null)throw new IOException("HTTP "+response.code()+" для обложки");
            String type=response.header("Content-Type","");if(!type.startsWith("image/"))throw new IOException("Ответ обложки не является изображением");
            if(response.body().contentLength()>6_000_000L)throw new IOException("Обложка больше 6 МБ");
            byte[] bytes=response.body().bytes();if(bytes.length<100||bytes.length>6_000_000)throw new IOException("Недопустимый размер обложки");
            return bytes;
        }
    }
}
