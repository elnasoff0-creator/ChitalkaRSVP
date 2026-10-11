package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Fetches the latest public GitHub Release, downloads an APK, verifies a
 * published SHA-256 checksum and asks Android's package installer to install.
 * Installation always requires user confirmation. */
public final class UpdateManager {
    private static final String API = "https://api.github.com/repos/elnasoff0-creator/ChitalkaRSVP/releases/latest";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String APK_NAME = "ChitalkaRSVP-update.apk";
    private final Activity activity;
    private boolean busy;
    private long downloadId = -1;
    private BroadcastReceiver receiver;
    private String expectedSha;

    public UpdateManager(Activity activity) { this.activity = activity; }

    public void check(boolean silent) {
        if (busy) return;
        busy = true;
        new Thread(() -> {
            try {
                JSONObject release = readJson(API);
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) throw new Exception("Последний выпуск не опубликован");
                JSONArray assets = release.optJSONArray("assets");
                String apkUrl = null;
                String sha = null;
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.optJSONObject(i);
                    if (a == null) continue;
                    String name = a.optString("name", "").toLowerCase(Locale.ROOT);
                    if (name.endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url", "");
                        String digest = a.optString("digest", "");
                        if (digest.startsWith("sha256:")) sha = digest.substring(7);
                    }
                }
                long remoteCode = parseVersionCode(release.optString("tag_name", ""));
                final String foundUrl = apkUrl;
                final String foundSha = sha;
                final String tag = release.optString("tag_name", "");
                final String notes = release.optString("body", "");
                MAIN.post(() -> {
                    busy = false;
                    if (remoteCode <= getLocalCode()) {
                        if (!silent) message("Обновлений нет", "Установлена актуальная версия приложения.");
                    } else if (foundUrl == null || foundUrl.isEmpty() || foundSha == null || !foundSha.matches("(?i)[0-9a-f]{64}")) {
                        if (!silent) message("Обновление недоступно", "В выпуске " + tag + " нет APK с SHA-256. Публикуйте подписанный APK через GitHub Releases.");
                    } else {
                        new AlertDialog.Builder(activity)
                            .setTitle("Доступна версия " + tag)
                            .setMessage("Что нового:\n" + (notes.isEmpty() ? "Исправления и улучшения" : notes))
                            .setNegativeButton("Позже", null)
                            .setPositiveButton("Скачать", (d, w) -> download(foundUrl, foundSha))
                            .show();
                    }
                });
            } catch (Exception e) {
                MAIN.post(() -> { busy = false; if (!silent) message("Проверка обновления", "Не удалось получить выпуск GitHub Releases: " + e.getMessage()); });
            }
        }).start();
    }

    private int getLocalCode() {
        try {
            PackageInfo p = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int)p.getLongVersionCode() : p.versionCode;
        } catch (Exception e) { return 0; }
    }
    // Tags must be in the form v1.9.0 (versionCode=190), v2.0.0 (200), etc.
    private long parseVersionCode(String tag) {
        try {
            String t = tag.toLowerCase(Locale.ROOT).replaceFirst("^v", "");
            String[] a = t.split("\\.");
            if (a.length < 2) return 0;
            int major = Integer.parseInt(a[0]); int minor = Integer.parseInt(a[1]);
            int patch = a.length > 2 ? Integer.parseInt(a[2]) : 0;
            return major * 10000L + minor * 100L + patch;
        } catch (Exception e) { return 0; }
    }

    private JSONObject readJson(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(path).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(12000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "ChitalkaRSVP-Android");
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) { out.write(b, 0, n); if (out.size() > 1_500_000) throw new Exception("Ответ слишком большой"); }
                return new JSONObject(out.toString("UTF-8"));
            }
        } finally { c.disconnect(); }
    }

    private void download(String url, String sha) {
        if (!url.startsWith("https://github.com/")) { message("Ошибка", "Ссылка на APK должна вести на GitHub."); return; }
        expectedSha = sha;
        File folder = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (folder == null) { message("Ошибка", "Папка загрузок недоступна"); return; }
        File old = new File(folder, APK_NAME);
        if (old.exists()) old.delete();
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
        r.setTitle("Читалка RSVP — обновление");
        r.setDescription("Загрузка новой версии");
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        r.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, APK_NAME);
        DownloadManager dm = (DownloadManager)activity.getSystemService(Context.DOWNLOAD_SERVICE);
        downloadId = dm.enqueue(r);
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -2L) != downloadId) return;
                try { activity.unregisterReceiver(this); } catch (Exception ignored) { }
                receiver = null;
                new Thread(() -> verifyAndInstall(new File(folder, APK_NAME))).start();
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else activity.registerReceiver(receiver, filter);
        Toast.makeText(activity, "Загрузка обновления началась", Toast.LENGTH_LONG).show();
    }

    private void verifyAndInstall(File file) {
        try {
            if (!file.isFile() || file.length() < 1000) throw new Exception("APK не загружен");
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new java.io.FileInputStream(file)) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) md.update(b, 0, n);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            if (!hex.toString().equalsIgnoreCase(expectedSha)) throw new Exception("SHA-256 не совпадает с опубликованной контрольной суммой");
            MAIN.post(() -> install(file));
        } catch (Exception e) { MAIN.post(() -> message("Не удалось обновить", e.getMessage())); }
    }

    private void install(File file) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity).setTitle("Разрешение на установку")
                    .setMessage("Разреши установку приложений из Читалки, затем снова проверь обновление.")
                    .setPositiveButton("Настройки", (d, w) -> {
                        Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName())); activity.startActivity(i);
                    }).setNegativeButton("Отмена", null).show();
                return;
            }
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
        } catch (Exception e) { message("Ошибка установки", e.getMessage()); }
    }

    private void message(String title, String body) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity).setTitle(title).setMessage(body).setPositiveButton("OK", null).show();
    }
}
