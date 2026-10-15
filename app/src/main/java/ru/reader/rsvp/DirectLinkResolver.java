package ru.reader.rsvp;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only explicit HTTPS MP3/FB2/EPUB files and supported public cloud shares; no site scraping. */
final class DirectLinkResolver {
    private static final int MAX_REDIRECTS=6;
    private DirectLinkResolver(){}
    static String encode(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return s;}}
    static void checkUrl(String raw) throws Exception {
        URI u=new URI(raw);if(!"https".equalsIgnoreCase(u.getScheme())||u.getRawUserInfo()!=null||u.getHost()==null||u.getPort()>0&&u.getPort()!=443)throw new Exception("Допускаются только публичные HTTPS-ссылки");
        String host=u.getHost().toLowerCase(Locale.ROOT);if(host.equals("localhost")||host.endsWith(".localhost")||host.endsWith(".local")||host.endsWith(".internal"))throw new Exception("Локальные адреса запрещены");
        for(InetAddress ip:InetAddress.getAllByName(host)){if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()||ip.isSiteLocalAddress()||ip.isMulticastAddress())throw new Exception("Ссылка ведёт в частную сеть");byte[] b=ip.getAddress();if(b.length==4){int first=b[0]&255,second=b[1]&255;if(first==0||first>=224||first==100&&second>=64&&second<=127||first==169&&second==254||first==192&&second==0||first==198&&(second==18||second==19))throw new Exception("Ссылка ведёт в служебную сеть");}else if(b.length==16){int first=b[0]&255;if((first&0xfe)==0xfc)throw new Exception("Приватный IPv6 адрес");}}
    }
    static HttpURLConnection connect(String raw,String range) throws Exception {
        String current=raw;
        for(int step=0;step<=MAX_REDIRECTS;step++){
            checkUrl(current);
            HttpURLConnection c=(HttpURLConnection)new URL(current).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(22000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","ChitalkaRSVP/1.11 Android");c.setRequestProperty("Accept","audio/mpeg,application/epub+zip,application/x-fictionbook+xml,application/octet-stream,application/json;q=0.6,*/*;q=0.1");
            if(range!=null)c.setRequestProperty("Range",range);
            int status=c.getResponseCode();if(status==301||status==302||status==303||status==307||status==308){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new Exception("Перенаправление без адреса");current=new URL(new URL(current),location).toExternalForm();continue;}
            if(status==401||status==403) {c.disconnect();throw new Exception("Доступ к файлу закрыт ("+status+")");}
            if(status==429){c.disconnect();throw new Exception("Сервис ограничил запросы (429)");}
            if(status<200||status>=300){c.disconnect();throw new Exception("HTTP "+status);}
            return c;
        }
        throw new Exception("Слишком много перенаправлений");
    }
    private static JSONObject json(String address)throws Exception{HttpURLConnection c=connect(address,null);try(InputStream in=c.getInputStream()){ByteArrayOutputStream buf=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1){buf.write(chunk,0,n);if(buf.size()>2*1024*1024)throw new Exception("Ответ сервиса слишком большой");}return new JSONObject(buf.toString("UTF-8"));}finally{c.disconnect();}}
    private static boolean isHost(String raw,String host){try{String h=new URI(raw).getHost();return h!=null&&(h.equalsIgnoreCase(host)||h.toLowerCase(Locale.ROOT).endsWith("."+host));}catch(Exception e){return false;}}
    private static String cloudUrl(String raw)throws Exception{
        // Dropbox public file shares can request a download instead of HTML preview.
        if(isHost(raw,"dropbox.com")){URI u=new URI(raw);String q=u.getRawQuery();q=q==null?"":q.replaceAll("(^|&)dl=[^&]*","$1").replaceAll("^&|&$","");
            if(!q.contains("dl=1"))q+=(q.isEmpty()?"":"&")+"dl=1";
            return raw.substring(0,raw.indexOf('?')>=0?raw.indexOf('?'):raw.length()).split("#")[0]+"?"+q;
        }
        // Public Google Drive files may require confirmation; HTML responses are rejected below.
        if(isHost(raw,"drive.google.com")){Matcher m=Pattern.compile("/file/d/([a-zA-Z0-9_-]+)").matcher(raw);if(m.find())return "https://drive.google.com/uc?export=download&id="+m.group(1);}
        return raw;
    }
    private static String yandexApi(String original,String path,boolean download){return "https://cloud-api.yandex.net/v1/disk/public/resources"+(download?"/download":"")+"?public_key="+encode(original)+(path.isEmpty()?"":"&path="+encode(path))+(download?"":"&limit=100");}
    private static String filenameHeader(String disp){if(disp==null)return "";Matcher star=Pattern.compile("(?i)filename\\*=[^']*''([^;]+)").matcher(disp);if(star.find()){try{return java.net.URLDecoder.decode(star.group(1).trim(),"UTF-8");}catch(Exception ignored){}}Matcher regular=Pattern.compile("(?i)filename=\"?([^\";]+)").matcher(disp);return regular.find()?regular.group(1).trim():"";}
    static LinkDownloadStore.Entry probe(String source,String download,String path,String suggested)throws Exception{
        HttpURLConnection c=connect(download,"bytes=0-3");try{
            String mime=c.getContentType();if(mime==null)mime="";mime=mime.toLowerCase(Locale.ROOT).split(";")[0].trim();
            String header=filenameHeader(c.getHeaderField("Content-Disposition"));String raw=LinkFileTypes.supported(header)?header:LinkFileTypes.supported(suggested)?suggested:header.isEmpty()?suggested:header;
            if(raw.isEmpty()){String p=new URL(c.getURL().toExternalForm()).getPath();raw=p.substring(p.lastIndexOf('/')+1);}
            // Name and true format must agree: never save EPUB/FB2 as fake MP3.
            String format=LinkFileTypes.extension(raw);
            if(format.isEmpty()){
                if(mime.equals("application/epub+zip"))format=LinkFileTypes.EPUB;
                else if(mime.equals("application/x-fictionbook+xml")||mime.equals("application/fb2"))format=LinkFileTypes.FB2;
                else if(mime.equals("audio/mpeg")||mime.equals("audio/mp3"))format=LinkFileTypes.MP3;
                else throw new Exception("Поддерживаются только MP3, FB2 и EPUB");
            }
            if(!LinkFileTypes.mimeAllowed(format,mime))throw new Exception("Ссылка открывает не "+LinkFileTypes.formatName(format)+" ("+mime+")");
            raw=LinkFileTypes.safeFilename(raw,format);
            try(InputStream in=c.getInputStream()){
                byte[] sig=new byte[32];int n=in.read(sig);
                LinkFileTypes.checkSignature(format,sig,n);
            }
            long size=-1;String cr=c.getHeaderField("Content-Range");if(cr!=null&&cr.contains("/")){try{size=Long.parseLong(cr.substring(cr.lastIndexOf('/')+1).trim());}catch(Exception ignored){}}if(size<0&&c.getResponseCode()==200)size=c.getContentLengthLong();
            LinkDownloadStore.Entry e=new LinkDownloadStore.Entry();e.source=source;e.cloudPath=path;e.url=download;e.name=raw;e.format=format;e.size=size;return e;
        }finally{c.disconnect();}
    }
    static String refresh(LinkDownloadStore.Entry e)throws Exception{if(isHost(e.source,"disk.yandex.ru")||isHost(e.source,"yadi.sk")){JSONObject j=json(yandexApi(e.source,e.cloudPath,true));String href=j.optString("href");if(href.isEmpty())throw new Exception("Яндекс.Диск не выдал ссылку скачивания");checkUrl(href);return href;}return cloudUrl(e.source);}
    static ArrayList<LinkDownloadStore.Entry> resolve(String raw)throws Exception {
        checkUrl(raw);ArrayList<LinkDownloadStore.Entry> result=new ArrayList<>();
        if(isHost(raw,"disk.yandex.ru")||isHost(raw,"yadi.sk")){
            JSONObject resource=json(yandexApi(raw,"",false));String type=resource.optString("type");if("dir".equals(type)){
                JSONObject embed=resource.optJSONObject("_embedded");JSONArray items=embed==null?null:embed.optJSONArray("items");if(items==null)throw new Exception("В публичной папке нет доступных файлов");
                for(int i=0;i<items.length()&&i<100;i++){JSONObject f=items.optJSONObject(i);if(f==null||!"file".equals(f.optString("type"))||!LinkFileTypes.supported(f.optString("name")))continue;
                    String path=f.optString("path");if(path.isEmpty())continue;try{JSONObject link=json(yandexApi(raw,path,true));result.add(probe(raw,link.getString("href"),path,f.optString("name")));}catch(Exception ignored){}
                }
                if(result.isEmpty())throw new Exception("В папке нет доступных MP3, FB2 или EPUB (в первых 100 объектах)");
            }else if("file".equals(type)){
                String name=resource.optString("name");if(!LinkFileTypes.supported(name))throw new Exception("Ссылка не указывает на MP3, FB2 или EPUB");
                JSONObject link=json(yandexApi(raw,"",true));result.add(probe(raw,link.getString("href"),"",name));
            }else throw new Exception("Неизвестный тип ресурса Яндекс.Диска");
        }else{String download=cloudUrl(raw);result.add(probe(raw,download,"",""));}
        return result;
    }
}
