package com.cylonid.nativealpha.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineUrlTest {
    @Test
    fun acceptsPathAndQueryPlaceholders() {
        assertTrue(SearchEngineUrl.isValidTemplate("https://search.example/path/%s"))
        assertTrue(SearchEngineUrl.isValidTemplate("https://search.example/find?q=%s"))
    }

    @Test
    fun preservesEncodedPathAndQuery() {
        val template = "https://search.example/a%20path/%2F?q=%s&source=%2F"

        assertTrue(SearchEngineUrl.isValidTemplate(template))
        assertEquals(
            "https://search.example/a%20path/%2F?q=hello%20world&source=%2F",
            SearchEngineUrl.buildSearchUrl(template, "hello world")
        )
    }

    @Test
    fun encodesUnicodeSpacePlusAndPercent() {
        assertEquals(
            "https://search.example/?q=%E4%B8%AD%E6%96%87%20%E7%A9%BA%E6%A0%BC%2B%E7%99%BE%E5%88%86%25",
            SearchEngineUrl.buildSearchUrl("https://search.example/?q=%s", "中文 空格+百分%")
        )
    }

    @Test
    fun pathEncodingUsesPercent20ForSpacesAndEncodesLiteralPlus() {
        val template = "https://search.example/find/%s/results"

        assertEquals(
            "https://search.example/find/space%20plus%2Bpercent%25/results",
            SearchEngineUrl.buildSearchUrl(template, "space plus+percent%")
        )
    }

    @Test
    fun rejectsMissingOrMultiplePlaceholdersAndFallsBack() {
        val query = "one + two%"
        val fallback = "https://duckduckgo.com/?q=one%20%2B%20two%25"

        assertFalse(SearchEngineUrl.isValidTemplate("https://search.example/find?q=test"))
        assertFalse(SearchEngineUrl.isValidTemplate("https://search.example/%s/%s"))
        assertEquals(fallback, SearchEngineUrl.buildSearchUrl("https://search.example/find?q=test", query))
        assertEquals(fallback, SearchEngineUrl.buildSearchUrl("https://search.example/%s/%s", query))
    }

    @Test
    fun rejectsUnsupportedSchemesAndInvalidHosts() {
        listOf(
            "ftp://search.example/?q=%s",
            "search.example/?q=%s",
            "https:///find?q=%s",
            "https://-search.example/?q=%s",
            "https://search..example/?q=%s",
            "https://not a host/?q=%s",
            "https://search.example:70000/?q=%s"
        ).forEach { template ->
            assertFalse(template, SearchEngineUrl.isValidTemplate(template))
        }
    }

    @Test
    fun rejectsPlaceholderInAuthority() {
        assertFalse(SearchEngineUrl.isValidTemplate("https://%s.example/search"))
        assertFalse(SearchEngineUrl.isValidTemplate("https://example.com%2F%s/search"))
    }

    @Test
    fun rejectsFragmentOnlyPlaceholder() {
        assertFalse(SearchEngineUrl.isValidTemplate("https://search.example/#%s"))
    }

    @Test
    fun invalidLegacyTemplateFallsBackToDuckDuckGo() {
        assertEquals(
            "https://duckduckgo.com/?q=%E4%B8%AD%E6%96%87%20query",
            SearchEngineUrl.buildSearchUrl("legacy-template", "中文 query")
        )
    }
}
