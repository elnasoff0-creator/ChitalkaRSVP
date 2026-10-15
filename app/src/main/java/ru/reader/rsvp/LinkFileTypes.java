package ru.reader.rsvp;

import android.util.Xml;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.xmlpull.v1.XmlPullParser;

/** Downloaded file formats supported by the reader and the audiobook player. */
final class LinkFileTypes {
    static final String MP3="mp3", EPUB="epub", FB2="fb2";
    private LinkFileTypes() {}
    static String extension(String name) {
        if(name==null)return "";
        String lower=name.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".mp3"))return MP3;
        if(lower.endsWith(".fb2"))return FB2;
        if(lower.endsWith(".epub"))return EPUB;
        return "";
    }
    static boolean supported(String name){return !extension(name).isEmpty();}
    static String mime(String format){
        if(EPUB.equals(format))return "application/epub+zip";
        if(FB2.equals(format))return "application/x-fictionbook+xml";
        return "audio/mpeg";
    }
    static String formatName(String format){return MP3.equals(format)?"MP3":EPUB.equals(format)?"EPUB":"FB2";}
    static String defaultName(String format){return MP3.equals(format)?"audio.mp3":"book."+format;}
    static String safeFilename(String candidate,String format){
        if(!MP3.equals(format)&&!EPUB.equals(format)&&!FB2.equals(format))throw new IllegalArgumentException("Неподдерживаемый формат");
        String safe=LinkDownloadStore.safeName(candidate==null||candidate.trim().isEmpty()?defaultName(format):candidate);
        String ext=extension(safe);
        if(!ext.isEmpty())safe=safe.substring(0,safe.length()-ext.length()-1);
        else if(safe.contains(".")){
            int dot=safe.lastIndexOf('.');
            // A different extension is not silently accepted or obscured.
            if(dot>0&&safe.length()-dot<=8)safe=safe.substring(0,dot);
        }
        if(safe.isEmpty())safe="book";
        return safe+"."+format;
    }
    static boolean mimeAllowed(String format,String contentType){
        String m=contentType==null?"":contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        if(m.isEmpty()||m.equals("application/octet-stream")||m.equals("binary/octet-stream"))return true;
        switch(format){
            case MP3:return m.equals("audio/mpeg")||m.equals("audio/mp3")||m.equals("audio/x-mpeg");
            case EPUB:return m.equals("application/epub+zip")||m.equals("application/zip")||m.equals("application/x-zip-compressed");
            case FB2:return m.equals("application/x-fictionbook+xml")||m.equals("application/fb2")||m.equals("application/xml")||m.equals("text/xml")||m.equals("text/plain");
            default:return false;
        }
    }
    static void checkSignature(String format,byte[] sig,int n) throws Exception {
        if(MP3.equals(format)){
            if(n<2||!(n>=3&&sig[0]=='I'&&sig[1]=='D'&&sig[2]=='3')&&!((sig[0]&255)==255&&((sig[1]&224)==224)))throw new Exception("Ответ не похож на MP3-файл");
        }else if(EPUB.equals(format)){
            if(n<4||sig[0]!='P'||sig[1]!='K'||(sig[2]!=3&&sig[2]!=5&&sig[2]!=7))throw new Exception("Ответ не похож на EPUB-архив");
        }else if(FB2.equals(format)){
            int i=0;
            if(n>=3&&(sig[0]&255)==239&&(sig[1]&255)==187&&(sig[2]&255)==191)i=3;
            if(n>=2&&((sig[0]&255)==255&&(sig[1]&255)==254||(sig[0]&255)==254&&(sig[1]&255)==255))return;
            while(i<n&&(sig[i]==' '||sig[i]=='\r'||sig[i]=='\n'||sig[i]=='\t'))i++;
            if(i>=n||sig[i]!='<')throw new Exception("Ответ не похож на FB2/XML");
        }
    }
    static void verify(File file,String format) throws Exception {
        if(file==null||!file.isFile()||file.length()<4)throw new Exception("Файл пустой или неполный");
        try(InputStream in=new FileInputStream(file)){
            byte[] sig=new byte[32];int n=in.read(sig);checkSignature(format,sig,n);
        }
        if(EPUB.equals(format)){
            try(ZipFile zip=new ZipFile(file)){
                ZipEntry mimetype=zip.getEntry("mimetype");
                ZipEntry container=zip.getEntry("META-INF/container.xml");
                if(mimetype==null||container==null)throw new Exception("EPUB не содержит обязательные файлы");
                if(mimetype.getSize()>128||mimetype.getSize()<0)throw new Exception("Некорректный EPUB mimetype");
                try(InputStream in=zip.getInputStream(mimetype)){
                    byte[] value=new byte[32];int n=in.read(value);
                    if(n!=20||!"application/epub+zip".equals(new String(value,0,n,java.nio.charset.StandardCharsets.US_ASCII)))throw new Exception("Неверный EPUB mimetype");
                }
            }
        }else if(FB2.equals(format)){
            try(InputStream in=new FileInputStream(file)){
                XmlPullParser xml=Xml.newPullParser();xml.setInput(in,null);int event;
                do{event=xml.next();}while(event!=XmlPullParser.START_TAG&&event!=XmlPullParser.END_DOCUMENT);
                if(event!=XmlPullParser.START_TAG||!"FictionBook".equals(xml.getName()))throw new Exception("Документ не является FB2");
            }
        }
    }
}
