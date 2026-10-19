package com.cylonid.nativealpha.util

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object SearchEngineUrl {
    private const val PLACEHOLDER = "%s"
    private const val FALLBACK_TEMPLATE = "https://duckduckgo.com/?q=%s"
    private const val PROBE_VALUE = "nativealpha-query"

    fun isValidTemplate(template: String): Boolean {
        if (template.isBlank() || template.windowed(PLACEHOLDER.length).count { it == PLACEHOLDER } != 1) {
            return false
        }

        return runCatching {
            val schemeSeparator = template.indexOf("://")
            if (schemeSeparator <= 0) return false

            val authorityStart = schemeSeparator + 3
            val authorityEnd = template.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
                .let { if (it == -1) template.length else it }
            val placeholderIndex = template.indexOf(PLACEHOLDER)
            val fragmentIndex = template.indexOf('#').let { if (it == -1) template.length else it }
            val placeholderIsInPathOrQuery =
                placeholderIndex >= authorityEnd && placeholderIndex < fragmentIndex

            val probe = template.replace(PLACEHOLDER, PROBE_VALUE)
            val uri = URI(probe)
            val authority = uri.rawAuthority ?: return false
            val scheme = uri.scheme?.lowercase()

            scheme in setOf("http", "https") &&
                placeholderIsInPathOrQuery &&
                isValidHost(uri.host) &&
                (uri.port == -1 || uri.port in 1..65535) &&
                authority.none { it.isWhitespace() || it in "@\\" } &&
                authority.isNotBlank()
        }.getOrDefault(false)
    }

    fun buildSearchUrl(template: String, query: String): String {
        val safeTemplate = if (isValidTemplate(template)) template else FALLBACK_TEMPLATE
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return safeTemplate.replace(PLACEHOLDER, encodedQuery)
    }

    private fun isValidHost(host: String?): Boolean {
        if (host.isNullOrBlank() || host.length > 253) return false
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.length > 2 &&
                host.substring(1, host.lastIndex).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.' }
        }

        val normalized = host.removeSuffix(".")
        if (normalized.isBlank()) return false
        return normalized.split('.').all { label ->
            label.isNotEmpty() &&
                label.length <= 63 &&
                label.first().isAsciiLetterOrDigit() &&
                label.last().isAsciiLetterOrDigit() &&
                label.all { it.isAsciiLetterOrDigit() || it == '-' }
        }
    }

    private fun Char.isAsciiLetterOrDigit(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}
