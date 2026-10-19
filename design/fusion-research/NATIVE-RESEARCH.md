# Native Alpha 本基線（base）全鏈路研究

> 研究範圍：工作目錄 `NativeAlphaForAndroid-dev`，只讀 Android 源碼；未 build、未改 Android 源碼、未改模型設定。  
> 行號以本基線檔案為準。標記「證實」代表可由源碼直接核對；「推測」代表需以執行或外部資料再驗證。  
> 本文件不宣稱任何本地依賴版本是「最新版」；只記錄本基線實際宣告與使用位置。

## 0. 基線與授權

### 0.1 版本與建置基線（證實）

- `app/build.gradle:28`：`compileSdk 35`；`app/build.gradle:57-60`：`minSdkVersion 28`、`targetSdkVersion 35`、`versionCode 1520`、`versionName "1.5.2"`。
- `app/build.gradle:69-71` 與 `app/build.gradle:109-112`：Kotlin/Java 目標為 JVM 17。
- `build.gradle:15-16`：Android Gradle Plugin `8.8.2`、Kotlin `2.1.20`；`gradle/wrapper/gradle-wrapper.properties` 使用 Gradle `8.10.2`。
- `app/build.gradle:37-53`：三個 flavor：`extended`、`extendedGithub`、`standard`；`app/build.gradle:77-80` 另有 debug 後綴。
- `app/src/main/AndroidManifest.xml:18-28`：`App` 為 Application 類、`allowBackup=true`、`usesCleartextTraffic=true`、`fullBackupContent=true`。

### 0.2 GPL-3.0 證據（證實）

- 根目錄 `LICENSE:1-2`：`GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007`，全文為 GPLv3。
- `README.md:132-137`：明示 GPL 第 3 版或任何較新版本（`either version 3 ... or (at your option) any later version`）。
- `app/src/main/res/values/strings.xml:45`：App 內顯示「GNU General Public License - Version 3」並連到 `gpl-3.0.txt`。
- `app/src/main/java/com/cylonid/nativealpha/AboutActivity.kt:100-109`：About 頁的 License 項目開啟上述 GPL 連結。

> 注意：`LICENSE` 是 GPLv3 全文，但 README 採「GPLv3 或更新版本」表述；若日後要嚴格標示，應統一成單一 SPDX 表達式。此為授權標示一致性問題，不是本 base 的功能缺陷。

### 0.3 `work-runtime` / `jsoup`（證實）

- `app/build.gradle:126`：`org.jsoup:jsoup:1.14.1`。
- `app/build.gradle:158`：`androidx.work:work-runtime:2.10.0`。
- jsoup 唯一實際使用點是捷徑圖示/PWA manifest 抓取：`app/src/main/java/com/cylonid/nativealpha/ShortcutDialogFragment.java:40-43`（import）、`:217`、`:229`、`:236`（連線與取得 HTML/manifest），以及 `:268-289`（title、icon、apple-touch-icon 選擇器）。未見其他 jsoup 呼叫。
- WorkManager 只有初始化，沒有 Worker、WorkRequest 或排程使用：`app/src/main/java/com/cylonid/nativealpha/util/App.java:7-8`（import）、`:20-22`（`isInitialized` 後 `initialize`）。全 repo 搜尋未見 `Worker`、`OneTimeWorkRequest`、`PeriodicWorkRequest`。

> 推測：WorkManager 在此基線可能是歷史殘留或供尚未接入的功能使用；目前沒有可觀察的工作排程行為。jsoup 版本老舊與否不在此判定，應另以官方 release/security advisory 核對；本研究不把 `1.14.1` 稱為最新。

## 1. 資料模型與儲存格式

### 1.1 `WebApp` 模型（證實）

- `app/src/main/kotlin/com/cylonid/nativealpha/model/WebApp.kt:18`：`data class WebApp(var baseUrl: String, val ID: Int)`。
- `:20-59`：所有可持久化欄位，包括 `isActiveEntry`、`isOverrideGlobalSettings`、`containerId`、`isUseContainer`、`userAgent`、`adBlockSettings` 等。
- `:61-64`：建構時由 `baseUrl` 推導標題，並呼叫 `initDefaultSettings()`；`:124-129` 對 facebook 預設桌面 UA。
- `:74-80`：複製建構子先複製 `title`、override、container，再呼叫 `copySettings`。
- `:85-122`：`copySettings` 逐欄複製；注意 `:121` 直接共享 `adBlockSettings` 清單參考（不是深拷貝）。
- `:139-145`：`markInactive()` 只設 `isActiveEntry=false` 並刪捷徑；不是從陣列移除。

