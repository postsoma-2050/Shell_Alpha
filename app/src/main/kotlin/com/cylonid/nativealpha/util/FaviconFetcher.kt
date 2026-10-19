package com.cylonid.nativealpha.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.WebApp
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object FaviconFetcher {
    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val inFlight = ConcurrentHashMap.newKeySet<Int>()
    private val failedCooldownMap = ConcurrentHashMap<Int, Long>()
    private const val FAILURE_COOLDOWN_MS = 60_000L
    private const val ICON_SIZE = 128

    private data class IconCandidate(val url: String, val priority: Int)

    private enum class TitleSource {
        MANIFEST,
        OPEN_GRAPH,
        DOCUMENT
    }

    @JvmStatic
    fun isFailedInCooldown(webAppId: Int): Boolean {
        val failedTime = failedCooldownMap[webAppId] ?: return false
        if (System.currentTimeMillis() - failedTime < FAILURE_COOLDOWN_MS) {
            return true
        }
        failedCooldownMap.remove(webAppId)
        return false
    }

    @JvmStatic
    fun clearCooldown(webAppId: Int) {
        failedCooldownMap.remove(webAppId)
    }

    @JvmStatic
    @JvmOverloads
    fun fetchAndSaveFaviconAsync(context: Context, webapp: WebApp, onComplete: (() -> Unit)? = null) {
        val appContext = context.applicationContext
        val webAppId = webapp.ID
        val requestedBaseUrl = webapp.baseUrl.trim()

        // Skip if this website recently failed and is currently within cooldown
        if (isFailedInCooldown(webAppId)) {
            return
        }

        // Avoid simultaneous duplicate network fetches for the same item
        if (!inFlight.add(webAppId)) {
            return
        }

        executor.execute {
            var fetchedBitmap: Bitmap? = null
            var fetchedTitle: String? = null
            var fetchedTitleSource: TitleSource? = null
            var documentTitle: String? = null

            try {
                val baseUrlStr = requestedBaseUrl
                val uri = Uri.parse(baseUrlStr)
                val origin = if (uri.scheme != null && uri.host != null) {
                    "${uri.scheme}://${uri.host}"
                } else {
                    "https://${uri.host ?: baseUrlStr}"
                }
                val host = uri.host ?: ""

                // 1. Try connecting with Jsoup to parse page HTML / manifest
                try {
                    val doc = Jsoup.connect(baseUrlStr)
                        .timeout(6000)
                        .userAgent(Const.DESKTOP_USER_AGENT)
                        .followRedirects(true)
                        .ignoreHttpErrors(true)
                        .get()

                    documentTitle = doc.title().trim().takeIf { it.isNotEmpty() }
                    val openGraphSiteName = doc.select(
                        "meta[property=og:site_name], meta[name=og:site_name]"
                    ).firstOrNull()?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }

                    val iconCandidates = mutableListOf<IconCandidate>()
                    var manifestTitle: String? = null

                    // Check PWA manifest
                    val manifestElement = doc.select("link[rel~=manifest]").firstOrNull()
                    if (manifestElement != null) {
                        val manifestUrl = manifestElement.absUrl("href")
                        if (manifestUrl.isNotEmpty()) {
                            runCatching {
                                val manifestBody = Jsoup.connect(manifestUrl)
                                    .timeout(5000)
                                    .ignoreContentType(true)
                                    .execute()
                                    .body()
                                val json = JSONObject(manifestBody)
                                manifestTitle = json.optString("short_name").trim()
                                    .takeIf { it.isNotEmpty() }
                                    ?: json.optString("name").trim().takeIf { it.isNotEmpty() }
                                if (json.has("icons")) {
                                    val iconsArr = json.getJSONArray("icons")
                                    for (i in 0 until iconsArr.length()) {
                                        val iconObj = iconsArr.getJSONObject(i)
                                        val src = iconObj.optString("src")
                                        val type = iconObj.optString("type").lowercase()
                                        if (src.isNotEmpty() &&
                                            !src.lowercase().endsWith(".svg") &&
                                            !type.contains("svg")) {
                                            val fullUrl = URL(URL(manifestUrl), src).toString()
                                            iconCandidates.add(
                                                IconCandidate(
                                                    fullUrl,
                                                    manifestIconPriority(iconObj.optString("sizes"))
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Apple touch icons (usually highest resolution raster icons)
                    val appleIcons = doc.select("link[rel*=apple-touch-icon]")
                    for (link in appleIcons) {
                        val href = link.absUrl("href")
                        if (href.isNotEmpty() && !href.lowercase().endsWith(".svg")) {
                            iconCandidates.add(IconCandidate(href, 10))
                        }
                    }

                    // Favicons
                    val relIcons = doc.select("link[rel*=icon]")
                    for (link in relIcons) {
                        val href = link.absUrl("href")
                        if (href.isNotEmpty() && !href.lowercase().endsWith(".svg")) {
                            iconCandidates.add(IconCandidate(href, 20))
                        }
                    }

                    // Try extracted candidates in quality order. Social preview images are excluded.
                    iconCandidates.sortWith(
                        compareBy<IconCandidate> { it.priority }.thenBy { it.url }
                    )
                    for (candidate in iconCandidates.distinctBy { it.url }) {
                        val bmp = downloadBitmap(candidate.url)
                        if (bmp != null && bmp.width >= 32 && bmp.height >= 32) {
                            fetchedBitmap = normalizeIcon(bmp)
                            break
                        }
                    }

                    fetchedTitle = manifestTitle ?: openGraphSiteName ?: documentTitle
                    fetchedTitleSource = when {
                        manifestTitle != null -> TitleSource.MANIFEST
                        openGraphSiteName != null -> TitleSource.OPEN_GRAPH
                        documentTitle != null -> TitleSource.DOCUMENT
                        else -> null
                    }
                } catch (_: Exception) {
                    // HTML scrape failed; fall through to direct service fallbacks
                }

                // 2. Multi-tier High Quality Fallbacks
                if (fetchedBitmap == null) {
                    // Fallback A: Google Favicon V2 (size=128px high-res service)
                    val googleUrl = "https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=" +
                            Uri.encode(baseUrlStr) + "&size=128"
                    val googleBmp = downloadBitmap(googleUrl)
                    if (googleBmp != null && googleBmp.width >= 16) {
                        fetchedBitmap = normalizeIcon(googleBmp)
                    }
                }

                if (fetchedBitmap == null && host.isNotEmpty()) {
                    // Fallback B: DuckDuckGo ICO API
                    val ddgUrl = "https://icons.duckduckgo.com/ip3/$host.ico"
                    val ddgBmp = downloadBitmap(ddgUrl)
                    if (ddgBmp != null && ddgBmp.width >= 16) {
                        fetchedBitmap = normalizeIcon(ddgBmp)
                    }
                }

                if (fetchedBitmap == null) {
                    // Fallback C: Direct /apple-touch-icon.png
                    val appleBmp = downloadBitmap("$origin/apple-touch-icon.png")
                    if (appleBmp != null && appleBmp.width >= 32) {
                        fetchedBitmap = normalizeIcon(appleBmp)
                    }
                }

                if (fetchedBitmap == null) {
                    // Fallback D: Direct /favicon.ico
                    val icoBmp = downloadBitmap("$origin/favicon.ico")
                    if (icoBmp != null && icoBmp.width >= 16) {
                        fetchedBitmap = normalizeIcon(icoBmp)
                    }
                }
            } catch (_: Exception) {
            } finally {
                // Free the in-flight lock so retries are possible if needed
                inFlight.remove(webAppId)
            }

            if (fetchedBitmap != null) {
                failedCooldownMap.remove(webAppId)
                ShortcutIconUtils.saveFavicon(appContext, webAppId, fetchedBitmap)
            } else {
                failedCooldownMap[webAppId] = System.currentTimeMillis()
            }

            mainHandler.post {
                val latest = DataManager.getInstance().getWebAppIgnoringGlobalOverride(webAppId, true)
                if (latest != null && latest.isActiveEntry && latest.baseUrl.trim() == requestedBaseUrl) {
                    val title = fetchedTitle
                    val currentTitle = latest.title.trim()
                    val replacesDocumentTitle =
                        fetchedTitleSource != null &&
                                fetchedTitleSource != TitleSource.DOCUMENT &&
                                documentTitle != null &&
                                currentTitle.trimEnd('/').equals(
                                    documentTitle?.trimEnd('/'),
                                    ignoreCase = true
                                )
                    if (!title.isNullOrBlank() &&
                        (isDefaultTitle(currentTitle, requestedBaseUrl) || replacesDocumentTitle)) {
                        latest.title = title
                        DataManager.getInstance().replaceWebApp(latest)
                    }
                }
                onComplete?.invoke()
            }
        }
    }

    private fun manifestIconPriority(sizes: String): Int {
        val largestDimension = Regex("(\\d+)[xX\\u00D7](\\d+)")
            .findAll(sizes.lowercase())
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .maxOrNull()
            ?: 0
        return when {
            largestDimension >= 512 -> 0
            largestDimension >= 192 -> 1
            else -> 20
        }
    }

    private fun isDefaultTitle(title: String, baseUrl: String): Boolean {
        if (title.isBlank()) return true
        val normalizedTitle = title.trim().trimEnd('/')
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        val readableUrl = normalizedUrl.removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
        val host = Uri.parse(normalizedUrl).host?.removePrefix("www.")
        return normalizedTitle.equals(normalizedUrl, ignoreCase = true) ||
                normalizedTitle.equals(readableUrl, ignoreCase = true) ||
                (!host.isNullOrBlank() &&
                        normalizedTitle.equals(host, ignoreCase = true))
    }

    private fun normalizeIcon(bitmap: Bitmap): Bitmap {
        val edge = minOf(bitmap.width, bitmap.height)
        val square = if (bitmap.width == bitmap.height) {
            bitmap
        } else {
            Bitmap.createBitmap(
                bitmap,
                (bitmap.width - edge) / 2,
                (bitmap.height - edge) / 2,
                edge,
                edge
            )
        }
        return if (square.width == ICON_SIZE && square.height == ICON_SIZE) {
            square
        } else {
            Bitmap.createScaledBitmap(square, ICON_SIZE, ICON_SIZE, true)
        }
    }

    private fun downloadBitmap(urlStr: String): Bitmap? {
        return runCatching {
            val url = URL(urlStr)
            val con = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", Const.DESKTOP_USER_AGENT)
            }
            try {
                con.inputStream.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } finally {
                con.disconnect()
            }
        }.getOrNull()
    }
}
