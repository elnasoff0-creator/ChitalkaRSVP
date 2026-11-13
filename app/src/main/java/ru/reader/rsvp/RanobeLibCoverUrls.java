package ru.reader.rsvp;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Strict, shared URL resolver for RanobeLIB covers and publisher illustrations. */
final class RanobeLibCoverUrls {
    static final String COVER_HOST = "cover.lib.social";
    private RanobeLibCoverUrls() {}

    static boolean trustedHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals(COVER_HOST) || h.equals("cover.cdnlibs.org")
            || h.equals("cover.imglib.info") || h.equals("ranobelib.me")
            || h.endsWith(".ranobelib.me") || h.endsWith(".cdnlibs.org")
            || h.endsWith(".imglib.info") || h.endsWith(".mixlib.me");
    }

    static String normalize(String input) {
        if (input == null) return "";
        String value = input.trim();
        if (value.isEmpty()) return "";
        if (value.startsWith("//")) value = "https:" + value;
        else if (value.startsWith("/uploads/cover/")) value = "https://" + COVER_HOST + value;
        else if (value.startsWith("uploads/cover/")) value = "https://" + COVER_HOST + "/" + value;
        else if (value.startsWith("/")) value = "https://ranobelib.me" + value;
        // Other relative URLs cannot be resolved without a known page origin.
        try {
            URI u = new URI(value);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getUserInfo() != null
                || !trustedHost(u.getHost()) || u.getPort() != -1) return "";
            if (u.getRawFragment() != null) return "";
            return u.toASCIIString();
        } catch (Exception error) { return ""; }
    }

    /** Some API responses still return only the uploaded cover identifier. */
    static String fromCoverId(String slug, String id) {
        if (slug == null || id == null) return "";
        int split = slug.indexOf("--");
        if (split < 1 || split + 2 >= slug.length()) return "";
        String titleSlug = slug.substring(split + 2);
        String key = id.trim();
        if (!titleSlug.matches("[a-zA-Z0-9_-]{1,180}")
            || !key.matches("[a-zA-Z0-9_-]{12,128}(?:\\.(?:jpe?g|png|webp))?")) return "";
        if (!key.matches("(?i).*\\.(?:jpe?g|png|webp)$")) key += ".jpg";
        return normalize("https://" + COVER_HOST + "/uploads/cover/"
            + titleSlug + "/cover/" + key);
    }

    static List<String> candidateUrls(String url) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String normalized = normalize(url);
        if (normalized.isEmpty()) return new ArrayList<>();
        try {
            URI u = new URI(normalized);
            if (u.getRawPath() != null && u.getRawPath().startsWith("/uploads/cover/")) {
                String suffix=u.getRawPath() + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
                if (COVER_HOST.equalsIgnoreCase(u.getHost()) || "cover.cdnlibs.org".equalsIgnoreCase(u.getHost())) {
                    // Current site serves cover.lib.social, legacy API clients use cover.cdnlibs.org.
                    candidates.add("https://" + COVER_HOST + suffix);
                    candidates.add("https://cover.cdnlibs.org" + suffix);
                }
            }
        } catch (Exception ignored) {}
        candidates.add(normalized);
        return new ArrayList<>(candidates);
    }
}
