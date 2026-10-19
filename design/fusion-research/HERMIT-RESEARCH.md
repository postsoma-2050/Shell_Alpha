# Hermit 功能證據研究

> 範圍：只研究 `/Users/grangerfdad/Desktop/hermit broswer` 的 `apktool_out` 與 `decompiled_hermit`，並以本專案現有程式碼作為落地邊界。`rea` 是逆向工具與其周邊，不作為 Hermit 功能報告來源。
> 方法：先以 `AndroidManifest.xml`、`res/xml`、`res/values` 建立介面與資源索引，再以 `decompiled_hermit/sources` 的類別、方法與呼叫點交叉核對。UI 字串只算「使用者可見宣稱」，不算實作證明。
> 重要限制：`decompiled_hermit` 是 JADX 反編譯結果，部分方法被標記為 `Method dump skipped`；本報告只把能讀到的欄位、常數、呼叫關係與可執行路徑列為「代碼／資源證實」，其餘明列「推測」或「未驗證」。

## 引用根目錄與短檔名對照

- **Hermit 根目錄**：`/Users/grangerfdad/Desktop/hermit broswer`。報告中所有 Hermit 檔案路徑（含 `apktool_out/...`、`decompiled_hermit/...`）皆相對於此目錄。
- **本專案根目錄**：`/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev`。Native Alpha 自身檔案以 `app/src/...` 相對路徑表示。

下表只收錄本報告實際引用的短檔名，供每一引用定位（未引用者不列）：

| 文中短名 | 完整相對路徑 |
|---|---|
| `AndroidManifest.xml` | `apktool_out/AndroidManifest.xml` |
| `colors.xml`（資源證實） | `apktool_out/res/values/colors.xml` |
| `strings.xml`（Monitors 文案，`218-219`） | `decompiled_hermit/resources/res/values/strings.xml` |
| `strings.xml`（userscripts 文案，`431`） | `apktool_out/res/values/strings.xml` |
| `settings_lite_app_main.xml` | `apktool_out/res/xml/settings_lite_app_main.xml` |
| `settings_lite_app_behavior.xml` | `apktool_out/res/xml/settings_lite_app_behavior.xml` |
| `settings_lite_app_privacy.xml` | `apktool_out/res/xml/settings_lite_app_privacy.xml` |
| `settings_admin_main.xml` | `apktool_out/res/xml/settings_admin_main.xml` |
| `settings_admin_privacy.xml` | `apktool_out/res/xml/settings_admin_privacy.xml` |
| `settings_admin_lite_apps.xml` | `apktool_out/res/xml/settings_admin_lite_apps.xml` |
| `fragment_lite_app_theme.xml` | `apktool_out/res/layout/fragment_lite_app_theme.xml` |
| `xf3.java` | `decompiled_hermit/sources/defpackage/xf3.java` |
| `wa0.java` | `decompiled_hermit/sources/defpackage/wa0.java` |
| `ua0.java` | `decompiled_hermit/sources/defpackage/ua0.java` |
| `qa0.java` | `decompiled_hermit/sources/defpackage/qa0.java` |
| `j44.java` | `decompiled_hermit/sources/defpackage/j44.java` |
| `lm3.java` | `decompiled_hermit/sources/defpackage/lm3.java` |
| `mp.java` | `decompiled_hermit/sources/defpackage/mp.java` |
| `nq2.java` | `decompiled_hermit/sources/defpackage/nq2.java` |
| `ac4.java` | `decompiled_hermit/sources/defpackage/ac4.java` |
| `hb4.java` | `decompiled_hermit/sources/defpackage/hb4.java` |
| `j72.java` | `decompiled_hermit/sources/defpackage/j72.java` |
| `o72.java` | `decompiled_hermit/sources/defpackage/o72.java` |
| `oc2.java` | `decompiled_hermit/sources/defpackage/oc2.java` |
| `v60.java` | `decompiled_hermit/sources/defpackage/v60.java` |
| `gk2.java` | `decompiled_hermit/sources/defpackage/gk2.java` |
| `CoreWebView.java` | `decompiled_hermit/sources/core/webview/CoreWebView.java` |
| `BrowserFragment.java` | `decompiled_hermit/sources/com/chimbori/hermitcrab/web/BrowserFragment.java` |
| `SandboxPreference.java` | `decompiled_hermit/sources/com/chimbori/hermitcrab/sandbox/SandboxPreference.java` |
| `LiteAppThemeSettingsFragment.java` | `decompiled_hermit/sources/com/chimbori/hermitcrab/settings/LiteAppThemeSettingsFragment.java` |
| `LiteAppTagsSettingsFragment.java` | `decompiled_hermit/sources/com/chimbori/hermitcrab/settings/LiteAppTagsSettingsFragment.java` |
| `AdminTagsSettingsFragment.java` | `decompiled_hermit/sources/com/chimbori/hermitcrab/settings/AdminTagsSettingsFragment.java` |
| `UserScriptsSettingsFragment.java` | `decompiled_hermit/sources/com/chimbori/hermitcrab/settings/UserScriptsSettingsFragment.java` |
| `UserScriptLibraryFragment.java` | `decompiled_hermit/sources/com/chimbori/hermitcrab/admin/UserScriptLibraryFragment.java` |
| `ContentBlockerFetcher.java` | `decompiled_hermit/sources/core/contentblocker/ContentBlockerFetcher.java` |
| `ContentBlockerSettingsFragment.java` | `decompiled_hermit/sources/core/contentblocker/ContentBlockerSettingsFragment.java` |
| `EndpointRole.java` | `decompiled_hermit/sources/com/chimbori/hermitcrab/schema/EndpointRole.java` |
| `NotificationsWork.java` | `decompiled_hermit/sources/com/chimbori/hermitcrab/feeds/NotificationsWork.java` |
| `WebViewActivity.java`、`CustomBrowser` | `app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java`（`CustomBrowser` 是其中的內部 `WebViewClient` 類別） |

