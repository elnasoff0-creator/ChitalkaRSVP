package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Online reading through the original public MRT WebSpeechKit page.
 *
 * The user-supplied MRT Desktop TTS inspired the early AudioContext hook.
 * In this version the hook only reports encoded buffer counts and lifecycle
 * events. Audio is PLAYED by MRT's own WebAudio: no MP3 export, ffmpeg, or
 * private synthesis endpoint is used. The electronic book has its own progress
 * and does not overwrite locally imported MP3 audiobook progress.
 */
public class MrtOnlineActivity extends Activity {
    private static final String PAGE = "https://alkohole.github.io/machine-reading-text/index.html";
    private static final String TRUSTED_ORIGIN = "https://alkohole.github.io";
    private static final int SEGMENT_LIMIT = 8000;
    private static final int BG = 0xfff3e7d5;
    private static final int INK = 0xff30221e;
    private static final int MUTED = 0xff716259;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<Integer> wordOffsets = new ArrayList<>();
    private final ArrayList<Integer> history = new ArrayList<>();
    private WebView web;
    private TextView info, captureInfo, hint;
    private Button play, pause, previous, next;
    private CheckBox continuous;
    private String bookUri = "", bookText = "";
    private int wordIndex, nextWord, segmentNumber = 1;
    private boolean bookLoaded, pageLoaded, engineReady, segmentInserted, captureSupported;
    private boolean playing, paused, finishing, closed;
    private int encodedChunks;
    private long encodedBytes;
    private int startToken = 0;
    private int insertFailures = 0;
    private int playbackStartRetries = 0;
    private long lastUiUpdate;

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
        // Prevent two simultaneous audio sources without changing audiobook position.
        AudioPlaybackService service = AudioPlaybackService.instance;
        if (service != null && !service.isPaused()) {
            startService(new android.content.Intent(this, AudioPlaybackService.class).setAction(AudioPlaybackService.PAUSE));
        }
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
        setContentView(root);

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
        play.setOnClickListener(v -> {playbackStartRetries = 0; startPlayback();});
        transport.addView(play, new LinearLayout.LayoutParams(0, dp(48), 1.25f));
        pause = button("Ⅱ Пауза");
        pause.setEnabled(false);
        pause.setOnClickListener(v -> togglePause());
        LinearLayout.LayoutParams pauseLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        pauseLp.leftMargin = dp(6);
        transport.addView(pause, pauseLp);
        Button stop = button("■ Стоп");
        stop.setOnClickListener(v -> stopPlayback());
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

