package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import android.text.Html;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public class BookLoader {
    static class Book { String title; String text; Book(String a,String b){title=a;text=b;} }
    static byte[] readAll(InputStream in) throws IOException { ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1)out.write(buf,0,n); return out.toByteArray(); }
    static String decode(byte[] b){ try { return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString(); }catch(Exception e){try{return new String(b,"windows-1251");}catch(Exception x){return new String(b,StandardCharsets.UTF_8);}} }
    static String htmlToText(String s) { s=s.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>"," ").replaceAll("(?i)<br\\s*/?>","\n").replaceAll("(?i)</(p|div|h[1-6]|li|blockquote)>","\n\n"); return Html.fromHtml(s,Html.FROM_HTML_MODE_LEGACY).toString().replace('\u00a0',' ').replaceAll("[ \t]+"," ").replaceAll("\n{3,}","\n\n").trim(); }
    static String xmlText(Element el){ StringBuilder s=new StringBuilder(); NodeList list=el.getChildNodes(); for(int i=0;i<list.getLength();i++){Node n=list.item(i); if(n.getNodeType()==Node.TEXT_NODE)s.append(n.getNodeValue()); else if(n.getNodeType()==Node.ELEMENT_NODE){String tag=n.getLocalName()!=null?n.getLocalName():n.getNodeName(); if(tag.equals("binary"))continue; if(tag.equals("p")||tag.equals("title")||tag.equals("subtitle"))s.append("\n"); s.append(xmlText((Element)n)); if(tag.equals("p")||tag.equals("title")||tag.equals("subtitle"))s.append("\n");}}return s.toString(); }
    static Document xml(byte[] bytes) throws Exception {DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false); return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));}
    static String content(Element e){return e==null?"":e.getTextContent().trim();}
    static Element first(Document d,String tag){NodeList n=d.getElementsByTagNameNS("*",tag);return n.getLength()>0?(Element)n.item(0):null;}
    static String base(String x){int p=x.lastIndexOf('/');return p<0?"":x.substring(0,p+1);}
    static String path(String base,String href){try{java.net.URI u=new java.net.URI(base).resolve(href);return u.normalize().getPath();}catch(Exception e){return base+href;}}
    static Book load(Context ctx,Uri uri) throws Exception {
        String filename="Книга";try(android.database.Cursor c=ctx.getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())filename=c.getString(0);}catch(Exception ignored){}
        byte[] input;try(InputStream in=ctx.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Файл недоступен");input=readAll(in);} String lower=filename.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".epub"))return epub(input,filename);
        if(lower.endsWith(".fb2")){Document d=xml(input);String title=content(first(d,"book-title"));Element body=first(d,"body");if(body==null)throw new IOException("В FB2 нет текста");return new Book(title.isEmpty()?filename:title,xmlText(body).replaceAll("\n{3,}","\n\n").trim());}
        if(lower.endsWith(".html")||lower.endsWith(".htm"))return new Book(filename,htmlToText(decode(input)));
        throw new IOException("Поддерживаются EPUB, FB2, HTML");
    }
    static Book epub(byte[] data,String filename)throws Exception{
        Map<String,byte[]> files=new HashMap<>();try(ZipInputStream zin=new ZipInputStream(new ByteArrayInputStream(data))){ZipEntry e;while((e=zin.getNextEntry())!=null){if(e.isDirectory())continue;String name=e.getName();if(name.startsWith("/")||name.contains(".."))continue;if(files.size()>15000)throw new IOException("Слишком много файлов");files.put(name,readAll(zin));}}
        String opfPath=null;byte[] container=files.get("META-INF/container.xml");if(container!=null){Document cd=xml(container);Element rf=first(cd,"rootfile");if(rf!=null)opfPath=rf.getAttribute("full-path");}
        if(opfPath==null||!files.containsKey(opfPath))for(String k:files.keySet())if(k.endsWith(".opf")){opfPath=k;break;}if(opfPath==null)throw new IOException("EPUB не содержит OPF");
        Document d=xml(files.get(opfPath));String title=content(first(d,"title"));Map<String,String> idToHref=new HashMap<>();NodeList items=d.getElementsByTagNameNS("*","item");for(int i=0;i<items.getLength();i++){Element el=(Element)items.item(i);idToHref.put(el.getAttribute("id"),el.getAttribute("href"));}
        StringBuilder text=new StringBuilder();String opfBase=base(opfPath);NodeList spine=d.getElementsByTagNameNS("*","itemref");for(int i=0;i<spine.getLength();i++){Element e=(Element)spine.item(i);String href=idToHref.get(e.getAttribute("idref"));if(href==null)continue;String target=path(opfBase,href.split("#")[0]);byte[] page=files.get(target);if(page!=null)text.append(htmlToText(decode(page))).append("\n\n");}
        if(text.length()==0)throw new IOException("В EPUB не найден читаемый текст");return new Book(title.isEmpty()?filename:title,text.toString().trim());
    }
}
