package com.cylonid.nativealpha.browser;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ReaderDocumentTest {

    private static final String VALID_SOURCE_URL = "https://example.com/news/article-2026";
    private static final String LONG_SAMPLE_TEXT =
            "Reader mode is designed to convert complex web documents into a distraction-free and passive reading view. "
            + "This sample text provides well over one hundred and sixty characters of clean prose content so that the "
            + "validation checks in ReaderDocument pass without being rejected for insufficient body length.";

    private static String makeJsPayload(String title, String byline, String dir, String content) {
        JSONObject article = new JSONObject();
        try {
            if (title != null) article.put("title", title);
            if (byline != null) article.put("byline", byline);
            if (dir != null) article.put("dir", dir);
            if (content != null) article.put("content", content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return JSONObject.quote(article.toString());
    }

    @Test
    public void fromJavascriptValidArticleParsesProperly() throws Exception {
        String htmlContent = "<p>" + LONG_SAMPLE_TEXT + "</p>";
        String payload = makeJsPayload("Sample Article Title", "Author Jane Doe", "ltr", htmlContent);

        ReaderDocument doc = ReaderDocument.fromJavascript(payload, VALID_SOURCE_URL);

        assertNotNull(doc);
        assertEquals("Sample Article Title", doc.title);
        assertEquals(VALID_SOURCE_URL, doc.sourceUrl);
    }

    @Test
    public void sanitizationStripsMaliciousScriptTagsAndEvents() throws Exception {
        String maliciousHtml = "<p>" + LONG_SAMPLE_TEXT + "</p>"
                + "<script>alert('xss_attack');</script>"
                + "<p onclick=\"evilClick()\" onmouseover=\"evilHover()\" onload=\"evilLoad()\">Interactive Paragraph</p>";
        String payload = makeJsPayload("Sanitization Test", "Security Audit", "ltr", maliciousHtml);

        ReaderDocument doc = ReaderDocument.fromJavascript(payload, VALID_SOURCE_URL);
        assertNotNull(doc);

        String html = doc.html(0, 20);
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("xss_attack"));
        assertFalse(html.contains("evilClick"));
        assertFalse(html.contains("evilHover"));
        assertFalse(html.contains("evilLoad"));
        assertTrue(html.contains("Interactive Paragraph"));
    }

    @Test
    public void sanitizationStripsJavascriptAndDataProtocolsFromLinks() throws Exception {
        String maliciousLinks = "<p>" + LONG_SAMPLE_TEXT + "</p>"
                + "<a href=\"javascript:alert('malicious')\">Malicious JS Link</a>"
                + "<a href=\"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==\">Data URI Link</a>"
                + "<a href=\"https://safe.example.com/info\">Safe Link</a>";
        String payload = makeJsPayload("Link Sanitization", "Byline", "ltr", maliciousLinks);

        ReaderDocument doc = ReaderDocument.fromJavascript(payload, VALID_SOURCE_URL);
        assertNotNull(doc);

        String html = doc.html(0, 20);
        assertFalse(html.contains("href=\"javascript:"));
        assertFalse(html.contains("href=\"data:"));
        assertTrue(html.contains("href=\"https://safe.example.com/info\""));
    }

    @Test
    public void resolvesHttpsRelativeLinksAgainstSourceUrl() throws Exception {
        String contentWithRelative = "<p>" + LONG_SAMPLE_TEXT + "</p>"
                + "<a href=\"/section/subpage.html\">Relative Path</a>"
                + "<a href=\"details.html\">Sibling Path</a>";
        String payload = makeJsPayload("Relative Links", "Byline", "ltr", contentWithRelative);

        ReaderDocument doc = ReaderDocument.fromJavascript(payload, VALID_SOURCE_URL);
        assertNotNull(doc);

        String html = doc.html(0, 20);
        assertTrue(html.contains("href=\"https://example.com/section/subpage.html\""));
        assertTrue(html.contains("href=\"https://example.com/news/details.html\""));
    }

    @Test
    public void sourceTitleAndBylineAreHtmlEscaped() throws Exception {
        String maliciousTitle = "Title with <script>alert('title')</script> & \"Quotes\"";
        String maliciousByline = "By <b onfocus=\"alert('byline')\">Hacker</b> & Team";
        String content = "<p>" + LONG_SAMPLE_TEXT + "</p>";
        String payload = makeJsPayload(maliciousTitle, maliciousByline, "ltr", content);

        ReaderDocument doc = ReaderDocument.fromJavascript(payload, VALID_SOURCE_URL);
        assertNotNull(doc);

        String rendered = doc.html(0, 20);
        assertFalse(rendered.contains("<title>" + maliciousTitle + "</title>"));
        assertFalse(rendered.contains("<h1>" + maliciousTitle + "</h1>"));
        assertTrue(rendered.contains("&lt;script&gt;alert('title')&lt;/script&gt;"));
        assertTrue(rendered.contains("&amp;"));
    }

    @Test
    public void sourceUrlCannotBreakOutOfQuotedLinkAttribute() throws Exception {
        String source = "https://example.com/article?q='onclick='bad";
        ReaderDocument doc = ReaderDocument.fromJavascript(makeJsPayload("Safe title", "Author", "ltr",
                "<p>" + LONG_SAMPLE_TEXT + "</p>"), source);
        assertNotNull(doc);
        String rendered = doc.html(0, 20);
        assertTrue(rendered.contains("q=&#39;onclick=&#39;bad"));
        assertFalse(rendered.contains("href='https://example.com/article?q='"));
    }

    @Test
    public void rejectsNullAndNonHttpOrHttpsSourceUrls() throws Exception {
        String payload = makeJsPayload("Title", "Byline", "ltr", "<p>" + LONG_SAMPLE_TEXT + "</p>");

        assertNull(ReaderDocument.fromJavascript(null, VALID_SOURCE_URL));
        assertNull(ReaderDocument.fromJavascript(payload, null));
        assertNull(ReaderDocument.fromJavascript(payload, ""));
        assertNull(ReaderDocument.fromJavascript(payload, "file:///android_asset/page.html"));
        assertNull(ReaderDocument.fromJavascript(payload, "javascript:void(0)"));
        assertNull(ReaderDocument.fromJavascript(payload, "about:blank"));
        assertNull(ReaderDocument.fromJavascript(payload, "data:text/html,Hello"));
    }

    @Test
    public void rejectsShortTextContentUnderMinimumLength() throws Exception {
        String shortContent = "<p>Too short article body.</p>";
        String payload = makeJsPayload("Short", "Byline", "ltr", shortContent);

        ReaderDocument doc = ReaderDocument.fromJavascript(payload, VALID_SOURCE_URL);
        assertNull(doc);
    }

    @Test
    public void rejectsEmptyOrOversizePayloads() throws Exception {
        String emptyContentPayload = makeJsPayload("Title", "Byline", "ltr", "");
        assertNull(ReaderDocument.fromJavascript(emptyContentPayload, VALID_SOURCE_URL));

        // Content exceeds 350,000 characters
        StringBuilder largeContent = new StringBuilder(360000);
        while (largeContent.length() < 355000) {
            largeContent.append("Large text content segment. ");
        }
        String oversizeContentPayload = makeJsPayload("Oversize Content", "Byline", "ltr", largeContent.toString());
        assertNull(ReaderDocument.fromJavascript(oversizeContentPayload, VALID_SOURCE_URL));

        // Overall JS value exceeds 1,600,000 characters
        StringBuilder hugeValue = new StringBuilder(1650000);
        while (hugeValue.length() < 1610000) {
            hugeValue.append("X");
        }
        assertNull(ReaderDocument.fromJavascript(hugeValue.toString(), VALID_SOURCE_URL));
    }

    @Test
    public void rejectsNonStringOrMalformedJsonValues() throws Exception {
        assertNull(ReaderDocument.fromJavascript("12345", VALID_SOURCE_URL));
        assertNull(ReaderDocument.fromJavascript("true", VALID_SOURCE_URL));
        assertNull(ReaderDocument.fromJavascript("[]", VALID_SOURCE_URL));
        assertNull(ReaderDocument.fromJavascript("{}", VALID_SOURCE_URL));
    }

    @Test
    public void clampsThemeColorsWithinZeroToThree() throws Exception {
        String payload = makeJsPayload("Theme Test", "Byline", "ltr", "<p>" + LONG_SAMPLE_TEXT + "</p>");
        ReaderDocument doc = ReaderDocument.fromJavascript(payload, VALID_SOURCE_URL);
        assertNotNull(doc);

        // Theme 0: Light (#fafaf7, #202124)
        String lightHtml = doc.html(0, 20);
        assertTrue(lightHtml.contains("background:#fafaf7;color:#202124"));

        // Theme 1: Sepia (#f4ecd8, #463a2a)
        String sepiaHtml = doc.html(1, 20);
        assertTrue(sepiaHtml.contains("background:#f4ecd8;color:#463a2a"));

        // Theme 2: Dark (#202124, #e4e4e4)
        String darkHtml = doc.html(2, 20);
        assertTrue(darkHtml.contains("background:#202124;color:#e4e4e4"));

        // Theme 3: Black (#000000, #dddddd)
        String blackHtml = doc.html(3, 20);
        assertTrue(blackHtml.contains("background:#000000;color:#dddddd"));

        // Clamping lower bound (< 0 clamps to 0)
        String clampedLow = doc.html(-5, 20);
        assertTrue(clampedLow.contains("background:#fafaf7;color:#202124"));

        // Clamping upper bound (> 3 clamps to 3)
        String clampedHigh = doc.html(99, 20);
        assertTrue(clampedHigh.contains("background:#000000;color:#dddddd"));
    }

    @Test
    public void clampsFontSizeBetweenSixteenAndThirtyTwo() throws Exception {
        String payload = makeJsPayload("Font Test", "Byline", "ltr", "<p>" + LONG_SAMPLE_TEXT + "</p>");
        ReaderDocument doc = ReaderDocument.fromJavascript(payload, VALID_SOURCE_URL);
        assertNotNull(doc);

        // Below minimum: 10 clamped to 16
        String clampedSmall = doc.html(0, 10);
        assertTrue(clampedSmall.contains("font-size:16px;"));

        // Within range: 24 kept as 24
        String normal = doc.html(0, 24);
        assertTrue(normal.contains("font-size:24px;"));

        // Above maximum: 50 clamped to 32
        String clampedLarge = doc.html(0, 50);
        assertTrue(clampedLarge.contains("font-size:32px;"));
    }

    @Test
    public void directionalMetadataAppliesRtlOrAuto() throws Exception {
        String content = "<p>" + LONG_SAMPLE_TEXT + "</p>";

        ReaderDocument docRtl = ReaderDocument.fromJavascript(makeJsPayload("RTL Title", "Author", "rtl", content), VALID_SOURCE_URL);
        assertNotNull(docRtl);
        assertTrue(docRtl.html(0, 20).contains("<html dir='rtl'>"));

        ReaderDocument docAuto = ReaderDocument.fromJavascript(makeJsPayload("Auto Title", "Author", "ltr", content), VALID_SOURCE_URL);
        assertNotNull(docAuto);
        assertTrue(docAuto.html(0, 20).contains("<html dir='auto'>"));
    }

    @Test
    public void contentSecurityPolicyHeaderIsIncluded() throws Exception {
        String payload = makeJsPayload("CSP Test", "Author", "ltr", "<p>" + LONG_SAMPLE_TEXT + "</p>");
        ReaderDocument doc = ReaderDocument.fromJavascript(payload, VALID_SOURCE_URL);
        assertNotNull(doc);

        String html = doc.html(0, 20);
        assertTrue(html.contains("<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\">"));
    }
}
