package com.cylonid.nativealpha.model.deserializer

import com.cylonid.nativealpha.model.AdblockConfig
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.util.Const
import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.lang.reflect.Type

class WebAppDeserializer : JsonDeserializer<WebApp> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): WebApp {
        val obj = json.asJsonObject
        val webapp = Gson().fromJson(obj, WebApp::class.java)
        if (!obj.has("themeColor")
            || obj.get("themeColor").isJsonNull
            || obj.get("themeColor").asString.isBlank()
        ) {
            webapp.themeColor = "Red"
        }
        patchDataVersion1500(webapp, obj)
        patchBatchBDefaults(webapp, obj)
        return webapp
    }

    private fun patchBatchBDefaults(webapp: WebApp, obj: JsonObject) {
        if (!obj.has("isAllowAppLaunch")) {
            webapp.isAllowAppLaunch = true
        }
        if (!obj.has("isAllowThirdPartyCookies")) {
            webapp.isAllowThirdPartyCookies = true
        }
        if (!obj.has("isUseAdblock")) {
            webapp.isUseAdblock = true
        }
        if (!obj.has("isSafeBrowsing")) {
            webapp.isSafeBrowsing = true
        }
        if (!obj.has("isAllowMediaPlaybackInBackground")) {
            webapp.isAllowMediaPlaybackInBackground = true
        }
        if (!obj.has("isStripTrackingParams")) {
            webapp.isStripTrackingParams = true
        }
        if (!obj.has("isPullToRefresh")) {
            webapp.isPullToRefresh = true
        }
        if (!obj.has("isEnableZooming")) {
            webapp.isEnableZooming = true
        }
        if (!obj.has("monitorSelector") || obj.get("monitorSelector").isJsonNull) {
            webapp.monitorSelector = "body"
        }
        if (!obj.has("lastMonitorHash") || obj.get("lastMonitorHash").isJsonNull) {
            webapp.lastMonitorHash = ""
        }
        if (!obj.has("userscriptCode") || obj.get("userscriptCode").isJsonNull) {
            webapp.userscriptCode = ""
        }
        if (!obj.has("textZoom")) {
            webapp.textZoom = 100
        }
    }

    /**
     *  With release v1.5.0 (code 1500), we added the array "adBlockSettings" to WebApp.
    If the value in JSON string is null, we set an empty array for usual web apps and the default adblock provider for the global web app.
     */
    private fun patchDataVersion1500(webapp: WebApp, obj: JsonObject) {
        var adblockKeyPresent = false

        val parsedAdblockSettings =
            if (obj.has("adBlockSettings") && obj.get("adBlockSettings").isJsonArray) {
                val array = obj.getAsJsonArray("adBlockSettings")
                adblockKeyPresent = true
                array.mapNotNull { item ->
                    try {
                        val configObj = item.asJsonObject
                        val label = configObj.get("label")?.asString ?: return@mapNotNull null
                        val value = configObj.get("value")?.asString ?: return@mapNotNull null
                        AdblockConfig(label, value)
                    } catch (e: Exception) {
                        null // Skip malformed entries
                    }
                }
            } else {
                emptyList()
            }

        if (adblockKeyPresent) {
            webapp.adBlockSettings = parsedAdblockSettings.toMutableList()
        } else {
            webapp.adBlockSettings = Const.getDefaultAdBlockConfig()
        }
        webapp.adBlockSettings = webapp.adBlockSettings.take(8).toMutableList()
    }
}