### 1.2 `DataManager` 儲存與載入（證實）

- `app/src/main/java/com/cylonid/nativealpha/model/DataManager.java:43-56`：SharedPreferences 檔名 `WEBSITEDATA`，鍵 `WEBSITEDATA`、`GLOBALSETTINGS`、`MAX_ID`、`NEXT_CONTAINER`。
- `:71-87`：單例；建構時 `websites` 空、`max_assigned_ID=-1`、預設 `GlobalSettings`。
- `:98-109`：`saveWebAppData()` 以 Gson 序列化整個 `websites` 清單到 `WEBSITEDATA`，並寫 `MAX_ID` 與 `NEXT_CONTAINER`。
- `:157-195`：`loadAppData()` 讀 `WEBSITEDATA`，註冊 `WebAppDeserializer`；`DataVersionConverter.getDataFormat` 判斷舊格式，必要時轉換；`:170` 比對舊/新清單並刪除變更 URL 的捷徑；`:171` 以新清單取代；`:175-176` 還原 ID/容器計數；`:178-193` 載入全域設定。
- `:207-218`：`saveGlobalSettings()` 把 `GlobalSettings` Gson 寫入 `GLOBALSETTINGS`。
- `:220-223`：`addWebsite` 直接 append 後存檔。
- `:250-271`：`getWebApp(i)` 每次都先 `loadAppData()`；若該 WebApp 未覆寫全域設定，則 `copySettings(globalWebApp)` 後回傳。
- `:273-277`：`replaceWebApp` 以 `webapp.getID()` 當索引覆寫，再存檔。
- `:289-305`：備份把 `WEBSITEDATA` 的全部 SharedPreferences 包成 `TreeMap`，連同 SHA-256 checksum 以 Java ObjectOutputStream + Base64 寫出。
- `:307-346`：還原 `edit()` 後的 `clear()`（`:314-315`）只是 Editor 暫存，直到 `:337 apply()` 才生效；`:316-317` 讀 checksum 與 map，`:320-321` 驗證 checksum，`:322-335` 逐鍵寫回，但只接受 Boolean/Float/Integer/Long/String，未知型別鍵靜默略過。

### 1.3 舊格式與版本轉換（證實）

- `app/src/main/java/com/cylonid/nativealpha/model/DataVersionConverter.java:17-21`：以 JSON 鍵 `allow_js` 判為 1000，以 `isAllowJs` 判為 1300；其他回 0。
- `:27-64`：legacy 到 1300 的鍵名映射。
- `app/src/main/java/com/cylonid/nativealpha/model/DataManager.java:348-357`：`LEGACY_DATA_FORMAT=1000` 時轉換；`1300` 或 default 原樣回傳。
- `app/src/main/kotlin/com/cylonid/nativealpha/model/deserializer/WebAppDeserializer.kt:14-23`：先以 Gson 反序列化，再呼叫 `patchDataVersion1500`。
- 同檔 `:29-55`：補 `adBlockSettings`；全域 WebApp（`ID == Int.MAX_VALUE`）缺鍵時給預設 adblock，最後截斷為最多 8 筆。
- `app/src/main/kotlin/com/cylonid/nativealpha/model/deserializer/GlobalSettingsDeserializer.kt:20-31`：若 `globalWebApp` 缺失造成 NPE，回退到全新 `GlobalSettings()`。

> 推測：資料格式仍以「1300」作為當前註解（`DataManager.java:354`），但 v1.5.0 的 adblock 補丁是 deserializer 層處理，沒有把格式常數升到 1500；因此格式版本與 App 版本不是一對一。

## 2. 建立、設定、繼承、啟動、導航、刪除、備份

### 2.1 建立（證實）

