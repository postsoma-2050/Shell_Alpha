package com.cylonid.nativealpha.model

import com.cylonid.nativealpha.util.Const


data class GlobalSettings(
    var isClearCache: Boolean = false,
    var isTwoFingerMultitouch: Boolean = true,
    var isThreeFingerMultitouch: Boolean = false,
    var isShowProgressbar: Boolean = false,
    var isMultitouchReload: Boolean = true,
    var themeId: Int = 0,
    var globalWebApp: WebApp = WebApp("about:blank", Int.MAX_VALUE, Const.getDefaultAdBlockConfig()),
    var alwaysShowSoftwareButtons: Boolean = false,
    var clear_cookies: Boolean = false,
    var searchEngineUrl: String = "https://duckduckgo.com/?q=%s",
    var toolbarPosition: Int = 0,
    var swipeGestureMode: Int = 0
) {

    fun setClearCookies(clear_cookies: Boolean) {
        this.clear_cookies = clear_cookies
    }
}
