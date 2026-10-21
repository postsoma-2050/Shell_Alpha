package com.cylonid.nativealpha.browser;

import android.net.Uri;
import android.content.Context;
import android.webkit.WebSettings;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Same on-demand Google provider as FreshRSS's reader translate route. */
public final class ReaderTranslation {
    public static final int MAX_TEXT = 2000;
    public static final List<String> LANGUAGES = java.util.Collections.unmodifiableList(Arrays.asList(
            "zh-CN", "zh-TW", "en", "ja", "ko", "fr", "ru", "th", "vi", "es", "de"));
    private ReaderTranslation() { }

    public static String requestUrl(String text, String language) {
        if (text == null || text.trim().isEmpty() || text.length() > MAX_TEXT || !LANGUAGES.contains(language))
            throw new IllegalArgumentException("Invalid translation input");
        return new Uri.Builder().scheme("https").authority("translate.googleapis.com")
                .path("/translate_a/single").appendQueryParameter("client", "gtx")
                .appendQueryParameter("sl", "auto").appendQueryParameter("tl", language)
                .appendQueryParameter("dt", "t").appendQueryParameter("q", text.trim()).build().toString();
    }

    public static String parse(String response) throws Exception {
        if (response == null || response.length() > 65536) throw new IllegalArgumentException("Invalid response");
        JSONArray segments = new JSONArray(response).getJSONArray(0);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < segments.length(); i++) {
            JSONArray segment = segments.optJSONArray(i);
            if (segment != null && segment.opt(0) instanceof String) text.append(segment.getString(0));
            if (text.length() > 16000) throw new IllegalArgumentException("Translation too large");
        }
        if (text.toString().trim().isEmpty()) throw new IllegalArgumentException("Empty translation");
        return text.toString().trim();
    }

    public static String browserUrl(String text, String language) {
        requestUrl(text, language); // Apply the same selection/target validation.
        return new Uri.Builder().scheme("https").authority("translate.google.com").path("/")
                .appendQueryParameter("sl", "auto").appendQueryParameter("tl", language)
                .appendQueryParameter("text", text.trim()).appendQueryParameter("op", "translate").build().toString();
    }

    public static boolean isProviderRedirect(URL url) {
        return "https".equals(url.getProtocol()) && (url.getPort() == -1 || url.getPort() == 443)
                && url.getUserInfo() == null && "/translate_a/single".equals(url.getPath())
                && Arrays.asList("translate.googleapis.com", "translate.google.com", "translate.google.com.hk").contains(url.getHost());
    }

    public static final class ProviderException extends java.io.IOException {
        public final int status;
        public ProviderException(int status, String destination) {
            super("Provider HTTP " + status + destination);
            this.status = status;
        }
    }

    /** One cancellable request. Never reads WebView cookies, page URL, or HTML. */
    public static final class Request {
        private final String userAgent;
        private volatile boolean cancelled;
        private volatile HttpURLConnection connection;
        public Request(Context context) {
            // Use the installed WebView's current browser identity. On-device
            // comparison: the default Android HTTP agent received /sorry,
            // while this agent returned translation JSON for the same request.
            userAgent = WebSettings.getDefaultUserAgent(context.getApplicationContext());
        }
        public void cancel() {
            cancelled = true;
            HttpURLConnection current = connection;
            if (current != null) current.disconnect();
        }
        public String execute(String text, String language) throws Exception {
            if (cancelled) throw new java.io.IOException("Cancelled");
            URL next = new URL(requestUrl(text, language));
            for (int redirects = 0; redirects < 3; redirects++) {
            if (cancelled) throw new java.io.IOException("Cancelled");
            HttpURLConnection current = (HttpURLConnection) next.openConnection();
            connection = current;
            try {
                if (cancelled) throw new java.io.IOException("Cancelled");
                current.setConnectTimeout(4000);
                current.setReadTimeout(6000);
                current.setInstanceFollowRedirects(false);
                current.setUseCaches(false);
                current.setRequestProperty("Accept", "application/json");
                current.setRequestProperty("User-Agent", userAgent);
                int status = current.getResponseCode();
                if (Arrays.asList(301, 302, 303, 307, 308).contains(status) && current.getHeaderField("Location") != null) {
                    URL destination = new URL(current.getURL(), current.getHeaderField("Location"));
                    if (isProviderRedirect(destination) && redirects < 2) {
                        next = destination;
                        continue;
                    }
                    // Diagnostic excludes the query, which contains selected text.
                    throw new ProviderException(status, " redirect: " + destination.getHost()
                            + (destination.getPath().startsWith("/sorry") ? "/sorry" : " (non-API destination)"));
                }
                if (status != 200) throw new ProviderException(status, "");
                try (InputStream stream = current.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = stream.read(buffer)) != -1) {
                        if (cancelled || bytes.size() + count > 65536) throw new java.io.IOException("Response rejected");
                        bytes.write(buffer, 0, count);
                    }
                    return parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                }
            } finally {
                current.disconnect();
                connection = null;
            }
            }
            throw new ProviderException(302, " redirect limit");
        }
    }
}
