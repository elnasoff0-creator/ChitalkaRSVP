package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import android.text.Html;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class BookLoader {
    static class Book {
        String title;
        String author;
        String text;
        BookChapterIndex chapters;
        Book(String title, String author, String text) {
            this(title,author,text,BookChapterIndex.empty());
        }
        Book(String title, String author, String text,BookChapterIndex chapters) {
            this.title=title;this.author=author;this.text=text;
            this.chapters=chapters==null?BookChapterIndex.empty():chapters;
        }
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    static String decode(byte[] b) {
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString();
        } catch (Exception e) {
            try { return new String(b, "windows-1251"); }
            catch (Exception x) { return new String(b, StandardCharsets.UTF_8); }
        }
    }

    static String htmlToText(String s) {
        s = s.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
             .replaceAll("(?i)<br\\s*/?>", "\n")
             .replaceAll("(?i)</(p|div|h[1-6]|li|blockquote)>", "\n\n");
        return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()
                .replace('\u00a0', ' ')
                .replaceAll("[ \t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    static String xmlText(Element el) {
        StringBuilder s = new StringBuilder();
        NodeList list = el.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (n.getNodeType() == Node.TEXT_NODE) s.append(n.getNodeValue());
            else if (n.getNodeType() == Node.ELEMENT_NODE) {
                String tag = n.getLocalName() != null ? n.getLocalName() : n.getNodeName();
                if (tag.equals("binary")) continue;
                if (tag.equals("p") || tag.equals("title") || tag.equals("subtitle")) s.append("\n");
                s.append(xmlText((Element) n));
                if (tag.equals("p") || tag.equals("title") || tag.equals("subtitle")) s.append("\n");
            }
        }
        return s.toString();
    }

    static void optionalFeature(DocumentBuilderFactory factory, String name, boolean value) {
        try { factory.setFeature(name, value); }
        catch (Exception ignored) { }
    }

    static Document xml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        optionalFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        optionalFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        optionalFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        optionalFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        optionalFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return builder.parse(new ByteArrayInputStream(bytes));
    }

    static String content(Element e) { return e == null ? "" : e.getTextContent().trim(); }
    static Element first(Document d, String tag) {
        NodeList n = d.getElementsByTagNameNS("*", tag);
        return n.getLength() > 0 ? (Element) n.item(0) : null;
    }
    static String base(String x) { int p = x.lastIndexOf('/'); return p < 0 ? "" : x.substring(0, p + 1); }
    static String path(String base, String href) {
        try { java.net.URI u = new java.net.URI(base).resolve(href); return u.normalize().getPath(); }
        catch (Exception e) { return base + href; }
    }

    static Book load(Context ctx, Uri uri) throws Exception {
        String filename = "Книга";
        try (android.database.Cursor c = ctx.getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) filename = c.getString(0);
        } catch (Exception ignored) {}

        byte[] input;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Файл недоступен");
            input = readAll(in);
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".epub")) return epub(input, filename);
        if (lower.endsWith(".fb2")) {
            Document d = xml(input);
            String title = content(first(d, "book-title"));
            String author = fb2Author(d);
            Element body = first(d, "body");
            if (body == null) throw new IOException("В FB2 нет текста");
            return fb2Book(title.isEmpty()?filename:title,author,body);
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return new Book(filename, "", htmlToText(decode(input)));
        throw new IOException("Поддерживаются EPUB, FB2, HTML");
    }

    private static final class ChapterStart {
        final String title;final int rawOffset,level;
        ChapterStart(String t,int start,int depth){title=t;rawOffset=start;level=depth;}
    }
    private static String tag(Node n){
        return n.getLocalName()!=null?n.getLocalName():n.getNodeName();
    }
    /** The same recursive text formatting as xmlText(), with structural offsets. */
    private static void fb2Write(Node node,StringBuilder out,List<ChapterStart> starts,int level){
        if(node.getNodeType()==Node.TEXT_NODE){out.append(node.getNodeValue());return;}
        if(node.getNodeType()!=Node.ELEMENT_NODE)return;
        Element e=(Element)node;
        String name=tag(e);
        if("binary".equals(name))return;
        if("section".equals(name)){
            for(Node child=e.getFirstChild();child!=null;child=child.getNextSibling()){
                if(child.getNodeType()==Node.ELEMENT_NODE&&"title".equals(tag(child))){
                    String heading=child.getTextContent().trim().replaceAll("\\s+"," ");
                    if(!heading.isEmpty())starts.add(new ChapterStart(heading,out.length(),level));
                    break;
                }
            }
            level++;
        }
        boolean separate=name.equals("p")||name.equals("title")||name.equals("subtitle");
        if(separate)out.append('\n');
        for(Node child=node.getFirstChild();child!=null;child=child.getNextSibling())
            fb2Write(child,out,starts,level);
        if(separate)out.append('\n');
    }
    private static Book fb2Book(String title,String author,Element body){
        StringBuilder raw=new StringBuilder();
        ArrayList<ChapterStart> starts=new ArrayList<>();
        fb2Write(body,raw,starts,0);
        String normalized=raw.toString().replaceAll("\\n{3,}","\\n\\n");
        int leading=0;while(leading<normalized.length()&&normalized.charAt(leading)<=' ')leading++;
        String text=normalized.trim();
        // A linear source-to-normalized offset map avoids rescanning large FB2 books.
        int[] normalizedPrefix=new int[raw.length()+1];
        int count=0,newlines=0;
        for(int i=0;i<raw.length();i++){
            char ch=raw.charAt(i);
            if(ch=='\n'){
                newlines++;
                if(newlines<=2)count++;
            }else{newlines=0;count++;}
            normalizedPrefix[i+1]=count;
        }
        ArrayList<BookChapterIndex.Chapter> chapters=new ArrayList<>();
        for(ChapterStart start:starts){
            int pos=normalizedPrefix[start.rawOffset]-leading;
            pos=Math.max(0,Math.min(text.length(),pos));
            chapters.add(new BookChapterIndex.Chapter(start.title,pos,0,start.level));
        }
        return new Book(title,author,text,BookChapterIndex.build(chapters,text.length()));
    }
    /** EPUB TOC navigation: prefer actual nav/NCX over guesses from spine filenames. */
    private static final class TocLink {
        final String label,href,base;final int level;
        TocLink(String t,String href,String base,int level){this.label=t;this.href=href;this.base=base;this.level=level;}
    }
    private static ArrayList<TocLink> epubToc(Map<String,byte[]> files,Map<String,String> idHref,
            Map<String,String> idMedia,String opfBase){
        ArrayList<TocLink> result=new ArrayList<>();
        String nav=null,ncx=null;
        for(Map.Entry<String,String> pair:idHref.entrySet()){
            String target=path(opfBase,pair.getValue().split("#")[0]);
            if(files.get(target)==null)continue;
            String type=idMedia.get(pair.getKey());
            if("application/x-dtbncx+xml".equals(type))ncx=target;
            if(type!=null&&type.contains("nav"))nav=target;
        }
        if(nav!=null){
            org.jsoup.nodes.Document d=Jsoup.parse(decode(files.get(nav)));
            for(org.jsoup.nodes.Element navEl:d.select("nav")){
                String semantic=navEl.attr("epub:type")+" "+navEl.attr("type")+" "+navEl.id();
                if(!semantic.contains("toc")&&d.select("nav").size()>1)continue;
                for(org.jsoup.nodes.Element a:navEl.select("a[href]")){
                    String heading=a.text().trim();
                    if(!heading.isEmpty())result.add(new TocLink(heading,a.attr("href"),base(nav),0));
                }
                if(!result.isEmpty())break;
            }
        }
        if(result.isEmpty()&&ncx!=null){
            try{
                Document d=xml(files.get(ncx));
                NodeList navPoints=d.getElementsByTagNameNS("*","navPoint");
                for(int i=0;i<navPoints.getLength();i++){
                    Element point=(Element)navPoints.item(i);
                    Element label=firstFrom(point,"text");Element content=firstFrom(point,"content");
                    if(label!=null&&content!=null){
                        String heading=label.getTextContent().trim();
                        if(!heading.isEmpty())result.add(new TocLink(heading,content.getAttribute("src"),base(ncx),0));
                    }
                }
            }catch(Exception ignored){}
        }
        return result;
    }
    /** Map an XHTML anchor into normalized visible text; source stays unchanged. */
    private static int anchorOffset(String xhtml,String plain,String fragment){
        if(fragment==null||fragment.isEmpty())return 0;
        org.jsoup.nodes.Element match=Jsoup.parse(xhtml).getElementById(fragment);
        if(match==null)return 0;
        String heading=match.text().trim();
        if(!heading.isEmpty()){
            int at=plain.indexOf(heading);
            if(at>=0)return at;
        }
        Pattern p=Pattern.compile("(?is)<[^>]*\\bid\\s*=\\s*['\\\"]"+Pattern.quote(fragment)+"['\\\"][^>]*>");
        Matcher m=p.matcher(xhtml);
        if(m.find())return Math.min(plain.length(),htmlToText(xhtml.substring(0,m.start())).length());
        return 0;
    }

    static String fb2Author(Document d) {
        NodeList authors = d.getElementsByTagNameNS("*", "author");
        if (authors.getLength() == 0) return "";
        Element a = (Element) authors.item(0);
        String first = content(firstFrom(a, "first-name"));
        String last = content(firstFrom(a, "last-name"));
        String middle = content(firstFrom(a, "middle-name"));
        String full = (first + " " + middle + " " + last).replaceAll("\\s+", " ").trim();
        return full;
    }

    static Element firstFrom(Element root, String tag) {
        NodeList n = root.getElementsByTagNameNS("*", tag);
        return n.getLength() > 0 ? (Element) n.item(0) : null;
    }

    static Book epub(byte[] data, String filename) throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (name.startsWith("/") || name.contains("..")) continue;
                if (files.size() > 15000) throw new IOException("Слишком много файлов");
                files.put(name, readAll(zin));
            }
        }

        String opfPath = null;
        byte[] container = files.get("META-INF/container.xml");
        if (container != null) {
            Document cd = xml(container);
            Element rf = first(cd, "rootfile");
            if (rf != null) opfPath = rf.getAttribute("full-path");
        }
        if (opfPath == null || !files.containsKey(opfPath)) {
            for (String k : files.keySet()) if (k.endsWith(".opf")) { opfPath = k; break; }
        }
        if (opfPath == null) throw new IOException("EPUB не содержит OPF");

        Document d = xml(files.get(opfPath));
        String title = content(first(d, "title"));
        String author = content(first(d, "creator"));
        Map<String, String> idToHref = new LinkedHashMap<>();
        Map<String, String> idToMedia = new HashMap<>();
        NodeList items = d.getElementsByTagNameNS("*", "item");
        for (int i = 0; i < items.getLength(); i++) {
            Element el = (Element) items.item(i);
            idToHref.put(el.getAttribute("id"), el.getAttribute("href"));
            idToMedia.put(el.getAttribute("id"), el.getAttribute("media-type")+(el.getAttribute("properties").contains("nav")?" nav":""));
        }

        StringBuilder text = new StringBuilder();
        String opfBase = base(opfPath);
        Map<String,Integer> spineStart=new HashMap<>();
        Map<String,String> spineRaw=new HashMap<>();
        Map<String,String> spinePlain=new HashMap<>();
        ArrayList<BookChapterIndex.Chapter> spineFallback=new ArrayList<>();
        NodeList spine = d.getElementsByTagNameNS("*", "itemref");
        for (int i = 0; i < spine.getLength(); i++) {
            Element e = (Element) spine.item(i);
            String href = idToHref.get(e.getAttribute("idref"));
            if (href == null) continue;
            String target = path(opfBase, href.split("#")[0]);
            byte[] page = files.get(target);
            if (page != null) {
                String html=decode(page);
                String plain=htmlToText(html);
                int offset=text.length();
                spineStart.put(target,offset);
                spineRaw.put(target,html);
                spinePlain.put(target,plain);
                text.append(plain).append("\n\n");
                String heading="";
                org.jsoup.nodes.Element h=Jsoup.parse(html).selectFirst("h1,h2,h3");
                if(h!=null)heading=h.text().trim();
                if(heading.isEmpty())heading="Раздел "+(spineFallback.size()+1);
                if(!plain.isEmpty())spineFallback.add(new BookChapterIndex.Chapter(heading,offset,0,1));
            }
        }
        if (text.length() == 0) throw new IOException("В EPUB не найден читаемый текст");
        String complete=text.toString().trim();
        ArrayList<BookChapterIndex.Chapter> chapters=new ArrayList<>();
        for(TocLink link:epubToc(files,idToHref,idToMedia,opfBase)){
            String target=path(link.base,link.href.split("#")[0]);
            Integer offset=spineStart.get(target);
            if(offset==null)continue;
            String frag=link.href.contains("#")?link.href.substring(link.href.indexOf('#')+1):"";
            int inside=anchorOffset(spineRaw.get(target),spinePlain.get(target),frag);
            chapters.add(new BookChapterIndex.Chapter(link.label,offset+inside,0,link.level));
        }
        // EPUBs without nav/NCX still have a reliable spine structure.
        if(chapters.isEmpty())chapters.addAll(spineFallback);
        return new Book(title.isEmpty()?filename:title,author,complete,
            BookChapterIndex.build(chapters,complete.length()));
    }
}
