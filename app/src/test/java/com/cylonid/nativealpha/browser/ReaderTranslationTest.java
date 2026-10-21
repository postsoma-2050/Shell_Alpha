package com.cylonid.nativealpha.browser;

import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ReaderTranslationTest {
    @Test public void selectionCannotInjectUrlParametersOrSendPageContext() {
        Uri uri = Uri.parse(ReaderTranslation.requestUrl("Read & tl=ru # fragment? 中文", "zh-TW"));
        assertEquals("https", uri.getScheme());
        assertEquals("translate.googleapis.com", uri.getHost());
        assertEquals("zh-TW", uri.getQueryParameter("tl"));
        assertEquals("Read & tl=ru # fragment? 中文", uri.getQueryParameter("q"));
        assertEquals(5, uri.getQueryParameterNames().size());
        assertNull(uri.getQueryParameter("url"));
    }
    @Test public void joinsProviderSegmentsWithoutOriginalTextOrMetadata() throws Exception {
        assertEquals("第一句。第二句。", ReaderTranslation.parse("[[[\"第一句。\",\"Original\"],[\"第二句。\",\"Another\"]],null,\"en\"]"));
    }
    @Test public void translationRemainsPlainText() throws Exception {
        assertEquals("<b>hello</b>", ReaderTranslation.parse("[[[\"<b>hello</b>\",\"original\"]]]"));
    }
    @Test public void rejectsInvalidInputRatherThanSilentlyTruncating() {
        for (String text : new String[]{null, " ", new String(new char[2001]).replace('\0', 'a')}) {
            try { ReaderTranslation.requestUrl(text, "zh-CN"); fail("Input accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        try { ReaderTranslation.requestUrl("Hello", "untrusted-target"); fail("Target accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void rejectsEmptyMalformedAndOversizedResponses() {
        for (String response : new String[]{null, "not-json", "[null]", "[[[null]]]", "[[[\"\"]]]",
                new String(new char[65537]).replace('\0', 'x')}) {
            try { ReaderTranslation.parse(response); fail("Response accepted"); }
            catch (Exception expected) { }
        }
    }
    @Test public void cancelledRequestCannotStartNetworkWork() {
        ReaderTranslation.Request request = new ReaderTranslation.Request(org.robolectric.RuntimeEnvironment.getApplication());
        request.cancel();
        try { request.execute("Hello", "zh-CN"); fail("Cancelled request accepted"); }
        catch (java.io.IOException expected) { }
        catch (Exception wrong) { throw new AssertionError(wrong); }
    }
    @Test public void followsOnlyHttpsGoogleTranslationApiRedirects() throws Exception {
        assertTrue(ReaderTranslation.isProviderRedirect(new java.net.URL("https://translate.google.com/translate_a/single?q=test")));
        for (String url : new String[]{"https://www.google.com/sorry/index", "https://translate.google.com.evil.test/translate_a/single",
                "http://translate.google.com/translate_a/single", "https://user@translate.google.com/translate_a/single",
                "https://translate.google.com:8443/translate_a/single"}) {
            assertFalse(ReaderTranslation.isProviderRedirect(new java.net.URL(url)));
        }
    }
    @Test public void officialWebFallbackPreservesOnlyExplicitSelectionAndLanguage() {
        Uri uri = Uri.parse(ReaderTranslation.browserUrl("A & text=other # selected", "en"));
        assertEquals("translate.google.com", uri.getHost());
        assertEquals("A & text=other # selected", uri.getQueryParameter("text"));
        assertEquals("en", uri.getQueryParameter("tl"));
        assertEquals(4, uri.getQueryParameterNames().size());
    }
}
