package com.cylonid.nativealpha.util

object UrlSanitizer {
    private val TRACKING_PARAMETERS = setOf(
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "fbclid",
        "gclid"
    )

    private val SENSITIVE_PARAMETERS = setOf(
        "code",
        "state",
        "token",
        "id_token",
        "access_token",
        "sig",
        "signature",
        "x-amz-signature",
        "expires"
    )

    @JvmStatic
    fun sanitizeTrackingParameters(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) {
            return rawUrl
        }

        val scheme = rawUrl.substringBefore(":", "").lowercase()
        if (scheme.isBlank()) {
            return rawUrl
        }
        if (scheme != "http" && scheme != "https") {
            return rawUrl
        }

        val fragmentStart = rawUrl.indexOf('#')
        val withoutFragment = if (fragmentStart >= 0) {
            rawUrl.substring(0, fragmentStart)
        } else {
            rawUrl
        }
        val fragment = if (fragmentStart >= 0) rawUrl.substring(fragmentStart) else ""
        val queryStart = withoutFragment.indexOf('?')
        if (queryStart < 0) {
            return rawUrl
        }
        val urlWithoutQuery = withoutFragment.substring(0, queryStart)
        val rawQuery = withoutFragment.substring(queryStart + 1)
        val queryParts = rawQuery.split("&")
        val keys = queryParts.map { it.substringBefore("=").decodeQueryComponent().lowercase() }
        if (keys.any { it in SENSITIVE_PARAMETERS }) {
            return rawUrl
        }

        val filtered = queryParts.filterNot {
            it.substringBefore("=").decodeQueryComponent().lowercase() in TRACKING_PARAMETERS
        }
        if (filtered.size == queryParts.size) {
            return rawUrl
        }

        return if (filtered.isEmpty()) {
            urlWithoutQuery + fragment
        } else {
            urlWithoutQuery + "?" + filtered.joinToString("&") + fragment
        }
    }

    private fun String.decodeQueryComponent(): String =
        try {
            java.net.URLDecoder.decode(this, Charsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            this
        }
}
