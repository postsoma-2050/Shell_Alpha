package com.cylonid.nativealpha.browser;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BrowserHeadersPolicyTest {

    @Test
    public void getCustomHeadersWithoutSaveData() {
        Map<String, String> headers = BrowserHeadersPolicy.getCustomHeaders(false);
        assertNotNull(headers);
        assertEquals("1", headers.get("DNT"));
        assertEquals("", headers.get("X-REQUESTED-WITH"));
        assertFalse(headers.containsKey("Save-Data"));
    }

    @Test
    public void getCustomHeadersWithSaveData() {
        Map<String, String> headers = BrowserHeadersPolicy.getCustomHeaders(true);
        assertNotNull(headers);
        assertEquals("1", headers.get("DNT"));
        assertEquals("", headers.get("X-REQUESTED-WITH"));
        assertTrue(headers.containsKey("Save-Data"));
        assertEquals("on", headers.get("Save-Data"));
    }

    @Test
    public void headersMapIsUnmodifiable() {
        Map<String, String> headers = BrowserHeadersPolicy.getCustomHeaders(false);
        try {
            headers.put("Test", "Value");
            fail("Headers map should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected behavior
        }
    }
}
