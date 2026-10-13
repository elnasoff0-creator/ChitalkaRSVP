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
 * caches MP3 candidates, and plays them in sequence through Android MediaPlayer.
 * The old direct WebAudio path remains available as a compatibility fallback.
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
    private TextView info, captureInfo, hint, fragmentText;
    private LinearLayout nativeReader;
    private ScrollView nativeTextScroll;
    private int optionsLoadAttempts;
    private Button voiceChoice, speedChoice, emotionChoice, fallbackButton;
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
    private MrtPlaybackEngine playback;
    private PlaybackAudioFocus audioFocus;
    private MediaSession mediaSession;
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) pauseForExternalAudio();
        }
    };
    private final BroadcastReceiver exclusiveAudioReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AudioModeCoordinator.STOP_MRT.equals(intent.getAction())) {
                stopPlayback();
                if (mediaSession != null) mediaSession.setActive(false);
                if (audioFocus != null) audioFocus.release();
            }
        }
    };
    private boolean prefetchActive, prefetchPaused, awaitingManualNext, suppressPrefetchToggle;
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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        bookUri = getIntent().getStringExtra("book_uri");
        if (bookUri == null || bookUri.isEmpty()) bookUri = getSharedPreferences("reader", 0).getString("uri", "");
        wordIndex = Math.max(0, getIntent().getIntExtra("word_index", 0));
        segmentLimit = MrtSegmentSettings.get(getSharedPreferences("reader", 0));
        playback = new MrtPlaybackEngine(new MrtPlaybackEngine.Listener() {
            @Override public void onStarted(Object clip) { displayNativeClip((PrefetchClip) clip); }
            @Override public void onCompleted(Object clip) { completeNativeClip((PrefetchClip) clip); }
            @Override public void onError(Object clip, String error) { playbackFailure((PrefetchClip) clip, error); }
        });
        audioFocus = new PlaybackAudioFocus(this, this::pauseForExternalAudio);
        setupMediaSession();
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
        root.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(50)));
        Button back = button("‹");
        back.setContentDescription("Назад к чтению");
        back.setOnClickListener(v -> finish());
        titleRow.addView(back, new LinearLayout.LayoutParams(dp(45), dp(45)));
        TextView heading = new TextView(this);
        heading.setText("Слушать онлайн · MRT");
        heading.setTextColor(INK);
        heading.setTextSize(20);
        heading.setTypeface(Typeface.SERIF, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(0, -1, 1);
        headLp.leftMargin = dp(8);
        titleRow.addView(heading, headLp);

        info = new TextView(this);
        info.setText("Открываю электронную книгу…");
        info.setTextColor(INK);
        info.setTextSize(13);
        info.setPadding(dp(5), dp(6), dp(5), dp(3));
        root.addView(info);

        captureInfo = new TextView(this);
        captureInfo.setText("Инициализация веб-озвучивания…");
        captureInfo.setTextColor(MUTED);
        captureInfo.setTextSize(12);
        captureInfo.setPadding(dp(5), 0, dp(5), dp(4));
        root.addView(captureInfo);

        LinearLayout transport = new LinearLayout(this);
        transport.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams transportLp = new LinearLayout.LayoutParams(-1, dp(50));
        transportLp.topMargin = dp(5);
        root.addView(transport, transportLp);

        play = button("▶ Слушать");
        play.setBackground(rounded(0xffa26949, 14));
        play.setTextColor(Color.WHITE);
        play.setOnClickListener(v -> startPlayback());
        transport.addView(play, new LinearLayout.LayoutParams(0, dp(48), 1.25f));
        pause = button("Ⅱ Пауза");
        pause.setEnabled(false);
        pause.setOnClickListener(v -> togglePause());
        LinearLayout.LayoutParams pauseLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        pauseLp.leftMargin = dp(6);
        transport.addView(pause, pauseLp);
        Button stop = button("■ Стоп");
        stop.setOnClickListener(v -> stopListeningKeepCache());
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        stopLp.leftMargin = dp(6);
        transport.addView(stop, stopLp);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(-1, dp(46));
        navLp.topMargin = dp(7);
        root.addView(navigation, navLp);
        previous = button("‹ Фрагмент");
        previous.setEnabled(false);
        previous.setOnClickListener(v -> previousSegment());
        navigation.addView(previous, new LinearLayout.LayoutParams(0, -1, 1));
        next = button("Фрагмент ›");
        next.setEnabled(false);
        next.setOnClickListener(v -> nextSegment(false));
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(0, -1, 1);
        nextLp.leftMargin = dp(7);
        navigation.addView(next, nextLp);

        lengthButton = button("Размер фрагмента: " + segmentLimit + " символов ▾");
        lengthButton.setTextSize(12);
        lengthButton.setContentDescription("Настроить количество символов в текстовом фрагменте MRT");
        lengthButton.setOnClickListener(v -> MrtSegmentSettings.show(this, getSharedPreferences("reader", 0), this::changeSegmentLength));
        LinearLayout.LayoutParams lengthLp = new LinearLayout.LayoutParams(-1, dp(40));
        lengthLp.topMargin = dp(7);
        root.addView(lengthButton, lengthLp);

        continuous = new CheckBox(this);
        continuous.setText("Автоматически озвучивать следующий фрагмент");
        continuous.setTextSize(12);
        continuous.setTextColor(INK);
        continuous.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff94583b));
        continuous.setChecked(getSharedPreferences("reader", 0).getBoolean("mrt_auto_continue", true));
        continuous.setOnCheckedChangeListener((v, isChecked) -> getSharedPreferences("reader", 0).edit().putBoolean("mrt_auto_continue", isChecked).apply());
        root.addView(continuous);

        prefetchToggle = new CheckBox(this);
        prefetchToggle.setText("Предзагрузка аудио в кэш · beta");
        prefetchToggle.setTextColor(INK);
        prefetchToggle.setTextSize(12);
        prefetchToggle.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff94583b));
        prefetchToggle.setChecked(getSharedPreferences("reader", 0).getBoolean("mrt_prefetch", true));
        prefetchToggle.setOnCheckedChangeListener((view, enabled) -> {
            getSharedPreferences("reader", 0).edit().putBoolean("mrt_prefetch", enabled).apply();
            if (suppressPrefetchToggle) return;
            if (!enabled) {
                if (prefetchActive) {
                    stopPrefetch();
                    requestStop(() -> {
                        if (!closed) {
                            eval("window.__rsvpMrt && window.__rsvpMrt.captureDisable(true)", null);
                            insertSegment(false);
                        }
                    });
                } else eval("window.__rsvpMrt && window.__rsvpMrt.captureDisable(true)", null);
            } else if (prefetchActive) {
                // Enabling/changing the setting must NEVER stop native playback.
                drivePrefetch();
            } else if (playing || paused) {
                // A manually running WebUI cannot generate in parallel with native audio.
                requestStop(this::beginPrefetch);
            }
        });
        root.addView(prefetchToggle);
        prefetchAhead = MrtPrefetchSettings.get(getSharedPreferences("reader", 0));
        aheadButton = button(MrtPrefetchSettings.label(prefetchAhead) + " ▾");
        aheadButton.setTextSize(12);
        aheadButton.setOnClickListener(v -> MrtPrefetchSettings.show(this,
                getSharedPreferences("reader", 0), count -> {
                    prefetchAhead = count;
                    aheadButton.setText(MrtPrefetchSettings.label(prefetchAhead) + " ▾");
                    // Increasing the window fills the extra slots without stopping audio.
                    if (prefetchActive) drivePrefetch();
                }));
        root.addView(aheadButton, new LinearLayout.LayoutParams(-1, dp(36)));

        LinearLayout storageActions = new LinearLayout(this);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, dp(39));
        actionsLp.topMargin = dp(4);
        root.addView(storageActions, actionsLp);
        Button openFolder = button("📁 Папка аудио");
        openFolder.setOnClickListener(v -> openAudioFolder());
        storageActions.addView(openFolder, new LinearLayout.LayoutParams(0, -1, 1));
        Button clearFiles = button("Очистить MP3 и очередь");
        clearFiles.setOnClickListener(v -> confirmClearAudio());
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(0, -1, 1.25f);
        clearLp.leftMargin = dp(6);
        storageActions.addView(clearFiles, clearLp);

        hint = new TextView(this);
        hint.setText("Текст отображается в читалке. Движок MRT работает в фоне экрана. Для проверки автоперехода выберите 100–300 символов; для обычного прослушивания удобнее 800–2000.");
        hint.setTextColor(MUTED);
        hint.setTextSize(12);
        hint.setPadding(dp(5), 0, dp(5), dp(5));
        root.addView(hint);

        LinearLayout selectors = new LinearLayout(this);
        selectors.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams selectorLp = new LinearLayout.LayoutParams(-1, dp(48));
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
            // A native transport sends touches to the actual MRT button.
            // The source page's editable textarea must never summon Android's keyboard.
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
                // A provider can advertise document-start support even if its
                // injection fails on this page. Recover with a late observer.
                eval("!!window.__rsvpMrt", present -> {
                    if (!"true".equals(present)) installLateObserverIfNeeded();
                    waitForEngine(0);
                });
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
                if (req.isForMainFrame()) {
                    info.setText("Страница MRT недоступна. Проверьте подключение и повторите попытку.");
                }
            }
        });
        FrameLayout engineRegion = new FrameLayout(this);
        LinearLayout.LayoutParams engineLp = new LinearLayout.LayoutParams(-1, dp(390));
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
        queueTitle.setText("Очередь MP3 · нажмите, чтобы свернуть");
        queueTitle.setTextColor(MUTED);
        queueTitle.setTextSize(12);
        LinearLayout.LayoutParams queueTitleLp = new LinearLayout.LayoutParams(-1, -2);
        queueTitleLp.topMargin = dp(5);
        nativeReader.addView(queueTitle, queueTitleLp);
        queueList = new ListView(this);
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
                item.setPadding(dp(8), dp(3), dp(8), dp(3));
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
                boolean current = prefetchActive && !audioQueue.isEmpty() && audioQueue.get(0)==clip;
                String status = clip.played ? "Прослушан" :
                        loading ? "Скачивается" : ready ? (current ? "Воспроизводится / завершён" : "Завершён") : "Ожидает";
                int n = clip.chunkCount;
                long bytes = clip.fileBytes;
                if (loading) { n = encodedChunks; bytes = encodedBytes; }
                else if (ready && bytes == 0) bytes = clip.file.length();
                heading.setText("№" + (index+1) + " · " + clip.text.length() + " символов · " + status);
                details.setText(clip.cacheKey.substring(0,Math.min(10,clip.cacheKey.length())) + ".mp3 · "
                        + "чанков: " + n + " · " + String.format(java.util.Locale.ROOT, "%.1f",bytes/1024.0) + " КБ");
                int color = clip.played ? 0xff999999 : (loading ? 0xff9b603e : INK);
                heading.setTextColor(color);
                details.setTextColor(clip.played ? 0xffaaaaaa : MUTED);
                return item;
            }
        };
        queueList.setAdapter(queueAdapter);
        nativeReader.addView(queueList, new LinearLayout.LayoutParams(-1, dp(137)));
        queueTitle.setOnClickListener(v -> {
            boolean expanded = queueList.getVisibility() == View.VISIBLE;
            queueList.setVisibility(expanded ? View.GONE : View.VISIBLE);
            queueTitle.setContentDescription(expanded ? "Развернуть очередь MP3" : "Свернуть очередь MP3");
        });
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
                    history.clear();
                    history.add(wordIndex);
                    insertSegment(false);
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
        if (info == null || !bookLoaded) return;
        updateQueueDisplay();
        int percent = Math.min(100, (int)((long)(wordIndex + 1) * 100 / wordOffsets.size()));
        info.setText("Прочитано: " + percent + "% · слова " + (wordIndex + 1) + "–" + nextWord + " из " + wordOffsets.size());
        if (prefetchActive) {
            String status = playback.hasCurrent() ? (prefetchPaused ? " · пауза" : " · воспроизведение") : " · ожидание аудио";
            captureInfo.setText("Аудиокэш · готово: " + prefetchedClips + " · чанков: " + encodedChunks
                    + " · " + (encodedBytes / 1024) + " КБ" + status);
        } else {
            captureInfo.setText("Фрагмент " + segmentNumber + " · чанков: " + encodedChunks + " · данных: "
                    + (encodedBytes / 1024) + " КБ" + (playing ? " · воспроизведение" : (paused ? " · пауза" : "")));
        }
        previous.setEnabled(history.size() > 1);
        next.setEnabled(nextWord < wordOffsets.size());
        pause.setEnabled(prefetchActive ? playback.hasCurrent() : (playing || paused));
        pause.setText((prefetchActive ? prefetchPaused : paused) ? "▶ Продолжить" : "Ⅱ Пауза");
        play.setText((prefetchActive ? prefetchPaused : paused) ? "▶ Продолжить" : "▶ Слушать");
        updateHeadsetState();
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
            hint.setText("Новая длина применена. Нажмите «Слушать», затем проверьте автоматический переход.");
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

    private void startPlayback() {
        if (!audioFocus.acquire()) {
            Toast.makeText(this, "Звук занят другим приложением или звонком", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mediaSession != null) mediaSession.setActive(true);
        if (prefetchActive) {
            awaitingManualNext = false;
            if (prefetchPaused) {
                prefetchPaused = false;
                updateHeadsetState();
                playback.resume();
                drivePrefetch();
                updateUi();
            } else drivePrefetch();
            return;
        }
        if (prefetchToggle != null && prefetchToggle.isChecked() && captureSupported && bookLoaded && engineReady) {
            beginPrefetch();
            return;
        }
        if (closed || !engineReady || !segmentInserted) {
            Toast.makeText(this, "Подождите загрузки текста и движка MRT", Toast.LENGTH_SHORT).show();
            return;
        }
        if (paused) {
            togglePause();
            return;
        }
        final int token = ++startToken;
        eval("window.__rsvpMrt && window.__rsvpMrt.captureDisable(true)", null);
        eval("window.__rsvpMrt && window.__rsvpMrt.prepareStart()", result -> {
            if (closed || token != startToken) return;
            if ("2".equals(result)) {
                playing = true;
                paused = false;
                updateUi();
                return;
            }
            if (!"1".equals(result)) {
                captureInfo.setText("MRT пока не готов: убедитесь, что текст загружен и голос выбран.");
                return;
            }
            tapNativeMrtButton(token, () -> main.postDelayed(() -> {
                if (closed || token != startToken) return;
                eval("window.__rsvpMrt && window.__rsvpMrt.isPlaying()", state -> {
                    if (closed || token != startToken) return;
                    if ("true".equals(state)) {
                        playing = true;
                        paused = false;
                        updateUi();
                    } else {
                        hint.setText("MRT не подтвердил запуск. Нажмите «Показать веб-панель MRT» для диагностики и ручного запуска.");
                        captureInfo.setText("Запуск не подтверждён движком MRT.");
                        fallbackButton.setVisibility(View.VISIBLE);
                    }
                });
            }, 4500));
        });
    }

    private void togglePause() {
        if (prefetchActive) {
            if (!playback.hasCurrent()) return;
            if (prefetchPaused && !audioFocus.acquire()) return;
            prefetchPaused = !prefetchPaused;
            if (prefetchPaused) { playback.pause(); audioFocus.release(); }
            else playback.resume();
            updateHeadsetState();
            updateUi();
            return;
        }
        if (!engineReady) return;
        if (paused && !audioFocus.acquire()) return;
        eval("window.__rsvpMrt && window.__rsvpMrt.togglePause()", result -> {
            if (!"true".equals(result)) Toast.makeText(this, "Пауза доступна на странице MRT", Toast.LENGTH_SHORT).show();
            else if (!paused) audioFocus.release();
            updateHeadsetState();
        });
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

    /** Stop audible playback but keep filling N slots and keep persistent files. */
    private void stopListeningKeepCache() {
        if (!prefetchActive) { stopPlayback(); return; }
        if (playback != null) playback.stop();
        prefetchPaused=true;
        awaitingManualNext=false;
        if (audioFocus!=null) audioFocus.release();
        captureInfo.setText("Звук остановлен. Предзагрузка продолжается; следующий Play начнёт текущий фрагмент заново.");
        updateHeadsetState();
        drivePrefetch();
    }

    private void stopPlayback() {
        if (audioFocus != null) audioFocus.release();
        updateHeadsetState();
        if (prefetchActive) stopPrefetch();
        requestStop(() -> {
            if (!closed) {
                updateUi();
                captureInfo.setText("Остановлено · фрагмент " + segmentNumber);
            }
        });
    }

    private void nextSegment(boolean autoplay) {
        if (prefetchActive) {
            int target = audioQueue.isEmpty() ? nextWord : audioQueue.get(0).end;
            stopPrefetch();
            requestStop(() -> {
                if (closed || target >= wordOffsets.size()) return;
                wordIndex = target; segmentNumber++; history.add(wordIndex);
                savePosition(); insertSegment(false);
                if (autoplay && prefetchToggle.isChecked()) main.postDelayed(this::beginPrefetch, 150);
            });
            return;
        }
        if (!bookLoaded || nextWord >= wordOffsets.size()) {
            if (bookLoaded) {
                wordIndex = wordOffsets.size() - 1;
                savePosition();
                captureInfo.setText("Озвучивание книги завершено.");
            }
            return;
        }
        requestStop(() -> {
            if (closed) return;
            wordIndex = nextWord;
            insertFailures = 0;
            playbackStartRetries = 0;
            segmentNumber++;
            history.add(wordIndex);
            savePosition();
            insertSegment(autoplay);
        });
    }

    private void previousSegment() {
        if (prefetchActive) {
            stopPrefetch();
            requestStop(() -> {
                if (closed || history.size() <= 1) return;
                history.remove(history.size()-1);
                wordIndex = history.get(history.size()-1);
                segmentNumber = Math.max(1, segmentNumber-1);
                savePosition(); insertSegment(false);
            });
            return;
        }
        if (history.size() <= 1) return;
        requestStop(() -> {
            if (closed) return;
            history.remove(history.size() - 1);
            wordIndex = history.get(history.size() - 1);
            insertFailures = 0;
            playbackStartRetries = 0;
            segmentNumber = Math.max(1, segmentNumber - 1);
            savePosition();
            insertSegment(false);
        });
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
    private void beginPrefetch() {
        if (closed || !bookLoaded || !engineReady || !captureSupported || prefetchActive) return;
        prefetchActive = true;
        prefetchPaused = !audioFocus.acquire();
        if (mediaSession != null) mediaSession.setActive(true);
        awaitingManualNext = false;
        prefetchedClips = 0;
        prefetchError = "";
        audioQueue.clear();
        generatingClip = null;
        finalizingCapture = false;
        playback.stop();
        if (prefetchPaused) playback.pause();
        updateHeadsetState();
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

    /** Maintain N completed/future segments ahead of the audible one. */
    private void drivePrefetch() {
        if (!prefetchActive || closed || !bookLoaded || clearingCache) return;
        while (audioQueue.size() < prefetchAhead) {
            int start = audioQueue.isEmpty() ? wordIndex : audioQueue.get(audioQueue.size()-1).end;
            if (start >= wordOffsets.size()) break;
            PrefetchClip clip = clipFromWord(start);
            if (clip == null || clip.text.isEmpty()) break;
            audioQueue.add(clip);
        }
        if (!prefetchPaused && !playback.hasCurrent() && !awaitingManualNext && !audioQueue.isEmpty()) {
            PrefetchClip first = audioQueue.get(0);
            if (first.file != null) playback.play(first.file, first);
        }
        if (playback.hasCurrent() && audioQueue.size()>1) {
            PrefetchClip next = audioQueue.get(1);
            if (next.file != null) playback.warm(next.file, next);
        }
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

    private void playbackFailure(PrefetchClip clip, String error) {
        if (!prefetchActive || closed) return;
        Log.w(MRT_LOG, "Playback: " + error);
        hint.setText("Ошибка воспроизведения MP3: " + error + ". Остальные файлы кэша сохранены.");
        if (clip != null && !audioQueue.isEmpty() && audioQueue.get(0) == clip) {
            // A genuinely broken cached file cannot be played: remove it and
            // regenerate, without interrupting unrelated WebView work.
            if (clip.file != null && clip.file.exists()) clip.file.delete();
            clip.file = null;
            clip.fileBytes = 0;
            clip.chunkCount = 0;
            persistArchive();
            clip.retryAfter = SystemClock.uptimeMillis() + 1000;
            main.postDelayed(this::drivePrefetch, 1100);
        }
    }

    private void displayNativeClip(PrefetchClip clip) {
        if (!prefetchActive || clip == null) return;
        updateHeadsetState();
        wordIndex = clip.start;
        nextWord = clip.end;
        if (fragmentText != null) fragmentText.setText(clip.text);
        updateQueueDisplay();
        if (nativeTextScroll != null) nativeTextScroll.post(() -> nativeTextScroll.scrollTo(0,0));
        savePosition();
        updateUi();
        drivePrefetch();
    }

    private void completeNativeClip(PrefetchClip clip) {
        if (!prefetchActive || clip == null) return;
        clip.played = true;
        persistArchive();
        updateQueueDisplay();
        wordIndex = clip.end;
        savePosition();
        if (!audioQueue.isEmpty() && audioQueue.get(0)==clip) audioQueue.remove(0);
        segmentNumber++;
        history.add(Math.min(wordIndex, Math.max(0, wordOffsets.size()-1)));
        if (wordIndex>=wordOffsets.size()) {
            stopPrefetch();
            captureInfo.setText("Книга прослушана до конца.");
            return;
        }
        if (!continuous.isChecked()) {
            awaitingManualNext = true;
            captureInfo.setText("Фрагмент завершён. Нажмите «Слушать» для продолжения.");
        }
        // If next file is ready, start it now. Otherwise wait only for cache;
        // synthesis never owns/cancels native playback.
        drivePrefetch();
    }

    private void stopPrefetch() {
        prefetchActive = false;
        captureSession++;
        if (audioCache != null) audioCache.abort();
        if (playback != null) playback.stop();
        generatingClip = null;
        finalizingCapture = false;
        audioQueue.clear();
        prefetchPaused = false;
        awaitingManualNext = false;
        if (audioFocus != null) audioFocus.release();
        updateHeadsetState();
        eval("window.__rsvpMrt && window.__rsvpMrt.captureDisable(false)", null);
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
                        captureInfo.setText("Аудиокэш этой книги очищен. Нажмите «Слушать» для новой загрузки.");
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
        if (closed) return;
        if (audioFocus != null) audioFocus.release();
        if (prefetchActive) {
            if (playback != null) playback.pause();
            prefetchPaused = true;
            updateUi();
        } else if (playing && !paused) {
            // Immediately silence WebAudio; the next manual Play performs a new start.
            eval("window.__rsvpMrt && window.__rsvpMrt.forceSilence()", null);
            playing = false;
            paused = false;
            updateUi();
        }
        updateHeadsetState();
    }

    private void setupMediaSession() {
        mediaSession = new MediaSession(this, "ChitalkaRSVP-MRT");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() {
                if (!prefetchActive || prefetchPaused || !playback.hasCurrent()) startPlayback();
            }
            @Override public void onPause() {
                if (prefetchActive && !prefetchPaused && playback.hasCurrent()) togglePause();
                else if (!prefetchActive && playing && !paused) togglePause();
            }
            @Override public void onStop() { stopPlayback(); }
            @Override public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent event=intent==null?null:intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if(event!=null && event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) {
                    switch (event.getKeyCode()) {
                        case KeyEvent.KEYCODE_HEADSETHOOK:
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                            if (prefetchActive ? (!prefetchPaused && playback.hasCurrent()) : (playing && !paused)) onPause();
                            else onPlay();
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_PLAY: onPlay();return true;
                        case KeyEvent.KEYCODE_MEDIA_PAUSE: onPause();return true;
                        case KeyEvent.KEYCODE_MEDIA_STOP: onStop();return true;
                    }
                }
                return super.onMediaButtonEvent(intent);
            }
        }, main);
        mediaSession.setActive(true);
        updateHeadsetState();
    }

    private void updateHeadsetState() {
        if (mediaSession == null) return;
        boolean audible = prefetchActive ? playback != null && playback.hasCurrent() && !prefetchPaused
                : playing && !paused;
        int state = audible ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_STOP;
        mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(actions)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, audible?1f:0f).build());
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
        if (audioFocus != null) audioFocus.release();
        if (mediaSession != null) {mediaSession.setActive(false);mediaSession.release();mediaSession=null;}
        stopPrefetch();
        if (audioCache != null) audioCache.close();
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