## 結論摘要

- **品牌色與色盤**：`#eb5757` 有精確資源證據；`xf3.java:16-20` 列舉 **22** 個 sandbox 身分（index 0–21：`DEFAULT`、`INCOGNITO` 與 20 個彩色身分），manifest 另有 `IncognitoActivity` 加 20 個彩色 sandbox Activity（共 21 個 sandbox Activity 宣告，另加主瀏覽器 Activity）。此 22 項列舉是「具名 sandbox 身分」，**不等於**「21 色盤」；沒有找到任何 21 筆 hex 的單一色盤陣列，故「21 色盤」仍是使用者提供、未驗證的宣稱。
- **進程與文件模式**：manifest 證實主瀏覽器用 `documentLaunchMode="intoExisting"`，Incognito 與 20 個 sandbox Activity 用 `documentLaunchMode="always"`；Incognito 與 sandbox 各有獨立 `android:process`。這是進程／任務層級隔離證據，不是 Cookie 隔離證據。
- **隱私與行為**：Behavior、Privacy、Theme、Tags 都有偏好 XML 與對應 Fragment；下拉刷新、`Save-Data` 標頭、`block_malware` 有可讀出的實際呼叫點；但 `frameless` 只證實設定模型與 UI 開關，更新路徑中未見其實作（見第 5 節），不可宣稱已實作。
- **tracking_protection**：有 88 個追蹤參數的常數表，且以 OkHttp interceptor 形式註冊；但實際移除方法 body 被 JADX 略過，因此「移除查詢參數」的演算法細節屬未完全驗證，功能存在則有充分間接證據。
- **Monitors / userscripts / tracking_protection 三選一**：建議先做**最小改動的 URL 清理**（tracking_protection 的最小版本）。Monitors 與 userscripts 都會引入新的資料模型、排程、網路抓取或腳本執行面，成本與攻擊面遠高於 URL 清理。