1. `MainActivity.kt:149-175` 的 `buildAddWebsiteDialog` 建立輸入框；`:155-157` 補 `https://`。
2. `:158-164` 以 `incrementedID`、`incrementedOrder` 建 `WebApp`，呼叫 `applySettingsForNewWebApp()`，再 `addWebsite()`。
3. `WebApp.kt:135-137`：新 WebApp 預設 `isOverrideGlobalSettings=false`，因此會繼承全域模板。
4. `MainActivity.kt:167-170`：可選建立捷徑，交給 `ShortcutDialogFragment`。
5. `DataManager.java:225-231`：`getIncrementedID()` 回傳目前清單大小，`getIncrementedOrder()` 回傳 active 數量 + 1。

> 風險（證實）：`max_assigned_ID` 只被讀寫，卻沒有在新增時遞增（`DataManager.java:106`、`:175`；全 repo 無其他賦值）。`getIncrementedID()` 使用 `size()`（`:225-227`），而本基線刪除是軟刪除、不移除清單元素（見 2.6），所以「新增→刪除→新增」在正常流程中 `size()` 仍遞增、**不能由代碼推出**重複 ID。真正被證實的耦合是 `replaceWebApp` 以 `webapp.getID()` 當陣列索引（`:273-277`）：只有 ID 恰等於清單索引時才正確，若資料經異常匯入或未來硬刪除使 ID 與索引不一致，就會寫錯槽位或丟出 `IndexOutOfBoundsException`。因此 ID/索引一致性是對異常資料與未來硬刪除的風險，不是正常流程已證實的缺陷。

### 2.2 設定（證實）

- `WebAppSettingsActivity.kt:37-44`：由 `INTENT_WEBAPPID` 取 ID；全域 WebApp 直接取 `settings.globalWebApp`，其餘用 `getWebAppIgnoringGlobalOverride(ID, true)`。
- `:50-52`：以 `WebApp(webapp!!)` 複製出可編輯物件，綁定到 `WebappSettingsBinding`。
- `:78-109`：儲存時，全域設定先關閉所有 WebApp 任務與 sandbox 程序，再寫 `globalWebApp` 並 `saveGlobalSettings()`；一般 WebApp 則關閉同 ID 任務、殺對應 sandbox 程序，再 `replaceWebApp()`。
- `app/src/main/res/layout/webapp_settings.xml:6-14`：Data Binding 變數 `webapp`、`activity`。
- 同檔 `:54-59`：重建捷徑按鈕；`:101-110`：覆寫全域設定開關；`:121-125`：詳細設定容器。
- 同檔 `:541-558`：Expert settings 開關與預設隱藏的 `sectionExpertSettings`；`:567-574`：可編輯 Start URL；`:584-590`：自訂 UA；`:648-653`：忽略 SSL 錯誤。
- `WebApp.kt:151-296`：各開關的 UI 連動（cookies、JS、desktop、UA、autoreload、expert、sandbox、override）。

### 2.3 全域設定的繼承（證實）

- `app/src/main/java/com/cylonid/nativealpha/model/GlobalSettings.kt:6-15`：全域設定包含 `globalWebApp`（`ID=Int.MAX_VALUE`）與全域快取/多指手勢/進度條等。
- `DataManager.java:250-263`：未覆寫時，讀取 WebApp 會以全域 `WebApp` 的設定覆蓋回傳物件。
- `DataManager.java:383-391`：載入後強制全域 WebApp 的 `isOverrideGlobalSettings=true` 且 `containerId=NO_CONTAINER`。
- `WebApp.kt:85-122`：繼承是逐欄複製；`adBlockSettings` 是共享參考。

> 推測：`copySettings` 直接共享 adblock 清單，若某處對回傳 WebApp 的 adBlockSettings 做就地修改，可能意外改到全域模板。本基線 UI 主要替換整個清單/寫回設定，未見直接就地修改該共享清單的明顯路徑，但這是值得加防禦性複製的點。

### 2.4 啟動（證實）

