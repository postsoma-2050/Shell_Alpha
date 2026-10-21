package com.cylonid.nativealpha.model

import com.cylonid.nativealpha.util.Const
import java.util.*

data class AdblockConfig(val label: String, val value: String)

data class WebApp(var baseUrl: String, val ID: Int) {
    var title: String
    var isActiveEntry = true
    var isOverrideGlobalSettings = true

    var isOpenUrlExternal = false
    var isAllowCookies = true
    var isAllowThirdPartyCookies = true
    var isRestorePage = false
    var isAllowJs = true
    var isRequestDesktop = false
    var isClearCache = false
    var isUseAdblock = true
    var isSendSavedataRequest = false
    var isBlockImages = false
    var isAllowHttp = false
    var isAllowLocationAccess = false
    var userAgent: String? = null
    var isUseCustomUserAgent = false
    var isAutoreload = false
    var timeAutoreload = 60
    var isForceDarkMode = false
    var isUseTimespanDarkMode = false
    var timespanDarkModeBegin: String? = "22:00"
    var timespanDarkModeEnd: String? = "06:00"
    var isIgnoreSslErrors = false
    var isShowExpertSettings = false
    var isSafeBrowsing = true
    var isBlockThirdPartyRequests = false
    var containerId: Int = 0
    var isUseContainer = true
    var isDrmAllowed = false
    var isShowFullscreen = false
    var isKeepAwake = false
    var isCameraPermission = false
    var isMicrophonePermission = false
    var isEnableZooming = true
    var isBiometricProtection = false
    var isAllowMediaPlaybackInBackground = true
    var order = 0
    var alwaysUseFallbackContextMenu = false
    var themeColor: String = "Red"
    var isStripTrackingParams = true
    var isPullToRefresh = true
    var isFrameless = false
    var isAllowAppLaunch = true
    var isMonitorEnabled = false
    var monitorSelector: String = "body"
    var lastMonitorHash: String = ""
    var isUserscriptEnabled = false
    var userscriptCode: String = ""
    var textZoom: Int = 100
    var adBlockSettings = mutableListOf<AdblockConfig>()
    @Transient var persistenceSnapshot: String? = null

    init {
        title = baseUrl.replace("http://", "")
            .replace("https://", "")
            .replace("www.", "")
            .trimEnd('/')
        initDefaultSettings()
    }

    constructor(baseUrl: String, ID: Int, order: Int): this(baseUrl, ID) {
        this.order = order
    }

    constructor(baseUrl: String, ID: Int, adBlockSettings: MutableList<AdblockConfig>): this(baseUrl, ID) {
        this.adBlockSettings = adBlockSettings
    }

    constructor(other: WebApp) : this(other.baseUrl, other.ID) {
        title = other.title
        isOverrideGlobalSettings = other.isOverrideGlobalSettings
        containerId = other.containerId
        isUseContainer = other.isUseContainer
        order = other.order
        isActiveEntry = other.isActiveEntry
        lastMonitorHash = other.lastMonitorHash
        persistenceSnapshot = other.persistenceSnapshot
        copySettings(other)
    }



    //This part of the copy ctor should be callable independently from actual object construction to copy values of the global web app template
    fun copySettings(other: WebApp) {
        isOpenUrlExternal = other.isOpenUrlExternal
        isAllowCookies = other.isAllowCookies
        isAllowThirdPartyCookies = other.isAllowThirdPartyCookies
        isRestorePage = other.isRestorePage
        isAllowJs = other.isAllowJs
        isRequestDesktop = other.isRequestDesktop
        isClearCache = other.isClearCache
        isUseAdblock = other.isUseAdblock
        isSendSavedataRequest = other.isSendSavedataRequest
        isBlockImages = other.isBlockImages
        isAllowHttp = other.isAllowHttp
        isAllowLocationAccess = other.isAllowLocationAccess
        userAgent = other.userAgent
        isUseCustomUserAgent = other.isUseCustomUserAgent
        isAutoreload = other.isAutoreload
        timeAutoreload = other.timeAutoreload
        isForceDarkMode = other.isForceDarkMode
        isUseTimespanDarkMode = other.isUseTimespanDarkMode
        timespanDarkModeBegin = other.timespanDarkModeBegin
        timespanDarkModeEnd = other.timespanDarkModeEnd
        isIgnoreSslErrors = other.isIgnoreSslErrors
        isShowExpertSettings = other.isShowExpertSettings
        isSafeBrowsing = other.isSafeBrowsing
        isBlockThirdPartyRequests = other.isBlockThirdPartyRequests
        isDrmAllowed = other.isDrmAllowed
        isShowFullscreen = other.isShowFullscreen
        isKeepAwake = other.isKeepAwake
        isCameraPermission = other.isCameraPermission
        isMicrophonePermission = other.isMicrophonePermission
        isEnableZooming = other.isEnableZooming
        isBiometricProtection = other.isBiometricProtection
        isAllowMediaPlaybackInBackground = other.isAllowMediaPlaybackInBackground
        alwaysUseFallbackContextMenu = other.alwaysUseFallbackContextMenu
        themeColor = other.themeColor
        isStripTrackingParams = other.isStripTrackingParams
        isPullToRefresh = other.isPullToRefresh
        isFrameless = other.isFrameless
        isAllowAppLaunch = other.isAllowAppLaunch
        isMonitorEnabled = other.isMonitorEnabled
        monitorSelector = other.monitorSelector
        isUserscriptEnabled = other.isUserscriptEnabled
        userscriptCode = other.userscriptCode
        textZoom = other.textZoom
        adBlockSettings = other.adBlockSettings.map { it.copy() }.toMutableList()
    }

    private fun initDefaultSettings() {
        if (baseUrl.contains("facebook.com")) {
            userAgent = Const.DESKTOP_USER_AGENT
            isUseCustomUserAgent = true
        }
        if (isUseAdblock && adBlockSettings.isEmpty()) {
            adBlockSettings = Const.getDefaultAdBlockConfig()
        }
    }

    /*
        This function is used for settings where the ctor needs to have a different setting because
        we want different behaviour for already existing and newly created Web Apps.
            */
    fun applySettingsForNewWebApp() {
        isOverrideGlobalSettings = false
        containerId = 0
        isUseContainer = true
        isAllowThirdPartyCookies = true
        isUseAdblock = true
        isSafeBrowsing = true
        isAllowMediaPlaybackInBackground = true
        isStripTrackingParams = true
        isPullToRefresh = true
        isEnableZooming = true
        if (adBlockSettings.isEmpty()) {
            adBlockSettings = Const.getDefaultAdBlockConfig()
        }
    }

    fun markInactive() {
        isActiveEntry = false
    }

    val alphanumericBaseUrl: String
        get() = baseUrl.replace("\\P{Alnum}".toRegex(), "").replace("https", "").replace("http", "").replace("www", "")

    fun updateSandboxState(enabled: Boolean) {
        if (!enabled) {
            containerId = Const.NO_CONTAINER
            isUseContainer = false
            return
        }
        if (!SandboxManager.isValidContainerId(containerId)) {
            containerId = 0
        }
        isUseContainer = SandboxManager.isValidContainerId(containerId)
    }

    fun onSwitchSandboxChanged(mSwitch: Any?, isChecked: Boolean) {
        updateSandboxState(isChecked)
    }
}
