package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import java.io.File;
import android.util.Log;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Online reading through the original public MRT WebSpeechKit page.
 *
 * The user-supplied MRT Desktop TTS inspired the early AudioContext hook.
 * The experimental prefetch path captures the original encoded audio buffers,
 * caches MP3 candidates, and hands ready files to the independent Media3 service.
 * Playback controls NEVER call WebAudio buttons; the WebUI is synthesis-only.
 * The electronic book has its own progress
 * and does not overwrite locally imported MP3 audiobook progress.
 */
public class MrtOnlineActivity extends Activity {
    private static final String PAGE = "https://alkohole.github.io/machine-reading-text/index.html";
    private static final String TRUSTED_ORIGIN = "https://alkohole.github.io";
    private static final int BG = 0xfff3e7d5;
    private static final int INK = 0xff30221e;
    private static final int MUTED = 0xff716259;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<Integer> wordOffsets = new ArrayList<>();
    private final ArrayList<Integer> history = new ArrayList<>();
    private WebView web;
    private TextView info, captureInfo, hint, fragmentText, queueStatus;
    private LinearLayout nativeReader;
    private ScrollView nativeTextScroll;
    private int optionsLoadAttempts;
    private Button voiceChoice, speedChoice, emotionChoice, fallbackButton, autoloadButton, stopButton;
    private final LinkedHashMap<String, String> voices = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> speeds = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> emotions = new LinkedHashMap<>();
    private boolean webPanelVisible;
    private Button play, pause, previous, next, lengthButton;
    private CheckBox continuous;
    private String bookUri = "", bookText = "";
    private int wordIndex, nextWord, segmentNumber = 1, segmentLimit = MrtSegmentSettings.DEFAULT;
    private boolean bookLoaded, pageLoaded, engineReady, segmentInserted, captureSupported;
    private boolean playing, paused, finishing, closed;
    private int encodedChunks;
    private long encodedBytes;
    private int startToken = 0;
    private int insertFailures = 0;
    private int playbackStartRetries = 0;
    private long lastUiUpdate;
    private CheckBox prefetchToggle;
    private Button aheadButton;
    private MrtAudioCache audioCache;
    private final ArrayList<PrefetchClip> audioArchive = new ArrayList<>();
    private ListView queueList;
    private BaseAdapter queueAdapter;
    private TextView queueTitle;
    private boolean clearingCache;
    private final ArrayList<PrefetchClip> audioQueue = new ArrayList<>();
    private PrefetchClip generatingClip;
    private boolean finalizingCapture;
    private MrtPlaybackService nativePlayer;
    private boolean playerBound;
    private int playlistGeneration;
    private SeekBar fileSeek, bookSeek;
    private TextView fileTime, bookTime;
    private boolean draggingFile, draggingBook;
    private final MrtPlaybackService.Listener nativeListener = new MrtPlaybackService.Listener() {
        @Override public void onTrackChanged(int start, int end, String key, boolean completedPrevious) {
            // Defer queue changes outside of ExoPlayer's own transition callback.
            final int generation=playlistGeneration;
            main.post(() -> {
                if (closed || !bookLoaded || generation!=playlistGeneration) return;
                for (PrefetchClip clip : audioArchive) {
                    if (completedPrevious && clip.end == start && !clip.played) clip.played = true;
                }
                PrefetchClip matched=null;
                for (PrefetchClip clip:audioArchive)
                    if (clip.start==start && key.equals(clip.cacheKey+":"+clip.start)) { matched=clip;break; }
                if (matched!=null) {
                    wordIndex=matched.start;
                    nextWord=matched.end;
                    if(fragmentText!=null)fragmentText.setText(matched.text);
                    if(nativeTextScroll!=null)nativeTextScroll.post(()->nativeTextScroll.scrollTo(0,0));
                    savePosition();
                    persistArchive();
                }
                while(!audioQueue.isEmpty() && audioQueue.get(0).end <= wordIndex)
                    audioQueue.remove(0);
                drivePrefetch();
                updateUi();
            });
        }
        @Override public void onPlaybackStateChanged() {main.post(() -> {
            if(closed)return;
            if(nativePlayer!=null && nativePlayer.isAtEnd()) {
                String playedKey=nativePlayer.currentKey();
                for(PrefetchClip clip:audioArchive) {
                    if(playedKey.equals(clip.cacheKey+":"+clip.start) && !clip.played){
                        clip.played=true;
                        persistArchive();
                        break;
                    }
                }
            }
            updateUi();
        });}
    };
    private final ServiceConnection playerConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed) return;
            nativePlayer=((MrtPlaybackService.LocalBinder)binder).getService();
            nativePlayer.prepareBook(bookUri);
            nativePlayer.setListener(nativeListener);
            nativePlayer.setAutoContinue(getSharedPreferences("reader",0).getBoolean("mrt_auto_continue",true));
            syncReadyFiles();
            updateUi();
        }
        @Override public void onServiceDisconnected(ComponentName name) {nativePlayer=null;}
    };
    private final Runnable timelineUpdater = new Runnable() {
        @Override public void run() {
            updateTimebars();
            if(!closed) main.postDelayed(this,500);
        }
    };
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) pauseForExternalAudio();
        }
    };
    private final BroadcastReceiver exclusiveAudioReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AudioModeCoordinator.STOP_MRT.equals(intent.getAction())) {
                if (nativePlayer != null) nativePlayer.stopAudio();
                stopPrefetch();
            }
        }
    };
    private boolean prefetchActive;
    private int prefetchAhead = MrtPrefetchSettings.DEFAULT, captureSession = 0;
    private int prefetchedClips;
    private String prefetchError = "";

    private static final String MRT_LOG = "MRT-Prefetch";
    private static final int READY_POLL_MS = 250;
    private static final int READY_WAIT_MS = 18000;
    private static final int RETRY_PAUSE_MS = 2500;

    private static final class PrefetchClip {
        int start, end;
        int failures;
        long retryAfter;
        long fileBytes;
        int chunkCount;
        boolean played;
        String text, cacheKey;
        File file;
        PrefetchClip(int start, int end, String text, String key) {
            this.start = start; this.end = end; this.text = text; this.cacheKey = key;
        }
    }

    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    private Button button(String title) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(title);
        b.setTextSize(13);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(5), dp(2), dp(5), dp(2));
        b.setTextColor(INK);
        b.setBackground(rounded(0xfffff9f0, 14));
        return b;
    }
    private TextView topAction(int icon, String description, View.OnClickListener action) {
        TextView view = new TextView(this);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(0xfffff9f0, 13));
        view.setPadding(dp(11), 0, dp(11), 0);
        view.setContentDescription(description);
        android.graphics.drawable.Drawable drawable = getDrawable(icon);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTint(INK);
            drawable.setBounds(0, 0, dp(22), dp(22));
            view.setCompoundDrawables(drawable, null, null, null);
        }
        view.setOnClickListener(action);
        return view;
    }
    private Button iconPill(int icon, String title, int bgColor, int textColor) {
        Button b = button(title);
        b.setTextColor(textColor);
        b.setBackground(rounded(bgColor, 15));
        android.graphics.drawable.Drawable drawable = getDrawable(icon);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTint(textColor);
            drawable.setBounds(0, 0, dp(18), dp(18));
            b.setCompoundDrawables(drawable, null, null, null);
            b.setCompoundDrawablePadding(dp(8));
        }
        b.setPadding(dp(12), dp(5), dp(12), dp(5));
        return b;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        bookUri = getIntent().getStringExtra("book_uri");
        if (bookUri == null || bookUri.isEmpty()) bookUri = getSharedPreferences("reader", 0).getString("uri", "");
        wordIndex = Math.max(0, getIntent().getIntExtra("word_index", 0));
        segmentLimit = MrtSegmentSettings.get(getSharedPreferences("reader", 0));
        bindService(new Intent(this,MrtPlaybackService.class),playerConnection,Context.BIND_AUTO_CREATE);
        playerBound=true;
        main.post(timelineUpdater); // MediaSession belongs exclusively to MrtPlaybackService.
        // On entering MRT, terminate the previous TTS/audiobook service.
        if (AudioPlaybackService.instance != null) {
            startService(new Intent(this, AudioPlaybackService.class).setAction(AudioPlaybackService.STOP));
        }
        IntentFilter exclusiveFilter = new IntentFilter(AudioModeCoordinator.STOP_MRT);
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(exclusiveAudioReceiver, exclusiveFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(exclusiveAudioReceiver, exclusiveFilter);
        IntentFilter noisyFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(noisyReceiver, noisyFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisyReceiver, noisyFilter);
        draw();
        if (bookUri.isEmpty()) {
            info.setText("Сначала выберите EPUB, FB2 или HTML в библиотеке.");
            return;
        }
        loadBook();
    }

    private void draw() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(20), dp(10), dp(8));
        root.setBackgroundColor(BG);
        ScrollView pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.addView(root);
        setContentView(pageScroll);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(52)));
        titleRow.addView(topAction(R.drawable.ic_back, "Назад к чтению", v -> finish()), new LinearLayout.LayoutParams(dp(44), dp(46)));
        TextView heading = new TextView(this);
        heading.setText("Слушать онлайн · MRT");
        heading.setTextColor(INK);
        heading.setTextSize(20);
        heading.setTypeface(Typeface.SERIF, Typeface.BOLD);
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(0, -2, 1);
        headLp.leftMargin = dp(8);
        titleRow.addView(heading, headLp);
        LinearLayout.LayoutParams topBtnLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        titleRow.addView(topAction(R.drawable.ic_queue, "Список очереди", v -> showQueueDialog()), topBtnLp);
        LinearLayout.LayoutParams topBtnLp2 = new LinearLayout.LayoutParams(dp(46), dp(46));
        topBtnLp2.leftMargin = dp(6);
        titleRow.addView(topAction(R.drawable.ic_folder_audio, "Папка аудио", v -> openAudioFolder()), topBtnLp2);
        LinearLayout.LayoutParams topBtnLp3 = new LinearLayout.LayoutParams(dp(46), dp(46));
        topBtnLp3.leftMargin = dp(6);
        titleRow.addView(topAction(R.drawable.ic_settings, "Онлайн-настройки", v -> startActivity(new Intent(this, SettingsActivity.class).putExtra("online_tab", true))), topBtnLp3);

        info = new TextView(this);
        info.setText("Открываю электронную книгу…");
        info.setTextColor(INK);
        info.setTextSize(13);
        info.setPadding(dp(5), dp(6), dp(5), dp(2));
        root.addView(info);

        captureInfo = new TextView(this);
        captureInfo.setText("Инициализация веб-озвучивания…");
        captureInfo.setTextColor(MUTED);
        captureInfo.setTextSize(12);
        captureInfo.setPadding(dp(5), 0, dp(5), dp(4));
        root.addView(captureInfo);

        LinearLayout autoloadRow = new LinearLayout(this);
        autoloadRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams autoloadLp = new LinearLayout.LayoutParams(-1, dp(44));
        autoloadLp.topMargin = dp(4);
        root.addView(autoloadRow, autoloadLp);
        autoloadButton = iconPill(R.drawable.ic_autoload, "Автоподгрузка", 0xffa26949, Color.WHITE);
        autoloadButton.setOnClickListener(v -> toggleAutoload());
        autoloadRow.addView(autoloadButton, new LinearLayout.LayoutParams(0, -1, 1.05f));
        queueStatus = new TextView(this);
        queueStatus.setTextColor(INK);
        queueStatus.setTextSize(13);
        queueStatus.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(0, -1, 1f);
        statusLp.leftMargin = dp(10);
        autoloadRow.addView(queueStatus, statusLp);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(-1, dp(60));
        controlsLp.topMargin = dp(8);
        root.addView(controls, controlsLp);
        previous = button("");
        previous.setBackground(rounded(0xfffff9f0, 16));
        previous.setContentDescription("Предыдущий фрагмент");
        previous.setOnClickListener(v -> previousSegment());
        setButtonIcon(previous, R.drawable.ic_prev_chapter, INK);
        controls.addView(previous, new LinearLayout.LayoutParams(0, dp(54), 1));
        Button rewind10 = button("");
        rewind10.setContentDescription("Назад на 10 секунд");
        rewind10.setOnClickListener(v -> {if(nativePlayer!=null) nativePlayer.rewind10();});
        setButtonIcon(rewind10,R.drawable.ic_rewind_10s,INK);
        LinearLayout.LayoutParams rewLp = new LinearLayout.LayoutParams(0, dp(54), 1);
        rewLp.leftMargin=dp(5);
        controls.addView(rewind10,rewLp);
        play = button("");
        play.setBackground(rounded(0xffa26949, 16));
        play.setContentDescription("Воспроизвести или поставить на паузу");
        play.setOnClickListener(v -> startPlayback());
        setButtonIcon(play, R.drawable.ic_play, Color.WHITE);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(0, dp(56), 1.15f);
        playLp.leftMargin = dp(5);
        controls.addView(play, playLp);
        stopButton = button("");
        stopButton.setContentDescription("Остановить воспроизведение MP3");
        stopButton.setOnClickListener(v -> stopListeningKeepCache());
        setButtonIcon(stopButton, R.drawable.ic_stop, INK);
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(0, dp(54), 1);
        stopLp.leftMargin = dp(5);
        controls.addView(stopButton, stopLp);
        Button forward10=button("");
        forward10.setContentDescription("Вперёд на 10 секунд");
        forward10.setOnClickListener(v -> {if(nativePlayer!=null) nativePlayer.forward10();});
        setButtonIcon(forward10,R.drawable.ic_forward_10s,INK);
        LinearLayout.LayoutParams fwLp=new LinearLayout.LayoutParams(0,dp(54),1);
        fwLp.leftMargin=dp(5);
        controls.addView(forward10,fwLp);
        next = button("");
        next.setContentDescription("Следующий фрагмент");
        next.setOnClickListener(v -> nextSegment(false));
        setButtonIcon(next, R.drawable.ic_next_chapter, INK);
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(0, dp(54), 1);
        nextLp.leftMargin = dp(5);
        controls.addView(next, nextLp);

        LinearLayout tracksCard=new LinearLayout(this);
        tracksCard.setOrientation(LinearLayout.VERTICAL);
        tracksCard.setPadding(dp(12),dp(10),dp(12),dp(10));
        tracksCard.setBackground(rounded(0xfffff9f0,16));
        LinearLayout.LayoutParams tracksLp = new LinearLayout.LayoutParams(-1, -2);
        tracksLp.topMargin=dp(9);
        root.addView(tracksCard,tracksLp);
        LinearLayout bookLine=new LinearLayout(this);bookLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView bookLabel=new TextView(this);bookLabel.setText("Вся книга");bookLabel.setTextSize(13);bookLabel.setTextColor(INK);
        bookLine.addView(bookLabel,new LinearLayout.LayoutParams(0,-2,1));
        bookTime=new TextView(this);bookTime.setTextColor(MUTED);bookTime.setTextSize(12);bookTime.setText("0%");
        bookLine.addView(bookTime);
        tracksCard.addView(bookLine);
        bookSeek=new SeekBar(this);bookSeek.setMax(10000);
        tracksCard.addView(bookSeek,new LinearLayout.LayoutParams(-1,dp(34)));
        bookSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar seek,int value,boolean fromUser){if(fromUser)bookTime.setText(value/100+"%");}
            @Override public void onStartTrackingTouch(SeekBar bar){draggingBook=true;}
            @Override public void onStopTrackingTouch(SeekBar bar){
                draggingBook=false;
                if(bookLoaded && !wordOffsets.isEmpty()){
                    int target=Math.min(wordOffsets.size()-1,(int)((long)bar.getProgress()*wordOffsets.size()/10000));
                    startPrefetchAt(target,nativePlayer!=null && nativePlayer.isPlaying());
                }
            }
        });
        LinearLayout fileLine=new LinearLayout(this);fileLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView fileLabel=new TextView(this);fileLabel.setText("Фрагмент MP3");fileLabel.setTextSize(13);fileLabel.setTextColor(INK);
        fileLine.addView(fileLabel,new LinearLayout.LayoutParams(0,-2,1));
        fileTime=new TextView(this);fileTime.setTextColor(MUTED);fileTime.setTextSize(12);fileTime.setText("00:00 / 00:00");
        fileLine.addView(fileTime);
        tracksCard.addView(fileLine);
        fileSeek=new SeekBar(this);fileSeek.setMax(1000);
        tracksCard.addView(fileSeek,new LinearLayout.LayoutParams(-1,dp(34)));
        fileSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean fromUser){
                if(fromUser && nativePlayer!=null) {
                    long length=nativePlayer.durationMs();
                    fileTime.setText(formatTime(length*value/1000)+" / "+formatTime(length));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar){draggingFile=true;}
            @Override public void onStopTrackingTouch(SeekBar bar){
                draggingFile=false;
                if(nativePlayer!=null) nativePlayer.seekWithin(nativePlayer.durationMs()*bar.getProgress()/1000);
            }
        });
        pause = button("");
        pause.setVisibility(View.GONE);
        lengthButton = button("Размер фрагмента");
        lengthButton.setVisibility(View.GONE);
        continuous = new CheckBox(this);
        continuous.setChecked(getSharedPreferences("reader", 0).getBoolean("mrt_auto_continue", true));
        continuous.setVisibility(View.GONE);
        prefetchToggle = new CheckBox(this);
        prefetchToggle.setChecked(getSharedPreferences("reader", 0).getBoolean("mrt_prefetch", true));
        prefetchToggle.setVisibility(View.GONE);
        prefetchAhead = MrtPrefetchSettings.get(getSharedPreferences("reader", 0));
        aheadButton = button(MrtPrefetchSettings.label(prefetchAhead));
        aheadButton.setVisibility(View.GONE);

        LinearLayout selectors = new LinearLayout(this);
        selectors.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams selectorLp = new LinearLayout.LayoutParams(-1, dp(48));
        selectorLp.topMargin = dp(10);
        selectorLp.bottomMargin = dp(8);
        root.addView(selectors, selectorLp);
        voiceChoice = button("Голос ▾");
        voiceChoice.setContentDescription("Выбрать голос MRT");
        voiceChoice.setSingleLine(true);
        voiceChoice.setEllipsize(android.text.TextUtils.TruncateAt.END);
        voiceChoice.setOnClickListener(v -> chooseMrtOption("ttsModel", voices, "Голос"));
        selectors.addView(voiceChoice, new LinearLayout.LayoutParams(0, -1, 1.6f));
        speedChoice = button("Скорость ▾");
        speedChoice.setTextSize(11);
        speedChoice.setSingleLine(true);
        speedChoice.setEllipsize(android.text.TextUtils.TruncateAt.END);
        speedChoice.setOnClickListener(v -> chooseMrtOption("ttsSpeed", speeds, "Скорость"));
        LinearLayout.LayoutParams speedLp = new LinearLayout.LayoutParams(0, -1, 1f);
        speedLp.leftMargin = dp(5);
        selectors.addView(speedChoice, speedLp);
        emotionChoice = button("Эмоция ▾");
        emotionChoice.setTextSize(11);
        emotionChoice.setSingleLine(true);
        emotionChoice.setEllipsize(android.text.TextUtils.TruncateAt.END);
        emotionChoice.setOnClickListener(v -> chooseMrtOption("ttsEmotion", emotions, "Эмоция"));
        LinearLayout.LayoutParams emotionLp = new LinearLayout.LayoutParams(0, -1, 1.1f);
        emotionLp.leftMargin = dp(5);
        selectors.addView(emotionChoice, emotionLp);

        web = new WebView(this) {
            @Override public boolean onCheckIsTextEditor() { return false; }
            @Override public InputConnection onCreateInputConnection(EditorInfo outAttrs) { return null; }
        };
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        web.setBackgroundColor(Color.WHITE);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri target = req.getUrl();
                return !isTrustedPage(target);
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!isTrustedPage(Uri.parse(url))) return;
                pageLoaded = true;
                eval("!!window.__rsvpMrt", present -> {
                    if (!"true".equals(present)) installLateObserverIfNeeded();
                    waitForEngine(0);
                });
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
                if (req.isForMainFrame()) info.setText("Страница MRT недоступна. Проверьте подключение и повторите попытку.");
            }
        });
        FrameLayout engineRegion = new FrameLayout(this);
        LinearLayout.LayoutParams engineLp = new LinearLayout.LayoutParams(-1, dp(430));
        root.addView(engineRegion, engineLp);
        engineRegion.addView(web, new FrameLayout.LayoutParams(-1, -1));
        web.setFocusable(false);
        web.setFocusableInTouchMode(false);

        nativeReader = new LinearLayout(this);
        nativeReader.setOrientation(LinearLayout.VERTICAL);
        nativeReader.setBackground(rounded(0xfffff9f1, 20));
        nativeReader.setPadding(dp(16), dp(13), dp(16), dp(10));
        engineRegion.addView(nativeReader, new FrameLayout.LayoutParams(-1, -1));
        TextView currentLabel = new TextView(this);
        currentLabel.setText("Текст текущего фрагмента");
        currentLabel.setTextColor(MUTED);
        currentLabel.setTextSize(13);
        nativeReader.addView(currentLabel);
        queueTitle = new TextView(this);
        queueTitle.setVisibility(View.GONE);
        nativeReader.addView(queueTitle, new LinearLayout.LayoutParams(-1, -2));
        queueList = new ListView(this);
        queueList.setVisibility(View.GONE);
        queueList.setDividerHeight(1);
        queueList.setCacheColorHint(Color.TRANSPARENT);
        queueList.setBackground(rounded(0xfff5ebdd, 9));
        queueAdapter = new BaseAdapter() {
            @Override public int getCount() { return audioArchive.size(); }
            @Override public Object getItem(int i) { return audioArchive.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int index, View convertView, android.view.ViewGroup parent) {
                LinearLayout item = convertView instanceof LinearLayout ? (LinearLayout)convertView : new LinearLayout(MrtOnlineActivity.this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(dp(10), dp(6), dp(10), dp(6));
                TextView heading, details;
                if (item.getChildCount() == 2) {
                    heading = (TextView)item.getChildAt(0);
                    details = (TextView)item.getChildAt(1);
                } else {
                    heading = new TextView(MrtOnlineActivity.this);
                    heading.setTextSize(12);
                    heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                    details = new TextView(MrtOnlineActivity.this);
                    details.setTextSize(11);
                    item.addView(heading);
                    item.addView(details);
                }
                PrefetchClip clip = audioArchive.get(index);
                boolean loading = clip == generatingClip;
                boolean ready = clip.file != null && clip.file.isFile();
                boolean current = prefetchActive && !audioQueue.isEmpty() && audioQueue.get(0) == clip;
                String status = clip.played ? "Прослушан" : loading ? "Скачивается" : ready ? (current ? "Текущий" : "Готов") : "Ожидает";
                int n = clip.chunkCount;
                long bytes = clip.fileBytes;
                if (loading) { n = encodedChunks; bytes = encodedBytes; }
                else if (ready && bytes == 0) bytes = clip.file.length();
                heading.setText("№" + (index + 1) + " · " + clip.text.length() + " символов · " + status);
                details.setText(clip.cacheKey.substring(0, Math.min(10, clip.cacheKey.length())) + ".mp3 · чанков: " + n + " · " + String.format(java.util.Locale.ROOT, "%.1f", bytes / 1024.0) + " КБ");
                int color = clip.played ? 0xff999999 : (loading ? 0xff9b603e : current ? 0xff305c35 : INK);
                heading.setTextColor(color);
                details.setTextColor(clip.played ? 0xffaaaaaa : MUTED);
                return item;
            }
        };
        queueList.setAdapter(queueAdapter);
        nativeReader.addView(queueList, new LinearLayout.LayoutParams(-1, 1));

        nativeTextScroll = new ScrollView(this);
        nativeTextScroll.setFillViewport(true);
        nativeTextScroll.setVerticalScrollBarEnabled(true);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollLp.topMargin = dp(8);
        nativeReader.addView(nativeTextScroll, scrollLp);
        fragmentText = new TextView(this);
        fragmentText.setText("Загружаю текст книги…");
        fragmentText.setTextSize(19);
        fragmentText.setTypeface(Typeface.SERIF);
        fragmentText.setTextColor(INK);
        fragmentText.setLineSpacing(dp(5), 1.1f);
        fragmentText.setTextIsSelectable(false);
        nativeTextScroll.addView(fragmentText, new ScrollView.LayoutParams(-1, -2));

        hint = new TextView(this);
        hint.setVisibility(View.GONE);
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        fallbackButton = button("Показать веб-панель MRT");
        fallbackButton.setTextSize(11);
        fallbackButton.setVisibility(View.GONE);
        fallbackButton.setOnClickListener(v -> showWebPanel(!webPanelVisible));
        LinearLayout.LayoutParams fallbackLp = new LinearLayout.LayoutParams(-1, dp(35));
        fallbackLp.topMargin = dp(6);
        root.addView(fallbackButton, fallbackLp);
        installEarlyObserver();
        web.loadUrl(PAGE);
    }

    private void setButtonIcon(Button button, int iconRes, int tint) {
        android.graphics.drawable.Drawable drawable = getDrawable(iconRes);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTint(tint);
            drawable.setBounds(0, 0, dp(22), dp(22));
            button.setCompoundDrawables(drawable, null, null, null);
        }
        button.setPadding(0, 0, 0, 0);
        button.setText("");
        button.setGravity(Gravity.CENTER);
    }

    /** Never give IME focus to the hidden third-party textarea. */
    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && web != null) imm.hideSoftInputFromWindow(web.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    /** Native-only reading by default; original web UI is a diagnostic fallback. */
    private void showWebPanel(boolean visible) {
        webPanelVisible = visible;
        if (nativeReader != null) nativeReader.setVisibility(visible ? View.GONE : View.VISIBLE);
        if (fallbackButton != null) fallbackButton.setText(visible ? "Скрыть веб-панель MRT" : "Показать веб-панель MRT");
        eval("window.__rsvpMrt && window.__rsvpMrt.setNativeUiHidden(" + (!visible) + ")", null);
        if (!visible) hideKeyboard();
    }

    private void toggleAutoload() {
        if (prefetchActive) {
            stopPrefetch();
            captureInfo.setText("Автоподгрузка остановлена.");
            updateUi();
            return;
        }
        beginPrefetch(false);
        captureInfo.setText(prefetchActive
                ? "Автоподгрузка включена. MP3 сохраняются без запуска звука."
                : "Ожидайте загрузки книги и движка MRT.");
        updateUi();
    }

    /** Explicit user navigation resets the native playlist, not the WebUI transport. */
    private void startPrefetchAt(int startWord, boolean autoplay) {
        ++playlistGeneration;
        stopPrefetch(); // cancellation is confined to the separate downloader
        if(nativePlayer!=null) nativePlayer.clearQueue();
        if(closed || !bookLoaded)return;
        wordIndex=Math.max(0,Math.min(startWord,Math.max(0,wordOffsets.size()-1)));
        nextWord=wordIndex;
        history.add(wordIndex);
        PrefetchClip preview=clipFromWord(wordIndex);
        if(preview!=null){nextWord=preview.end;fragmentText.setText(preview.text);}
        savePosition();
        if(autoplay && nativePlayer!=null) {
            // Queue-only Play request; the downloader does not control the player.
            startService(new Intent(this,MrtPlaybackService.class));
            nativePlayer.play();
        }
        if(getSharedPreferences("reader",0).getBoolean("mrt_prefetch",true))beginPrefetch(false);
        else syncReadyFiles();
        updateUi();
    }

    private void showQueueDialog() {
        final AlertDialog[] holder = new AlertDialog[1];
        ListView list = new ListView(this);
        list.setDividerHeight(1);
        list.setAdapter(queueAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            PrefetchClip clip = audioArchive.get(position);
            if (holder[0] != null) holder[0].dismiss();
            startPrefetchAt(clip.start, true);
        });
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        TextView note = new TextView(this);
        note.setText("Выберите, с какого фрагмента продолжить.");
        note.setTextColor(MUTED);
        note.setTextSize(12);
        note.setPadding(dp(16), dp(12), dp(16), dp(8));
        wrapper.addView(note);
        wrapper.addView(list, new LinearLayout.LayoutParams(-1, dp(360)));
        Button clear = iconPill(R.drawable.ic_clear_audio, "Очистить MP3", 0xfffff9f0, INK);
        clear.setTextColor(0xffba322d);
        GradientDrawable clearOutline=rounded(0xfffff9f0,15);
        clearOutline.setStroke(dp(2),0xffd44b44);
        clear.setBackground(clearOutline);
        android.graphics.drawable.Drawable clearIcon=clear.getCompoundDrawables()[0];
        if(clearIcon!=null)clearIcon.setTint(0xffba322d);
        clear.setOnClickListener(v -> confirmClearAudio());
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.END);
        footer.setPadding(dp(12), dp(10), dp(12), dp(10));
        footer.addView(clear, new LinearLayout.LayoutParams(-2, dp(42)));
        wrapper.addView(footer);
        holder[0] = new AlertDialog.Builder(this).setTitle("Список очереди")
                .setView(wrapper)
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void loadMrtOptions() {
        String query = "(function(){function options(id){var el=document.getElementById(id);"
                + "return el?Array.from(el.options).filter(function(o){return !!o.value}).map(function(o){"
                + "return {value:o.value,label:(o.textContent||o.value).trim()};}):[];}"
                + "function value(id){var el=document.getElementById(id);return el?el.value:'';}"
                + "return {voices:options('ttsModel'),speeds:options('ttsSpeed'),emotions:options('ttsEmotion'),"
                + "selectedVoice:value('ttsModel'),selectedSpeed:value('ttsSpeed'),selectedEmotion:value('ttsEmotion')};})()";
        eval(query, result -> {
            if (closed || result == null) return;
            try {
                JSONObject all = new JSONObject(result);
                fillOptions(voices, all.optJSONArray("voices"));
                fillOptions(speeds, all.optJSONArray("speeds"));
                fillOptions(emotions, all.optJSONArray("emotions"));
                if (voices.isEmpty() && ++optionsLoadAttempts < 15) {
                    main.postDelayed(this::loadMrtOptions, 450);
                    return;
                }
                if (voices.isEmpty()) {
                    fallbackButton.setVisibility(View.VISIBLE);
                    hint.setText("Веб-движок не предоставил список голосов. Откройте веб-панель, чтобы выбрать голос вручную.");
                }
                String selectedVoice = getSharedPreferences("reader", 0).getString("mrt_selected_voice", "");
                String selectedSpeed = getSharedPreferences("reader", 0).getString("mrt_selected_speed", "");
                String selectedEmotion = getSharedPreferences("reader", 0).getString("mrt_selected_emotion", "");
                if (selectedVoice.isEmpty()) selectedVoice = all.optString("selectedVoice", "");
                if (selectedSpeed.isEmpty()) selectedSpeed = all.optString("selectedSpeed", "");
                if (selectedEmotion.isEmpty()) selectedEmotion = all.optString("selectedEmotion", "");
                if (voices.containsKey(selectedVoice) || speeds.containsKey(selectedSpeed) || emotions.containsKey(selectedEmotion)) {
                    SharedPreferences.Editor ed = getSharedPreferences("reader", 0).edit();
                    if (voices.containsKey(selectedVoice)) ed.putString("mrt_selected_voice", selectedVoice);
                    if (speeds.containsKey(selectedSpeed)) ed.putString("mrt_selected_speed", selectedSpeed);
                    if (emotions.containsKey(selectedEmotion)) ed.putString("mrt_selected_emotion", selectedEmotion);
                    ed.apply();
                }
                if (voices.containsKey(selectedVoice)) applyMrtOption("ttsModel", selectedVoice);
                if (speeds.containsKey(selectedSpeed)) applyMrtOption("ttsSpeed", selectedSpeed);
                if (emotions.containsKey(selectedEmotion)) applyMrtOption("ttsEmotion", selectedEmotion);
                updateChoiceLabels();
            } catch (Exception error) {
                captureInfo.setText("Не удалось получить голоса MRT: " + error.getMessage());
                if (fallbackButton != null) fallbackButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private void fillOptions(LinkedHashMap<String, String> target, JSONArray data) {
        target.clear();
        if (data == null) return;
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;
            String key = item.optString("value", "");
            if (!key.isEmpty()) target.put(key, item.optString("label", key));
        }
    }

    private void chooseMrtOption(String selectId, LinkedHashMap<String, String> options, String title) {
        if (options.isEmpty()) {
            Toast.makeText(this, "Ожидаю список настроек MRT…", Toast.LENGTH_SHORT).show();
            loadMrtOptions();
            return;
        }
        String[] keys = options.keySet().toArray(new String[0]);
        String[] labels = options.values().toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(title).setItems(labels, (dialog, position) -> {
            String chosen = keys[position];
            String preference = selectId.equals("ttsModel") ? "mrt_selected_voice" :
                    selectId.equals("ttsSpeed") ? "mrt_selected_speed" : "mrt_selected_emotion";
            getSharedPreferences("reader", 0).edit().putString(preference, chosen).apply();
            applyMrtOption(selectId, chosen);
            updateChoiceLabels();
        }).setNegativeButton("Отмена", null).show();
    }

    private void applyMrtOption(String selectId, String choice) {
        String js = "(function(){var x=document.getElementById(" + JSONObject.quote(selectId) + ");"
                + "if(!x)return false;x.value=" + JSONObject.quote(choice) + ";"
                + "x.dispatchEvent(new Event('change',{bubbles:true}));return true;})()";
        eval(js, null);
    }

    private void updateChoiceLabels() {
        SharedPreferences preferences = getSharedPreferences("reader", 0);
        String voice = preferences.getString("mrt_selected_voice", "");
        String speed = preferences.getString("mrt_selected_speed", "");
        String mood = preferences.getString("mrt_selected_emotion", "");
        if (voiceChoice != null) voiceChoice.setText(voices.containsKey(voice) ? voices.get(voice) + " ▾" : "Голос ▾");
        if (speedChoice != null) speedChoice.setText(speeds.containsKey(speed) ? speeds.get(speed) + " ▾" : "Скорость ▾");
        if (emotionChoice != null) emotionChoice.setText(emotions.containsKey(mood) ? emotions.get(mood) + " ▾" : "Эмоция ▾");
    }

    private boolean isTrustedPage(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && "alkohole.github.io".equalsIgnoreCase(uri.getHost())
                && uri.getPath() != null && uri.getPath().startsWith("/machine-reading-text/");
    }

    private String captureScript() {
        try (InputStream input = getAssets().open("mrt_online_capture.js")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = input.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception ignored) { return ""; }
    }

    private void installEarlyObserver() {
        Set<String> origins = new HashSet<>(Collections.singleton(TRUSTED_ORIGIN));
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(web, "RsvpMrtBridge", origins,
                        (view, message, origin, isMainFrame, reply) -> {
                            if (!isMainFrame || origin == null || !TRUSTED_ORIGIN.equalsIgnoreCase(origin.toString())) return;
                            if (message.getType() != WebMessageCompat.TYPE_STRING) return;
                            String payload = message.getData();
                            if (payload != null) main.post(() -> onEngineMessage(payload));
                        });
                captureSupported = true;
            }
            if (captureSupported && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                String script = captureScript();
                if (!script.isEmpty()) WebViewCompat.addDocumentStartJavaScript(web, script, origins);
                else captureSupported = false;
            } else captureSupported = false;
        } catch (Exception e) {
            captureSupported = false;
        }
    }

    // Fallback for older WebView: online listening still works, but AudioContext
    // instances may already have been created before this observer runs.
    private void installLateObserverIfNeeded() {
        String script = captureScript();
        if (!script.isEmpty()) eval(script, null);
        captureInfo.setText("Совместимый режим WebView: управление звуком доступно, автоматический переход может быть ограничен.");
    }

    private void loadBook() {
        final String source = bookUri;
        new Thread(() -> {
            try {
                BookLoader.Book book = BookLoader.load(this, Uri.parse(source));
                String text = book.text == null ? "" : book.text;
                ArrayList<Integer> offsets = new ArrayList<>();
                Matcher words = Pattern.compile("\\S+").matcher(text);
                while (words.find()) offsets.add(words.start());
                main.post(() -> {
                    if (closed || isFinishing()) return;
                    bookText = text;
                    audioCache = new MrtAudioCache(this, bookUri, book.title,
                            getSharedPreferences("reader", 0).getInt("mrt_cache_limit_mb", 100));
                    audioCache.keepBookCopy(this, bookUri);
                    wordOffsets.clear();
                    wordOffsets.addAll(offsets);
                    restoreArchive();
                    bookLoaded = !wordOffsets.isEmpty();
                    if (!bookLoaded) {
                        info.setText("В выбранной книге нет текста для озвучивания.");
                        return;
                    }
                    wordIndex = Math.max(0, Math.min(wordIndex, wordOffsets.size() - 1));
                    if(nativePlayer!=null && nativePlayer.currentStartWord()>=0)
                        wordIndex=Math.min(wordOffsets.size()-1,nativePlayer.currentStartWord());
                    history.clear();
                    history.add(wordIndex);
                    insertSegment(false);
                    syncReadyFiles();
                    updateUi();
                });
            } catch (Exception e) {
                main.post(() -> info.setText("Не удалось загрузить книгу: " + e.getMessage()));
            }
        }, "mrt-online-book-loader").start();
    }

    private void waitForEngine(int attempt) {
        if (closed || !pageLoaded || web == null) return;
        eval("(function(){return !!(window.app && document.getElementById('textarea') && document.getElementById('textSpeaker'));})()",
                value -> {
                    if (closed) return;
                    if ("true".equals(value)) {
                        engineReady = true;
                        eval("window.__rsvpMrt && window.__rsvpMrt.setNativeUiHidden(true)", null);
                        loadMrtOptions();
                        insertSegment(false);
                    } else if (attempt < 70) {
                        main.postDelayed(() -> waitForEngine(attempt + 1), 300);
                    } else {
                        info.setText("Страница MRT не запустила движок. Проверьте интернет или обновите Android System WebView.");
                    }
                });
    }

    private void eval(String script, android.webkit.ValueCallback<String> callback) {
        if (web == null || closed) return;
        try { web.evaluateJavascript(script, callback); }
        catch (Exception e) { if (callback != null) callback.onReceiveValue("false"); }
    }

    private void insertSegment(boolean startAutomatically) {
        if (!bookLoaded || !engineReady || closed || !pageLoaded || web == null) return;
        int begin = wordOffsets.get(wordIndex);
        int end = Math.min(bookText.length(), begin + segmentLimit);
        if (end < bookText.length()) {
            int sentence = -1;
            for (char c : new char[]{'.', '!', '?', '\n'}) sentence = Math.max(sentence, bookText.lastIndexOf(c, end - 1));
            if (sentence >= begin + segmentLimit / 2) end = sentence + 1;
            else {
                int space = bookText.lastIndexOf(' ', end - 1);
                if (space > begin + segmentLimit / 2) end = space + 1;
            }
            // Small test segments must not cut a word in half.
            if (end < bookText.length() && !Character.isWhitespace(bookText.charAt(end - 1))
                    && !Character.isWhitespace(bookText.charAt(end))) {
                int space = bookText.lastIndexOf(' ', end - 1);
                if (space > begin) end = space + 1;
            }
        }
        int after = Collections.binarySearch(wordOffsets, end);
        if (after < 0) after = -after - 1;
        nextWord = Math.min(wordOffsets.size(), Math.max(wordIndex + 1, after));
        String part = bookText.substring(begin, end).trim();
        if (part.isEmpty()) {
            if (nextWord < wordOffsets.size()) {
                wordIndex = nextWord;
                insertSegment(startAutomatically);
            }
            return;
        }
        if (fragmentText != null) {
            fragmentText.setText(part);
            if (nativeTextScroll != null) nativeTextScroll.post(() -> nativeTextScroll.scrollTo(0, 0));
        }
        segmentInserted = false;
        encodedChunks = 0;
        encodedBytes = 0;
        finishing = false;
        playing = false;
        paused = false;
        startToken++;
        final int token = startToken;
        String script = "(function(){var s=window.__rsvpMrt;return !!s && s.setText(" + JSONObject.quote(part) + ");})()";
        eval(script, result -> {
            if (closed || token != startToken) return;
            if (!"true".equals(result)) {
                if (++insertFailures > 8) {
                    captureInfo.setText("Не удалось передать текст в MRT. Перезапустите онлайн-озвучивание.");
                    return;
                }
                captureInfo.setText("Ожидаю готовность поля текста MRT (" + insertFailures + "/8)…");
                main.postDelayed(() -> {
                    if (!closed && token == startToken && !segmentInserted) insertSegment(startAutomatically);
                }, 1200);
                return;
            }
            insertFailures = 0;
            segmentInserted = true;
            updateUi();
            if (startAutomatically) {
                main.postDelayed(() -> {
                    if (!closed && token == startToken) startPlayback();
                }, 80);
            }
        });
        updateUi();
    }

    private void updateUi() {
        if (info == null || !bookLoaded || wordOffsets.isEmpty()) return;
        prefetchAhead=MrtPrefetchSettings.get(getSharedPreferences("reader",0));
        updateQueueDisplay();
        int percent=Math.min(100,(int)((long)(wordIndex+1)*100/wordOffsets.size()));
        info.setText("Прочитано: "+percent+"% · слова "+(wordIndex+1)+"–"+Math.max(wordIndex+1,nextWord)+" из "+wordOffsets.size());
        if (prefetchActive) {
            captureInfo.setText(generatingClip!=null
                    ? "Скачивается · чанков: "+encodedChunks+" · "+(encodedBytes/1024)+" КБ"
                    : "Автоподгрузка активна · готовые MP3 в очереди");
        } else captureInfo.setText("Автоподгрузка выключена");
        if (autoloadButton!=null)autoloadButton.setText(prefetchActive?"Загрузка: ВКЛ":"Автоподгрузка");
        int ready=0,currentIndex=0;
        for(PrefetchClip clip:audioArchive){
            boolean exists=clip.file!=null&&clip.file.isFile();
            if(exists)ready++;
            if(exists&&clip.start<=wordIndex)currentIndex=ready;
        }
        if(queueStatus!=null){
            if(ready==0){
                queueStatus.setText(android.text.Html.fromHtml(
                        "<font color='#30221E'>0/0</font> <font color='#B53930'>Запустите автоподгрузку</font>",
                        android.text.Html.FROM_HTML_MODE_LEGACY));
            } else {
                queueStatus.setTextColor(0xff305c35);
                queueStatus.setText(Math.max(1,currentIndex)+"/"+ready);
            }
        }
        previous.setEnabled(wordIndex>0);
        next.setEnabled(wordIndex<wordOffsets.size()-1);
        stopButton.setEnabled(nativePlayer!=null&&nativePlayer.hasFiles());
        setButtonIcon(play,nativePlayer!=null&&nativePlayer.isPlaying()?R.drawable.ic_pause:R.drawable.ic_play,Color.WHITE);
        updateTimebars();
    }

    private static String formatTime(long ms) {
        long sec=Math.max(0,ms/1000);
        return String.format(java.util.Locale.ROOT,"%02d:%02d",sec/60,sec%60);
    }
    private void updateTimebars() {
        if (closed || !bookLoaded) return;
        if(bookSeek!=null&&!draggingBook&&!wordOffsets.isEmpty())
            bookSeek.setProgress((int)((long)wordIndex*10000/wordOffsets.size()));
        if(bookTime!=null&&!draggingBook)
            bookTime.setText((int)((long)wordIndex*100/Math.max(1,wordOffsets.size()))+"% · "+(wordIndex+1)+"/"+wordOffsets.size()+" слов");
        long position=nativePlayer==null?0:nativePlayer.positionMs();
        long duration=nativePlayer==null?0:nativePlayer.durationMs();
        if(fileSeek!=null&&!draggingFile)
            fileSeek.setProgress(duration>0?(int)Math.min(1000,position*1000/duration):0);
        if(fileTime!=null&&!draggingFile)fileTime.setText(formatTime(position)+" / "+formatTime(duration));
    }

    private void changeSegmentLength(int count) {
        if (closed) return;
        count = MrtSegmentSettings.clamp(count);
        if (segmentLimit == count) return;
        segmentLimit = count;
        lengthButton.setText("Размер фрагмента: " + segmentLimit + " символов ▾");
        // Preserve the first word of the current segment; do not skip anything.
        if (prefetchActive) stopPrefetch();
        requestStop(() -> {
            if (closed || !bookLoaded) return;
            history.clear();
            history.add(wordIndex);
            segmentNumber = 1;
            insertFailures = 0;
            insertSegment(false);
            hint.setText("Новая длина применена.");
        });
    }

    /**
     * A JavaScript element.click() is not a user activation. MRT's original
     * WebAudio UI may ignore it, even when WebView permits media autoplay.
     * Dispatch touchscreen input to the ACTUAL HTML button instead.
     * This matches the user-supplied desktop prototype's trusted-click idea,
     * adapted to an Android WebView rather than Qt/Windows SendInput.
     */
    private void tapNativeMrtButton(int token, Runnable callback) {
        String script = "(function(){var b=document.getElementById('textSpeaker');"
                + "if(!b)return {ok:false,reason:'Не найдена кнопка MRT'};"
                + "b.scrollIntoView({block:'center',inline:'nearest'});"
                + "var r=b.getBoundingClientRect(),w=window.innerWidth,h=window.innerHeight;"
                + "if(!w||!h||r.width<1||r.height<1)return {ok:false,reason:'Кнопка MRT скрыта'};"
                + "return {ok:true,x:(r.left+r.width/2)/w,y:(r.top+r.height/2)/h};})()";
        eval(script, response -> {
            if (closed || token != startToken || web == null) return;
            try {
                JSONObject coordinates = new JSONObject(response);
                if (!coordinates.optBoolean("ok", false)) {
                    captureInfo.setText(coordinates.optString("reason", "Кнопка MRT недоступна"));
                    if (callback != null) callback.run();
                    return;
                }
                float x = (float)(coordinates.getDouble("x") * web.getWidth());
                float y = (float)(coordinates.getDouble("y") * web.getHeight());
                if (x < 0 || y < 0 || x >= web.getWidth() || y >= web.getHeight()) {
                    captureInfo.setText("Кнопка MRT за пределами видимой области WebView");
                    if (callback != null) callback.run();
                    return;
                }
                long downTime = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
                down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                web.dispatchTouchEvent(down);
                down.recycle();
                main.postDelayed(() -> {
                    if (closed || token != startToken || web == null) return;
                    MotionEvent up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
                    up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                    web.dispatchTouchEvent(up);
                    up.recycle();
                    hideKeyboard();
                    if (callback != null) callback.run();
                }, 85);
            } catch (Exception error) {
                captureInfo.setText("Ошибка нажатия MRT: " + error.getMessage());
                if (callback != null) callback.run();
            }
        });
    }

    /** Playback buttons never invoke WebView, JavaScript, nor the synthesis scheduler. */
    private void startPlayback() {
        if (nativePlayer == null) {
            Toast.makeText(this,"MP3-плеер подключается…",Toast.LENGTH_SHORT).show();
            return;
        }
        if (nativePlayer.isPlaying()) {nativePlayer.pause();updateUi();return;}
        syncReadyFiles();
        if (!nativePlayer.hasFiles()) {
            Toast.makeText(this,"Нет готовых MP3. Запустите автоподгрузку.",Toast.LENGTH_SHORT).show();
            return;
        }
        // Starting the foreground service keeps local MP3 playback alive after navigation.
        startService(new Intent(this,MrtPlaybackService.class));
        nativePlayer.setAutoContinue(getSharedPreferences("reader",0).getBoolean("mrt_auto_continue",true));
        nativePlayer.play();
        updateUi();
    }

    private void togglePause() {
        if (nativePlayer == null)return;
        if(nativePlayer.isPlaying())nativePlayer.pause();
        else startPlayback();
        updateUi();
    }

    /** Stop and wait for the MRT transport before switching to another text. */
    private void requestStop(Runnable after) {
        int token = ++startToken;
        boolean wasPlaying = playing || paused;
        playing = false;
        paused = false;
        finishing = false;
        updateUi();
        savePosition();
        if (!pageLoaded || !engineReady || web == null) {
            if (after != null) after.run();
            return;
        }
        eval("window.__rsvpMrt && window.__rsvpMrt.prepareStop()", result -> {
            if (closed || token != startToken) return;
            if (!"1".equals(result)) {
                if (after != null) after.run();
                return;
            }
            tapNativeMrtButton(token, () -> main.postDelayed(() -> {
                if (closed || token != startToken) return;
                eval("window.__rsvpMrt && window.__rsvpMrt.isPlaying()", state -> {
                    if (closed || token != startToken) return;
                    if ("true".equals(state)) {
                        // Last-resort stop for WebView implementations that reject
                        // synthetic MotionEvents. It silences current AudioContexts.
                        eval("window.__rsvpMrt && window.__rsvpMrt.forceSilence()", null);
                        if (wasPlaying) hint.setText("Звук остановлен. WebView не подтвердил нажатие «Стоп»; для нового запуска может понадобиться встроенная кнопка MRT.");
                    }
                    if (after != null) after.run();
                });
            }, 750));
        });
    }

    /** Stop local audio only; background MP3 synthesis keeps its queue intact. */
    private void stopListeningKeepCache() {
        if (nativePlayer != null) nativePlayer.stopAudio();
        captureInfo.setText("Воспроизведение остановлено. Очередь MP3 сохранена.");
        updateUi();
    }

    private void stopPlayback() {
        if (nativePlayer != null) nativePlayer.stopAudio();
        // A competing audio source also stops the local synthesizer if requested.
        updateUi();
    }

    /** Previous/next work on local MP3s, never on the WebUI's Play button. */
    private void nextSegment(boolean autoplay) {
        if(nativePlayer!=null && nativePlayer.trackIndex()+1<nativePlayer.trackCount()) {
            nativePlayer.next();
            updateUi();
            return;
        }
        if(!bookLoaded)return;
        int target=nextWord;
        if(target<=wordIndex){
            PrefetchClip clip=clipFromWord(wordIndex);
            target=clip==null?wordOffsets.size():clip.end;
        }
        if(target<wordOffsets.size())startPrefetchAt(target,autoplay);
    }

    private void previousSegment() {
        if(nativePlayer!=null && nativePlayer.trackIndex()>0) {
            nativePlayer.previous();
            updateUi();
            return;
        }
        if(!bookLoaded||wordIndex<=0)return;
        int prev=0;
        for(PrefetchClip clip:audioArchive) {
            if(clip.start>=wordIndex)break;
            prev=clip.start;
        }
        startPrefetchAt(prev,false);
    }

    private void onEngineMessage(String raw) {
        if (closed) return;
        try {
            JSONObject message = new JSONObject(raw);
            String type = message.optString("type", "");
            if (prefetchActive) {
                // Do not accept stale 'finished' or 'error' notifications from a prior chunk.
                int eventSession = message.optInt("session", -1);
                if (generatingClip == null || eventSession != captureSession) return;
                if ("audioPart".equals(type)) {
                    audioCache.append(captureSession, message.optString("data", ""));
                    return;
                }
                if ("chunk".equals(type)) {
                    encodedChunks = message.optInt("chunks", encodedChunks);
                    encodedBytes = message.optLong("bytes", encodedBytes);
                    long now = SystemClock.uptimeMillis();
                    if (now - lastUiUpdate > 600) { lastUiUpdate = now; updateUi(); }
                    return;
                }
                if ("finished".equals(type)) { finishSynthesis(); return; }
                if ("error".equals(type)) {
                    if (finalizingCapture) return;
                    retrySynthesis("WebUI: " + message.optString("detail", "неизвестная ошибка"));
                    return;
                }
                return;
            }
            switch (type) {
                case "ready":
                    // Engine readiness is checked separately after JS app loads.
                    break;
                case "started":
                    playing = true;
                    paused = false;
                    finishing = false;
                    encodedChunks = 0;
                    encodedBytes = 0;
                    updateUi();
                    break;
                case "chunk":
                    encodedChunks = message.optInt("chunks", encodedChunks);
                    encodedBytes = message.optLong("bytes", encodedBytes);
                    long now = android.os.SystemClock.uptimeMillis();
                    if (now - lastUiUpdate > 400) {
                        lastUiUpdate = now;
                        updateUi();
                    }
                    break;
                case "paused":
                    paused = true;
                    updateUi();
                    break;
                case "resumed":
                    paused = false;
                    updateUi();
                    break;
                case "finished":
                    if (finishing || !playing || paused) break;
                    finishing = true;
                    playing = false;
                    paused = false;
                    updateUi();
                    if (nextWord >= wordOffsets.size()) {
                        wordIndex = wordOffsets.size() - 1;
                        savePosition();
                        captureInfo.setText("Озвучивание завершено. Последний фрагмент книги прослушан.");
                    } else if (continuous.isChecked()) {
                        main.postDelayed(() -> {
                            if (!closed && finishing) nextSegment(true);
                        }, 40);
                    } else {
                        captureInfo.setText("Фрагмент завершён. Нажмите «Фрагмент ›» для продолжения.");
                    }
                    break;
                case "error":
                    captureInfo.setText("Ошибка веб-движка: " + message.optString("detail", "неизвестная"));
                    break;
            }
        } catch (Exception ignored) {}
    }

    /**
     * Queue and WebView synthesis run on the Activity thread. A separate
     * MrtPlaybackEngine owns both Android MediaPlayers and NEVER reacts to
     * WebView errors; errors only reschedule synthesis.
     */
    /** This is a DOWNLOAD-only scheduler. It never calls Play or Pause. */
    private void beginPrefetch() { beginPrefetch(false); }
    private void beginPrefetch(boolean ignoredStartAudible) {
        if (closed || !bookLoaded || !engineReady || !captureSupported || prefetchActive) return;
        prefetchActive=true;
        prefetchedClips=0;
        prefetchError="";
        audioQueue.clear();
        generatingClip=null;
        finalizingCapture=false;
        drivePrefetch();
        updateUi();
    }

    private PrefetchClip clipFromWord(int start) {
        if (start >= wordOffsets.size()) return null;
        int begin = wordOffsets.get(start);
        int end = Math.min(bookText.length(), begin + segmentLimit);
        if (end < bookText.length()) {
            int sentence = -1;
            for (char c : new char[]{'.','!','?','\n'}) sentence = Math.max(sentence, bookText.lastIndexOf(c, end-1));
            if (sentence >= begin + segmentLimit/2) end = sentence+1;
            else { int space = bookText.lastIndexOf(' ',end-1); if (space>begin+segmentLimit/2) end=space+1; }
            if (end<bookText.length() && !Character.isWhitespace(bookText.charAt(end-1))
                    && !Character.isWhitespace(bookText.charAt(end))) {
                int space = bookText.lastIndexOf(' ',end-1);
                if (space > begin) end=space+1;
            }
        }
        int after = Collections.binarySearch(wordOffsets,end);
        if (after<0) after=-after-1;
        after=Math.min(wordOffsets.size(),Math.max(start+1,after));
        String text = bookText.substring(begin,end).trim();
        SharedPreferences prefs=getSharedPreferences("reader",0);
        String key=MrtAudioCache.key(text,prefs.getString("mrt_selected_voice",""),
                prefs.getString("mrt_selected_speed",""),prefs.getString("mrt_selected_emotion",""));
        for (PrefetchClip archived : audioArchive) {
            if (archived.start == start && archived.end == after && key.equals(archived.cacheKey)) {
                archived.file = audioCache.cached(key);
                return archived;
            }
        }
        PrefetchClip clip=new PrefetchClip(start,after,text,key);
        clip.file=audioCache.cached(key);
        if (clip.file != null) clip.fileBytes = clip.file.length();
        audioArchive.add(clip);
        Collections.sort(audioArchive,(a,b)->Integer.compare(a.start,b.start));
        persistArchive();
        updateQueueDisplay();
        return clip;
    }

    /** Keep current + N-1 future MP3 files available according to PLAYBACK position. */
    private void drivePrefetch() {
        if (!prefetchActive || closed || !bookLoaded || clearingCache) return;
        // A jump in playback position leaves obsolete slots behind; trim them.
        while (!audioQueue.isEmpty() && audioQueue.get(0).end <= wordIndex) audioQueue.remove(0);
        while (audioQueue.size() < prefetchAhead) {
            int start = audioQueue.isEmpty() ? wordIndex : audioQueue.get(audioQueue.size()-1).end;
            if (start >= wordOffsets.size()) break;
            PrefetchClip clip = clipFromWord(start);
            if (clip == null || clip.text.isEmpty()) break;
            audioQueue.add(clip);
        }
        syncReadyFiles();
        if (generatingClip == null) {
            for (PrefetchClip clip : audioQueue) {
                if (clip.file != null) continue;
                long wait = clip.retryAfter - SystemClock.uptimeMillis();
                if (wait > 0) break;
                startSynthesis(clip);
                break;
            }
        }
        updateUi();
    }

    /** Append ready files without changing the native player transport state. */
    private void syncReadyFiles() {
        if (nativePlayer == null || !bookLoaded) return;
        nativePlayer.setAutoContinue(getSharedPreferences("reader",0).getBoolean("mrt_auto_continue",true));
        if (!audioQueue.isEmpty()) {
            for (PrefetchClip clip : audioQueue) {
                if (clip.file == null || !clip.file.isFile()) break;
                nativePlayer.appendReady(clip.cacheKey+":"+clip.start,clip.start,clip.end,clip.file);
            }
            return;
        }
        // Restored cache is playable even when the downloader is turned off.
        int cursor=wordIndex;
        for(PrefetchClip clip:audioArchive) {
            if(clip.end<=cursor)continue;
            if(clip.start!=cursor || clip.file==null || !clip.file.isFile())break;
            nativePlayer.appendReady(clip.cacheKey+":"+clip.start,clip.start,clip.end,clip.file);
            cursor=clip.end;
        }
    }

    /** Begin synthesis, but do not treat temporary WebUI busy states as errors. */
    private void startSynthesis(PrefetchClip clip) {
        if (!prefetchActive || closed || clip.file != null || generatingClip != null) return;
        generatingClip = clip;
        finalizingCapture = false;
        captureSession++;
        int session = captureSession;
        audioCache.begin(session, clip.cacheKey);
        encodedBytes = 0;
        encodedChunks = 0;
        String js = "(function(){var m=window.__rsvpMrt;return !!m&&m.setText("
                + JSONObject.quote(clip.text) + ")&&m.captureBegin(" + session + ");})()";
        eval(js, value -> {
            if (!isGenerating(clip, session)) return;
            if (!"true".equals(value)) {
                retrySynthesis("Не удалось передать текст в MRT"); return;
            }
            waitMrtReady(clip, session, SystemClock.uptimeMillis() + READY_WAIT_MS);
        });
        // Network/WebUI timeout applies ONLY to the generator, never the player.
        main.postDelayed(() -> {
            if (isGenerating(clip, session) && !finalizingCapture) retrySynthesis("Тайм-аут генерации аудиофрагмента");
        }, 120000);
    }

    private boolean isGenerating(PrefetchClip clip, int session) {
        return !closed && prefetchActive && generatingClip == clip && captureSession == session;
    }

    private void waitMrtReady(PrefetchClip clip, int session, long deadline) {
        if (!isGenerating(clip, session)) return;
        eval("window.__rsvpMrt && window.__rsvpMrt.prepareStart()", state -> {
            if (!isGenerating(clip, session)) return;
            if ("1".equals(state)) {
                final int token = ++startToken;
                tapNativeMrtButton(token, () -> main.postDelayed(() -> {
                    if (!isGenerating(clip, session) || encodedChunks > 0) return;
                    eval("window.__rsvpMrt && window.__rsvpMrt.isPlaying()", playingState -> {
                        if (!isGenerating(clip, session) || encodedChunks > 0) return;
                        if (!"true".equals(playingState)) retrySynthesis("MRT не начал синтез");
                    });
                }, 5000));
            } else if (SystemClock.uptimeMillis() < deadline) {
                // States 0 (disabled) and 2 (still finishing) are transient.
                main.postDelayed(() -> waitMrtReady(clip, session, deadline), READY_POLL_MS);
            } else {
                retrySynthesis("MRT не готов к следующему синтезу (" + state + ")");
            }
        });
    }

    private void finishSynthesis() {
        PrefetchClip clip = generatingClip;
        if (!prefetchActive || clip == null || finalizingCapture) return;
        int session = captureSession;
        finalizingCapture = true;
        eval("window.__rsvpMrt && window.__rsvpMrt.captureEnd()", null);
        audioCache.finish(session, (file, bytes, chunks, error) -> {
            if (!isGenerating(clip, session)) return;
            generatingClip = null;
            finalizingCapture = false;
            if (file == null) {
                scheduleRetry(clip, "MP3-кэш: " + (error == null ? "неизвестная ошибка" : error));
                return;
            }
            clip.file = file;
            clip.fileBytes = bytes;
            clip.chunkCount = chunks;
            clip.failures = 0;
            persistArchive();
            prefetchedClips++;
            prefetchError = "";
            drivePrefetch();
        });
    }

    /** Reattempt only the failed synthesis. Do not touch any player or the checkbox. */
    private void retrySynthesis(String reason) {
        PrefetchClip clip = generatingClip;
        if (clip == null || !prefetchActive || closed) return;
        generatingClip = null;
        finalizingCapture = false;
        captureSession++; // invalidate delayed JS callbacks from the old synthesis
        audioCache.abort();
        eval("window.__rsvpMrt && window.__rsvpMrt.captureEnd()", null);
        scheduleRetry(clip, reason);
    }

    private void scheduleRetry(PrefetchClip clip, String reason) {
        if (!prefetchActive || closed || clip.file != null) return;
        clip.failures++;
        int delay = clip.failures <= 3 ? RETRY_PAUSE_MS * clip.failures : 12000;
        clip.retryAfter = SystemClock.uptimeMillis() + delay;
        prefetchError = "Фрагмент " + (segmentNumber + Math.max(0,audioQueue.indexOf(clip)))
                + ": " + reason + " (повтор через " + (delay/1000) + " с)";
        Log.w(MRT_LOG, prefetchError);
        hint.setText("Предзагрузка: " + prefetchError + ". Текущий MP3 продолжает играть.");
        main.postDelayed(this::drivePrefetch, delay + 50);
        // Do not clear queued cached MP3s, stop playback or uncheck the option.
    }

    /** Cancel generator only; not a single method here controls native playback. */
    private void stopPrefetch() {
        prefetchActive=false;
        captureSession++;
        if(audioCache!=null)audioCache.abort();
        generatingClip=null;
        finalizingCapture=false;
        audioQueue.clear();
        eval("window.__rsvpMrt && window.__rsvpMrt.captureDisable(false)",null);
    }

    private void updateQueueDisplay() {
        if (queueAdapter != null) queueAdapter.notifyDataSetChanged();
        if (queueTitle != null) {
            int ready=0, played=0;
            for (PrefetchClip clip:audioArchive) {
                if (clip.file != null && clip.file.isFile()) ready++;
                if (clip.played) played++;
            }
            queueTitle.setText("Очередь MP3 · " + ready + " готово из " + audioArchive.size()
                    + " · " + played + " прослушано");
        }
    }

    private void restoreArchive() {
        audioArchive.clear();
        if (audioCache == null) return;
        try {
            JSONArray entries = new JSONArray(audioCache.readIndex());
            for (int i=0;i<entries.length() && i<10000;i++) {
                JSONObject item=entries.optJSONObject(i);
                if(item==null)continue;
                int start=item.optInt("start",-1), end=item.optInt("end",-1);
                if(start<0 || end<=start || end>wordOffsets.size())continue;
                String text=item.optString("text","");
                String key=item.optString("key","");
                if(text.isEmpty() || key.isEmpty())continue;
                PrefetchClip clip=new PrefetchClip(start,end,text,key);
                clip.file=audioCache.cached(key);
                clip.fileBytes=clip.file==null?0:item.optLong("bytes",clip.file.length());
                clip.chunkCount=item.optInt("chunks",0);
                clip.played=item.optBoolean("played",false);
                audioArchive.add(clip);
            }
        } catch (Exception error) { Log.w(MRT_LOG,"Cannot restore queue",error); }
        Collections.sort(audioArchive,(a,b)->Integer.compare(a.start,b.start));
        updateQueueDisplay();
    }

    private void persistArchive() {
        if (audioCache == null || clearingCache) return;
        JSONArray entries=new JSONArray();
        try {
            for (PrefetchClip clip:audioArchive) {
                JSONObject item=new JSONObject();
                item.put("start",clip.start).put("end",clip.end).put("text",clip.text)
                        .put("key",clip.cacheKey).put("chunks",clip.chunkCount)
                        .put("bytes",clip.fileBytes).put("played",clip.played);
                entries.put(item);
            }
            audioCache.saveIndex(entries.toString());
        } catch (Exception e) { Log.w(MRT_LOG,"Cannot save queue",e); }
    }

    private void openAudioFolder() {
        if (audioCache == null) { Toast.makeText(this,"Подождите загрузки книги",Toast.LENGTH_SHORT).show(); return; }
        startActivity(new Intent(this, MrtFileExplorerActivity.class)
                .putExtra("book_folder",audioCache.directory().getAbsolutePath()));
    }

    private void confirmClearAudio() {
        if (audioCache == null || clearingCache) return;
        new AlertDialog.Builder(this).setTitle("Очистить аудиофайлы этой книги?")
                .setMessage("Будут удалены все собранные MP3, временные файлы и история очереди. Книга и её позиция чтения останутся.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Очистить",(dialog,which) -> {
                    stopPrefetch();
                    eval("window.__rsvpMrt && window.__rsvpMrt.forceSilence()", null);
                    clearingCache=true;
                    audioCache.clear(() -> {
                        if (closed) return;
                        audioArchive.clear();
                        audioQueue.clear();
                        clearingCache=false;
                        prefetchedClips=0;
                        updateQueueDisplay();
                        captureInfo.setText("Аудиокэш этой книги очищен. Запустите автоподгрузку заново.");
                    });
                }).show();
    }

    private void savePosition() {
        if (wordOffsets.isEmpty() || bookUri.isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences("reader", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit().putInt("tts_word:" + bookUri, wordIndex);
        try {
            JSONArray books = new JSONArray(prefs.getString("library", "[]"));
            for (int i = 0; i < books.length(); i++) {
                JSONObject book = books.optJSONObject(i);
                if (book == null || !bookUri.equals(book.optString("uri"))) continue;
                book.put("index", wordIndex);
                book.put("progress", (int)((long)(wordIndex + 1) * 100 / wordOffsets.size()));
                book.put("openedAt", System.currentTimeMillis());
                editor.putString("library", books.toString());
                break;
            }
        } catch (Exception ignored) {}
        editor.apply();
    }

    /** Pause all audible audio on calls, loss of focus or another audio application.
     * Never stop/erase the already cached MP3 and never auto-resume on focus gain. */
    private void pauseForExternalAudio() {
        if(nativePlayer!=null)nativePlayer.pause();
        if(playing||paused){
            eval("window.__rsvpMrt && window.__rsvpMrt.forceSilence()",null);
            playing=false;paused=false;
        }
        updateUi();
    }

    // Media buttons and audio focus are exclusively managed by MrtPlaybackService.
    private void updateHeadsetState() {}

    @Override protected void onResume() {
        super.onResume();
        if(nativePlayer!=null)nativePlayer.setAutoContinue(getSharedPreferences("reader",0).getBoolean("mrt_auto_continue",true));
        segmentLimit=MrtSegmentSettings.get(getSharedPreferences("reader",0));
        prefetchAhead=MrtPrefetchSettings.get(getSharedPreferences("reader",0));
        if(prefetchActive)drivePrefetch();
        updateUi();
    }

    @Override protected void onPause() {
        savePosition();
        super.onPause();
    }

    @Override public void onBackPressed() {
        savePosition();
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        closed = true;
        try { unregisterReceiver(exclusiveAudioReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(noisyReceiver); } catch (Exception ignored) {}
        stopPrefetch();
        if (audioCache != null) audioCache.close();
        if(nativePlayer!=null)nativePlayer.setListener(null);
        if(playerBound){try{unbindService(playerConnection);}catch(Exception ignored){}playerBound=false;}
        ++startToken;
        main.removeCallbacksAndMessages(null);
        if (web != null) {
            try {
                web.evaluateJavascript("window.__rsvpMrt && window.__rsvpMrt.stopPlayback()", null);
                web.stopLoading();
                web.loadUrl("about:blank");
                web.destroy();
            } catch (Exception ignored) {}
            web = null;
        }
        super.onDestroy();
    }
}
