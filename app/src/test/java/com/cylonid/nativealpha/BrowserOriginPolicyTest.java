package com.cylonid.nativealpha;

import com.cylonid.nativealpha.browser.BrowserOriginPolicy;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BrowserOriginPolicyTest {

    @Test
    public void originLowercasesSchemeAndHostAndRemovesOtherComponents() {
        assertEquals("https://example.com",
                BrowserOriginPolicy.origin("HtTpS://ExAmPlE.CoM/path?q=value#fragment"));
        assertEquals("http://example.com", BrowserOriginPolicy.origin("HTTP://EXAMPLE.COM"));
    }

    @Test
    public void originNormalizesDefaultPortsAndKeepsNonDefaultPorts() {
        assertEquals("http://example.com", BrowserOriginPolicy.origin("http://example.com:80"));
        assertEquals("https://example.com", BrowserOriginPolicy.origin("https://example.com:443"));
        assertEquals("https://example.com", BrowserOriginPolicy.origin("https://example.com:00443"));
        assertEquals("http://example.com:443", BrowserOriginPolicy.origin("http://example.com:443"));
        assertEquals("https://example.com:80", BrowserOriginPolicy.origin("https://example.com:80"));
        assertEquals("https://example.com:8443", BrowserOriginPolicy.origin("https://example.com:8443"));
        assertEquals("http://example.com:0", BrowserOriginPolicy.origin("http://example.com:0"));
        assertEquals("http://example.com:65535", BrowserOriginPolicy.origin("http://example.com:65535"));
    }

    @Test
    public void userinfoDoesNotControlTheOrigin() {
        assertEquals("https://evil.test",
                BrowserOriginPolicy.origin("https://example.com:443@evil.test/path"));
        assertEquals("https://example.com",
                BrowserOriginPolicy.origin("https://user:p%40ss@EXAMPLE.COM:443/path"));
        assertFalse(BrowserOriginPolicy.sameOrigin(
                "https://example.com@evil.test", "https://example.com"));
        assertTrue(BrowserOriginPolicy.sameOrigin(
                "https://user:password@example.com", "https://example.com"));
    }

    @Test
    public void sameOriginComparesSchemeHostAndEffectivePort() {
        assertTrue(BrowserOriginPolicy.sameOrigin(
                "HTTPS://EXAMPLE.COM:443/first", "https://example.com/second?q=1"));
        assertTrue(BrowserOriginPolicy.sameOrigin(
                "http://example.com:8080", "http://EXAMPLE.COM:08080/path"));
        assertFalse(BrowserOriginPolicy.sameOrigin("http://example.com", "https://example.com"));
        assertFalse(BrowserOriginPolicy.sameOrigin("https://example.com:8443", "https://example.com"));
        assertFalse(BrowserOriginPolicy.sameOrigin("https://www.example.com", "https://example.com"));
        assertFalse(BrowserOriginPolicy.sameOrigin("https://other.test", "https://example.com"));
    }

    @Test
    public void belongsToSiteAllowsHostsAndSubdomainsAcrossHttpSchemesAndPorts() {
        assertTrue(BrowserOriginPolicy.belongsToSite(
                "http://EXAMPLE.COM:8080/path", "https://example.com:8443/base"));
        assertTrue(BrowserOriginPolicy.belongsToSite(
                "https://deep.app.example.com", "https://example.com"));
        assertTrue(BrowserOriginPolicy.belongsToSite(
                "https://example.com", "https://www.example.com"));
        assertTrue(BrowserOriginPolicy.belongsToSite(
                "https://www.example.com", "https://example.com"));
        assertTrue(BrowserOriginPolicy.belongsToSite(
                "https://app.example.com", "https://WWW.EXAMPLE.COM"));
    }

    @Test
    public void belongsToSiteIsDirectionalAndRequiresADotBoundary() {
        String base = "https://example.com";
        assertFalse(BrowserOriginPolicy.belongsToSite("https://notexample.com", base));
        assertFalse(BrowserOriginPolicy.belongsToSite("https://example.com.evil.test", base));
        assertFalse(BrowserOriginPolicy.belongsToSite("https://example-com", base));
        assertFalse(BrowserOriginPolicy.belongsToSite("https://example.com@evil.test", base));
        assertFalse(BrowserOriginPolicy.belongsToSite("https://example.com", "https://app.example.com"));
        assertFalse(BrowserOriginPolicy.belongsToSite("https://other.example.com", "https://app.example.com"));
    }

    @Test
    public void ipv6OriginsKeepBracketsAndNormalizeCaseAndDefaultPorts() {
        assertEquals("https://[2001:db8::a]",
                BrowserOriginPolicy.origin("HTTPS://[2001:DB8::A]:443/path"));
        assertEquals("http://[::1]:8080", BrowserOriginPolicy.origin("http://[::1]:8080"));
        assertTrue(BrowserOriginPolicy.sameOrigin("http://[::1]:80", "http://[::1]/path"));
        assertFalse(BrowserOriginPolicy.sameOrigin("http://[::1]:8080", "http://[::1]"));
        assertTrue(BrowserOriginPolicy.belongsToSite("https://[2001:DB8::A]:8443", "http://[2001:db8::a]"));
        assertFalse(BrowserOriginPolicy.belongsToSite("https://[::2]", "https://[::1]"));
    }

    @Test
    public void ipv4SitesRequireTheSameAddress() {
        assertTrue(BrowserOriginPolicy.belongsToSite("http://127.0.0.1:8080", "https://127.0.0.1"));
        assertFalse(BrowserOriginPolicy.belongsToSite("https://127.0.0.2", "https://127.0.0.1"));
        assertFalse(BrowserOriginPolicy.belongsToSite("https://app.127.0.0.1", "https://127.0.0.1"));
    }

    @Test
    public void malformedOpaqueAndUnsupportedUrlsNeverProduceMatchingOrigins() {
        String[] invalidUrls = {
                null, "", " ", "example.com", "//example.com", "/relative/path",
                "https:example.com", "https:///example.com", "https://", "https://?example.com",
                "about:blank", "data:text/html,hello", "javascript:alert(1)",
                "blob:https://example.com/id", "file:///example.com", "ftp://example.com",
                " https://example.com", "https://example.com/path with spaces", "https://example.com/%zz",
                "https://example.com:", "https://example.com:-1", "https://example.com:+80",
                "https://example.com:65536", "https://example.com:999999999999", "https://example.com:abc",
                "https://example.com@evil.test@other.test", "https://example.com\\@evil.test",
                "https://bad_host.test", "https://exa%6dple.com", "https://[::1", "https://[gggg::1]",
                "https://2001:db8::1", "http://[fe80::1%25eth0]"
        };
        for (String invalidUrl : invalidUrls) {
            assertNull(String.valueOf(invalidUrl), BrowserOriginPolicy.origin(invalidUrl));
            assertFalse(String.valueOf(invalidUrl), BrowserOriginPolicy.sameOrigin(invalidUrl, invalidUrl));
            assertFalse(String.valueOf(invalidUrl), BrowserOriginPolicy.sameOrigin("https://example.com", invalidUrl));
            assertFalse(String.valueOf(invalidUrl), BrowserOriginPolicy.belongsToSite(invalidUrl, "https://example.com"));
            assertFalse(String.valueOf(invalidUrl), BrowserOriginPolicy.belongsToSite("https://example.com", invalidUrl));
        }
    }

    @Test
    public void lowercasingDoesNotDependOnDefaultLocale() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals("https://i.example", BrowserOriginPolicy.origin("HTTPS://I.EXAMPLE"));
            assertTrue(BrowserOriginPolicy.belongsToSite("https://I.EXAMPLE", "https://i.example"));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }
}
