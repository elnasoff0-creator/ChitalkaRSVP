package ru.reader.rsvp;

import android.graphics.Color;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
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
    private static final class PageAsset {
        String relativePath;File localFile;String mime="image/*";
        int width=0,height=0;DocumentFile tree;volatile Uri cachedUri;
    }
    private static final class PreparedChapter {
        final String html;final ArrayList<PageAsset> assets;
        PreparedChapter(String html,ArrayList<PageAsset> assets){this.html=html;this.assets=assets;}
    }
    // WebView requests are asynchronous. Keep the immutable asset set indexed by
    // chapter generation so late requests never render another chapter's pages.
    private final java.util.concurrent.ConcurrentHashMap<Integer,ArrayList<PageAsset>> generations=
        new java.util.concurrent.ConcurrentHashMap<>();
    private ComicLibrary.Comic comic; private ArrayList<ComicLibrary.Chapter> chapters=new ArrayList<>(); private WebView web;
    private TextView chapterTitle, chapterInfo, comicHeading;private ImageView modeAction,orientationAction;
    private boolean restoringPage=false;private int restoringToken=0;
    private LinearLayout toolbarOverlay; private FrameLayout footerOverlay;
    private ImageView drawerAction;private int displayedUiAccent=Integer.MIN_VALUE;
    private boolean chromeVisible=true, gestureMoved=false, touchPinching=false;
    private float gestureStartX, gestureStartY;
    private long gestureStartedAt;
    private int restoringScroll=0,restoringIndex=0;
    private volatile int generation=0;private final ArrayList<PageAsset> currentAssets=new ArrayList<>();
    private boolean lastPositionKnown=false;
    private final Handler tracker=new Handler(Looper.getMainLooper());private boolean tracking=false;
    private final Runnable trackPosition=new Runnable(){@Override public void run(){
        if(!tracking||web==null||isFinishing())return;
        if(restoringPage){tracker.postDelayed(this,500L);return;}
        final int token=generation;
        try{web.evaluateJavascript("positionInfo()",value->{
            if(token!=generation||value==null||comic==null)return;
            String[] parts=value.replace("\"","").split("\\|");
            if(parts.length==2)try{
                int page=Integer.parseInt(parts[0]),offset=Integer.parseInt(parts[1]);
                int max; synchronized(currentAssets){max=currentAssets.size();}
                if(page<0||page>=max)return; // No trustworthy page before WebView layout.
                int nextScroll=Math.max(0,offset),oldPage=comic.page;
                if(oldPage==page&&comic.scroll==nextScroll)return;
                comic.page=page;comic.scroll=nextScroll;lastPositionKnown=true;
                getSharedPreferences("reader",0).edit()
                    .putBoolean("comic_anchor_v175:"+comic.uri,true).apply();
                if(oldPage!=page)ChteicoStore.recordComicPage(ComicReaderActivity.this,comic.uri,comic.chapter,page);
                updateChapterButton();
            }catch(Exception ignored){}
        });}catch(Exception ignored){}
        tracker.postDelayed(this,600L);
    }};
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}    

    @Override public void onCreate(Bundle state){super.onCreate(state);
        String uri=getIntent().getStringExtra("comic_uri"); comic=ComicLibrary.find(this,uri==null?"":uri);
        if(comic==null){finish();return;}
        int savedOrientation=getSharedPreferences("reader",0).getInt("comic_screen_orientation:"+comic.uri,0);
        int targetOrientation=savedOrientation==0?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        if(getRequestedOrientation()!=targetOrientation)setRequestedOrientation(targetOrientation);
        // v174: SAF traversal/archive extraction must never delay the Activity first frame.
        // chapter list and thumbnails are resolved after the WebView shell is shown.
        FrameLayout main=new FrameLayout(this);
        SharedPreferences prefs=getSharedPreferences("reader",0);
        int bg=prefs.getInt("bg",0xfff3e7d5), fg=prefs.getInt("fg",0xff30221e);
        final int neutral=ReaderButtonColors.neutral(this,
            ReadingThemeCycle.isDark(bg)?0xff303238:0xfffff9f0);
        final int accent=ReaderButtonColors.accent(this,0xff915439);
        displayedUiAccent=accent;
        final int neutralInk=ReaderButtonColors.contrastingText(neutral);
        final int accentInk=ReaderButtonColors.contrastingText(accent);
        main.setBackgroundColor(bg);setContentView(main);
        // Match ReaderActivity's edge-to-edge content and system-bar treatment.
        SafeSystemBars.install(this);
        SafeSystemBars.matchBackground(this,bg);

        // Floating controls are independent chips, like ReaderActivity's chapter-statistics pill.
        // No gradient and no second/third rows below the toolbar; the WebView remains full screen.
        LinearLayout toolbarStack=new LinearLayout(this);
        toolbarStack.setOrientation(LinearLayout.VERTICAL);toolbarOverlay=toolbarStack;
        toolbarStack.setBackgroundColor(Color.TRANSPARENT);
        toolbarStack.setPadding(dp(5),dp(3),dp(5),dp(3));
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbarStack.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(50)));
        ImageView drawer=toolIcon(R.drawable.ic_menu_lines,neutralInk);drawerAction=drawer;
        drawer.setBackground(rounded(neutral,12));
        toolbar.addView(drawer,new LinearLayout.LayoutParams(dp(44),dp(44)));
        drawer.setContentDescription("Открыть боковое меню");drawer.setOnClickListener(v->UniversalDrawer.show(this));
        modeAction=toolIcon(comic.mode==0?R.drawable.ic_comic_mode_pages:R.drawable.ic_comic_mode_feed,accentInk);
        modeAction.setBackground(rounded(accent,12));
        LinearLayout.LayoutParams modeLp=new LinearLayout.LayoutParams(dp(44),dp(44));
        modeLp.leftMargin=dp(5);toolbar.addView(modeAction,modeLp);
        modeAction.setContentDescription(comic.mode==0?"Страницы":"Лента");
        modeAction.setOnClickListener(v->UiNavigationSheet.showComicModes(this,comic.mode,n->{
            if(n!=comic.mode){comic.mode=n;comic.scroll=0;setChromeVisible(true);persist();updateModeIcon();loadChapter();}
        }));
        orientationAction=toolIcon(savedOrientation==0?R.drawable.ic_comic_portrait:R.drawable.ic_comic_landscape,neutralInk);
        orientationAction.setBackground(rounded(neutral,12));
        orientationAction.setContentDescription("Ориентация экрана комикса");
        LinearLayout.LayoutParams orientLp=new LinearLayout.LayoutParams(dp(44),dp(44));
        orientLp.leftMargin=dp(5);toolbar.addView(orientationAction,orientLp);
        orientationAction.setOnClickListener(v->{
            int current=getSharedPreferences("reader",0).getInt("comic_screen_orientation:"+comic.uri,0);
            UiNavigationSheet.showComicOrientation(this,current,n->{
                if(n==current)return;
                Runnable rotate=()->{
                    persist();getSharedPreferences("reader",0).edit()
                        .putInt("comic_screen_orientation:"+comic.uri,n).apply();
                    setRequestedOrientation(n==0?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                };
                if(!restoringPage&&web!=null)web.evaluateJavascript("positionInfo()",value->{
                    try{String[] parts=value.replace("\"","").split("\\|");
                        int pg=Integer.parseInt(parts[0]);if(pg>=0){comic.page=pg;comic.scroll=Math.max(0,Integer.parseInt(parts[1]));
                            getSharedPreferences("reader",0).edit().putBoolean("comic_anchor_v175:"+comic.uri,true).apply();}}
                    catch(Exception ignored){}
                    rotate.run();
                });else rotate.run();
            });
        });
        // Title and current file sit to the RIGHT of the icon controls, ON ONE neutral chip.
        LinearLayout titleChip=new LinearLayout(this);
        titleChip.setOrientation(LinearLayout.HORIZONTAL);
        titleChip.setGravity(Gravity.CENTER_VERTICAL);
        titleChip.setPadding(dp(9),dp(2),dp(9),dp(2));
        titleChip.setBackground(rounded(neutral,13));
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);
        titleLp.topMargin=dp(2);toolbarStack.addView(titleChip,titleLp);
        toolbar.addView(new View(this),new LinearLayout.LayoutParams(0,dp(44),1));
        comicHeading=button(comic.title,neutralInk);
        comicHeading.setTextSize(12);comicHeading.setSingleLine(true);
        comicHeading.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        comicHeading.setPadding(0,0,0,0);
        comicHeading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleChip.addView(comicHeading,new LinearLayout.LayoutParams(-1,-2));
        chapterInfo=null; // Filename is appended to title in the SAME row; statistics remain below.

        web=new WebView(this); web.setBackgroundColor(bg); web.getSettings().setJavaScriptEnabled(true); web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // Native WebView pinch zoom is enabled in both comic reading modes.
        // Android handles scaling; the web content continues to handle page swipes/vertical scrolling.
        web.getSettings().setSupportZoom(true);
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setDisplayZoomControls(false);
        web.getSettings().setUseWideViewPort(true);
        web.getSettings().setLoadWithOverviewMode(true);
        web.setOnTouchListener((v,event)->handleReaderTouch(event));
        web.addJavascriptInterface(new Object(){
            @android.webkit.JavascriptInterface public void onPositionRestored(int token){
                runOnUiThread(()->{if(token!=generation||token!=restoringToken||web==null)return;
                    restoringPage=false;web.animate().cancel();web.setAlpha(1f);
                    if(tracking){tracker.removeCallbacks(trackPosition);tracker.postDelayed(trackPosition,400L);}
                });
            }
        },"ComicRestore");
        // Feed scrolling only moves pages; both floating panels toggle by a short tap.
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){return true;}
            @Override public void onPageFinished(WebView view,String url){
                if(view!=web||!restoringPage||!url.startsWith("https://comic.local/"))return;
                final int token=restoringToken;
                String call="restoreWhenReady("+restoringScroll+","+restoringIndex+","+token+")";
                view.evaluateJavascript(call,null);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri requestUri=request.getUrl(); if(!"comic.local".equals(requestUri.getHost())) return forbidden();
                String path=requestUri.getPath();
                if(path==null||!path.startsWith("/chapter/"))return forbidden();
                try {
                    String[] bits=path.split("/");
                    if(bits.length!=5||!"page".equals(bits[3]))return forbidden();
                    int version=Integer.parseInt(bits[2]),index=Integer.parseInt(bits[4]);
                    ArrayList<PageAsset> pages=generations.get(version);
                    if(pages==null||index<0||index>=pages.size())return forbidden();
                    PageAsset asset=pages.get(index);
                    if(asset.localFile!=null&&asset.localFile.isFile())
                        return new WebResourceResponse(asset.mime,null,new FileInputStream(asset.localFile));
                    if(asset.relativePath!=null){
                        Uri uri=asset.cachedUri;
                        if(uri==null)synchronized(asset){
                            uri=asset.cachedUri;
                            if(uri==null&&asset.tree!=null){
                                DocumentFile file=lookup(asset.tree,asset.relativePath);
                                if(file!=null&&file.isFile())asset.cachedUri=uri=file.getUri();
                            }
                        }
                        if(uri==null)return forbidden();
                        InputStream input=getContentResolver().openInputStream(uri);
                        if(input!=null)return new WebResourceResponse(asset.mime,null,input);
                    }
                } catch(Exception ignored){}
                return forbidden();
            }
        });
        // The comic viewport fills the screen. Floating bars cannot resize it or reset scroll.
        main.addView(web,new FrameLayout.LayoutParams(-1,-1));
        // The bottom panel shares ReaderActivity's transparent 74dp overlay and
        // its left chapter chip: neutral rounded pill, accent 42x44dp list button.
        FrameLayout footer=new FrameLayout(this);footerOverlay=footer;
        footer.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout chaptersAction=new LinearLayout(this);
        chaptersAction.setGravity(Gravity.CENTER_VERTICAL);
        chaptersAction.setBackground(rounded(neutral,13));
        chaptersAction.setOnClickListener(v->contents());
        chaptersAction.setContentDescription("Оглавление комикса и позиция страницы");
        FrameLayout.LayoutParams chipLp=new FrameLayout.LayoutParams(
            Math.max(dp(105),(getResources().getDisplayMetrics().widthPixels/2)-dp(74)),
            dp(54),Gravity.LEFT|Gravity.CENTER_VERTICAL);
        footer.addView(chaptersAction,chipLp);
        ImageView contentsIcon=toolIcon(R.drawable.ic_list_audio,accentInk);
        contentsIcon.setBackground(rounded(accent,12));
        chaptersAction.addView(contentsIcon,new LinearLayout.LayoutParams(dp(42),dp(44)));
        // Only one click listener is needed: the whole chip, including its icon, is clickable.
        chapterTitle=button("",neutralInk);
        chapterTitle.setTextSize(10);chapterTitle.setGravity(Gravity.CENTER);
        chapterTitle.setPadding(dp(3),0,dp(3),0);
        chapterTitle.setMaxLines(2);
        chapterTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chaptersAction.addView(chapterTitle,new LinearLayout.LayoutParams(0,dp(52),1));
        FrameLayout.LayoutParams topParams=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        topParams.setMargins(dp(8),0,dp(8),0);
        main.addView(toolbarOverlay,topParams);
        FrameLayout.LayoutParams bottomParams=new FrameLayout.LayoutParams(-1,dp(74),Gravity.BOTTOM);
        bottomParams.setMargins(dp(8),0,dp(8),dp(8));
        main.addView(footerOverlay,bottomParams);
        web.loadData("<p style='padding:16px'>Подготовка оглавления и страниц…</p>",
            "text/html","UTF-8");
        final String openedUri=comic.uri;
        new Thread(()->{
            final ArrayList<ComicLibrary.Chapter> loaded=ComicLibrary.chapters(getApplicationContext(),comic);
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||comic==null||!openedUri.equals(comic.uri))return;
                chapters=loaded;comic.chapterTotal=loaded.size();updateChapterButton();loadChapter();
                // Counting all page assets is deferred until after the first chapter starts.
                new Thread(()->{
                    ComicLibrary.indexCounts(getApplicationContext(),comic);
                    runOnUiThread(()->{
                        if(isFinishing()||isDestroyed()||comic==null)return;
                        ComicLibrary.Comic current=ComicLibrary.find(this,openedUri);
                        if(current!=null&&!current.pageCounts.isEmpty()){
                            comic.pageCounts=new ArrayList<>(current.pageCounts);
                            updateChapterButton();
                        }
                    });
                },"comic-index").start();
            });
        },"comic-chapter-discovery").start();
    }

    /** ReaderActivity-like chrome: overlays leave the WebView viewport untouched. */
    private void setChromeVisible(boolean show){
        if(chromeVisible==show)return;
        chromeVisible=show;
        ReaderToolbarController.reveal(toolbarOverlay,show,64);
        animateChrome(footerOverlay,show,dp(82));
    }
    private void animateChrome(View bar,boolean show,int offscreen){
        if(bar==null)return;
        bar.animate().cancel();bar.setTag(Boolean.valueOf(show));
        bar.setVisibility(View.VISIBLE);
        bar.animate().translationY(show?0:offscreen).alpha(show?1f:0f)
            .setDuration(180L).withEndAction(()->{
                if(Boolean.FALSE.equals(bar.getTag()))bar.setVisibility(View.INVISIBLE);
            }).start();
        bar.setClickable(show);
    }
    /** Return false so neither page swipes nor native pinch zoom are swallowed. */
    private boolean handleReaderTouch(MotionEvent event){
        final int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            gestureStartX=event.getX();gestureStartY=event.getY();
            gestureStartedAt=android.os.SystemClock.uptimeMillis();
            gestureMoved=false;touchPinching=false;
        }else if(action==MotionEvent.ACTION_POINTER_DOWN || event.getPointerCount()>1){
            touchPinching=true;gestureMoved=true;
        }else if(action==MotionEvent.ACTION_MOVE){
            if(Math.abs(event.getX()-gestureStartX)>dp(14)||
                Math.abs(event.getY()-gestureStartY)>dp(14))gestureMoved=true;
        }else if(action==MotionEvent.ACTION_UP){
            if(comic!=null&&!gestureMoved&&!touchPinching&&
                android.os.SystemClock.uptimeMillis()-gestureStartedAt<350L){
                // In BOTH modes a short single-finger tap toggles both overlays.
                // Horizontal swipes, vertical feed scroll and two-finger pinch remain native.
                setChromeVisible(!chromeVisible);
            }
            touchPinching=false;
        }else if(action==MotionEvent.ACTION_CANCEL){touchPinching=false;gestureMoved=true;}
        return false;
    }

    private android.graphics.drawable.Drawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private ImageView toolIcon(int drawable,int fg){ImageView icon=new ImageView(this);icon.setImageResource(drawable);icon.setColorFilter(fg);icon.setPadding(dp(10),dp(10),dp(10),dp(10));return icon;}
    private void updateModeIcon(){if(modeAction!=null){modeAction.setImageResource(comic.mode==0?R.drawable.ic_comic_mode_pages:R.drawable.ic_comic_mode_feed);modeAction.setContentDescription(comic.mode==0?"Режим Страницы":"Режим Лента");}}
    private void updateChapterButton(){
        if(chapterTitle==null||comic==null)return;
        int total=chapters.size(),count; synchronized(currentAssets){count=currentAssets.size();}
        comic.chapterTotal=total;
        if(count>0){
            comic.pageTotal=count;
            while(comic.pageCounts.size()<total)comic.pageCounts.add(-1);
            if(comic.chapter>=0&&comic.chapter<comic.pageCounts.size())comic.pageCounts.set(comic.chapter,count);
        }
        String name=total>0?chapters.get(Math.max(0,Math.min(comic.chapter,total-1))).title:"Оглавление";
        int fileNumber=total==0?0:comic.chapter+1;
        chapterTitle.setText(fileNumber+"/"+total+" · Стр "+(comic.page+1)+(comic.pageTotal>0?"/"+comic.pageTotal:""));
        // Upper toolbar contains only the title and actual filename, never 1/4 statistics.
        if(comicHeading!=null)comicHeading.setText(comic.title+(name.isEmpty()?"":" · "+name));
    }
    private static WebResourceResponse forbidden(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    private static DocumentFile lookup(DocumentFile root,String path){DocumentFile current=root; for(String part:path.split("/")){ if(part.isEmpty()||part.equals(".")) continue; if(part.equals("..")) return null; current=current.findFile(part); if(current==null) return null; } return current; }
    private static String guessMime(String path){String lower=path.toLowerCase(Locale.ROOT); if(lower.endsWith(".png")) return "image/png"; if(lower.endsWith(".webp")) return "image/webp"; if(lower.endsWith(".gif")) return "image/gif"; if(lower.endsWith(".bmp")) return "image/bmp"; if(lower.endsWith(".avif")) return "image/avif"; return "image/jpeg";}
    private TextView button(String text,int fg){TextView v=new TextView(this); v.setText(text); v.setTextColor(fg); v.setGravity(Gravity.CENTER); v.setTextSize(15); v.setPadding(dp(9),dp(7),dp(9),dp(7)); return v;}
    private void persist(){comic.opened=System.currentTimeMillis(); ComicLibrary.save(this,comic);}    
    private void navigate(int delta){int target=comic.chapter+delta; if(target>=0&&target<chapters.size()){comic.chapter=target; comic.scroll=0;comic.page=0; loadChapter();}}
    private void contents(){
        String[] titles=new String[chapters.size()];int[] counts=new int[chapters.size()];
        for(int i=0;i<chapters.size();i++){titles[i]=chapters.get(i).title;counts[i]=ComicLibrary.chapterPages(chapters.get(i));}
        int pages; synchronized(currentAssets){pages=currentAssets.size();}
        UiNavigationSheet.showComicChapters(this,titles,counts,comic.chapter,comic.page,pages,n->{comic.chapter=n;comic.page=0;comic.scroll=0;loadChapter();});
    }

    private void loadChapter(){
        if(web==null) return;
        if(chapters.isEmpty()){
            String msg = ComicLibrary.isArchiveKind(comic.kind) ? "Комикс не содержит изображений или архив не удалось прочитать" : "В папке нет HTML-файлов или изображений";
            web.loadData("<p style='padding:16px'>"+msg+"</p>","text/html","UTF-8");
            return;
        }
        comic.chapter=Math.max(0,Math.min(comic.chapter,chapters.size()-1));
        // Retain existing assets until the prepared replacement is ready.
        comic.pageTotal=comic.chapter<comic.pageCounts.size()?Math.max(0,comic.pageCounts.get(comic.chapter)):0;
        persist();updateChapterButton();
        final ComicLibrary.Chapter chapter=chapters.get(comic.chapter);final int token=++generation;
        final int previousScroll=comic.scroll,previousPage=comic.page,mode=comic.mode;
        restoringScroll=previousScroll;restoringIndex=previousPage;restoringToken=token;restoringPage=true;
        web.animate().cancel(); // Never hide the entire WebView during lazy-image loading.
        new Thread(() -> {
            try {
                PreparedChapter ready=buildChapterHtml(chapter,mode,token);
                runOnUiThread(() -> {if(token!=generation||isFinishing())return;
                    generations.put(token,ready.assets);
                    // Keep only the current and immediately previous generation.
                    for(Integer old:generations.keySet())if(old<token-1)generations.remove(old);
                    synchronized(currentAssets){currentAssets.clear();currentAssets.addAll(ready.assets);}
                    updateChapterButton();
                    web.loadDataWithBaseURL("https://comic.local/",ready.html,"text/html","UTF-8",null);
                    web.postDelayed(()->{if(token==generation&&restoringPage){
                        web.evaluateJavascript("restore("+previousScroll+","+previousPage+")",null);
                        restoringPage=false;
                    }},1800L);
                });
            } catch(Exception e){runOnUiThread(()->{if(token!=generation)return;
                    restoringPage=false;
                    Toast.makeText(this,"Ошибка открытия главы: "+e.getMessage(),Toast.LENGTH_LONG).show();});}
        },"comic-open").start();
    }

    private PreparedChapter buildChapterHtml(ComicLibrary.Chapter chapter,int mode,int token) throws Exception {
        ArrayList<PageAsset> assets="html".equals(chapter.type)
            ?prepareHtmlAssets(chapter):prepareImageAssets(chapter);
        return new PreparedChapter(renderCurrentAssets(assets,mode,token),assets);
    }
    private ArrayList<PageAsset> prepareHtmlAssets(ComicLibrary.Chapter chapter) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(InputStream in=getContentResolver().openInputStream(chapter.htmlFile.getUri())){
            if(in==null)throw new Exception("HTML недоступен");
            byte[] buffer=new byte[8192];int total=0,n;
            while((n=in.read(buffer))!=-1){total+=n;
                if(total>4*1024*1024)throw new Exception("HTML превышает 4 МБ");output.write(buffer,0,n);}
        }
        Document doc=Jsoup.parse(new String(output.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
        ArrayList<PageAsset> pages=new ArrayList<>();
        DocumentFile tree=ComicLibrary.tree(this,comic);
        for(Element img:doc.select("img")){
            String path=img.hasAttr("data-src")?img.attr("data-src"):img.attr("src");
            if(path.isEmpty()||path.startsWith("data:")||path.contains(":")||path.startsWith("/")||path.contains(".."))continue;
            PageAsset asset=new PageAsset();asset.relativePath=path;asset.mime=guessMime(path);asset.tree=tree;
            try{asset.width=Integer.parseInt(img.attr("width"));asset.height=Integer.parseInt(img.attr("height"));}
            catch(Exception ignored){}
            pages.add(asset);
        }
        return pages;
    }
    private ArrayList<PageAsset> prepareImageAssets(ComicLibrary.Chapter chapter) throws Exception {
        ArrayList<PageAsset> pages=new ArrayList<>();
        ArrayList<String> imagePaths="mangalib_cbz".equals(chapter.type)
            ?MangaLibStorage.extractForReading(this,chapter.imagePaths.get(0))
            :ComicLibrary.pagesForChapter(this,comic,chapter);
        chapter.pageCount=imagePaths.size();
        DocumentFile tree=null;
        if(!"archive".equals(chapter.type)&&!"archive_tree".equals(chapter.type)
                &&!"mangalib_cbz".equals(chapter.type))tree=ComicLibrary.tree(this,comic);
        for(String path:imagePaths){
            PageAsset asset=new PageAsset();
            if("archive".equals(chapter.type)||"archive_tree".equals(chapter.type)||"mangalib_cbz".equals(chapter.type)){
                asset.localFile=new File(path);asset.mime=guessMime(path);
            }else{asset.relativePath=path;asset.mime=guessMime(path);asset.tree=tree;}
            pages.add(asset);
        }
        return pages;
    }
    private String renderCurrentAssets(ArrayList<PageAsset> pages,int mode,int token){
        StringBuilder html=new StringBuilder();
        html.append("<html><head><meta name='viewport' content='width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=5, user-scalable=yes'>");
        int bg=getSharedPreferences("reader",0).getInt("bg",0xfff3e7d5);
        String background=String.format(Locale.ROOT,"#%06X",bg&0xffffff);
        html.append("<style>html,body{margin:0;padding:0;background:").append(background)
            .append("}img{display:block;margin:0 auto;image-rendering:auto;}");
        if(mode==0) html.append("#pages{display:flex;overflow-x:auto;overflow-y:hidden;scroll-snap-type:x mandatory;width:100vw;height:100vh;-webkit-overflow-scrolling:touch}img{width:100vw;height:100vh;object-fit:contain;flex-shrink:0;scroll-snap-align:start}");
        else html.append("html{overflow-anchor:auto}#pages{display:block}img{width:100%;height:auto;aspect-ratio:3 / 4;overflow-anchor:auto}");
        html.append("</style></head><body><div id='pages'>");
        for(int i=0;i<pages.size();i++){
            PageAsset asset=pages.get(i);
            html.append("<img loading='").append(i==0?"eager":"lazy").append("' ");
            if(asset.width>0&&asset.height>0)html.append("width='").append(asset.width)
                .append("' height='").append(asset.height).append("' ");
            html.append("src='https://comic.local/chapter/").append(token).append("/page/").append(i).append("'>");
        }
        html.append("</div><script>");
        html.append("function pictures(){return document.getElementById('pages').getElementsByTagName('img')}");
        html.append("function page(){var images=pictures();if(!images.length)return 0;");
        if(mode==0)html.append("var el=document.getElementById('pages');return Math.min(images.length-1,Math.round(el.scrollLeft/Math.max(1,el.clientWidth)))");
        else html.append("var target=window.innerHeight*.3,best=-1,dist=1e30;for(var i=0;i<images.length;i++){var r=images[i].getBoundingClientRect();if(r.height<2)continue;if(r.top<=target&&r.bottom>=target)return i;var d=r.top>target?r.top-target:target-r.bottom;if(d<dist){dist=d;best=i}}return best");
        html.append("}");
        html.append("function positionInfo(){var n=page();var v=");
        html.append(mode==0?"document.getElementById('pages').scrollLeft":"window.scrollY");
        html.append(mode==0?";return n+'|'+Math.round(v)}":";return n+'|'+(n<0?0:Math.round(v-pictures()[n].offsetTop))}");
        html.append("function restore(offset,index){var imgs=pictures();if(!imgs.length)return;var n=Math.max(0,Math.min(index,imgs.length-1));");
        if(mode==0)html.append("var el=document.getElementById('pages');el.scrollLeft=n*el.clientWidth;");
        else {
            boolean anchored=getSharedPreferences("reader",0).getBoolean("comic_anchor_v175:"+comic.uri,false);
            html.append("window.scrollTo(0,")
                .append(anchored?"imgs[n].offsetTop+Math.max(0,offset)":"offset>0?offset:imgs[n].offsetTop")
                .append(");");
        }
        html.append("}");
        // Geometry is reserved before image loads. Restore the page anchor immediately;
        // never force downloads of all preceding pages or do repeated scrollTo loops.
        html.append("function restoreWhenReady(offset,index,token){var imgs=pictures(),n=Math.max(0,Math.min(index,imgs.length-1));");
        html.append("for(var i=Math.max(0,n-1);i<=Math.min(imgs.length-1,n+2);i++)imgs[i].loading='eager';");
        html.append("requestAnimationFrame(function(){restore(offset,index);requestAnimationFrame(function(){if(window.ComicRestore)ComicRestore.onPositionRestored(token);});});}");
        // Page swipes remain native; an ACTION_UP without movement toggles the floating chrome.
        html.append("</script></body></html>");
        return html.toString();
    }

    @Override protected void onResume(){super.onResume();
        // Repaint toolbar chips in place; never reset the WebView, chapter or scroll position.
        int fresh=UiColorScheme.accent(this);
        if(displayedUiAccent!=fresh){displayedUiAccent=fresh;
            if(modeAction!=null){modeAction.setBackground(rounded(fresh,12));
                modeAction.setColorFilter(ReaderButtonColors.contrastingText(fresh));}
        }
        tracking=true;tracker.removeCallbacks(trackPosition);tracker.postDelayed(trackPosition,600);}
    @Override protected void onPause(){
        tracking=false;tracker.removeCallbacks(trackPosition);
        if(web!=null&&!restoringPage){try{web.evaluateJavascript("positionInfo()",value->{try{
            String[] parts=value.replace("\"","").split("\\|");
            int page=Integer.parseInt(parts[0]);
            if(page>=0){comic.page=page;comic.scroll=Math.max(0,Integer.parseInt(parts[1]));
                getSharedPreferences("reader",0).edit().putBoolean("comic_anchor_v175:"+comic.uri,true).apply();persist();}
        }catch(Exception ignored){}});}catch(Exception ignored){}}
        super.onPause();
    }
    @Override protected void onDestroy(){tracker.removeCallbacks(trackPosition);if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
