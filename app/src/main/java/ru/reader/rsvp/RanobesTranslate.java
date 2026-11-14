package ru.reader.rsvp;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.TextNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/** Optional EN -> RU on-device translation. This is NOT the Ranobes GTranslate API. */
final class RanobesTranslate {
    private RanobesTranslate(){}
    static String toRussian(String html)throws Exception {
        Translator translator=Translation.getClient(new TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.RUSSIAN).build());
        try{
            Tasks.await(translator.downloadModelIfNeeded(new DownloadConditions.Builder().build()),300,TimeUnit.SECONDS);
            Document doc=Jsoup.parseBodyFragment(html);
            ArrayList<TextNode> texts=new ArrayList<>();
            doc.body().traverse(new org.jsoup.select.NodeVisitor(){
                @Override public void head(org.jsoup.nodes.Node node,int depth){if(node instanceof TextNode)texts.add((TextNode)node);}
                @Override public void tail(org.jsoup.nodes.Node node,int depth){}
            });
            int meaningful=0;
            for(TextNode n:texts){String original=n.getWholeText();if(original.trim().length()<3)continue;
                String translated=Tasks.await(translator.translate(original.trim()),120,TimeUnit.SECONDS);
                if(translated==null||translated.trim().isEmpty())throw new IOException("Машинный перевод вернул пустой фрагмент");
                String leading=original.substring(0,original.indexOf(original.trim()));
                String trailing=original.substring(original.indexOf(original.trim())+original.trim().length());
                n.text(leading+translated+trailing);meaningful++;
            }
            if(meaningful<1)throw new IOException("Нет абзацев для перевода");
            return doc.body().html();
        }finally{translator.close();}
    }
}