1. `WebAppListAdapter.kt:34-40`：列表項點擊呼叫 `openWebView`；`:48-50` 呼叫 `WebViewLauncher.startWebView`。
2. `WebViewLauncher.kt:16-27`：`startWebView` 用 `createWebViewIntent` 後 `startActivity`。
3. `WebViewLauncher.kt:44-65`：若 `containerId != NO_CONTAINER`，反射載入 `.__WebViewActivity_<id>`；否則載入 `WebViewActivity`；`:61` 放入 `INTENT_WEBAPPID`；`:62` 設定 data URI 為 `baseUrl + ID`；`:60` 生物辨識時排除最近任務。
4. `AndroidManifest.xml:41-52`：主 `WebViewActivity` 的設定；`app/build.gradle:169-191` 為 8 個 sandbox 產生 `.__WebViewActivity_0..7`，各自 `android:process=":web_sandbox_i"`。
5. `WebViewActivity.java:122-142`：`onCreate` 取 ID、`getWebApp`；`isBiometricProtection()` 為真時啟動 `BiometricPromptHelper`（成功回呼 `setupWebView()`、拒絕/取消回呼 `finish()`，`:137-139`），但 `:140` 在 `if/else` 內又**無條件**呼叫 `setupWebView()`；因此實際行為可能是不論生物辨識結果都先初始化一次 WebView，`finish()` 只是後續關閉。此為門控（gating）靜態疑點，需以模擬拒絕/取消/成功驗證「未提前載入頁面」且「只初始化一次」，不得直接宣稱生物辨識無效。
6. `:145-167`：判斷目前 process；主 process 遇到 sandbox WebApp 會用 `ProcessPhoenix` 重啟到新 process；sandbox process 會註冊 sandbox 並設定 WebView data directory suffix。
7. `:169-249`：載入 `full_webview`、選一般或 adblock WebView、設定 UA/JS/cookies/圖片/desktop/zoom，最後 `loadURL`。

> 注意（證實）：`WebViewActivity.java:180-187` 的 adblock 設定來源是 `settings.getGlobalWebApp().getAdBlockSettings()`，不是目前 `WebApp` 自己的 `adBlockSettings`；因此每-App 的 adblock 清單即使存在於模型中，本基線的 WebView 啟動路徑也不會使用它。

### 2.5 導航（證實）

- `WebViewActivity.java:602-622`：`loadURL` 檢查 `http://` 且 `isAllowHttp=false` 時顯示警告；使用者接受才設 `allowHttp=true`、`overrideGlobalSettings=true`、存檔並載入。
- `:987-1015`：`shouldOverrideUrlLoading` 先處理 `tel:`、`mailto:`；若 `isOpenUrlExternal` 且 host 不是 base host，交給外部瀏覽器；否則 `loadURL`。
- `:516-534`：返回鍵先 `wv.goBack()`；再按一次回首頁；第二次才 `moveTaskToBack`。
- `:342-361`：三指左右滑切換前/後 WebApp；二指左右滑前進/後退。
- `:431-508`：長按 context menu 的 forward/back/reload/copy URL/share URL/close/main menu。

> 風險（證實）：`:1007` 以 `url.contains(host)` 判斷是否外部連結，可能把 `https://evil.example/?next=bank.example` 誤判為站內；也可能把同站不同子網域判為外部。這是導航邊界判斷，不只是 UI 問題。

### 2.6 刪除（證實）

- `WebAppListFragment.kt:49-67`：右向左滑動呼叫 `item.markInactive(activity)`，再存檔；Snackbar 的 Undo 只把 `isActiveEntry=true` 並存檔。
- `WebApp.kt:139-145`：`markInactive` 設 `isActiveEntry=false`，並透過 `ShortcutIconUtils.deleteShortcuts` 停用捷徑。
- `ShortcutIconUtils.kt:10-24`：以捷徑 Intent 的 `INTENT_WEBAPPID` 比對，使用 `ShortcutManager.disableShortcuts`。
- `DataManager.java:238-247`：active 清單只納入 `isActiveEntry`，再依 `order` 排序。

> 結論（證實）：本基線的「刪除」是軟刪除，資料仍留在 `WEBSITEDATA`，清單大小不縮減、ID/索引不重排；沒有硬刪除 API。因此正常刪除不會讓 `getIncrementedID()` 重用舊 ID；被刪項目仍會進入備份內容。

### 2.7 備份／還原（證實）

