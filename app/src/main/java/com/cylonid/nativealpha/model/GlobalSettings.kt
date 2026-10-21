package com.cylonid.nativealpha.model

import com.cylonid.nativealpha.util.Const


data class GlobalSettings(
    var isClearCache: Boolean = false,
    var isTwoFingerMultitouch: Boolean = true,
    var isThreeFingerMultitouch: Boolean = false,
    var isShowProgressbar: Boolean = false,
    var isMultitouchReload: Boolean = true,
    var themeId: Int = 0,
    var globalWebApp: WebApp = WebApp("about:blank", Int.MAX_VALUE, Const.getDefaultAdBlockConfig()).apply {
        containerId = Const.NO_CONTAINER
        isOverrideGlobalSettings = true
        isUseAdblock = true
        isSafeBrowsing = true
        isAllowMediaPlaybackInBackground = true
        isStripTrackingParams = true
        isPullToRefresh = true
        isEnableZooming = true
    },
    var alwaysShowSoftwareButtons: Boolean = false,
    var clear_cookies: Boolean = false,
    var searchEngineUrl: String = "https://duckduckgo.com/?q=%s",
    var toolbarPosition: Int = 0,
    var swipeGestureMode: Int = 0
) {
    @Transient var persistenceSnapshot: String? = null

    fun setClearCookies(clear_cookies: Boolean) {
        this.clear_cookies = clear_cookies
    }
}
