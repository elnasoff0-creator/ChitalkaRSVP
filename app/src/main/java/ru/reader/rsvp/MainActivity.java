package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    static class BookMeta {
        String uri = "";
        String title = "Книга";
        String author = "";
        String excerpt = "";
        String chapter = "";
        String format = "";
        int progress = 0;
        int index = 0;
        int scroll = 0;
        long openedAt = 0L;

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("uri", uri);
                o.put("title", title);
                o.put("author", author);
                o.put("excerpt", excerpt);
                o.put("chapter", chapter);
                o.put("format", format);
                o.put("progress", progress);
                o.put("index", index);
                o.put("scroll", scroll);
                o.put("openedAt", openedAt);
            } catch (Exception ignored) {}
            return o;
        }

        static BookMeta fromJson(JSONObject o) {
            BookMeta m = new BookMeta();
            if (o == null) return m;
            m.uri = o.optString("uri", "");
            m.title = o.optString("title", "Книга");
            m.author = o.optString("author", "");
            m.excerpt = o.optString("excerpt", "");
            m.chapter = o.optString("chapter", "");
            m.format = o.optString("format", "");
            m.progress = o.optInt("progress", 0);
            m.index = o.optInt("index", 0);
            m.scroll = o.optInt("scroll", 0);
            m.openedAt = o.optLong("openedAt", 0L);
            return m;
        }
    }

    SharedPreferences prefs;
    Handler handler = new Handler(Looper.getMainLooper());

    LinearLayout root;
    TextView wordView, progressView, speedView, modeButton, playButton;
    ScrollView normalScroll;
    TextView normalText;
    View rsvpPane;
    long lastScrollSave = 0;

    ArrayList<BookMeta> library = new ArrayList<>();
    String currentUri = "";
    String title = "";
    String author = "";
    String body = "";
    String[] words = new String[0];
    int[] wordStarts = new int[0];
    int pageStart = 0, pageEnd = 0;
    static final int PAGE_CHARS = 3000;

    int index = 0, wpm = 350, display = 1, size = 36, bg = 0xfff3e7d5, fg = 0xff30221e, font = 1;
    boolean rsvp = true, playing = false;
    int normalY = 0;
    int screen = 0; // 0 home, 1 reader

    final String[] fontNames = {"Без засечек", "С засечками", "Моноширинный"};

    Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!playing) return;
            if (rsvp) {
                if (index < words.length - 1) index++; else { stop(); return; }
                updateWord();
            } else if (normalScroll != null) {
                normalScroll.smoothScrollBy(0, Math.max(2, size / 4));
                normalY = normalScroll.getScrollY();
                syncIndexFromScroll();
                if (normalScroll.getScrollY() >= Math.max(0, normalText.getHeight() - normalScroll.getHeight()) - dp(3) && pageEnd < body.length()) {
                    int found = java.util.Arrays.binarySearch(wordStarts, pageEnd);
                    index = Math.min(words.length - 1, Math.max(0, found < 0 ? -found - 2 : found));
                    updateNormalPage(true);
                }
            }
            if (index % 8 == 0) saveAll();
            handler.postDelayed(this, rsvp ? Math.max(60, 60000L / Math.max(1, wpm)) : 80);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        getWindow().setStatusBarColor(0xffefe3d3);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        prefs = getSharedPreferences("reader", MODE_PRIVATE);
        loadPrefs();
        if (!currentUri.isEmpty()) {
            int requestedScreen = screen;
            screen = 0;
            draw();
            openBook(currentUri, false, requestedScreen == 1);
        } else {
            screen = 0;
            draw();
        }
        if (System.currentTimeMillis() - prefs.getLong("update_last_check", 0L) > 24L * 60 * 60 * 1000) {
            prefs.edit().putLong("update_last_check", System.currentTimeMillis()).apply();
            new UpdateManager(this).check(true);
        }
    }

    void loadPrefs() {
        wpm = prefs.getInt("wpm", 350);
        display = prefs.getInt("display", 1);
        size = prefs.getInt("size", 36);
        bg = prefs.getInt("bg", 0xfff3e7d5);
        fg = prefs.getInt("fg", 0xff30221e);
        font = prefs.getInt("font", 1);
        rsvp = prefs.getBoolean("rsvp", true);
        screen = 0;
        currentUri = prefs.getString("uri", "");
        String data = prefs.getString("library", "[]");
        library.clear();
        try {
            JSONArray arr = new JSONArray(data);
            for (int i = 0; i < arr.length(); i++) library.add(BookMeta.fromJson(arr.optJSONObject(i)));
        } catch (Exception ignored) {}
    }

    void saveAll() {
        if (screen == 1 && words.length > 0) updateCurrentMeta();
        JSONArray arr = new JSONArray();
        for (BookMeta m : library) arr.put(m.toJson());
        prefs.edit()
                .putInt("wpm", wpm)
                .putInt("display", display)
                .putInt("size", size)
                .putInt("bg", bg)
                .putInt("fg", fg)
                .putInt("font", font)
                .putBoolean("rsvp", rsvp)
                .putString("uri", currentUri)
                .putString("library", arr.toString())
                .putInt("screen", screen)
                .apply();
    }

    void updateCurrentMeta() {
        if (currentUri.isEmpty() || words.length == 0 || body.isEmpty()) return;
        BookMeta m = meta(currentUri);
        if (m == null) {
            m = new BookMeta();
            m.uri = currentUri;
            library.add(0, m);
        }
        m.title = title.isEmpty() ? m.title : title;
        m.author = author == null ? "" : author;
        m.progress = words.length == 0 ? 0 : Math.max(0, Math.min(100, (index + 1) * 100 / Math.max(1, words.length)));
        m.index = index;
        m.scroll = normalY;
        if (m.chapter.isEmpty()) m.chapter = findChapterForIndex(index);
        if (m.excerpt.isEmpty()) m.excerpt = excerptAroundIndex(index);
        if (m.format.isEmpty()) m.format = extensionFromUri(currentUri);
        m.openedAt = System.currentTimeMillis();
        bringFirst(m);
    }

    void bringFirst(BookMeta m) {
        library.remove(m);
        library.add(0, m);
    }

    BookMeta meta(String uri) {
        for (BookMeta m : library) if (uri.equals(m.uri)) return m;
        return null;
    }

    int dp(int n) { return (int) (getResources().getDisplayMetrics().density * n + .5f); }
    Typeface type() { return font == 1 ? Typeface.SERIF : font == 2 ? Typeface.MONOSPACE : Typeface.SANS_SERIF; }
    int accent() { return 0xff9A5E40; }
    int panelColor() { return bg == 0xff1e2025 ? 0xff2a2d33 : 0xfffff9f1; }
    int softPanel() { return bg == 0xff1e2025 ? 0xff333842 : 0xfff5ede2; }
    int faintText() { return bg == 0xff1e2025 ? 0xffcbc6bb : 0xff6d635a; }

    void stop() { playing = false; handler.removeCallbacks(ticker); updatePlayButton(); }

    LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    void add(LinearLayout p, View c) { p.addView(c, new LinearLayout.LayoutParams(-1, -2)); }
    void add(LinearLayout p, View c, LinearLayout.LayoutParams lp) { p.addView(c, lp); }
    void pad(View v, int l, int t, int r, int b) { v.setPadding(dp(l), dp(t), dp(r), dp(b)); }
    void pad(View v, int all) { pad(v, all, all, all, all); }
    GradientDrawable shape(int fill, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); return d; }

    TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(fg);
        t.setTypeface(type(), bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    TextView buttonish(String s, int fill, int color, boolean bold) {
        TextView t = text(s, 16, bold);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(color);
        t.setBackground(shape(fill, 16));
        t.setClickable(true);
        t.setFocusable(true);
        pad(t, 14, 10, 14, 10);
        return t;
    }

    void draw() {
        stop();
        root = column();
        root.setBackgroundColor(bg);
        setContentView(root);
        if (screen == 1 && words.length > 0 && !body.isEmpty()) drawReader(); else drawHome();
    }

    void drawHome() {
        root.setPadding(dp(12), dp(23), dp(12), dp(8));
        LinearLayout contentWrap = column();
        ScrollView scroll = new ScrollView(this);
        scroll.addView(contentWrap, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout top = row();
        TextView titleView = text("Читалка RSVP", 23, true);
        titleView.setTypeface(Typeface.SERIF, Typeface.BOLD);
        top.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));
        View search = iconButton(android.R.drawable.ic_menu_search, v -> showLibraryChooser());
        top.addView(search, new LinearLayout.LayoutParams(dp(52), dp(46)));
        View importBtn = iconButton(android.R.drawable.ic_menu_add, v -> pick());
        top.addView(importBtn, new LinearLayout.LayoutParams(dp(52), dp(46)));
        View menuBtn = iconButton(android.R.drawable.ic_menu_preferences, v -> settings());
        top.addView(menuBtn, new LinearLayout.LayoutParams(dp(52), dp(46)));
        add(contentWrap, top);

        TextView sub = text("Быстрое чтение. Больше смысла.", 13, false);
        sub.setTextColor(faintText());
        add(contentWrap, sub);

        add(contentWrap, gap(10));
        add(contentWrap, importCard());
        add(contentWrap, gap(12));

        LinearLayout head1 = sectionHeader("Продолжить чтение", currentUri.isEmpty() ? "" : "");
        add(contentWrap, head1);
        if (!currentUri.isEmpty() && meta(currentUri) != null) add(contentWrap, continueCard(meta(currentUri)));
        else {
            TextView empty = text("У тебя пока нет открытой книги. Нажми «Импортировать книги», чтобы добавить EPUB, FB2 или HTML.", 15, false);
            empty.setTextColor(faintText());
            empty.setBackground(shape(panelColor(), 22));
            pad(empty, 16);
            add(contentWrap, empty);
        }

        add(contentWrap, gap(12));
        add(contentWrap, sectionHeader("Библиотека", library.isEmpty() ? "" : ""));
        if (library.isEmpty()) {
            TextView empty = text("Здесь появятся добавленные книги с мини-обложками и сохранённым прогрессом.", 15, false);
            empty.setTextColor(faintText());
            empty.setBackground(shape(panelColor(), 22));
            pad(empty, 16);
            add(contentWrap, empty);
        } else {
            LinearLayout libPanel = column();
            libPanel.setBackground(shape(panelColor(), 22));
            for (int i = 0; i < library.size(); i++) {
                if (i > 0) libPanel.addView(divider());
                libPanel.addView(libraryRow(library.get(i)));
            }
            add(contentWrap, libPanel);
        }

        add(contentWrap, gap(12));
        add(contentWrap, themePanel());
        add(contentWrap, gap(16));

        root.addView(bottomNav(0), new LinearLayout.LayoutParams(-1, -2));
    }

    View gap(int n) { TextView t = new TextView(this); t.setHeight(dp(n)); return t; }
    View divider() { View v = new View(this); v.setBackgroundColor(bg == 0xff1e2025 ? 0xff3a3d43 : 0xffede4d8); v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1))); return v; }

    LinearLayout sectionHeader(String title, String action) {
        LinearLayout r = row();
        TextView t = text(title, 18, true);
        t.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (!action.isEmpty()) {
            TextView act = buttonish(action, softPanel(), fg, false);
            r.addView(act);
        }
        return r;
    }

    View importCard() {
        LinearLayout card = row();
        card.setBackground(shape(panelColor(), 24));
        pad(card, 14);

        TextView icon = buttonish("📄＋", softPanel(), fg, true);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(76), dp(76));
        iconLp.setMargins(0, 0, dp(14), 0);
        card.addView(icon, iconLp);

        LinearLayout info = column();
        TextView h = text("Импортировать книги", 18, true);
        info.addView(h);
        TextView p = text("Поддерживаются форматы EPUB, FB2 и HTML", 15, false);
        p.setTextColor(faintText());
        pad(p, 0, 4, 0, 6);
        info.addView(p);
        LinearLayout badges = row();
        badges.addView(badge("EPUB", 0xffdfead1, 0xff345a20));
        badges.addView(space(8));
        badges.addView(badge("FB2", 0xffdbe7f7, 0xff355381));
        badges.addView(space(8));
        badges.addView(badge("HTML", 0xfff6e3c8, 0xff8a5421));
        info.addView(badges);
        card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        card.setOnClickListener(v -> pick());
        return card;
    }

    View space(int n) { TextView t = new TextView(this); t.setWidth(dp(n)); return t; }

    TextView badge(String s, int fill, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setBackground(shape(fill, 12));
        pad(t, 12, 6, 12, 6);
        return t;
    }

    View continueCard(BookMeta m) {
        LinearLayout card = row();
        card.setBackground(shape(panelColor(), 24));
        pad(card, 14);
        card.addView(coverView(m.title, m.uri), new LinearLayout.LayoutParams(dp(108), dp(156)));

        LinearLayout info = column();
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, -2, 1);
        infoLp.setMargins(dp(14), 0, 0, 0);
        TextView titleV = text(m.title, 20, true); titleV.setTypeface(Typeface.SERIF, Typeface.BOLD); info.addView(titleV);
        if (!m.author.isEmpty()) {
            TextView a = text(m.author, 15, false); a.setTextColor(faintText()); info.addView(a);
        }
        TextView ex = text(shorten(m.excerpt, 110), 15, false); ex.setTextColor(faintText()); pad(ex, 0, 6, 0, 6); info.addView(ex);
        info.addView(progressBar(m.progress));
        LinearLayout pr = row();
        TextView chapter = text(m.chapter.isEmpty() ? "Последняя позиция" : m.chapter, 15, false);
        pr.addView(chapter, new LinearLayout.LayoutParams(0, -2, 1));
        TextView percent = text(m.progress + "%", 15, true); pr.addView(percent);
        info.addView(pr);
        TextView btn = buttonish("Продолжить", accent(), 0xffffffff, true);
        btn.setOnClickListener(v -> openBook(m.uri, false, true));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(dp(160), -2);
        btnLp.setMargins(0, dp(10), 0, 0);
        info.addView(btn, btnLp);
        card.addView(info, infoLp);
        return card;
    }

    View libraryRow(BookMeta m) {
        LinearLayout row = row();
        pad(row, 12, 12, 12, 12);
        row.addView(coverView(m.title, m.uri), new LinearLayout.LayoutParams(dp(72), dp(100)));
        LinearLayout info = column();
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, -2, 1);
        infoLp.setMargins(dp(12), 0, dp(8), 0);
        TextView titleV = text(m.title, 17, true); titleV.setEllipsize(TextUtils.TruncateAt.END); titleV.setMaxLines(2); info.addView(titleV);
        if (!m.author.isEmpty()) {
            TextView a = text(m.author, 14, false); a.setTextColor(faintText()); info.addView(a);
        }
        TextView ch = text(m.chapter.isEmpty() ? (m.format.isEmpty() ? "Книга" : m.format.toUpperCase(Locale.ROOT)) : m.chapter, 14, false);
        ch.setTextColor(faintText()); pad(ch, 0, 4, 0, 4); info.addView(ch);
        info.addView(progressBar(m.progress));
        TextView p = text("Прочитано: " + m.progress + "%", 13, false); p.setTextColor(faintText()); info.addView(p);
        row.addView(info, infoLp);
        LinearLayout actions = column();
        TextView open = buttonish("Открыть", accent(), 0xffffffff, true);
        open.setOnClickListener(v -> openBook(m.uri, false, true));
        TextView more = buttonish("⋮", softPanel(), fg, true);
        more.setOnClickListener(v -> bookMenu(m));
        actions.addView(open, new LinearLayout.LayoutParams(dp(92), -2));
        LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(dp(92), -2);
        moreLp.topMargin = dp(8);
        actions.addView(more, moreLp);
        row.addView(actions);
        return row;
    }

    View coverView(String title, String uri) {
        android.widget.FrameLayout frame=new android.widget.FrameLayout(this);
        android.widget.ImageView image=new android.widget.ImageView(this);
        image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        image.setVisibility(View.GONE);
        frame.addView(image,new android.widget.FrameLayout.LayoutParams(-1,-1));
        LinearLayout card = column();
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff2c241f, 0xff544033, 0xff1d1a19});
        d.setCornerRadius(dp(18));
        card.setBackground(d);
        pad(card, 10);
        TextView moon = new TextView(this);
        moon.setText("☾");
        moon.setTextSize(18);
        moon.setTextColor(0xffd9b87a);
        moon.setGravity(Gravity.END);
        card.addView(moon, new LinearLayout.LayoutParams(-1, -2));
        TextView cat = new TextView(this);
        cat.setText("🐈");
        cat.setTextSize(18);
        cat.setTextColor(0xffd9b87a);
        card.addView(cat);
        TextView t = new TextView(this);
        t.setText(coverTitle(title));
        t.setTextSize(16);
        t.setTypeface(Typeface.SERIF, Typeface.BOLD);
        t.setTextColor(0xfff7e5c7);
        t.setGravity(Gravity.BOTTOM | Gravity.START);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1);
        card.addView(t, lp);
        frame.addView(card,new android.widget.FrameLayout.LayoutParams(-1,-1));
        if(uri!=null && !uri.isEmpty()){
            new Thread(()->{
                byte[] bytes=CoverLoader.get(getApplicationContext(),uri);
                if(bytes==null)return;
                android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();
                opts.inJustDecodeBounds=true;
                android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
                opts.inSampleSize=Math.max(1,Math.max(opts.outWidth/400,opts.outHeight/600));
                opts.inJustDecodeBounds=false;
                android.graphics.Bitmap bmp=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
                if(bmp!=null)runOnUiThread(()->{image.setImageBitmap(bmp);image.setVisibility(View.VISIBLE);frame.bringChildToFront(image);});
            }).start();
        }
        return frame;
    }

    String coverTitle(String t) {
        if (t == null || t.trim().isEmpty()) return "Книга";
        String[] parts = t.trim().split("\\s+");
        if (parts.length == 1) return parts[0];
        return parts[0] + "\n" + parts[1];
    }

    View progressBar(int percent) {
        LinearLayout box = row();
        box.setBackground(shape(bg == 0xff1e2025 ? 0xff3c4149 : 0xffeadfce, 99));
        LinearLayout fill = new LinearLayout(this);
        fill.setBackground(shape(accent(), 99));
        int width = Math.max(dp(18), dp(180) * Math.max(0, Math.min(100, percent)) / 100);
        box.addView(fill, new LinearLayout.LayoutParams(width, dp(8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(8));
        lp.topMargin = dp(3); lp.bottomMargin = dp(3);
        box.setLayoutParams(lp);
        return box;
    }

    View themePanel() {
        LinearLayout panel = column();
        panel.setBackground(shape(panelColor(), 24));
        pad(panel, 14);
        TextView h = text("Тема чтения", 18, true); panel.addView(h);
        TextView p = text("Выбери комфортный стиль для чтения", 14, false); p.setTextColor(faintText()); panel.addView(p);
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = row();
        row.addView(themeChip("Светлая", 0xffffffff, 0xff202020, () -> { bg = 0xffffffff; fg = 0xff202020; saveAll(); draw(); }));
        row.addView(space(8));
        row.addView(themeChip("Сепия", 0xfff3e7d5, 0xff30221e, () -> { bg = 0xfff3e7d5; fg = 0xff30221e; saveAll(); draw(); }));
        row.addView(space(8));
        row.addView(themeChip("Ночная", 0xff1e2025, 0xffe8e5db, () -> { bg = 0xff1e2025; fg = 0xffe8e5db; saveAll(); draw(); }));
        row.addView(space(8));
        row.addView(themeChip("Мягкая", 0xffdfe9d9, 0xff223a29, () -> { bg = 0xffdfe9d9; fg = 0xff223a29; saveAll(); draw(); }));
        hsv.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(10);
        panel.addView(hsv, lp);
        return panel;
    }

    TextView themeChip(String name, int fill, int textColor, Runnable action) {
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(textColor);
        t.setBackground(shape(fill, 18));
        pad(t, 18, 14, 18, 14);
        t.setMinWidth(dp(118));
        t.setOnClickListener(v -> action.run());
        return t;
    }

    View bottomNav(int active) {
        LinearLayout nav = row();
        nav.setBackground(shape(panelColor(), 22));
        pad(nav, 6);
        nav.addView(navItem("📚", "Библиотека", active == 0, v -> { screen = 0; saveAll(); draw(); }), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(navItem("▶", "Чтение", active == 1, v -> { if (currentUri.isEmpty()) pick(); else { openBook(currentUri, false, true); } }), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(navItem("⭐", "Закладки", false, v -> bookmarks()), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(navItem("⚙️", "Настройки", false, v -> settings()), new LinearLayout.LayoutParams(0, -2, 1));
        return nav;
    }

    View navItem(String icon, String label, boolean active, View.OnClickListener click) {
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);
        item.setBackground(active ? shape(softPanel(), 16) : null);
        pad(item, 6, 10, 6, 10);
        if (label.equals("Настройки") || label.equals("Библиотека") || label.equals("Чтение")) {
            int image = label.equals("Настройки") ? android.R.drawable.ic_menu_preferences :
                    label.equals("Библиотека") ? android.R.drawable.ic_menu_agenda : android.R.drawable.ic_media_play;
            ImageView iv = new ImageView(this);
            iv.setImageResource(image);
            iv.setColorFilter(active ? accent() : fg);
            item.addView(iv, new LinearLayout.LayoutParams(dp(23), dp(23)));
        } else {
            TextView ic = text(icon, 20, false); ic.setGravity(Gravity.CENTER); ic.setTextColor(active ? accent() : fg); item.addView(ic);
        }
        TextView lb = text(label, 13, active); lb.setGravity(Gravity.CENTER); lb.setTextColor(active ? accent() : fg); item.addView(lb);
        item.setOnClickListener(click);
        return item;
    }

    View iconButton(int drawable, View.OnClickListener click) {
        LinearLayout shell = row();
        shell.setGravity(Gravity.CENTER);
        shell.setBackground(shape(softPanel(), 16));
        ImageView img = new ImageView(this);
        img.setImageResource(drawable);
        img.setColorFilter(fg);
        shell.addView(img, new LinearLayout.LayoutParams(dp(23), dp(23)));
        shell.setOnClickListener(click);
        return shell;
    }

    void drawReader() {
        if (words.length == 0 || body.trim().isEmpty()) {
            screen = 0;
            drawHome();
            Toast.makeText(this, "Текст книги не загружен. Открой книгу повторно.", Toast.LENGTH_LONG).show();
            return;
        }
        root.setPadding(dp(10), dp(21), dp(10), dp(7));
        LinearLayout top = row();
        TextView back = buttonish("←", softPanel(), fg, true); back.setOnClickListener(v -> { screen = 0; saveAll(); draw(); });
        top.addView(back, new LinearLayout.LayoutParams(dp(54), dp(48)));
        LinearLayout titles = column();
        TextView name = text(title.isEmpty() ? "Чтение" : title, 21, true); name.setTypeface(Typeface.SERIF, Typeface.BOLD); name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END); titles.addView(name);
        if (!author.isEmpty()) { TextView au = text(author, 14, false); au.setTextColor(faintText()); titles.addView(au); }
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView mark = buttonish("⭐", softPanel(), fg, false); mark.setOnClickListener(v -> bookmark());
        top.addView(mark, new LinearLayout.LayoutParams(dp(54), dp(48)));
        View more = iconButton(android.R.drawable.ic_menu_preferences, v -> settings());
        top.addView(more, new LinearLayout.LayoutParams(dp(54), dp(48)));
        add(root, top);

        LinearLayout modeRow = row();
        TextView chapter = text(findChapterForIndex(index), 15, true);
        chapter.setEllipsize(TextUtils.TruncateAt.END); chapter.setSingleLine(true);
        modeRow.addView(chapter, new LinearLayout.LayoutParams(0, -2, 1));
        TextView mode = buttonish(rsvp ? "📖 Обычное чтение" : "⚡ Скорочтение", softPanel(), fg, false);
        modeButton = mode; mode.setOnClickListener(v -> toggleMode());
        modeRow.addView(mode);
        add(root, modeRow);
        add(root, divider());
        add(root, gap(8));

        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER);
        controls.setBackground(shape(panelColor(), 22));
        LinearLayout.LayoutParams ctlp = new LinearLayout.LayoutParams(-1, -2); ctlp.topMargin = dp(8);
        root.addView(controls, ctlp);
        controls.addView(circleButton("‹", softPanel(), fg, v -> step(-1)), new LinearLayout.LayoutParams(0, dp(70), 1));
        TextView play = circleButton("▶", accent(), 0xffffffff, v -> { if (playing) stop(); else { playing = true; handler.post(ticker); } updatePlayButton(); });
        playButton = play;
        controls.addView(play, new LinearLayout.LayoutParams(0, dp(76), 1));
        controls.addView(circleButton("›", softPanel(), fg, v -> step(1)), new LinearLayout.LayoutParams(0, dp(70), 1));

        LinearLayout speedPanel = column();
        speedPanel.setBackground(shape(panelColor(), 22));
        pad(speedPanel, 14);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(-1, -2); spLp.topMargin = dp(8);
        root.addView(speedPanel, spLp);
        speedView = text("Скорость чтения", 15, false);
        speedPanel.addView(speedView);
        SeekBar seek = new SeekBar(this);
        seek.setMax(900);
        seek.setProgress(wpm - 100);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean from) { wpm = p + 100; updateSpeedLabel(); if (from) saveAll(); }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        speedPanel.addView(seek);

        LinearLayout footer = row();
        TextView settingsAction = buttonish("Aa  Шрифт и размер", softPanel(), fg, false);
        settingsAction.setOnClickListener(v -> settings());
        footer.addView(settingsAction, new LinearLayout.LayoutParams(0, dp(46), 1));
        TextView wordsAction = buttonish("Показ слов", softPanel(), fg, false);
        wordsAction.setOnClickListener(v -> displayDialog());
        LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        wordsLp.leftMargin = dp(8);
        footer.addView(wordsAction, wordsLp);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, -2);
        footerLp.topMargin = dp(6);
        root.addView(footer, footerLp);

        // The reader owns all remaining vertical space. FrameLayout ensures that
        // whichever reading mode is visible always has a non-zero, full-size viewport.
        FrameLayout readerWrap = new FrameLayout(this);
        readerWrap.setBackground(shape(panelColor(), 24));
        readerWrap.setMinimumHeight(dp(100));
        LinearLayout.LayoutParams readerLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(readerWrap, readerLp);

        wordView = text("", size, true);
        wordView.setGravity(Gravity.CENTER);
        wordView.setTypeface(type(), Typeface.BOLD);
        pad(wordView, 20);
        readerWrap.addView(wordView, new FrameLayout.LayoutParams(-1, -1));
        rsvpPane = wordView;

        normalScroll = new ScrollView(this);
        normalScroll.setFillViewport(true);
        normalScroll.setVerticalScrollBarEnabled(true);
        normalText = text("", Math.max(12, (int) (size * .58f)), false);
        updateNormalPage(false);
        normalText.setTypeface(type());
        normalText.setLineSpacing(dp(6), 1.18f);
        pad(normalText, 18);
        normalScroll.addView(normalText, new ScrollView.LayoutParams(-1, -2));
        readerWrap.addView(normalScroll, new FrameLayout.LayoutParams(-1, -1));
        normalScroll.setOnScrollChangeListener((View.OnScrollChangeListener) (v, x, y, ox, oy) -> {
            if (rsvp || y == oy) return;
            normalY = y;
            syncIndexFromScroll();
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastScrollSave > 800) { lastScrollSave = now; saveAll(); }
        });
        normalScroll.post(() -> { if (!rsvp) normalScroll.scrollTo(0, 0); });

        showMode();
        updateSpeedLabel();
        updateWord();
    }

    void refreshReaderContent() {
        if (wordView != null) wordView.setTextSize(size);
        if (normalText != null) normalText.setTextSize(Math.max(12, (int) (size * .58f)));
        updateWord();
    }

    TextView circleButton(String s, int fill, int color, View.OnClickListener click) {
        TextView t = text(s, 20, true);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(color);
        t.setBackground(shape(fill, 99));
        t.setOnClickListener(click);
        return t;
    }

    void showMode() {
        if (rsvpPane == null || normalScroll == null) return;
        rsvpPane.setVisibility(rsvp ? View.VISIBLE : View.GONE);
        normalScroll.setVisibility(rsvp ? View.GONE : View.VISIBLE);
        if (modeButton != null) modeButton.setText(rsvp ? "📖 Обычное чтение" : "⚡ Скорочтение");
        updateProgress();
    }

    void toggleMode() {
        stop();
        rsvp = !rsvp;
        if (!rsvp) {
            updateNormalPage(false);
            if (normalScroll != null) normalScroll.post(() -> normalScroll.scrollTo(0, 0));
        }
        showMode();
        saveAll();
    }

    void updatePlayButton() { if (playButton != null) playButton.setText(playing ? "❚❚" : "▶"); }
    void updateSpeedLabel() { if (speedView != null) speedView.setText("Скорость чтения        " + wpm + " слов/мин"); }
    void updateProgress() { if (progressView != null) progressView.setText((index + 1) + " / " + words.length + " слов · " + ((index + 1) * 100 / Math.max(1, words.length)) + "%"); }

    void updateWord() {
        if (wordView != null && words.length > 0) {
            String current = words[Math.max(0, Math.min(index, words.length - 1))];
            String before = index > 0 ? words[index - 1] : "";
            String after = index + 1 < words.length ? words[index + 1] : "";
            String result = display == 0 ? current : (display == 1 ? before + "\n\n" + current + "\n\n" + after : current + "\n\n" + after);
            SpannableString span = new SpannableString(result);
            int begin = result.indexOf(current, display == 1 ? before.length() : 0);
            if (begin >= 0) {
                span.setSpan(new RelativeSizeSpan(1.0f), begin, begin + current.length(), 33);
                span.setSpan(new StyleSpan(Typeface.BOLD), begin, begin + current.length(), 33);
            }
            if (begin > 0) span.setSpan(new RelativeSizeSpan(.40f), 0, begin, 33);
            if (begin + current.length() < result.length()) span.setSpan(new RelativeSizeSpan(.40f), begin + current.length(), result.length(), 33);
            wordView.setText(span);
            wordView.setTextSize(size);
            wordView.setTypeface(type(), Typeface.BOLD);
        }
        updateProgress();
    }

    // Only a small slice of an EPUB is ever set into the normal TextView.
    // Supplying the entire 500,000+ character novel caused slow UI construction
    // and could leave the screen blank before playback controls were created.
    void updateNormalPage(boolean scrollTop) {
        if (normalText == null || body == null || body.isEmpty()) return;
        int wordPos = (index >= 0 && index < wordStarts.length) ? wordStarts[index] : 0;
        // Start near the selected word without splitting a word.
        int start = Math.max(0, wordPos - 120);
        while (start > 0 && !Character.isWhitespace(body.charAt(start - 1))) start--;
        int end = Math.min(body.length(), start + PAGE_CHARS);
        while (end < body.length() && !Character.isWhitespace(body.charAt(end))) end++;
        pageStart = start;
        pageEnd = end;
        normalText.setText(body.substring(start, end));
        if (scrollTop && normalScroll != null) normalScroll.post(() -> normalScroll.scrollTo(0, 0));
    }

    void syncIndexFromScroll() {
        if (normalText == null || words.length == 0 || normalScroll == null) return;
        int range = Math.max(1, normalText.getHeight() - normalScroll.getHeight());
        int estimate = pageStart + Math.round((float) normalScroll.getScrollY() / range * Math.max(1, pageEnd - pageStart));
        int found = java.util.Arrays.binarySearch(wordStarts, estimate);
        if (found < 0) found = Math.max(0, -found - 2);
        index = Math.min(words.length - 1, found);
        updateCurrentMeta();
    }

    void step(int by) {
        if (words.length == 0) return;
        if (rsvp) {
            index = Math.max(0, Math.min(words.length - 1, index + by));
            updateWord();
        } else {
            if (normalScroll != null) {
                int range = Math.max(0, normalText.getHeight() - normalScroll.getHeight());
                if (by > 0 && normalScroll.getScrollY() < range - dp(16)) {
                    normalScroll.smoothScrollBy(0, normalScroll.getHeight() * 3 / 4);
                } else if (by < 0 && normalScroll.getScrollY() > dp(16)) {
                    normalScroll.smoothScrollBy(0, -normalScroll.getHeight() * 3 / 4);
                } else {
                    int target = by > 0 ? Math.min(body.length(), pageEnd) : Math.max(0, pageStart - PAGE_CHARS + 100);
                    int found = java.util.Arrays.binarySearch(wordStarts, target);
                    if (found < 0) found = Math.max(0, -found - 2);
                    index = Math.min(words.length - 1, found);
                    updateNormalPage(true);
                }
            }
        }
        saveAll();
    }

    void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, 7);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 7 && res == RESULT_OK && data != null) {
            Uri u = data.getData();
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            openBook(u.toString(), true, true);
        }
    }

    void openBook(String uriString, boolean reset, boolean switchToReader) {
        if (switchToReader) {
            stop();
            saveAll();
            Intent reader = new Intent(this, ReaderActivity.class);
            reader.putExtra("book_uri", uriString);
            reader.putExtra("reset", reset);
            startActivity(reader);
            return;
        }
        if (uriString == null || uriString.isEmpty()) return;
        if (uriString.equals(currentUri) && !body.isEmpty() && words.length > 0) {
            BookMeta m = meta(uriString);
            if (m != null) {
                if (reset) { index = 0; normalY = 0; } else { index = m.index; normalY = m.scroll; }
            }
            screen = switchToReader ? 1 : screen;
            saveAll();
            draw();
            return;
        }
        Toast.makeText(this, "Загрузка книги…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                BookLoader.Book book = BookLoader.load(this, Uri.parse(uriString));
                runOnUiThread(() -> {
                    stop();
                    if (!currentUri.isEmpty() && !currentUri.equals(uriString)) saveAll();
                    currentUri = uriString;
                    title = book.title == null ? "Книга" : book.title;
                    author = book.author == null ? "" : book.author;
                    body = book.text == null ? "" : book.text;
                    prepareWords();
                    if (words.length == 0) {
                        screen = 0;
                        Toast.makeText(this, "В книге нет доступного текста", Toast.LENGTH_LONG).show();
                        draw();
                        return;
                    }
                    BookMeta m = meta(currentUri);
                    if (m == null) { m = new BookMeta(); m.uri = currentUri; library.add(0, m); }
                    if (reset) { index = 0; normalY = 0; }
                    else { index = m.index; normalY = m.scroll; }
                    m.title = title;
                    m.author = author;
                    m.excerpt = excerptAroundIndex(index);
                    m.chapter = findChapterForIndex(index);
                    m.format = extensionFromUri(uriString);
                    m.progress = words.length == 0 ? 0 : Math.max(0, Math.min(100, (index + 1) * 100 / Math.max(1, words.length)));
                    bringFirst(m);
                    if (switchToReader) screen = 1;
                    saveAll();
                    draw();
                });
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setMessage("Не удалось открыть книгу: " + e.getMessage())
                        .setPositiveButton("ОК", null).show());
            }
        }).start();
    }

    void prepareWords() {
        ArrayList<String> list = new ArrayList<>();
        ArrayList<Integer> starts = new ArrayList<>();
        Matcher m = Pattern.compile("\\S+").matcher(body == null ? "" : body);
        while (m.find()) { list.add(m.group()); starts.add(m.start()); }
        words = list.toArray(new String[0]);
        wordStarts = new int[starts.size()];
        for (int i = 0; i < starts.size(); i++) wordStarts[i] = starts.get(i);
        if (index >= words.length) index = Math.max(0, words.length - 1);
    }

    String findChapterForIndex(int idx) {
        if (body == null || body.isEmpty()) return "";
        int pos = (idx >= 0 && idx < wordStarts.length) ? wordStarts[idx] : Math.min(body.length(), Math.max(0, body.length() - 1));
        String head = body.substring(0, Math.max(0, Math.min(pos, body.length())));
        Matcher m = Pattern.compile("(?im)^\\s*((Глава|Часть|Chapter)[^\\n]{0,80})").matcher(head);
        String last = "";
        while (m.find()) last = m.group(1).trim();
        return last.isEmpty() ? "Последнее место" : last;
    }

    String excerptAroundIndex(int idx) {
        if (body == null || body.isEmpty()) return "";
        int pos = (idx >= 0 && idx < wordStarts.length) ? wordStarts[idx] : 0;
        int start = Math.max(0, pos - 60);
        int end = Math.min(body.length(), pos + 100);
        String s = body.substring(start, end).replaceAll("\\s+", " ").trim();
        return shorten(s, 140);
    }

    String shorten(String s, int n) {
        if (s == null) return "";
        s = s.trim();
        return s.length() <= n ? s : s.substring(0, Math.max(0, n - 1)).trim() + "…";
    }

    String extensionFromUri(String u) {
        String low = u == null ? "" : u.toLowerCase(Locale.ROOT);
        if (low.contains(".epub")) return "epub";
        if (low.contains(".fb2")) return "fb2";
        if (low.contains(".html") || low.contains(".htm")) return "html";
        return "";
    }

    void bookmark() {
        if (words.length == 0 || currentUri.isEmpty()) return;
        String key = "bookmark:" + currentUri;
        Set<String> set = new HashSet<>(prefs.getStringSet(key, new HashSet<>()));
        set.add(String.valueOf(index));
        prefs.edit().putStringSet(key, set).apply();
        Toast.makeText(this, "Закладка добавлена", Toast.LENGTH_SHORT).show();
    }

    void bookmarks() {
        if (currentUri.isEmpty() || words.length == 0) {
            Toast.makeText(this, "Сначала открой книгу", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<Integer> list = new ArrayList<>();
        for (String s : prefs.getStringSet("bookmark:" + currentUri, new HashSet<>())) try { list.add(Integer.parseInt(s)); } catch (Exception ignored) {}
        Collections.sort(list);
        if (list.isEmpty()) { Toast.makeText(this, "Закладок пока нет", Toast.LENGTH_SHORT).show(); return; }
        String[] rows = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            int n = list.get(i);
            rows[i] = findChapterForIndex(n) + "\n" + excerptAroundIndex(n);
        }
        new AlertDialog.Builder(this)
                .setTitle("Закладки")
                .setItems(rows, (d, which) -> {
                    index = list.get(which);
                    normalY = 0;
                    screen = 1;
                    saveAll();
                    draw();
                })
                .setNeutralButton("Удалить все", (d, w) -> prefs.edit().remove("bookmark:" + currentUri).apply())
                .setPositiveButton("Закрыть", null)
                .show();
    }

    void showLibraryChooser() {
        if (library.isEmpty()) { Toast.makeText(this, "Библиотека пока пуста", Toast.LENGTH_SHORT).show(); return; }
        String[] items = new String[library.size()];
        for (int i = 0; i < library.size(); i++) items[i] = library.get(i).title;
        new AlertDialog.Builder(this)
                .setTitle("Библиотека")
                .setItems(items, (d, which) -> openBook(library.get(which).uri, false, true))
                .setNegativeButton("Отмена", null)
                .show();
    }

    void bookMenu(BookMeta m) {
        String[] items = {"Открыть", "Сделать текущей", "Удалить из библиотеки"};
        new AlertDialog.Builder(this)
                .setTitle(m.title)
                .setItems(items, (d, which) -> {
                    if (which == 0) openBook(m.uri, false, true);
                    else if (which == 1) {
                        currentUri = m.uri;
                        screen = 0;
                        saveAll();
                        Toast.makeText(this, "Книга отмечена как текущая", Toast.LENGTH_SHORT).show();
                        draw();
                    } else if (which == 2) {
                        library.remove(m);
                        if (m.uri.equals(currentUri)) {
                            currentUri = ""; body = ""; title = ""; author = ""; words = new String[0]; wordStarts = new int[0]; screen = 0;
                        }
                        saveAll();
                        draw();
                    }
                }).show();
    }

    void displayDialog() {
        String[] opts = {"Только одно слово", "Предыдущее и следующее", "Текущее и следующее"};
        new AlertDialog.Builder(this)
                .setTitle("Показ слов в скорочтении")
                .setSingleChoiceItems(opts, display, (d, i) -> {
                    display = i; saveAll(); d.dismiss(); updateWord();
                }).setNegativeButton("Отмена", null).show();
    }

    void settings() {
        String[] options = {
                "Режим показа слов", "Шрифт", "Размер шрифта", "Цвет текста", "Цвет фона",
                "Тема: светлая", "Тема: сепия", "Тема: ночная", "Тема: мягкая зелёная",
                "Открыть книгу", "Закладки", "Проверить обновления"
        };
        new AlertDialog.Builder(this).setTitle("Настройки")
                .setItems(options, (d, n) -> {
                    switch (n) {
                        case 0: displayDialog(); break;
                        case 1: fontDialog(); break;
                        case 2: sizeDialog(); break;
                        case 3: colorDialog(false); break;
                        case 4: colorDialog(true); break;
                        case 5: theme(0xffffffff, 0xff202020); break;
                        case 6: theme(0xfff3e7d5, 0xff30221e); break;
                        case 7: theme(0xff1e2025, 0xffe8e5db); break;
                        case 8: theme(0xffdfe9d9, 0xff223a29); break;
                        case 9: pick(); break;
                        case 10: bookmarks(); break;
                        case 11: new UpdateManager(this).check(false); break;
                    }
                }).show();
    }

    void theme(int b, int f) { bg = b; fg = f; saveAll(); draw(); }

    void fontDialog() {
        new AlertDialog.Builder(this).setTitle("Шрифт")
                .setSingleChoiceItems(fontNames, font, (d, i) -> { font = i; saveAll(); d.dismiss(); draw(); })
                .setNegativeButton("Отмена", null).show();
    }

    void sizeDialog() {
        SeekBar seek = new SeekBar(this);
        seek.setMax(60);
        seek.setProgress(size - 16);
        new AlertDialog.Builder(this).setTitle("Размер шрифта: от 16 до 76")
                .setView(seek)
                .setPositiveButton("Применить", (d, w) -> { size = seek.getProgress() + 16; saveAll(); draw(); })
                .setNegativeButton("Отмена", null).show();
    }

    void colorDialog(boolean background) {
        LinearLayout c = column();
        pad(c, 20);
        int color = background ? bg : fg;
        String[] labels = {"Красный", "Зелёный", "Синий"};
        SeekBar[] sliders = new SeekBar[3];
        for (int i = 0; i < 3; i++) {
            c.addView(text(labels[i], 16, false));
            SeekBar s = new SeekBar(this);
            s.setMax(255);
            s.setProgress(i == 0 ? Color.red(color) : i == 1 ? Color.green(color) : Color.blue(color));
            sliders[i] = s;
            c.addView(s);
        }
        new AlertDialog.Builder(this).setTitle(background ? "Цвет фона (RGB)" : "Цвет текста (RGB)")
                .setView(c)
                .setPositiveButton("Применить", (d, w) -> {
                    int n = Color.rgb(sliders[0].getProgress(), sliders[1].getProgress(), sliders[2].getProgress());
                    if (background) bg = n; else fg = n;
                    saveAll(); draw();
                }).setNegativeButton("Отмена", null).show();
    }

    private boolean hasResumedOnce = false;
    @Override protected void onResume() {
        super.onResume();
        if (hasResumedOnce) {
            loadPrefs();
            screen = 0;
            draw();
        }
        hasResumedOnce = true;
    }

    @Override protected void onPause() { super.onPause(); saveAll(); stop(); }
    @Override protected void onDestroy() { stop(); super.onDestroy(); }
}
