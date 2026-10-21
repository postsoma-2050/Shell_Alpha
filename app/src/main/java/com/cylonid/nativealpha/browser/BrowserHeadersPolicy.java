package com.cylonid.nativealpha.browser;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates standard privacy and data-saver HTTP headers for WebView requests.
 * Modelled after lightweight browser architectural patterns to decouple network policies.
 */
public final class BrowserHeadersPolicy {
    private BrowserHeadersPolicy() {}

    @NonNull
    public static Map<String, String> getCustomHeaders(boolean saveData) {
        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("DNT", "1");
        extraHeaders.put("X-REQUESTED-WITH", "");
        if (saveData) {
            extraHeaders.put("Save-Data", "on");
        }
        return Collections.unmodifiableMap(extraHeaders);
    }
}
