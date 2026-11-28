package ru.reader.rsvp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/** Removes explicitly marked publisher navigation; original chapter headings are preserved. */
final class RanobeLibHtmlCleaner {
    private RanobeLibHtmlCleaner(){}
    static String heading(RanobeLibStorage.Job job){
        String label="Глава "+job.number;
        String name=job.chapter==null?"":job.chapter.trim();
        return name.isEmpty()?label:label+". "+name;
    }
    static String chapterBody(String input,String expectedTitle){
        Document parsed=Jsoup.parse(input==null?"":input);
        Element source=parsed.selectFirst("div.node-doc.text-content,.node-doc.text-content,"+
            ".chapter-content,.chapter__content,.reading-content,article,.text-content,main,body");
        if(source==null)source=parsed.body();
        Element body=source.clone();
        body.select("script,style,iframe,form,nav,button,textarea,"+
            ".chapter-navigation,.source,.saved-at,"+
            ".comments,.comment-section,.user-comments,[data-role=comments],"+
            "[class~=.*comment-list.*],[class~=.*comment-item.*],"+
            ".breadcrumbs,.pagination,.chapter-controls,.reading-controls").remove();
        Safelist safe=Safelist.relaxed().addTags("h1","h2","h3","h4","h5","h6","figure","figcaption","blockquote","details","summary")
            .addAttributes("img","src","alt").preserveRelativeLinks(true);
        return Jsoup.clean(body.html(),"",safe);
    }
}
