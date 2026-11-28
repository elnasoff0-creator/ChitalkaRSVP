package ru.reader.rsvp;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
public final class TestBookOriginalText197 {
    static void eq(Object a,Object b){if(!a.equals(b))throw new AssertionError("Expected "+b+", got "+a);}
    static void yes(boolean value,String what){if(!value)throw new AssertionError(what);}
    static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    static byte[] zip(Map<String,String> files)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)){
            for(Map.Entry<String,String> item:files.entrySet()){
                zip.putNextEntry(new ZipEntry(item.getKey()));zip.write(bytes(item.getValue()));zip.closeEntry();
            }
        }return out.toByteArray();
    }
    static void fb2()throws Exception{
        String fb="<FictionBook xmlns='http://www.gribuser.ru/xml/fictionbook/2.0'><description><title-info><book-title>Metadata</book-title></title-info></description><body><section><title><p>Original heading</p></title><p>alpha beta</p><section><p>Nested text</p></section></section><section><p>Last chapter</p></section></body><body name='notes'><section><p>Original footnote</p></section></body></FictionBook>";
        BookLoader.Book f=BookLoader.fb2Book("Metadata","Author",BookLoader.xml(bytes(fb)));
        yes(f.text.contains("Original heading")&&!f.text.contains("Metadata")&&!f.text.contains("Author")&&!f.text.contains("Раздел"),"FB2 no synthetic words");
        yes(f.text.contains("Original footnote")&&f.text.contains("Nested text"),"FB2 nested sections and notes");
        yes(f.chapters.size()>=3,"FB2 navigation without inserted titles");
    }
    public static void main(String[] args)throws Exception{
        fb2();
        if(args.length>0&&args[0].equals("fb2-only")){System.out.println("PASS: FB2 real XML parser, original headings, nested sections, notes");return;}
        BookLoader.Book html=BookLoader.htmlBook("<html><head><title>Metadata title</title><style>x</style></head><body><nav role='doc-toc'><a href='#one'>NAV LABEL</a></nav><h1 id='one'>Original chapter</h1><p>First line.<br>Second line.</p><h1>Original chapter</h1><p>Last words.</p><script>noise</script></body></html>","File name");
        eq(html.text,"Original chapter\n\nFirst line.\nSecond line.\n\nOriginal chapter\n\nLast words.");
        eq(html.chapters.size(),2);
        BookLoader.Book plain=BookLoader.htmlBook("<head><title>Metadata</title></head><body><p>Only text.</p></body>","File name");eq(plain.text,"Only text.");
        BookLoader.Book authoredToc=BookLoader.htmlBook("<h1>Contents</h1><p>Original printed contents.</p>","x");yes(authoredToc.text.contains("Original printed contents."),"ordinary author text preserved");
        BookLoader.Book ran=BookLoader.ranobeHtml("<head><title>Book</title></head><body><section class='ranobe-chapter' data-chapter-title='NAV NAME'><h2>Original name</h2><p>Story.</p></section></body>","x");eq(ran.text,"Original name\n\nStory.");eq(ran.chapters.get(0).title,"NAV NAME");
        BookLoader.Book legacy=BookLoader.ranobeHtml("<header class='ranobe-book-header'><h1>Book</h1></header><section class='ranobe-chapter' data-volume='1' data-number='2' data-branch='1'><h2>GENERATED NAME</h2><h2>Actual heading</h2><p>Story.</p></section>","x");eq(legacy.text,"Actual heading\n\nStory.");eq(legacy.chapters.get(0).title,"GENERATED NAME");
        Map<String,String> files=new LinkedHashMap<>();
        files.put("META-INF/container.xml","<container><rootfiles><rootfile full-path='OPS/book.opf'/></rootfiles></container>");
        files.put("OPS/book.opf","<package><metadata><title>Metadata</title></metadata><manifest><item id='nav' href='nav.xhtml' properties='nav' media-type='application/xhtml+xml'/><item id='a' href='story.xhtml' media-type='application/xhtml+xml'/><item id='notes' href='notes.xhtml' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='nav'/><itemref idref='a'/><itemref idref='notes' linear='no'/></spine></package>");
        files.put("OPS/nav.xhtml","<html xmlns:epub='http://www.idpf.org/2007/ops'><body><h1>CONTENTS</h1><nav epub:type='toc'><ol><li><a href='story.xhtml#one'>Navigation one</a><ol><li><a href='story.xhtml#two'>Navigation two</a></li></ol></li></ol></nav><nav epub:type='page-list'><a href='story.xhtml#one'>Page 1</a></nav></body></html>");
        files.put("OPS/story.xhtml","<body><p id='one'>Repeat heading.</p><p>First paragraph.</p><h2 id='two'>Repeat heading.</h2><p>Second paragraph.</p><nav epub:type='toc'>REMOVE ME</nav></body>");
        files.put("OPS/notes.xhtml","<body><p>Original note.</p></body>");
        BookLoader.Book epub=BookLoader.epub(zip(files),"x.epub");eq(epub.text,"Repeat heading.\n\nFirst paragraph.\n\nRepeat heading.\n\nSecond paragraph.\n\nOriginal note.");eq(epub.chapters.size(),2);
        eq(epub.chapters.get(0).title,"Navigation one");eq(epub.chapters.get(1).title,"Navigation two");eq(epub.chapters.get(1).level,1);eq(epub.chapters.get(1).startChar,epub.text.lastIndexOf("Repeat heading."));
        // EPUB2 NCX with a marked guide TOC inside the spine.
        files.put("OPS/book.opf","<package><metadata><title>Metadata</title></metadata><manifest><item id='ncx' href='toc.ncx' media-type='application/x-dtbncx+xml'/><item id='toc' href='nav.xhtml' media-type='application/xhtml+xml'/><item id='a' href='story.xhtml' media-type='application/xhtml+xml'/></manifest><spine toc='ncx'><itemref idref='toc'/><itemref idref='a'/></spine><guide><reference type='toc' href='nav.xhtml'/></guide></package>");
        files.put("OPS/nav.xhtml","<h1>CONTENTS</h1><p><a href='story.xhtml#one'>NCX item</a></p>");
        files.put("OPS/toc.ncx","<ncx><navMap><navPoint><navLabel><text>NCX item</text></navLabel><content src='story.xhtml#one'/><navPoint><navLabel><text>Nested item</text></navLabel><content src='story.xhtml#two'/></navPoint></navPoint></navMap></ncx>");
        BookLoader.Book epub2=BookLoader.epub(zip(files),"x.epub");yes(!epub2.text.contains("CONTENTS")&&!epub2.text.contains("NCX item"),"EPUB2 guide and NCX are not readable text");eq(epub2.chapters.get(1).level,1);
        System.out.println("PASS: EPUB2/3 TOC, duplicate anchor labels, navigation-only spine, linear=no notes, original HTML and FB2 headings, legacy and new downloaded chapter containers");
    }
}
