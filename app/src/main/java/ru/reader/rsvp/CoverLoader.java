package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class CoverLoader {
    static File target(Context ctx,String uri){return new File(ctx.getCacheDir(),"cover_"+Integer.toHexString(uri.hashCode())+".img");}
    static byte[] get(Context ctx,String uri){
        try{
            File f=target(ctx,uri);
            if(f.isFile() && f.length()>0){try(FileInputStream in=new FileInputStream(f)){return BookLoader.readAll(in);}}
            byte[] bytes;
            try(InputStream in=ctx.getContentResolver().openInputStream(Uri.parse(uri))){if(in==null)return null;bytes=BookLoader.readAll(in);}
            byte[] cover=(bytes.length>3 && bytes[0]=='P' && bytes[1]=='K')?epub(bytes):fb2(bytes);
            if(cover==null || cover.length<50 || cover.length>15*1024*1024)return null;
            try(FileOutputStream out=new FileOutputStream(f)){out.write(cover);}
            return cover;
        }catch(Exception ignored){return null;}
    }
    static byte[] fb2(byte[] b)throws Exception{
        Document d=BookLoader.xml(b);
        NodeList covers=d.getElementsByTagNameNS("*","coverpage");
        if(covers.getLength()==0)return null;
        Element c=(Element)covers.item(0);
        NodeList images=c.getElementsByTagNameNS("*","image");
        if(images.getLength()==0)return null;
        Element im=(Element)images.item(0);
        String ref=im.getAttributeNS("http://www.w3.org/1999/xlink","href");
        if(ref.isEmpty())ref=im.getAttribute("l:href");
        if(ref.isEmpty())ref=im.getAttribute("href");
        if(ref.startsWith("#"))ref=ref.substring(1);
        NodeList list=d.getElementsByTagNameNS("*","binary");
        for(int i=0;i<list.getLength();i++){
            Element e=(Element)list.item(i);
            if(ref.equals(e.getAttribute("id")))return Base64.decode(e.getTextContent(),Base64.DEFAULT);
        }
        return null;
    }
    static byte[] epub(byte[] b)throws Exception{
        Map<String,byte[]> map=new HashMap<>();
        try(ZipInputStream zin=new ZipInputStream(new ByteArrayInputStream(b))){
            ZipEntry z;while((z=zin.getNextEntry())!=null){
                if(z.isDirectory()||z.getName().contains(".."))continue;
                map.put(z.getName(),BookLoader.readAll(zin));
            }
        }
        String opf=null;
        byte[] container=map.get("META-INF/container.xml");
        if(container!=null){Element rf=BookLoader.first(BookLoader.xml(container),"rootfile");if(rf!=null)opf=rf.getAttribute("full-path");}
        if(opf==null)for(String key:map.keySet())if(key.endsWith(".opf")){opf=key;break;}
        if(opf==null||!map.containsKey(opf))return null;
        Document d=BookLoader.xml(map.get(opf));
        String coverId="";
        NodeList metas=d.getElementsByTagNameNS("*","meta");
        for(int i=0;i<metas.getLength();i++){
            Element e=(Element)metas.item(i);
            if("cover".equalsIgnoreCase(e.getAttribute("name"))){coverId=e.getAttribute("content");break;}
        }
        String href=null;
        NodeList items=d.getElementsByTagNameNS("*","item");
        for(int i=0;i<items.getLength();i++){
            Element e=(Element)items.item(i);
            String prop=e.getAttribute("properties");
            if(Arrays.asList(prop.split("\\s+")).contains("cover-image") || (!coverId.isEmpty() && coverId.equals(e.getAttribute("id")))){
                href=e.getAttribute("href");break;
            }
        }
        if(href==null){
            for(int i=0;i<items.getLength();i++){
                Element e=(Element)items.item(i);
                if(e.getAttribute("id").toLowerCase(Locale.ROOT).contains("cover") && e.getAttribute("media-type").startsWith("image/")){
                    href=e.getAttribute("href");break;
                }
            }
        }
        if(href==null)return null;
        return map.get(BookLoader.path(BookLoader.base(opf),href.split("#")[0]));
    }
}