## 證據分級定義

| 等級 | 定義 | 本報告用法 |
|---|---|---|
| 使用者提供 | 題目直接給出的功能名稱或品牌宣稱 | 只當待驗證線索，不當結論 |
| 代碼／資源證實 | 有精確檔案、行號、方法、資源鍵或呼叫點 | 可作為設計依據 |
| 推測 | 由多個間接證據合理推得，但缺少直接 method body 或執行驗證 | 需標明假設與風險 |
| 未驗證 | 只看到 UI 字串、圖示、欄位名稱，或反編譯缺失 | 不可拿來宣稱已實作 |

## 逐項核對

### 1. 品牌 `#eb5757`

- **代碼／資源證實**：`apktool_out/res/values/colors.xml:74` 定義 `<color name="hermit_app_theme">#eb5757</color>`；`decompiled_hermit/resources/res/values/colors.xml:74` 相同。
- **判讀**：這是 Hermit 的應用主題色資源，不是只出現在文案或截圖的顏色。
- **未驗證**：沒有逐一證明所有主要畫面都以此色作為唯一主色；只證實資源名與值存在。

### 2. sandbox 身分（22 項）與「21 色盤」（使用者提供、未驗證）

- **代碼／資源證實**：`decompiled_hermit/sources/defpackage/xf3.java:16-20` 建立 `DEFAULT`（index 0）、`INCOGNITO`（index 1），再加上 `RED`、`GREEN`、`BLUE`、`PURPLE`、`YELLOW`、`PINK`、`ORANGE`、`BROWN`、`LIME`、`CYAN`、`CRIMSON`、`VIOLET`、`LAVENDER`、`OLIVE`、`INDIGO`、`TEAL`、`AQUAMARINE`、`BRICK`、`AMBER`、`TANGERINE`（index 2–21），**合計 22 個 sandbox 名稱**（2 個特殊身分＋20 個彩色身分）。
- **判讀**：這 22 項列舉是「具名 sandbox 身分」，與題目「21 色盤」不是同一件事；不可把 22 項列舉（或其中的 20 個色名）當成 21 筆 hex 色盤。
- **代碼／資源證實**：`apktool_out/AndroidManifest.xml:63-83` 有 `IncognitoActivity` 與 20 個 `BrowserActivity<Color>`，且各自帶 `:incognito` 或 `:sandbox.<name>` 進程（共 21 個 sandbox Activity 宣告，另加主瀏覽器 `BrowserActivity`）。
- **推測**：這些名稱構成使用者可見的 sandbox 選擇器；`SandboxPreference.java:70` 以 `getColor(...)` 設定圖示 tint，支持「每個 sandbox 有對應顏色」的設計。
- **未驗證**：「21 色盤」為使用者提供，尚未驗證；沒有找到任何 21 筆 hex 的色盤常數陣列。若需要精準色票，應另做資源／執行時取樣，不應從名稱推色碼。

### 3. 獨立進程與 `documentLaunchMode`

- **代碼／資源證實**：`apktool_out/AndroidManifest.xml:62`：`BrowserActivity` 為 `documentLaunchMode="intoExisting"`、`launchMode="singleInstancePerTask"`。
- **代碼／資源證實**：`apktool_out/AndroidManifest.xml:63`：`IncognitoActivity` 為 `documentLaunchMode="always"`、`android:process=":incognito"`、`launchMode="standard"`。
- **代碼／資源證實**：`apktool_out/AndroidManifest.xml:64-83`：每個 sandbox Activity 都是 `documentLaunchMode="always"`、`android:process=":sandbox.<name>"`、`launchMode="standard"`，且 `persistableMode="persistNever"`、`autoRemoveFromRecents="true"`。
- **判讀**：`documentLaunchMode` 影響最近任務／文件的呈現與重用方式；`android:process` 影響程序邊界。兩者都不是 Cookie 隔離的直接證明。
- **推測**：Hermit 以「每個 sandbox 一個 Activity 類別＋一個 process」換取任務與程序層面的區隔，並用 `intoExisting` 讓主瀏覽器重用既有文件任務。
- **未驗證**：未證實每個 process 都有獨立 WebView 資料目錄、獨立 CookieStore 或獨立 storage partition。`CoreWebView.java:587`、`:702` 只看到全域 `CookieManager.getInstance()` 的接受設定，未看到 per-sandbox cookie store。