        continuous = new CheckBox(this);
        continuous.setText("Автоматически озвучивать следующий фрагмент");
        continuous.setTextSize(12);
        continuous.setTextColor(INK);
        continuous.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff94583b));
        continuous.setChecked(getSharedPreferences("reader", 0).getBoolean("mrt_auto_continue", true));
        continuous.setOnCheckedChangeListener((v, isChecked) -> getSharedPreferences("reader", 0).edit().putBoolean("mrt_auto_continue", isChecked).apply());
        root.addView(continuous);

        hint = new TextView(this);
        hint.setText("Голос, эмоция и скорость — в панели MRT ниже. При первом запуске может потребоваться нажать ▶ на самой веб-странице.");
        hint.setTextColor(MUTED);
        hint.setTextSize(12);
        hint.setPadding(dp(5), 0, dp(5), dp(5));
        root.addView(hint);

        web = new WebView(this);
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
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        installEarlyObserver();
        web.loadUrl(PAGE);
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
                    wordOffsets.clear();
                    wordOffsets.addAll(offsets);
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
        int end = Math.min(bookText.length(), begin + SEGMENT_LIMIT);
        if (end < bookText.length()) {
            int sentence = -1;
            for (char c : new char[]{'.', '!', '?', '\n'}) sentence = Math.max(sentence, bookText.lastIndexOf(c, end - 1));
            if (sentence >= begin + SEGMENT_LIMIT / 2) end = sentence + 1;
            else {
                int space = bookText.lastIndexOf(' ', end - 1);
                if (space > begin + SEGMENT_LIMIT / 2) end = space + 1;
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
                }, 700);
            }
        });
        updateUi();
    }

    private void updateUi() {
        if (info == null || !bookLoaded) return;
        int percent = Math.min(100, (int)((long)(wordIndex + 1) * 100 / wordOffsets.size()));
        info.setText("Прочитано: " + percent + "% · слова " + (wordIndex + 1) + "–" + nextWord + " из " + wordOffsets.size());
        captureInfo.setText("Фрагмент " + segmentNumber + " · чанков: " + encodedChunks + " · данных: "
                + (encodedBytes / 1024) + " КБ" + (playing ? " · воспроизведение" : (paused ? " · пауза" : "")));
        previous.setEnabled(history.size() > 1);
        next.setEnabled(nextWord < wordOffsets.size());
        pause.setEnabled(playing || paused);
        pause.setText(paused ? "▶ Продолжить" : "Ⅱ Пауза");
    }

    private void startPlayback() {
        if (closed || !engineReady || !segmentInserted) {
            Toast.makeText(this, "Подождите загрузки текста и движка MRT", Toast.LENGTH_SHORT).show();
            return;
        }
        int token = ++startToken;
        // Press the original button programmatically. WebView's media gesture
        // setting allows WebAudio on many devices. Some MRT builds additionally
        // require a physical user press on the visible web page.
        eval("window.__rsvpMrt && window.__rsvpMrt.startPlayback()", result -> {
            if (closed || token != startToken) return;
            if (!"true".equals(result)) {
                if (playbackStartRetries++ < 5) {
                    captureInfo.setText("Ожидаю готовность кнопки MRT…");
                    main.postDelayed(() -> {
                        if (!closed && token == startToken && !playing) startPlayback();
                    }, 850);
                    return;
                }
                captureInfo.setText("Нажмите ▶ на странице MRT ниже для начала озвучивания.");
            }
            main.postDelayed(() -> {
                if (closed || token != startToken || playing) return;
                eval("window.__rsvpMrt && window.__rsvpMrt.isPlaying()", state -> {
                    if (!closed && token == startToken && !"true".equals(state)) {
                        hint.setText("Автозапуск ограничен WebView: нажмите кнопку ▶ в самой странице MRT. Голос и скорость также выбираются там.");
                    }
                });
            }, 5500);
        });
    }

    private void togglePause() {
        if (!engineReady) return;
        eval("window.__rsvpMrt && window.__rsvpMrt.togglePause()", result -> {
            if (!"true".equals(result)) Toast.makeText(this, "Пауза доступна на странице MRT", Toast.LENGTH_SHORT).show();
        });
    }

    private void stopPlayback() {
        ++startToken;
        eval("window.__rsvpMrt && window.__rsvpMrt.stopPlayback()", null);
        playing = false;
        paused = false;
        finishing = false;
        updateUi();
        savePosition();
    }

    private void nextSegment(boolean autoplay) {
        if (!bookLoaded || nextWord >= wordOffsets.size()) {
            if (bookLoaded) {
                wordIndex = wordOffsets.size() - 1;
                savePosition();
                captureInfo.setText("Озвучивание книги завершено.");
            }
            return;
        }
        // Prevent MRT's stop button from being interpreted as a natural end.
        eval("window.__rsvpMrt && window.__rsvpMrt.stopPlayback()", null);
        wordIndex = nextWord;
        insertFailures = 0;
        playbackStartRetries = 0;
        segmentNumber++;
        history.add(wordIndex);
        savePosition();
        insertSegment(autoplay);
    }

    private void previousSegment() {
        if (history.size() <= 1) return;
        eval("window.__rsvpMrt && window.__rsvpMrt.stopPlayback()", null);
        history.remove(history.size() - 1);
        wordIndex = history.get(history.size() - 1);
        insertFailures = 0;
        playbackStartRetries = 0;
        segmentNumber = Math.max(1, segmentNumber - 1);
        savePosition();
        insertSegment(false);
    }

    private void onEngineMessage(String raw) {
        if (closed) return;
        try {
            JSONObject message = new JSONObject(raw);
            String type = message.optString("type", "");
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
                        }, 300);
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
