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
    private static final String ALL_RELEASES_API = "https://api.github.com/repos/elnasoff0-creator/ChitalkaRSVP/releases?per_page=30";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String APK_NAME = "ChitalkaRSVP-update.apk";
    private final Activity activity;
    private boolean busy;
    private long downloadId = -1;
    private BroadcastReceiver receiver;
    private String expectedSha;

    public UpdateManager(Activity activity) { this.activity = activity; }

    /** Manual version report: includes published prereleases, unlike GitHub /releases/latest. */
    public void checkVersion() {
        if (busy) return;
        busy = true;
        final String local = getLocalVersion();
        final int localCode = getLocalCode();
        Toast.makeText(activity, "Проверка версии в GitHub…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                JSONArray releases = readJsonArray(ALL_RELEASES_API);
                JSONObject newest = null;
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.optJSONObject(i);
                    if (release == null || release.optBoolean("draft")) continue;
                    String tag = release.optString("tag_name", "");
                    if (!tag.matches("(?i)^v?[0-9]+(\\.[0-9]+)*(?:[-+].*)?$")) continue;
                    if (newest == null || compareVersions(tag, newest.optString("tag_name", "")) > 0)
                        newest = release;
                }
                final JSONObject found = newest;
                MAIN.post(() -> {
                    busy = false;
                    if (activity.isFinishing()) return;
                    if (found == null) {
                        message("Проверка версии", "Установлена v" + local + " (сборка " + localCode + ").\n\nВ GitHub пока нет опубликованных релизов, доступных для сравнения. APK из Artifacts проверкой не учитываются.");
                        return;
                    }
                    String remote = found.optString("tag_name", "");
                    int cmp = compareVersions(remote, local);
                    String report = "Установлена: v" + local + " (сборка " + localCode + ")\n" +
                        "В GitHub: " + remote + (found.optBoolean("prerelease") ? " (beta)" : "") + "\n\n" +
                        (cmp > 0 ? "Доступна более новая версия." : cmp == 0 ? "Установлена актуальная опубликованная версия." : "Установленная версия новее опубликованной в GitHub.");
                    if (cmp <= 0) { message("Проверка версии", report); return; }
                    JSONArray assets = found.optJSONArray("assets");
                    String apkUrl = "", sha = "";
                    if (assets != null) for (int i=0;i<assets.length();i++) {
                        JSONObject asset = assets.optJSONObject(i);
                        if (asset == null || !asset.optString("name", "").toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                        apkUrl = asset.optString("browser_download_url", "");
                        String digest = asset.optString("digest", "");
                        if (digest.startsWith("sha256:")) sha = digest.substring(7);
                        break;
                    }
                    final String url = apkUrl, checksum = sha;
                    AlertDialog.Builder dialog = new SwipeDialogs.Builder(activity)
                        .setTitle("Проверка версии")
                        .setMessage(report)
                        .setNegativeButton("Закрыть", null);
                    if (url.startsWith("https://github.com/") && checksum.matches("(?i)[0-9a-f]{64}")) {
                        dialog.setPositiveButton("Скачать APK", (d, w) -> download(url, checksum));
                    } else {
                        dialog.setMessage(report + "\n\nПодписанный APK с проверяемой SHA-256 пока не опубликован. Откройте GitHub Releases для ручной проверки.");
                        String page = found.optString("html_url", "");
                        if (page.startsWith("https://github.com/elnasoff0-creator/ChitalkaRSVP/"))
                            dialog.setPositiveButton("Открыть релиз", (d, w) -> activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(page))));
                    }
                    dialog.show();
                });
            } catch (Exception e) {
                MAIN.post(() -> {
                    busy = false;
                    if (!activity.isFinishing()) message("Проверка версии",
                        "Установлена v" + local + " (сборка " + localCode + ").\n\nНе удалось проверить GitHub: " + e.getMessage());
                });
            }
        }, "ChitalkaRSVP-version-check").start();
    }

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
                String remoteVersion = release.optString("tag_name", "");
                final String foundUrl = apkUrl;
                final String foundSha = sha;
                final String tag = release.optString("tag_name", "");
                final String notes = release.optString("body", "");
                MAIN.post(() -> {
                    busy = false;
                    if (compareVersions(remoteVersion, getLocalVersion()) <= 0) {
                        if (!silent) message("Обновлений нет", "Установлена актуальная версия приложения.");
                    } else if (foundUrl == null || foundUrl.isEmpty() || foundSha == null || !foundSha.matches("(?i)[0-9a-f]{64}")) {
                        if (!silent) message("Обновление недоступно", "В выпуске " + tag + " нет APK с SHA-256. Публикуйте подписанный APK через GitHub Releases.");
                    } else {
                        new SwipeDialogs.Builder(activity)
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
    private String getLocalVersion() {
        try {
            PackageInfo p = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return p.versionName == null ? "0" : p.versionName;
        } catch (Exception e) { return "0"; }
    }

    // GitHub tags and Android versionCode are independent numbering systems.
    // Compare semantic versionName components numerically, including fourth components.
    private int compareVersions(String remote, String local) {
        int[] a = versionParts(remote);
        int[] b = versionParts(local);
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        }
        // A stable release supersedes a prerelease of the same numeric version.
        boolean remoteBeta = remote != null && remote.toLowerCase(Locale.ROOT).matches(".*[-](?:alpha|beta|rc|pre).*\\b.*");
        boolean localBeta = local != null && local.toLowerCase(Locale.ROOT).matches(".*[-](?:alpha|beta|rc|pre).*\\b.*");
        return Boolean.compare(localBeta, remoteBeta);
    }

    private int[] versionParts(String value) {
        int[] out = new int[4];
        if (value == null) return out;
        String name = value.trim().replaceFirst("^[vV]", "");
        String[] fields = name.split("[.]", -1);
        for (int i = 0; i < Math.min(fields.length, out.length); i++) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([0-9]+)").matcher(fields[i]);
            if (m.find()) {
                try { out[i] = Integer.parseInt(m.group(1)); }
                catch (NumberFormatException ignored) { out[i] = Integer.MAX_VALUE; }
            }
        }
        return out;
    }

    private JSONObject readJson(String path) throws Exception { return new JSONObject(readJsonText(path)); }
    private JSONArray readJsonArray(String path) throws Exception { return new JSONArray(readJsonText(path)); }
    private String readJsonText(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(path).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(12000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "ChitalkaRSVP-Android");
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) { out.write(b, 0, n); if (out.size() > 1_500_000) throw new Exception("Ответ слишком большой"); }
                return out.toString("UTF-8");
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
        r.setTitle("ПРОЧиталка — обновление");
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
                new SwipeDialogs.Builder(activity).setTitle("Разрешение на установку")
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
        new SwipeDialogs.Builder(activity).setTitle(title).setMessage(body).setPositiveButton("OK", null).show();
    }
}
