package com.cylonid.nativealpha.util

import android.content.Context
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.util.LruCache
import com.cylonid.nativealpha.R
import java.io.File
import java.io.FileOutputStream


object ShortcutIconUtils {
    private const val ICON_DIRECTORY = "icons"
    private const val MAX_FAVICON_SIZE = 256
    private val memoryCache = LruCache<Int, Bitmap>(100)

    @JvmStatic
    fun deleteShortcuts(removableWebAppIds: List<Int>, context: Context) {
        val manager = context.getSystemService(
            ShortcutManager::class.java
        )
        for (info in manager.pinnedShortcuts) {
            val id = info.intent!!
                .getIntExtra(Const.INTENT_WEBAPPID, -1)
            if (removableWebAppIds.contains(id)) {
                manager.disableShortcuts(
                    listOf(info.id),
                    context.getString(R.string.webapp_already_deleted)
                )
            }
        }
    }

    @JvmStatic
    fun getWidthFromIcon(sizeString: String): Int {
        var xIndex = sizeString.indexOf("x")
        if (xIndex == -1) xIndex = sizeString.indexOf("×")
        if (xIndex == -1) xIndex = sizeString.indexOf("*")

        if (xIndex == -1) return 1
        val width = sizeString.substring(0, xIndex)

        return width.toInt()
    }

    @JvmStatic
    fun saveFavicon(context: Context, webappId: Int, bitmap: Bitmap): Boolean {
        val directory = File(context.filesDir, ICON_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            return false
        }

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
        val maxDimension = maxOf(square.width, square.height)
        val savedBitmap = if (maxDimension > MAX_FAVICON_SIZE) {
            val scale = MAX_FAVICON_SIZE.toFloat() / maxDimension
            Bitmap.createScaledBitmap(
                square,
                maxOf(1, (square.width * scale).toInt()),
                maxOf(1, (square.height * scale).toInt()),
                true
            )
        } else {
            square
        }

        val saved = runCatching {
            FileOutputStream(faviconFile(context, webappId)).use { output ->
                savedBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }.getOrDefault(false)
        if (saved) memoryCache.put(webappId, savedBitmap)
        return saved
    }

    @JvmStatic
    fun loadFavicon(context: Context, webappId: Int): Bitmap? {
        memoryCache.get(webappId)?.let { return it }
        val file = faviconFile(context, webappId)
        var bitmap = if (file.isFile) android.graphics.BitmapFactory.decodeFile(file.absolutePath) else null
        if (bitmap != null && bitmap.width != bitmap.height) {
            val edge = minOf(bitmap.width, bitmap.height)
            bitmap = runCatching {
                Bitmap.createBitmap(
                    bitmap!!,
                    (bitmap!!.width - edge) / 2,
                    (bitmap!!.height - edge) / 2,
                    edge,
                    edge
                )
            }.getOrNull() ?: bitmap
        }
        if (bitmap != null) memoryCache.put(webappId, bitmap)
        return bitmap
    }

    @JvmStatic
    fun deleteFavicon(context: Context, webappId: Int) {
        memoryCache.remove(webappId)
        faviconFile(context, webappId).delete()
    }

    
    @JvmStatic
    @JvmOverloads
    fun fetchAndSaveFaviconAsync(context: Context, webapp: com.cylonid.nativealpha.model.WebApp, onComplete: (() -> Unit)? = null) {
        FaviconFetcher.fetchAndSaveFaviconAsync(context, webapp, onComplete)
    }

    private fun faviconFile(context: Context, webappId: Int): File =
        File(File(context.filesDir, ICON_DIRECTORY), "webapp_$webappId.png")
}
