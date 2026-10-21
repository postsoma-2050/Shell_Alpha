package com.cylonid.nativealpha.browser;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;

/** Converts extracted page data into a passive, local reading document. */
public final class ReaderDocument {
    public final String title;
    public final String sourceUrl;
    private final String byline;
    private final String content;
    private final String direction;

    private ReaderDocument(String title, String sourceUrl, String byline, String content, String direction) {
        this.title = title;
        this.sourceUrl = sourceUrl;
        this.byline = byline;
        this.content = content;
        this.direction = direction;
    }

    public static ReaderDocument fromJavascript(String value, String sourceUrl) throws Exception {
        if (value == null || value.length() > 1600000 || BrowserOriginPolicy.origin(sourceUrl) == null) return null;
        Object decoded = new JSONTokener(value).nextValue();
        if (!(decoded instanceof String)) return null;
        JSONObject article = new JSONObject((String) decoded);
        String html = article.optString("content", "");
        if (html.isEmpty() || html.length() > 350000) return null;
        Safelist allowed = new Safelist().addTags("p", "br", "h1", "h2", "h3", "h4", "h5", "h6",
                "blockquote", "ul", "ol", "li", "dl", "dt", "dd", "pre", "code", "strong", "b", "em", "i",
                "u", "s", "sub", "sup", "hr", "a", "table", "thead", "tbody", "tr", "th", "td", "caption",
                "figure", "figcaption", "div", "span")
                .addAttributes("a", "href", "title").addProtocols("a", "href", "http", "https");
        String clean = Jsoup.clean(html, sourceUrl, allowed, new Document.OutputSettings().prettyPrint(false));
        if (Jsoup.parse(clean).text().length() < 160) return null;
        return new ReaderDocument(limit(article.optString("title", ""), 500), sourceUrl,
                limit(article.optString("byline", ""), 300), clean,
                "rtl".equals(article.optString("dir")) ? "rtl" : "auto");
    }

    private static String limit(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    public String html(int theme, int fontSize) {
        ReaderAppearance appearance = ReaderAppearance.of(theme);
        String[] color = {ReaderAppearance.css(appearance.paper), ReaderAppearance.css(appearance.ink), ReaderAppearance.css(appearance.accent)};
        return "<!doctype html><html dir='" + direction + "'><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\">"
                + "<title>" + Entities.escape(title) + "</title><style>"
                + "body{margin:0;background:" + color[0] + ";color:" + color[1]
                + ";font-family:Georgia,serif;font-size:" + Math.max(16, Math.min(fontSize, 32)) + "px;line-height:1.8;overflow-wrap:anywhere}"
                + "main{max-width:42rem;margin:auto;padding:24px 20px 64px}h1{font-size:1.7em;line-height:1.3}"
                + "a{color:" + color[2] + "}header{font-family:sans-serif;font-size:.8em;opacity:.8}"
                + "pre,table{max-width:100%;overflow:auto;display:block}blockquote{margin:1em 0;padding-inline-start:1em;border-inline-start:3px solid currentColor}"
                + "</style></head><body><main><header><a href='" + Entities.escape(sourceUrl).replace("'", "&#39;") + "'>"
                + Entities.escape(java.net.URI.create(sourceUrl).getHost()) + "</a><p>" + Entities.escape(byline) + "</p></header><h1>"
                + Entities.escape(title) + "</h1><article>" + content + "</article></main></body></html>";
    }
}