- `SettingsActivity.kt:58-93`：匯出用 `ACTION_CREATE_DOCUMENT`，匯入用 `ACTION_GET_CONTENT`。
- `SettingsActivity.kt:109-151`：匯出先 `saveGlobalSettings()` 再 `saveSharedPreferencesToFile(uri)`；匯入成功後清 WebStorage、清 cookies、`loadAppData()`，回首頁顯示匯入結果。
- `MainActivity.kt:56-68`：`onNewIntent` 收到 `INTENT_BACKUP_RESTORED` 時更新列表並顯示重建捷徑對話框；`:110-147` 逐一提示重建捷徑。
- `DataManager.java:289-346`：備份/還原實作（見 1.2）。

> 風險（證實）：備份格式使用 Java 原生序列化（`ObjectOutputStream`/`ObjectInputStream`），檔案可攜性與跨版本相容性取決於 Java 類別結構；checksum 是對 `TreeMap.toString()` 計算（`:297`、`:318`），並非對檔案位元組簽章，也不具認證性。`prefEdit.clear()`（`:315`）只是 Editor 暫存，`apply()` 在 `:337` 才寫入，因此「apply 前解析/checksum 失敗」不會清空原資料（先前敘述有誤，特此更正）。實際問題是**型別／schema／業務資料驗證不足**：`:316-317` 的強制轉型只被 `catch (InvalidChecksumException | IOException | ClassNotFoundException)`（`:340`）涵蓋，未捕捉 `ClassCastException`；`:322-335` 對未知型別鍵靜默略過，但成功路徑仍會 clear+apply，造成部分鍵靜默遺失卻回報成功。

## 3. Views / XML 適配點

### 3.1 WebView 畫面（證實）

- `app/src/main/res/layout/full_webview.xml:2-12`：`ConstraintLayout` 內含 `FrameLayout id=webviewActivity`。
- `:13-20`：主 `WebView id=webview`，`forceDarkAllowed=false`。
- `:22-29`：adblock 用第二個 `WebView id=adblockwebview`，預設 `gone`；`:180-197` 依設定切換。
- `:31-39`：底部 5dp 水平 `ProgressBar`。
- `:40-47`：`anchorCenterScreen` 供 popup menu 定位。

> 適配點：任何 URL 清理/導航列或安全提示若要在 WebView 畫面呈現，應加在 `FrameLayout` 內，並避免覆蓋 `ProgressBar` 與 `anchorCenterScreen` 的定位假設。

### 3.2 設定畫面（證實）

- `webapp_settings.xml:16-26`：`CoordinatorLayout` → 垂直 `LinearLayout` → `ScrollView`（權重 99）→ 內容。
- `:39-47`：`ScrollView` 設定 `fillViewport=false`、`scrollbarStyle=outsideOverlay`。
- `:54-76`：重建捷徑、名稱欄位。
- `:121-125`：`sectionWebAppDetailSettings` 是所有細節設定的啟用/停用容器。
- `:550-558`：Expert 區塊預設 `gone`。
- `:665-686`：底部 Cancel/Save 按鈕列。

> 適配點：新增「URL 追蹤參數清理」開關時，最小落點是 `sectionWebAppDetailSettings` 內的隱私/資料區塊；若屬進階行為，也可放在 `sectionExpertSettings`。需同步在 `WebApp.kt` 加欄位與 `copySettings`，並在 `WebAppSettingsActivity` 的 Data Binding 生效。

### 3.3 列表與主畫面（證實）

- `activity_main.xml:9-21`：`AppBarLayout` + `Toolbar`；`:25-31` 浮動新增按鈕。
- `content_main.xml:9-13`：`FragmentContainerView` 直接指定 `WebAppListFragment`。
- `fragment_web_app_list.xml:7-18`：`DragDropSwipeRecyclerView`，左右滑動作與圖示由此設定。
- `web_app_list_item.xml:2-36`：每列高度 `@dimen/line_height`，標題 `btnWebAppTitle`，拖曳錨點 `dragAnchor`。
- `dimens.xml:3`：`line_height=60dp`。

> 適配點：URL 清理若要做「分享/複製時去除追蹤參數」，需改 `WebViewActivity.java:472-483` 的 copy/share，不應改列表 XML。若要做站內/站外導航提示，則在 WebView 層處理，避免污染列表項。

### 3.4 主題與元件（證實）