### 4. Behavior / Privacy / Theme / Tags 設定分區

- **代碼／資源證實**：`apktool_out/res/xml/settings_lite_app_main.xml:9-12` 列出 Behavior、Privacy、Theme、Tags 四個入口；`:15` 另有 Monitors。
- **代碼／資源證實**：`settings_lite_app_behavior.xml:4-13` 以 `HeaderPreference` 標題「Behavior」，內含 full screen、frameless、pull to refresh、load images、desktop mode、data saver、custom user agent。
- **代碼／資源證實**：`settings_lite_app_privacy.xml:4-14` 以「Privacy」分區，含 `block_malware`、Global Settings、popups、third-party cookies、open links、app launches/installs。
- **代碼／資源證實**：`settings_admin_main.xml:13` 有 `pref_tracking_protection` 的 Global Settings 開關；`settings_admin_privacy.xml:4-13` 另有搜尋與清除資料的 Privacy 分區。
- **代碼／資源證實**：Theme 由 `LiteAppThemeSettingsFragment.java:63-78` 與 `fragment_lite_app_theme.xml:5-18` 組成，包含明暗模式與 Hex 色編輯器。
- **代碼／資源證實**：Tags 由 `LiteAppTagsSettingsFragment.java:49-62`、`AdminTagsSettingsFragment.java:44-57` 操作 RecyclerView 與資料流。
- **判讀**：這些不是單純字串；有偏好 XML、Fragment、資料流與 UI 元件。但「分區名稱」本身仍只是資訊架構，功能強弱要回到各子項的實作。

### 5. Frameless

- **代碼／資源證實**：`settings_lite_app_behavior.xml:7` 綁定 `pref_frameless`；`wa0.java:91` 的 `CoreWebViewSettings` 明確有 `frameless` 欄位。
- **推得路徑**：`BrowserFragment` 透過 `CoreWebViewSettings` 更新 WebView；`CoreWebView.java:685-735` 的 `updateSettings` 是設定落地點。
- **未驗證**：`updateSettings` 中可讀到的行段沒有直接出現 `wa0Var.m`（frameless）的視窗裝飾處理；因此只能證實設定模型與 UI 存在，不能從現有 method body 完整證明 frameless 的視覺效果。

### 6. 下拉刷新

- **代碼／資源證實**：`settings_lite_app_behavior.xml:8` 綁定 `pref_pull_to_refresh`，預設 `true`。
- **代碼／資源證實**：`BrowserFragment.java:11` 匯入 `SwipeRefreshLayout`；`:1275-1278` 設定 `setOnRefreshListener` 與 `setOnChildScrollUpCallback`。
- **判讀**：這是完整可執行路徑，不只是 UI 開關。

### 7. 省流量（Data Saver）

- **代碼／資源證實**：`settings_lite_app_behavior.xml:13` 綁定 `pref_save_data`；同檔 `:10` 另有獨立的 `pref_load_images`。
- **代碼／資源證實**：`CoreWebView.java:690` 以 `wa0Var.e`（saveData）將 `Save-Data` 設為 `on` 或空字串；`:697` 以 `setBlockNetworkImage(!wa0Var.b)` 依 `wa0Var.b`（loadImages）控制圖片載入。
- **代碼／資源證實**：`wa0.java:91` 的 `toString` 同時列出獨立的 `loadImages`、`saveData` 欄位；`:95` 的預設建構子顯示 `saveData=false`、`loadImages=true`。
- **判讀**：`Save-Data` 標頭與圖片封鎖是兩個**獨立設定**：前者由 `pref_save_data` 驅動，後者由 `pref_load_images` 驅動。開啟省流量（送 `Save-Data` 標頭）不會同時封鎖圖片，反之亦然。
- **未驗證**：沒有證實 `Save-Data` 對所有第三方資源都有效；伺服器可忽略此標頭。

