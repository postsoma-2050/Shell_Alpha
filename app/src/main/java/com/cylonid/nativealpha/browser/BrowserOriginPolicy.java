package com.cylonid.nativealpha.browser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class BrowserOriginPolicy {

    private BrowserOriginPolicy() {
    }

    public static String origin(String url) {
        URI uri = parse(url);
        if (uri == null) {
            return null;
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return scheme + "://" + host + (port == -1 || defaultPort ? "" : ":" + port);
    }

    public static boolean sameOrigin(String first, String second) {
        String firstOrigin = origin(first);
        return firstOrigin != null && firstOrigin.equals(origin(second));
    }

    public static boolean belongsToSite(String url, String baseUrl) {
        URI uri = parse(url);
        URI baseUri = parse(baseUrl);
        if (uri == null || baseUri == null) {
            return false;
        }

        String host = siteHost(uri);
        String baseHost = siteHost(baseUri);
        if (host.equals(baseHost)) {
            return true;
        }
        if (baseHost.startsWith("[") || baseHost.matches("[0-9.]+")) {
            return false;
        }
        return host.endsWith("." + baseHost);
    }

    private static String siteHost(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private static URI parse(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            URI uri = new URI(url).parseServerAuthority();
            String scheme = uri.getScheme();
            if (uri.isOpaque() || !("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme))) {
                return null;
            }
            String host = uri.getHost();
            if (host == null || host.isEmpty() || host.indexOf('%') >= 0
                    || uri.getPort() > 65535 || uri.getRawAuthority().endsWith(":")) {
                return null;
            }
            return uri;
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}
