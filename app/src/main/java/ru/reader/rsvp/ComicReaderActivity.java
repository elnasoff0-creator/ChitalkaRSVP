package ru.reader.rsvp;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

/** Local comic reader supporting HTML chapters, image folders and CBZ/CBR archives. */
public final class ComicReaderActivity extends NavigationActivity {
    private static final class PageAsset { String relativePath; File localFile; String mime="image/*"; }
    private ComicLibrary.Comic comic; private ArrayList<ComicLibrary.Chapter> chapters=new ArrayList<>(); private WebView web;
    private TextView chapterTitle; private Spinner modePicker; private int generation=0; private final ArrayList<PageAsset> currentAssets=new ArrayList<>();
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}    

    @Override public void onCreate(Bundle state){super.onCreate(state);
        String uri=getIntent().getStringExtra("comic_uri"); comic=ComicLibrary.find(this,uri==null?"":uri);
        if(comic==null){finish();return;} chapters=ComicLibrary.chapters(this,comic);
        LinearLayout main=new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL);
        int bg=getSharedPreferences("reader",0).getInt("bg",0xfff3e7d5), fg=getSharedPreferences("reader",0).getInt("fg",0xff30221e);
        main.setBackgroundColor(bg); setContentView(main); SafeSystemBars.matchBackground(this,bg);

        LinearLayout toolbar=new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL); main.addView(toolbar);
        TextView back=button("‹",fg); toolbar.addView(back); back.setOnClickListener(v->finish());
        TextView title=button(comic.title,fg); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END); toolbar.addView(title,new LinearLayout.LayoutParams(0,dp(50),1));
        modePicker=new Spinner(this); modePicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Постраничный","Вертикальный"}));
        modePicker.setSelection(Math.max(0,Math.min(1,comic.mode))); toolbar.addView(modePicker,new LinearLayout.LayoutParams(dp(153),dp(50)));
        modePicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(comic.mode!=pos){comic.mode=pos; comic.scroll=0; persist(); loadChapter();}}});

        LinearLayout nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER_VERTICAL); main.addView(nav);
        TextView prev=button("‹",fg); nav.addView(prev); prev.setOnClickListener(v->navigate(-1));
        chapterTitle=button("Оглавление ▾",fg); nav.addView(chapterTitle,new LinearLayout.LayoutParams(0,dp(45),1)); chapterTitle.setOnClickListener(v->contents());
        TextView next=button("›",fg); nav.addView(next); next.setOnClickListener(v->navigate(1));

        web=new WebView(this); web.setBackgroundColor(bg); web.getSettings().setJavaScriptEnabled(true); web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri requestUri=request.getUrl(); if(!"comic.local".equals(requestUri.getHost())) return forbidden();
                String path=requestUri.getPath(); if(path==null||!path.startsWith("/page/")) return forbidden();
                try {
                    int index=Integer.parseInt(path.substring(6)); PageAsset asset; synchronized(currentAssets){ if(index<0||index>=currentAssets.size()) return forbidden(); asset=currentAssets.get(index); }
                    if(asset.localFile!=null && asset.localFile.isFile()) return new WebResourceResponse(asset.mime,null,new FileInputStream(asset.localFile));
                    if(asset.relativePath!=null){
                        DocumentFile tree=ComicLibrary.tree(ComicReaderActivity.this,comic); if(tree==null) return forbidden();
                        DocumentFile file=lookup(tree,asset.relativePath); if(file==null||!file.isFile()) return forbidden();
                        InputStream input=getContentResolver().openInputStream(file.getUri()); if(input==null) return forbidden();
                        String mime=getContentResolver().getType(file.getUri()); if(mime==null) mime=guessMime(asset.relativePath); asset.mime=mime; return new WebResourceResponse(mime,null,input);
                    }
                } catch(Exception ignored){}
                return forbidden();
            }
        });
        main.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        loadChapter();
    }

    private static WebResourceResponse forbidden(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    private static DocumentFile lookup(DocumentFile root,String path){DocumentFile current=root; for(String part:path.split("/")){ if(part.isEmpty()||part.equals(".")) continue; if(part.equals("..")) return null; current=current.findFile(part); if(current==null) return null; } return current; }
    private static String guessMime(String path){String lower=path.toLowerCase(Locale.ROOT); if(lower.endsWith(".png")) return "image/png"; if(lower.endsWith(".webp")) return "image/webp"; if(lower.endsWith(".gif")) return "image/gif"; if(lower.endsWith(".bmp")) return "image/bmp"; if(lower.endsWith(".avif")) return "image/avif"; return "image/jpeg";}
    private TextView button(String text,int fg){TextView v=new TextView(this); v.setText(text); v.setTextColor(fg); v.setGravity(Gravity.CENTER); v.setTextSize(15); v.setPadding(dp(9),dp(7),dp(9),dp(7)); return v;}
    private void persist(){comic.opened=System.currentTimeMillis(); ComicLibrary.save(this,comic);}    
    private void navigate(int delta){int target=comic.chapter+delta; if(target>=0&&target<chapters.size()){comic.chapter=target; comic.scroll=0; loadChapter();}}
    private void contents(){String[] titles=new String[chapters.size()]; for(int i=0;i<chapters.size();i++) titles[i]=chapters.get(i).title;
        new android.app.AlertDialog.Builder(this).setTitle("Оглавление").setSingleChoiceItems(titles,comic.chapter,(d,n)->{comic.chapter=n; comic.scroll=0; d.dismiss(); loadChapter();}).setNegativeButton("Закрыть",null).show();}

    private void loadChapter(){
        if(web==null) return;
        if(chapters.isEmpty()){
            String msg = ComicLibrary.isArchiveKind(comic.kind) ? "Комикс не содержит изображений или архив не удалось прочитать" : "В папке нет HTML-файлов или изображений";
            web.loadData("<p style='padding:16px'>"+msg+"</p>","text/html","UTF-8");
            return;
        }
        comic.chapter=Math.max(0,Math.min(comic.chapter,chapters.size()-1)); persist(); chapterTitle.setText(chapters.get(comic.chapter).title+" ▾");
        final ComicLibrary.Chapter chapter=chapters.get(comic.chapter); final int token=++generation; final int previousScroll=comic.scroll; final int mode=comic.mode;
        new Thread(() -> {
            try {
                String content = buildChapterHtml(chapter, mode);
                runOnUiThread(() -> { if(token!=generation||isFinishing()) return; web.loadDataWithBaseURL("https://comic.local/",content,"text/html","UTF-8",null); web.postDelayed(() -> web.evaluateJavascript("restore("+previousScroll+")",null),350); });
            } catch(Exception e){ runOnUiThread(() -> Toast.makeText(this,"Ошибка открытия главы: "+e.getMessage(),Toast.LENGTH_LONG).show()); }
        },"comic-open").start();
    }

    private String buildChapterHtml(ComicLibrary.Chapter chapter, int mode) throws Exception {
        synchronized(currentAssets){ currentAssets.clear(); }
        if("html".equals(chapter.type)) prepareHtmlAssets(chapter); else prepareImageAssets(chapter);
        return renderCurrentAssets(mode);
    }
    private void prepareHtmlAssets(ComicLibrary.Chapter chapter) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(InputStream in=getContentResolver().openInputStream(chapter.htmlFile.getUri())){
            if(in==null) throw new Exception("HTML недоступен"); byte[] buffer=new byte[8192]; int total=0,n;
            while((n=in.read(buffer))!=-1){ total+=n; if(total>4*1024*1024) throw new Exception("HTML превышает 4 МБ"); output.write(buffer,0,n); }
        }
        Document doc=Jsoup.parse(new String(output.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
        ArrayList<PageAsset> pages=new ArrayList<>();
        for(Element img:doc.select("img")){
            String path=img.hasAttr("data-src")?img.attr("data-src"):img.attr("src");
            if(path.isEmpty()||path.startsWith("data:")||path.contains(":")||path.startsWith("/")||path.contains("..")) continue;
            PageAsset asset=new PageAsset(); asset.relativePath=path; asset.mime=guessMime(path); pages.add(asset);
        }
        synchronized(currentAssets){ currentAssets.addAll(pages); }
    }
    private void prepareImageAssets(ComicLibrary.Chapter chapter){
        ArrayList<PageAsset> pages=new ArrayList<>();
        for(String path:chapter.imagePaths){
            PageAsset asset=new PageAsset();
            if("archive".equals(chapter.type)){ asset.localFile=new File(path); asset.mime=guessMime(path); }
            else { asset.relativePath=path; asset.mime=guessMime(path); }
            pages.add(asset);
        }
        synchronized(currentAssets){ currentAssets.addAll(pages); }
    }
    private String renderCurrentAssets(int mode){
        StringBuilder html=new StringBuilder();
        html.append("<html><head><meta name='viewport' content='width=device-width, initial-scale=1, user-scalable=yes'>");
        html.append("<style>html,body{margin:0;padding:0;background:#fff}img{display:block;margin:0 auto;image-rendering:auto;}");
        if(mode==0) html.append("#pages{display:flex;overflow-x:auto;overflow-y:hidden;scroll-snap-type:x mandatory;width:100vw;height:100vh}img{width:100vw;height:100vh;object-fit:contain;flex-shrink:0;scroll-snap-align:start}");
        else html.append("#pages{display:block}img{width:100%;height:auto}");
        html.append("</style></head><body><div id='pages'>");
        synchronized(currentAssets){ for(int i=0;i<currentAssets.size();i++) html.append("<img loading='lazy' src='https://comic.local/page/").append(i).append("'>"); }
        html.append("</div><script>");
        html.append("function position(){return (").append(mode==0?"document.getElementById('pages').scrollLeft":"window.scrollY").append(").toString()}");
        html.append("function restore(n){if(").append(mode).append("===0){document.getElementById('pages').scrollLeft=n;}else{window.scrollTo(0,n);}}");
        if(mode==0) html.append("document.body.onclick=function(e){var p=document.getElementById('pages');p.scrollBy({left:e.clientX<innerWidth*.30?-innerWidth:innerWidth,behavior:'smooth'});};");
        html.append("</script></body></html>");
        return html.toString();
    }

    @Override protected void onPause(){ if(web!=null){ try{ web.evaluateJavascript("position()", value -> { try{ comic.scroll=Integer.parseInt(value.replace("\"","")); persist(); }catch(Exception ignored){} }); }catch(Exception ignored){} } super.onPause(); }
    @Override protected void onDestroy(){ if(web!=null){ web.stopLoading(); web.destroy(); } super.onDestroy(); }
}
