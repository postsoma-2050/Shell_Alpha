package com.cylonid.nativealpha.browser;

import android.content.Context;
import android.net.http.SslCertificate;
import android.net.http.SslError;

import com.cylonid.nativealpha.model.WebApp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.security.cert.X509Certificate;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class BrowserSecurityPolicyTest {

    @Test
    public void isWebSchemeIdentifiesHttpAndHttps() {
        assertTrue(BrowserSecurityPolicy.isWebScheme("http"));
        assertTrue(BrowserSecurityPolicy.isWebScheme("https"));
        assertTrue(BrowserSecurityPolicy.isWebScheme("HTTP"));
        assertTrue(BrowserSecurityPolicy.isWebScheme("HTTPS"));
        assertTrue(BrowserSecurityPolicy.isWebScheme("HtTp"));

        assertFalse(BrowserSecurityPolicy.isWebScheme("ftp"));
        assertFalse(BrowserSecurityPolicy.isWebScheme("tel"));
        assertFalse(BrowserSecurityPolicy.isWebScheme("mailto"));
        assertFalse(BrowserSecurityPolicy.isWebScheme("file"));
        assertFalse(BrowserSecurityPolicy.isWebScheme("javascript"));
        assertFalse(BrowserSecurityPolicy.isWebScheme(null));
        assertFalse(BrowserSecurityPolicy.isWebScheme(""));
    }

    @Test
    public void isExternalSchemeDispatchableAllowsStandardExternalSchemes() {
        assertTrue(BrowserSecurityPolicy.isExternalSchemeDispatchable("tel"));
        assertTrue(BrowserSecurityPolicy.isExternalSchemeDispatchable("mailto"));
        assertTrue(BrowserSecurityPolicy.isExternalSchemeDispatchable("sms"));
        assertTrue(BrowserSecurityPolicy.isExternalSchemeDispatchable("geo"));
        assertTrue(BrowserSecurityPolicy.isExternalSchemeDispatchable("market"));
        assertTrue(BrowserSecurityPolicy.isExternalSchemeDispatchable("intent"));
        assertTrue(BrowserSecurityPolicy.isExternalSchemeDispatchable("whatsapp"));

        // Web schemes are not external
        assertFalse(BrowserSecurityPolicy.isExternalSchemeDispatchable("http"));
        assertFalse(BrowserSecurityPolicy.isExternalSchemeDispatchable("https"));

        // Unsafe or internal schemes are blocked
        assertFalse(BrowserSecurityPolicy.isExternalSchemeDispatchable("javascript"));
        assertFalse(BrowserSecurityPolicy.isExternalSchemeDispatchable("file"));
        assertFalse(BrowserSecurityPolicy.isExternalSchemeDispatchable("content"));
        assertFalse(BrowserSecurityPolicy.isExternalSchemeDispatchable("about"));
        assertFalse(BrowserSecurityPolicy.isExternalSchemeDispatchable("data"));
        assertFalse(BrowserSecurityPolicy.isExternalSchemeDispatchable(null));
        assertFalse(BrowserSecurityPolicy.isExternalSchemeDispatchable("  "));
    }

    @Test
    public void shouldOpenExternallyEnforcesAllConstraints() {
        WebApp webApp = new WebApp("https://example.com", 1);
        webApp.setOpenUrlExternal(true);
        webApp.setAllowAppLaunch(true);

        // Different site, no popup parents -> allowed
        assertTrue(BrowserSecurityPolicy.shouldOpenExternally(
                webApp, "https://otherdomain.org/path", false));

        // Same site -> stays in browser
        assertFalse(BrowserSecurityPolicy.shouldOpenExternally(
                webApp, "https://example.com/other", false));
        assertFalse(BrowserSecurityPolicy.shouldOpenExternally(
                webApp, "https://sub.example.com/other", false));

        // When openUrlExternal is disabled -> false
        webApp.setOpenUrlExternal(false);
        assertFalse(BrowserSecurityPolicy.shouldOpenExternally(
                webApp, "https://otherdomain.org/path", false));
        webApp.setOpenUrlExternal(true);

        // When allowAppLaunch is disabled -> false
        webApp.setAllowAppLaunch(false);
        assertFalse(BrowserSecurityPolicy.shouldOpenExternally(
                webApp, "https://otherdomain.org/path", false));
        webApp.setAllowAppLaunch(true);

        // When popup parents are open -> false
        assertFalse(BrowserSecurityPolicy.shouldOpenExternally(
                webApp, "https://otherdomain.org/path", true));

        // Null checks
        assertFalse(BrowserSecurityPolicy.shouldOpenExternally(null, "https://other.org", false));
        assertFalse(BrowserSecurityPolicy.shouldOpenExternally(webApp, null, false));
    }

    @Test
    public void formatSslErrorMessageFormatsExpectedText() {
        Context context = RuntimeEnvironment.getApplication();
        SslCertificate cert = new SslCertificate("test", "test", new Date(0), new Date(1000));
        SslError error = new SslError(SslError.SSL_EXPIRED, cert, "https://example.com");

        String msg = BrowserSecurityPolicy.formatSslErrorMessage(context, error);
        assertNotNull(msg);
        assertTrue(msg.contains(context.getString(com.cylonid.nativealpha.R.string.ssl_error_expired)));
    }
}
