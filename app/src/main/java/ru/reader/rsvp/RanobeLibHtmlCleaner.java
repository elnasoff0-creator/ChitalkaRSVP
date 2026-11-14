package ru.reader.rsvp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import java.util.ArrayList;
import java.util.Locale;

/** Removes publisher/offline navigation and duplicate headings, never narrative or author footnotes. */
final class RanobeLibHtmlCleaner {
    private RanobeLibHtmlCleaner(){}
    static String heading(RanobeLibStorage.Job job){
        String label="Глава "+job.number;
        String name=job.chapter==null?"":job.chapter.trim();
        return name.isEmpty()?label:label+". "+name;
    }
    private static boolean technical(String value){
        String str=value.replace('\u00a0',' ').replaceAll("\\s+"," ").trim();
        if(str.isEmpty())return true;
        if(str.matches("(?iu)^(оглавление|предыдущая глава|следующая глава|назад к оглавлению|вернуться к оглавлению)$"))return true;
        if(str.matches("(?iu)^(?:→|←)?\\s*(?:офлайн[- ]копия|локальная копия|сохранено:?)\\s*[:·—-]?\\s*.*$"))return true;
        if(str.matches("(?iu)^оглавление\\s*глава\\s*\\d+[\\s.:-]*$"))return true;
        return false;
    }
    private static String normalized(String text){
        return text.toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("[^\\p{L}\\p{N}]+","");
    }
    private static boolean headingDuplicate(String text,String expected){
        String v=normalized(text),full=normalized(expected);
        if(v.isEmpty())return false;
        if(v.equals(full)||v.equals(full+full))return true;
        // Older offline exports append an extra "Том N · Глава M" after the title.
        // Strip only a complete title followed by an exact chapter label.
        if(!full.isEmpty()&&v.startsWith(full)&&
            v.substring(full.length()).matches("(?:том\\d+)?глава\\d+"))return true;
        // A repeated volume/chapter prefix is publisher metadata, not a body paragraph.
        String simple=full.replaceFirst("^том\\d+","");
        if(v.equals(simple)||v.equals(simple+simple))return true;
        return v.matches("^(?:том\\d+)?глава\\d+(?:том\\d+глава\\d+)?$");
    }
    static String chapterBody(String input,String expectedTitle){
        Document parsed=Jsoup.parse(input==null?"":input);
        Element source=parsed.selectFirst("div.node-doc.text-content,.node-doc.text-content,"+
            ".chapter-content,.chapter__content,.reading-content,article,.text-content,main,body");
        if(source==null)source=parsed.body();
        Element body=source.clone();
        body.select("script,style,iframe,form,nav,footer,aside,time,button,textarea,"+
            ".chapter-navigation,.chapter-header,.chapter-number,.source,.saved-at,"+
            ".comments,.comment-section,.user-comments,[data-role=comments],"+
            "[class~=.*comment-list.*],[class~=.*comment-item.*],"+
            ".breadcrumbs,.pagination,.chapter-controls,.reading-controls").remove();
        // Only short metadata near the start of the chapter is stripped. Preserve story body.
        ArrayList<Element> top=new ArrayList<>();
        for(Element el:body.children()){
            if(top.size()>=7)break;
            top.add(el);
        }
        for(Element el:top){
            String text=el.text().trim();
            boolean block=el.is("h1,h2,h3,h4,p,small,div,span,a");
            if(!block||text.isEmpty())continue;
            if(technical(text)||headingDuplicate(text,expectedTitle))el.remove();
            else if(el.is("h1,h2,h3,h4")&&text.matches("(?iu)^(?:Том\\s*\\d+\\s*[·—.:-]?\\s*)?Глава\\s*[\\d.,]+(?:\\s*[·—:.-].*)?$"))el.remove();
        }
        // Some saved pages place small technical labels inside a wrapper rather than top-level.
        for(Element el:new ArrayList<>(body.select("p,small,a,h1,h2,h3,h4"))){
            if(el.parent()==null)continue;
            String text=el.text().trim();
            if(text.length()<=130&&technical(text))el.remove();
        }
        Safelist safe=Safelist.relaxed().addTags("h1","h2","h3","h4","figure","figcaption","blockquote","details","summary")
            .addAttributes("img","src","alt").preserveRelativeLinks(true);
        return Jsoup.clean(body.html(),"",safe);
    }
}
