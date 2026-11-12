package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import android.text.Html;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

/** Light-weight book title extraction; never parses an entire book or scans on the UI thread. */
final class EbookTitleExtractor {
    private EbookTitleExtractor(){}
    private static final int LIMIT=131072;
    private static String text(InputStream in)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];
        int n;while(out.size()<LIMIT&&(n=in.read(buf,0,Math.min(buf.length,LIMIT-out.size())))>0)out.write(buf,0,n);
        return BookLoader.decode(out.toByteArray());
    }
    static String fromXml(String xml,String tag){
        if(xml==null)return "";
        String t=tag.equals("dc:title")?"(?:[a-zA-Z0-9_-]+:)?title":Pattern.quote(tag);
        Pattern pattern=Pattern.compile("(?is)<"+t+"(?:\\s+[^>]*)?>(.*?)</"+t+"\\s*>");
        Matcher m=pattern.matcher(xml);
        if(!m.find())return "";
        String cleaned=m.group(1).replaceAll("(?s)<[^>]*>"," ").trim();
        cleaned=Html.fromHtml(cleaned,Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("[\\p{Cntrl}\\s]+"," ").trim();
        return cleaned.length()>160?cleaned.substring(0,160):cleaned;
    }
    static String extract(Context context,Uri uri,String format){
        if(context==null||uri==null)return "";
        try(InputStream raw=context.getContentResolver().openInputStream(uri)){
            if(raw==null)return "";
            if("fb2".equals(format)){
                String xml=text(raw);String info=xml;
                Matcher head=Pattern.compile("(?is)<title-info(?:\\s+[^>]*)?>(.*?)</title-info>").matcher(xml);
                if(head.find())info=head.group(1);
                return fromXml(info,"book-title");
            }
            if("epub".equals(format)){
                try(ZipInputStream zip=new ZipInputStream(raw)){
                    ZipEntry e;int count=0;
                    while((e=zip.getNextEntry())!=null&&count++<120){
                        if(!e.isDirectory()&&e.getName().toLowerCase(Locale.ROOT).endsWith(".opf"))
                            return fromXml(text(zip),"dc:title");
                    }
                }
            }
        }catch(Exception ignored){}
        return "";
    }
}