- `themes.xml:2-51`：Material3 Light NoActionBar，完整 light/dark 色票；`:49` 針對 API 35 關閉 edge-to-edge enforcement。
- `themes.xml:57-59`：WebView 主題只覆寫狀態列顏色。
- `webapp_settings.xml` 大量使用 `com.google.android.material.materialswitch.MaterialSwitch`（例如 `:92-98`、`:144-150`、`:541-548`），與 Material3 一致。
- `activity_toolbar_base.xml:7-13`：共用返回工具列 + `activity_content` 容器。

> 適配點：任何新設定列應沿用 `MaterialSwitch` 與既有 `TextAppearance.AppCompat.Small` 區塊標題，避免另造元件；深色模式由 `themes.xml`/`values-night` 既有色票處理。

## 4. 最小改動候選（<= 8）與驗收標準

以下皆為候選，不是已實作。優先順序以「風險低、可獨立驗收、不動 Android 源碼」為原則描述；若日後實作，仍需另行授權修改源碼。

### 候選 1：在導航層加入可選的 URL 追蹤參數清理

- 落點：`WebViewActivity.java:987-1015` 的 `shouldOverrideUrlLoading`，在 `tel:`/`mailto:` 與外部瀏覽器判斷之後、呼叫 `loadURL(view, url)` 之前，對站內 `request.getUrl()` 做清理；不要改 `loadURL` 的首頁/返回首頁呼叫，以免動到已儲存的 base URL。
- 最小做法：建立純函式（例如 `UrlSanitizer`）回傳清理後 URL；追蹤鍵採明確清單（如 `utm_*`、`fbclid`、`gclid`、`mc_eid`、`igshid`），不改 path、fragment、其他 query，也不改外部瀏覽器分支的原始 URL。
- 保留簽名 URL／登入：若 query 含 `sig`、`signature`、`token`、`expires`、`X-Amz-*`、`GoogleAccessId` 等簽章或授權鍵，直接原樣回傳；若移除後會使 URL 失去必要參數，也應回退原 URL。登入流程（OAuth `state`、`code`、`redirect_uri`）不得清理。
- 驗收：
  - 含 `utm_source` 的站內連結載入後 query 不含該鍵，且頁面正常。
  - 含 `sig`/`token` 的簽名 URL 位元組不變，下載/登入成功。
  - `tel:`、`mailto:`、外部瀏覽器分支行為不變；首頁與返回首頁的 base URL 不被清理。
  - 返回鍵、三指切換、分享/複製 URL 不因清理而崩潰；分享/複製需明確定義是否也清理（建議先只清理導航，避免破壞使用者複製原始連結的期待）。

### 候選 2：修正外部連結 host 判斷

- 落點：`WebViewActivity.java:1003-1011`。
- 最小做法：以 `Uri.parse(url).getHost()` 與 `webapp.getBaseUrl()` 的 host 做精確或明確子網域比對，不再用 `String.contains(host)`。
- 驗收：`https://evil.example/?next=bank.example` 被判為外部；`https://sub.bank.example` 依產品定義判定；`tel:`/`mailto:` 不受影響。

### 候選 3：強化 ID/索引一致性與「刪除」語意（已合併候選 5）

- 落點：`WebAppListFragment.kt:49-67`、`WebApp.kt:139-145`、`DataManager.java:225-231`、`:273-277`；`max_assigned_ID` 的持久化見 `:73`、`:106`、`:175`、`:220-231`。
- 最小做法：`replaceWebApp` 改用 ID 搜尋（線性查找或 ID→索引映射）而非直接以 `getID()` 當索引；`addWebsite` 時更新 `max_assigned_ID = max(max_assigned_ID, new_site.getID())`，載入時也以清單最大 ID 校正為單調遞增；刪除仍保留軟刪除，但 UI 文案明確標示「已移除，可由備份/Undo 恢復」。
- 注意（修訂）：本基線正常軟刪除不移除清單元素，`getIncrementedID()`（`size()`）不會因此產生重複 ID，故「新增→刪除→新增產生重複 ID」**不能由現有代碼推出**；不需要為此無端重構 ID 分配器。本候選聚焦於 `replaceWebApp` 對異常匯入／未來硬刪除的索引耦合，以及刪除語意文案。
- 驗收：餵入 ID 與索引不一致的匯入資料後，`replaceWebApp` 仍更新正確項目且不丟 `IndexOutOfBoundsException`；`max_assigned_ID` 在新增與載入後維持單調遞增；Undo 可恢復且捷徑可重建。