### 8. `block_malware`

- **代碼／資源證實**：`settings_lite_app_privacy.xml:6` 綁定 `pref_content_blocker`，標題為 `block_malware`；`:7` 的 Global Settings 依賴此開關。
- **代碼／資源證實**：`wa0.java:91` 有 `blockMalware` 欄位；`ua0.java:22` 的序列化鍵為 `block_malware`。
- **代碼／資源證實**：`CoreWebView.java:688` 將 `wa0Var.g` 存入 `this.p`；`qa0.java:339-363` 在 `shouldInterceptRequest` 中依 `coreWebView.p` 決定是否回傳阻擋回應。
- **代碼／資源證實**：`ContentBlockerFetcher.java:22-38` 是 WorkManager worker，會依 `m70.i.a() + 7` 判斷清單是否過期並觸發抓取；`ContentBlockerSettingsFragment.java:145-147` 有手動刷新清單的點擊路徑。
- **判讀**：這是「可更新的內容阻擋清單＋WebView 請求攔截」的管線，不是只有 Safe Browsing 開關。
- **未驗證**：沒有逐一驗證阻擋清單的供應來源、規則語法與誤擋率；不能把 `block_malware` 等同於完整惡意網站防護。

### 9. Global Settings 分區

- **代碼／資源證實**：`settings_lite_app_main.xml:20` 以「Global Settings」入口連到全域設定，摘要為 `global_settings_for_all_lite_apps`。
- **代碼／資源證實**：`settings_admin_main.xml:4-16` 以 `HeaderPreference` 標題「Global Settings」組織多個全域開關與付費項目；`settings_admin_lite_apps.xml:4-24` 另有 Lite Apps 的全域外觀、通知、下載與備份分區。
- **判讀**：Global Settings 是有獨立 PreferenceScreen 與多個分類的實際設定面，而非單一按鈕。

### 10. `tracking_protection`：移除追蹤參數

- **代碼／資源證實**：`settings_admin_main.xml:13` 綁定 `pref_tracking_protection`，預設 `false`，摘要明示「removing tracking parameters from URLs」。
- **代碼／資源證實**：`j44.java:6` 有 88 個參數名稱的常數陣列，包含 `fbclid`、`gclid`、`msclkid`、`twclid`、`yclid`、`utm_*`、`mc_cid`、`mc_eid` 等。
- **代碼／資源證實**：`j44.java:16` 的 `a(okhttp3.t)` 是 OkHttp interceptor 介面方法；`lm3.java:418` 在攔截器清單中 `new j44()`；`mp.java:33` 也在另一個清單中 `new j44()`。
- **代碼／資源證實**：`nq2.java:10` 以 `new ac0(..., new j44())` 建構攔截器；`nq2.java:20` 另有以 `Uri` 為輸入的 `a(Uri, ...)` 方法。
- **推測**：`j44` 的實際 body 被 JADX 標記 `Method dump skipped`，但常數表、interceptor 介面與註冊點足以支持「Hermit 有 URL 追蹤參數清理功能」。
- **未驗證**：無法從現有反編譯內容確認它是否保留重複參數、是否處理 fragment、是否只在主文件請求或所有子資源請求生效，也無法確認它是否會改寫 redirect 後的最終 URL。
- **設計含義**：這是最適合先落地的一項，因為輸入與輸出都是 URL，不需要新資料表、背景排程或腳本執行環境。

### 11. userscripts 沙盒

