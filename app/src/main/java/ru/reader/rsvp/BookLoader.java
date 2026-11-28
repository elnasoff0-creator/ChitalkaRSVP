package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;

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
import org.jsoup.Jsoup;
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

    /** Reading content and navigation are deliberately separate. Never render HEAD metadata. */
    static org.jsoup.nodes.Element readingBody(String markup){
        org.jsoup.nodes.Element body=Jsoup.parse(markup).body();
        body.select("script,style,template,iframe,form,button,textarea,[hidden]").remove();
        for(org.jsoup.nodes.Element e:new ArrayList<>(body.select("nav,[epub:type],[role]"))){
            String semantic=e.attr("epub:type").trim();
            String role=e.attr("role").trim();
            if(e.normalName().equals("nav")||hasToken(semantic,"toc")||hasToken(semantic,"page-list")||
                hasToken(semantic,"landmarks")||hasToken(role,"doc-toc")||hasToken(role,"doc-pagelist"))e.remove();
        }
        return body;
    }
    private static boolean hasToken(String value,String token){
        if(value==null)return false;
        for(String part:value.split("\\s+"))if(token.equals(part))return true;
        return false;
    }
    /** Normalize whitespace without introducing words. Record DOM anchors at their actual occurrence. */
    private static final class HtmlReading {
        final String text;final Map<String,Integer> anchors;
        final ArrayList<BookChapterIndex.Chapter> headings;
        HtmlReading(String text,Map<String,Integer> anchors,ArrayList<BookChapterIndex.Chapter> headings){
            this.text=text;this.anchors=anchors;this.headings=headings;
        }
    }
    private static void line(StringBuilder out,int count){
        while(out.length()>0&&out.charAt(out.length()-1)==' ')out.setLength(out.length()-1);
        if(out.length()==0)return;
        int n=0;for(int i=out.length()-1;i>=0&&out.charAt(i)=='\n';i--)n++;
        while(n++<count)out.append('\n');
    }
    private static void render(org.jsoup.nodes.Node node,StringBuilder out,Map<String,Integer> anchors,
            ArrayList<BookChapterIndex.Chapter> headings){
        if(node instanceof org.jsoup.nodes.TextNode){
            String v=((org.jsoup.nodes.TextNode)node).getWholeText();
            for(org.jsoup.nodes.Node parent=node.parent();parent!=null;parent=parent.parent())if(parent instanceof org.jsoup.nodes.Element&&((org.jsoup.nodes.Element)parent).normalName().equals("pre")){out.append(v.replace("\r\n","\n").replace('\r','\n'));return;}
            for(int i=0;i<v.length();i++){
                char c=v.charAt(i);
                if(Character.isWhitespace(c)||c=='\u00a0'){
                    if(out.length()>0&&out.charAt(out.length()-1)!=' '&&out.charAt(out.length()-1)!='\n')out.append(' ');
                }else out.append(c);
            }
            return;
        }
        if(!(node instanceof org.jsoup.nodes.Element))return;
        org.jsoup.nodes.Element e=(org.jsoup.nodes.Element)node;
        String tag=e.normalName();
        boolean heading=tag.matches("h[1-6]"),block=heading||tag.matches("p|div|section|article|main|blockquote|li|ul|ol|dl|dt|dd|pre|figure|figcaption|table|tr|header|footer|aside|address");
        if(block)line(out,2);
        int start=out.length();
        if(!e.id().isEmpty())anchors.putIfAbsent(e.id(),start);
        if(tag.equals("a")&&e.hasAttr("name"))anchors.putIfAbsent(e.attr("name"),start);
        if(tag.equals("br")){line(out,1);return;}
        if(heading&&!e.text().trim().isEmpty())headings.add(new BookChapterIndex.Chapter(e.text(),start,0,Integer.parseInt(tag.substring(1))-1));
        for(org.jsoup.nodes.Node child:e.childNodes())render(child,out,anchors,headings);
        if(tag.equals("td")||tag.equals("th")){if(out.length()>0&&out.charAt(out.length()-1)!=' ')out.append(' ');}
        if(block)line(out,2);
    }
    private static HtmlReading readHtml(org.jsoup.nodes.Element body){
        StringBuilder out=new StringBuilder();Map<String,Integer> anchors=new HashMap<>();
        ArrayList<BookChapterIndex.Chapter> headings=new ArrayList<>();
        render(body,out,anchors,headings);
        String text=out.toString().trim();
        for(Map.Entry<String,Integer> a:anchors.entrySet())a.setValue(nextText(text,a.getValue()));
        return new HtmlReading(text,anchors,headings);
    }
    private static int nextText(String text,int offset){
        int i=Math.max(0,Math.min(text.length(),offset));
        while(i<text.length()&&Character.isWhitespace(text.charAt(i)))i++;
        return i;
    }
    static String htmlToText(String s){return readHtml(readingBody(s)).text;}
    static Book htmlBook(String markup,String fallback){
        org.jsoup.nodes.Document original=Jsoup.parse(markup);
        if(!original.select("section.ranobe-chapter[data-chapter-title],section.ranobe-chapter[data-chapter-key],section.ranobe-chapter[data-volume][data-number][data-branch]").isEmpty())return ranobeHtml(markup,fallback);
        if(!original.select("section[data-chapter]").isEmpty()&&original.title().matches("(?s).*( — оригинал| — русский машинный перевод)$"))
            return ranobeHtml(markup,fallback);
        HtmlReading r=readHtml(readingBody(markup));
        return new Book(fallback,"",r.text,BookChapterIndex.build(r.headings,r.text.length()));
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
        try { java.net.URI u = new java.net.URI(base.replace(" ","%20")).resolve(href.replace(" ","%20")); return u.normalize().getPath(); }
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
        // file:// URIs normally do not support OpenableColumns queries.
        if ("Книга".equals(filename) && uri.getLastPathSegment()!=null)
            filename=uri.getLastPathSegment();
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".epub")) return epub(input, filename);
        if (lower.endsWith(".fb2")) {
            Document d = xml(input);
            String title = content(first(d, "book-title"));
            String author = fb2Author(d);
            Element body = first(d, "body");
            if (body == null) throw new IOException("В FB2 нет текста");
            return fb2Book(title.isEmpty()?filename:title,author,d);
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            String html=decode(input);
            return htmlBook(html,filename);
        }
        throw new IOException("Поддерживаются EPUB, FB2, HTML");
    }

    /** Chapter markers emitted by RanobeLibStorage become real ReaderActivity TOC entries. */
    static Book ranobeHtml(String markup,String fallback){
        org.jsoup.nodes.Document doc=Jsoup.parse(markup);
        String title=doc.title().trim();if(title.isEmpty())title=fallback;
        org.jsoup.nodes.Element authorMeta=doc.selectFirst("meta[name=author]");
        String author=authorMeta==null?"":authorMeta.attr("content");
        StringBuilder raw=new StringBuilder();ArrayList<BookChapterIndex.Chapter> markers=new ArrayList<>();
        org.jsoup.nodes.Element body=readingBody(markup);
        for(org.jsoup.nodes.Element section:body.select("section.ranobe-chapter,section[data-chapter]")){
            String name=section.attr("data-chapter-title");
            // Old application-generated book.html: its first direct H2 was inserted by rebuildBook.
            // Do not remove ordinary headings from imported HTML or the narrative beneath it.
            if(name.isEmpty()&&section.hasAttr("data-volume")&&section.hasAttr("data-number")&&section.hasAttr("data-branch")&&
                !doc.select("header.ranobe-book-header").isEmpty()&&section.childrenSize()>0&&section.child(0).normalName().equals("h2")){
                org.jsoup.nodes.Element synthetic=section.child(0);name=synthetic.text();synthetic.remove();
            }
            if(name.isEmpty()&&section.hasAttr("data-chapter-key")&&section.childrenSize()>0&&section.child(0).normalName().equals("h2")){
                org.jsoup.nodes.Element synthetic=section.child(0);name=synthetic.text();synthetic.remove();
            }
            if(name.isEmpty()&&section.hasAttr("data-chapter")&&doc.title().matches("(?s).*( — оригинал| — русский машинный перевод)$")&&section.childrenSize()>0&&section.child(0).normalName().equals("h3")){
                org.jsoup.nodes.Element synthetic=section.child(0);name=synthetic.text();synthetic.remove();
            }
            if(name.isEmpty()){
                org.jsoup.nodes.Element h=section.selectFirst("h1,h2,h3,h4,h5,h6");
                name=h==null?"Раздел "+(markers.size()+1):h.text();
            }
            HtmlReading r=readHtml(section);String part=r.text;
            if(part.isEmpty())continue;
            int begin=raw.length();markers.add(new BookChapterIndex.Chapter(name,begin,0,0));
            raw.append(part).append("\n\n");
        }
        String plain=raw.toString().trim();
        return new Book(title,author,plain,BookChapterIndex.build(markers,plain.length()));
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
        if(node.getNodeType()==Node.TEXT_NODE||node.getNodeType()==Node.CDATA_SECTION_NODE){out.append(node.getNodeValue());return;}
        if(node.getNodeType()!=Node.ELEMENT_NODE)return;
        Element e=(Element)node;
        String name=tag(e);
        if("binary".equals(name))return;
        if("section".equals(name)){
            boolean named=false;
            for(Node child=e.getFirstChild();child!=null;child=child.getNextSibling()){
                if(child.getNodeType()==Node.ELEMENT_NODE&&"title".equals(tag(child))){
                    String heading=child.getTextContent().trim().replaceAll("\\s+"," ");
                    if(!heading.isEmpty()){starts.add(new ChapterStart(heading,out.length(),level));named=true;}
                    break;
                }
            }
            if(!named)starts.add(new ChapterStart("Раздел "+(starts.size()+1),out.length(),level));
            level++;
        }
        boolean separate=name.equals("p")||name.equals("title")||name.equals("subtitle")||name.equals("v")||name.equals("stanza")||name.equals("epigraph")||name.equals("empty-line");
        if(separate)out.append('\n');
        for(Node child=node.getFirstChild();child!=null;child=child.getNextSibling())
            fb2Write(child,out,starts,level);
        if(separate)out.append('\n');
    }
    static Book fb2Book(String title,String author,Document doc){
        StringBuilder raw=new StringBuilder();
        ArrayList<ChapterStart> starts=new ArrayList<>();
        NodeList bodies=doc.getElementsByTagNameNS("*","body");
        for(int i=0;i<bodies.getLength();i++){if(i>0)raw.append("\n\n");fb2Write(bodies.item(i),raw,starts,0);}
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
            chapters.add(new BookChapterIndex.Chapter(start.title,nextText(text,pos),0,start.level));
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
            if(type!=null&&hasToken(type,"nav"))nav=target;
        }
        if(nav!=null){
            org.jsoup.nodes.Document d=Jsoup.parse(decode(files.get(nav)));
            for(org.jsoup.nodes.Element navEl:d.select("nav")){
                String semantic=navEl.attr("epub:type")+" "+navEl.attr("type")+" "+navEl.id();
                if(!hasToken(semantic,"toc")&&!"doc-toc".equals(navEl.attr("role")))continue;
                for(org.jsoup.nodes.Element a:navEl.select("a[href]")){
                    String heading=a.text().trim();
                    int depth=0;for(org.jsoup.nodes.Element parent=a.parent();parent!=null&&parent!=navEl;parent=parent.parent())if(parent.normalName().equals("ol")||parent.normalName().equals("ul"))depth++;
                    if(!heading.isEmpty())result.add(new TocLink(heading,a.attr("href"),nav,Math.max(0,depth-1)));
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
                    Element label=null,content=null;
                    for(Node child=point.getFirstChild();child!=null;child=child.getNextSibling())if(child instanceof Element){
                        if("navLabel".equals(tag(child)))label=firstFrom((Element)child,"text");
                        if("content".equals(tag(child)))content=(Element)child;
                    }
                    int depth=0;for(Node parent=point.getParentNode();parent!=null;parent=parent.getParentNode())if("navPoint".equals(tag(parent)))depth++;
                    if(label!=null&&content!=null){
                        String heading=label.getTextContent().trim();
                        if(!heading.isEmpty())result.add(new TocLink(heading,content.getAttribute("src"),ncx,depth));
                    }
                }
            }catch(Exception ignored){}
        }
        return result;
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
            idToMedia.put(el.getAttribute("id"), el.getAttribute("media-type")+(hasToken(el.getAttribute("properties"),"nav")?" nav":""));
        }

        StringBuilder text = new StringBuilder();
        String opfBase = base(opfPath);
        Map<String,Integer> spineStart=new HashMap<>();
        Map<String,HtmlReading> spinePages=new HashMap<>();
        ArrayList<BookChapterIndex.Chapter> spineFallback=new ArrayList<>();
        java.util.HashSet<String> guideToc=new java.util.HashSet<>();
        NodeList refs=d.getElementsByTagNameNS("*","reference");
        for(int i=0;i<refs.getLength();i++){
            Element ref=(Element)refs.item(i);
            if(hasToken(ref.getAttribute("type"),"toc"))guideToc.add(path(opfBase,ref.getAttribute("href").split("#",-1)[0]));
        }
        NodeList spine = d.getElementsByTagNameNS("*", "itemref");
        for (int i = 0; i < spine.getLength(); i++) {
            Element e = (Element) spine.item(i);
            String href = idToHref.get(e.getAttribute("idref"));
            if (href == null||"application/x-dtbncx+xml".equals(idToMedia.get(e.getAttribute("idref")))) continue;
            String target = path(opfBase, href.split("#")[0]);
            byte[] page = files.get(target);
            if (page != null) {
                String html=decode(page);
                org.jsoup.nodes.Element readable=readingBody(html);
                // A manifest navigation document may contain only a title plus its TOC.
                // Drop its own structural heading only when no other narrative remains.
                if(hasToken(idToMedia.get(e.getAttribute("idref")),"nav")){
                    org.jsoup.nodes.Element probe=readable.clone();probe.select("h1,h2,h3,h4,h5,h6").remove();
                    if(probe.text().trim().isEmpty())continue;
                }
                if(guideToc.contains(target)){
                    org.jsoup.nodes.Element probe=readable.clone();probe.select("h1,h2,h3,h4,h5,h6,a").remove();
                    if(probe.text().trim().isEmpty())continue;
                }
                HtmlReading pageText=readHtml(readable);String plain=pageText.text;
                if(plain.isEmpty())continue;
                int offset=text.length();
                spineStart.put(target,offset);
                spinePages.put(target,pageText);
                text.append(plain).append("\n\n");
                String heading="";
                org.jsoup.nodes.Element h=readable.selectFirst("h1,h2,h3,h4,h5,h6");
                if(h!=null)heading=h.text().trim();
                if(heading.isEmpty())heading="Раздел "+(spineFallback.size()+1);
                if(!plain.isEmpty())spineFallback.add(new BookChapterIndex.Chapter(heading,offset,0,1));
            }
        }
        if (text.length() == 0) throw new IOException("В EPUB не найден читаемый текст");
        String complete=text.toString().trim();
        ArrayList<BookChapterIndex.Chapter> chapters=new ArrayList<>();
        for(TocLink link:epubToc(files,idToHref,idToMedia,opfBase)){
            String relative=link.href.split("#",-1)[0];
            String target=relative.isEmpty()?link.base:path(base(link.base),relative);
            Integer offset=spineStart.get(target);
            if(offset==null)continue;
            String frag=link.href.contains("#")?link.href.substring(link.href.indexOf('#')+1):"";
            try{frag=new java.net.URI("#"+frag).getFragment();}catch(Exception ignored){}
            HtmlReading page=spinePages.get(target);
            Integer inside=frag.isEmpty()?Integer.valueOf(0):page.anchors.get(frag);
            if(inside==null||inside>=page.text.length())continue;
            chapters.add(new BookChapterIndex.Chapter(link.label,offset+inside,0,link.level));
        }
        // EPUBs without nav/NCX still have a reliable spine structure.
        if(chapters.isEmpty()){
            chapters.addAll(spineFallback);
            for(Map.Entry<String,HtmlReading> page:spinePages.entrySet())for(BookChapterIndex.Chapter h:page.getValue().headings)
                chapters.add(new BookChapterIndex.Chapter(h.title,spineStart.get(page.getKey())+h.startChar,0,h.level));
        }
        return new Book(title.isEmpty()?filename:title,author,complete,
            BookChapterIndex.build(chapters,complete.length()));
    }
}
