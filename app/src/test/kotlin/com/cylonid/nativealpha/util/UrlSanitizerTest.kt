package com.cylonid.nativealpha.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlSanitizerTest {
    @Test
    fun removesOnlyAllowlistedTrackingParameters() {
        val input =
            "https://example.com/a?keep=1&UTM_Source=newsletter&b=%2F&Gclid=click#section?value=1"

        assertEquals(
            "https://example.com/a?keep=1&b=%2F#section?value=1",
            UrlSanitizer.sanitizeTrackingParameters(input)
        )
    }

    @Test
    fun removesExpandedHermitTrackingParameters() {
        val input =
            "https://example.com/page?keep=important&msclkid=123&twclid=456&igshid=789&mc_cid=abc&wbraid=def&gbraid=ghi"

        assertEquals(
            "https://example.com/page?keep=important",
            UrlSanitizer.sanitizeTrackingParameters(input)
        )
    }

    @Test
    fun leavesSignedAndOauthUrlsUntouched() {
        val input =
            "https://example.com/callback?utm_source=mail&access_token=secret&state=signed#code"

        assertEquals(input, UrlSanitizer.sanitizeTrackingParameters(input))
        val signedInput = input.replace("access_token", "X-Amz-Signature")
        assertEquals(
            signedInput,
            UrlSanitizer.sanitizeTrackingParameters(signedInput)
        )
    }

    @Test
    fun ignoresNonHttpUrlsAndUrlsWithoutTrackingParameters() {
        val customScheme = "myapp://open?utm_source=mail&keep=1#fragment"
        val plainUrl = "https://example.com/path?a=1&b=2#fragment"

        assertEquals(customScheme, UrlSanitizer.sanitizeTrackingParameters(customScheme))
        assertEquals(plainUrl, UrlSanitizer.sanitizeTrackingParameters(plainUrl))
    }

    @Test
    fun decodesEncodedParameterNamesBeforeMatching() {
        val input = "http://example.com/?keep=1&%75tm_medium=email&fbclid=2"

        assertEquals(
            "http://example.com/?keep=1",
            UrlSanitizer.sanitizeTrackingParameters(input)
        )
    }

    @Test
    fun preservesNull() {
        assertNull(UrlSanitizer.sanitizeTrackingParameters(null))
    }
}