- **代碼／資源證實**：`settings_lite_app_main.xml` 未直接列 userscripts，但 `BrowserFragment.java:501`、`:862` 有 `com.chimbori.hermitcrab.feature.userscripts` 功能旗標與載入路徑；`:1271` 有 `reloadUserScripts` 日誌點。
- **代碼／資源證實**：`UserScriptsSettingsFragment.java` 與 `UserScriptLibraryFragment.java` 存在，對應檔案管理、安裝與編輯流程；`ac4.java:8` 有 `userscripts` 目錄。
- **文案宣稱（非實作證實）**：`apktool_out/res/values/strings.xml:431`（`userscripts_unsupported_features`）以使用者可見文案宣稱不支援 `@grant`、`@require`、`@resource`，理由是安全與隱私；`hb4.java:16` 的序列化模型名為 `core.userscripts.UserScript`。
- **未驗證**：沒有找到可直接讀出的腳本注入 method body；`CoreWebView.java:469` 只回傳 `Set<jb4>`，`:635` 的 `n(str)` 只是一般 evaluate 入口，因此不能把「有 userscripts 目錄與設定」等同於「已證實完整沙盒隔離」。此外，`@grant`／`@require`／`@resource` 的「不支援」僅見於上述文案，沒有可讀出的封鎖實作，不能當成「已證實封鎖」。
- **判讀**：userscripts 的存在有功能旗標、設定頁與檔案模型支持；但外部程式碼匯入是否真的被擋下（目前僅文案宣稱），以及真正的 JS 沙盒邊界、同源政策與 WebView 介面暴露，均尚未驗證。

### 12. Monitors

- **代碼／資源證實**：`settings_lite_app_main.xml:15` 有 Monitors 入口；`strings.xml:218-219` 描述為追蹤網頁變更。
- **代碼／資源證實**：`EndpointRole.java:31` 定義 `MONITOR` 角色；`j72.java:35` 的 Manifest schema 有 `monitors` 欄位；`o72.java:166` 的 `toString` 也列出 `monitors`。
- **代碼／資源證實**：`oc2.java:12-17` 定義 `MonitorEntry`（title、url、publishedAtMs、description、imageUrl）；`v60.java:33-39` 有 `webMonitorUrl`、`webMonitorSelector` 等日誌欄位。
- **代碼／資源證實**：`gk2.java:795` 有 `saveMonitorEntry` 日誌與資料儲存路徑；`NotificationsWork.java:58-63` 是 WorkManager 週期工作入口。
- **未驗證**：沒有可讀出的 CSS selector 解析與差異比對 method body；`NotificationsWork` 只看到啟動 coroutine，沒有直接看到 monitor 抓取細節。
- **判讀**：Monitors 是完整產品線（schema、角色、entry、儲存、通知工作），但要在本專案落地，成本遠高於 URL 清理。

## 三選一比較：Monitors / userscripts / tracking_protection

| 方案 | 最小可行切片 | 主要痛點 | 代價 | 不做或暫緩的理由 |
|---|---|---|---|---|
| Monitors | 定期抓取頁面、以 CSS selector 取內容、比對差異、發通知 | 需要排程、網路抓取、HTML 解析、selector 錯誤處理、去重與通知權限；網站改版會造成誤報 | 高：新資料表、Worker、通知、錯誤重試、抓取禮貌與隱私政策 | 對「輕量 WebView 容器」是明顯擴張；沒有產品需求前不應引入背景網路與通知管線 |
| userscripts | 讓使用者載入 JS，在頁面執行 | 需要腳本儲存、安裝／編輯 UI、注入時機、來源限制、除錯與安全邊界；腳本可讀寫頁面與外洩資料 | 高：新的執行面與權限模型；`@grant`／`@require`／`@resource` 的「不支援」僅是文案宣稱，尚未證實有實際封鎖 | 攻擊面與維護成本最大；若沒有明確進階使用者需求，不應先做 |
| tracking_protection | 在 URL 進入 WebView 前移除已知追蹤參數 | 需要維護參數清單、處理重複參數與 fragment、避免破壞簽名或功能性查詢 | 低：純字串／Uri 轉換，無新資料表、無背景工作、無腳本 | 建議先做；以最小清單與可關閉開關控制風險，再視需求擴充 |