### 候選 4：補強備份還原的型別／schema 驗證與例外覆蓋

- 落點：`DataManager.java:307-346`（特別是 `:314-317`、`:322-337`）、`SettingsActivity.kt:131-150`。
- 最小做法：`clear()` 只是 Editor 暫存、`apply()` 才寫入（`:315`、`:337`），失敗時原資料不會被清空，因此不需要「原子化」重寫；改為在 `apply()` 前先驗證：以安全轉型（`instanceof`/`TreeMap<String,Object>`）取代裸轉型以避免未捕捉的 `ClassCastException`；將 `catch` 擴大涵蓋 `RuntimeException` 並回傳失敗；對未知型別鍵明確回報，不再靜默略過（`:322-335`）；把 checksum 定位為完整性提示而非認證（`:297`、`:318`）。
- 驗收：餵入含未知型別或裸轉型失敗內容的備份時，函式回傳失敗、UI 顯示訊息、不接受部分還原且不崩潰；合法備份可完整還原。

### 候選 5：已合併至候選 3

- 原「`max_assigned_ID` 持久化一致性」與原「ID 分配」屬同一條根因（ID 語意與索引耦合），故合併處理，不另列獨立候選。修訂後請以候選 3 為準。

### 候選 6：補上 jsoup 抓取的逾時與錯誤邊界

- 落點：`ShortcutDialogFragment.java:217`、`:229`、`:236`。
- 最小做法：對三次 `Jsoup.connect(...)` 設定 `timeout`、限制 redirect 與 content type；失敗時走既有 `prepareFailedUI()`，不要讓 favicon 執行緒長時間卡住。
- 驗收：無網路、慢速站、非 HTML manifest 時，5 秒內回到失敗 UI 或預設圖示；正常站仍可取得 title/icon。

### 候選 7：移除或正式接入 WorkManager

- 落點：`App.java:20-22`、`app/build.gradle:158`。
- 最小做法：若無排程需求，移除初始化與依賴；若要做背景 adblock 更新，新增具名 Worker 與约束，並以 `enqueueUniquePeriodicWork` 管理。
- 驗收：App 啟動不因 WorkManager 初始化增加可見延遲；若保留，log 可觀察到唯一工作且不重複排程。

### 候選 8：補強 WebView 設定的可測試性與安全預設

- 落點：`WebViewActivity.java:214-249`、`full_webview.xml:13-29`。
- 最小做法：把 `WebSettings` 套用邏輯抽成可測試函式；確認 `isSafeBrowsing` 欄位與 `setSafeBrowsingEnabled(false)` 的關係（目前 `WebApp.isSafeBrowsing` 存在但未在 `WebViewActivity` 使用），避免 UI 顯示與實際行為不一致。
- 驗收：一般/桌面/自訂 UA、JS 關閉、圖片封鎖、第三方請求封鎖、adblock 切換皆有對應測試或可重現檢查表；不會把 `isSafeBrowsing` 誤當成已生效。

## 5. 結論摘要

- 本基線的 WebApp 生命週期是「SharedPreferences + Gson 全量清單」；建立、設定、繼承、啟動、導航、軟刪除、備份都有明確單一路徑，主要結構風險是 `replaceWebApp` 的 ID/索引耦合（對異常匯入與未來硬刪除，非正常流程已證實缺陷）與備份還原的型別／schema 驗證不足（非原子 clear 問題）。
- URL 追蹤參數清理的最佳單一適配點是 `WebViewActivity.shouldOverrideUrlLoading`；清理器必須白名單式保留簽章/授權/OAuth 參數，且不應改動 `tel:`/`mailto:` 與外部瀏覽器分支。
- Views/XML 的改動應集中在 `webapp_settings.xml` 的既有 section 與 `WebViewActivity` 的 context menu；列表與主題不需為此重構。
- `jsoup 1.14.1` 僅用於捷徑圖示抓取；`work-runtime 2.10.0` 目前只有初始化、沒有 Worker。兩者都應以「本基線版本」描述，不宣稱最新。