### 建議的最小改動方向

1. **先做 URL 清理，不做完整攔截器框架**：在本專案既有的 `WebViewActivity.loadURL(...)` 與 `CustomBrowser.shouldOverrideUrlLoading(...)` 路徑前處理 URL，避免引入 Hermit 的 OkHttp 攔截器架構。現有路徑可見 `app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java:602-620` 與 `:986-1014`。
2. **只移除明確追蹤鍵**：先採用小型、可審核的清單（例如 `utm_*`、`fbclid`、`gclid`、`msclkid`），不要一次複製 Hermit 的 88 項常數表。理由是清單越長，越容易誤刪網站功能性參數。
3. **保留原始 URL 作為顯示與分享依據**：清理只用於載入請求；網址列、書籤或分享若需要原始連結，應明確區分，避免使用者以為連結被永久改寫。
4. **做成可關閉的隱私選項**：預設值與 UI 文案要說清楚「只移除已知追蹤參數，不阻擋追蹤腳本或 Cookie」。這可避免把 URL 清理誤導成完整防追蹤。
5. **不在 base 複製 Hermit 的第三方實作**：Hermit 的 sandbox、content blocker、Monitors 與 userscripts 都牽涉額外模組與授權／維護邊界；本專案應以自身 `WebApp`／`GlobalSettings` 模型實作最小切片。

## 對 Native Alpha 的具體參照

- **既有可重用點**：`WebViewActivity.java:593-600` 已有 `Save-Data` header；`:227-228` 已有圖片封鎖；`:602-620` 是集中載入點；`:986-1014` 是 URL 攔截點。這些讓 URL 清理可以用很小改動接入。
- **既有 sandbox 邊界**：`WebViewActivity.java:147-163` 已使用 `ProcessPhoenix` 與 `WebView.setDataDirectorySuffix(...)`；因此本專案對「進程＋資料目錄」已有基礎，但這仍不等於每個 sandbox 都有獨立 CookieStore。若要談 Cookie 隔離，應另做專項驗證。
- **不要直接照搬的部分**：Hermit 的 21 個 sandbox Activity 宣告（Incognito＋20 彩色，另加主瀏覽器 Activity）與其 20 個彩色 sandbox process、`documentLaunchMode` 組合、content blocker 資料庫與 WorkManager 抓取、Monitors 的 schema 與通知工作、userscripts 的檔案與注入流程。這些都超出最小 URL 清理切片。

## 未驗證與後續驗證清單

- `j44.a(okhttp3.t)` 的實際 body 被 JADX 略過；需以 smali 或執行時測試確認參數移除語意。
- `frameless` 的實際視窗裝飾效果未在可讀 method body 中出現；需做 UI 執行驗證。
- 「21 色盤」（使用者提供）未驗證：沒有找到任何 21 筆 hex 的單一常數表。目前只證實 `xf3.java:16-20` 的 22 個具名 sandbox 身分（index 0–21），以及 manifest 的 21 個 sandbox Activity 宣告（Incognito＋20 彩色，另加主瀏覽器 Activity）。
- sandbox 是否有獨立 Cookie／storage partition 未證實；`CookieManager.getInstance()` 的全域呼叫不足以支持 Cookie 隔離宣稱。
- userscripts 的注入時機、sandbox 邊界與 WebView bridge 暴露面未證實；只能證實有功能旗標、檔案模型與 `@grant`／`@require` 限制文案。
- Monitors 的 selector 解析、差異比對與通知去重未證實；只能證實有 schema、entry、儲存與 Worker 入口。
- `block_malware` 的清單來源、規則格式與誤擋率未驗證；只能證實有請求攔截與可更新清單管線。
