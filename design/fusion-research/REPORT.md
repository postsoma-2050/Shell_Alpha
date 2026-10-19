# Native Alpha × Hermit 融合版 App 開發指南（定稿 v1.0）

> 文件性質：設計與實作基線，不代表下列功能已存在。本文只依本地基線源碼與研究筆記描述接點；本次未修改 Android 業務源碼、未執行 Gradle build、未製作 HTML 原型。
>
> 產品地基：Native Alpha 的 `WebApp`／per-app 設置／全域模板、`WebViewActivity`、8 個 `:web_sandbox_i` 進程、指紋鎖與 adblock、`DataManager` 持久化。專案繼續遵守 **GPL-3.0**，維持 Android Views/XML，不切換 Compose。
>
> 證據來源：`design/fusion-research/NATIVE-RESEARCH.md`、`HERMIT-RESEARCH.md` 與本地源碼；行號以本基線為準。Hermit 的「21 色盤」是既定設計方向，但研究未找到可直接複用的 21 筆 hex；因此本版只從中選 8 個 Material 標準色，落在 Native Alpha 自有的 `container_color_0..7` 資源，不宣稱已還原 Hermit 原色值。

## 階段一：學習筆記

### 1. Native Alpha 的全鏈路

**建立與模型。** `MainActivity.onCreate()` 把浮動按鈕接到新增流程（`MainActivity.kt:42-44`）；`buildAddWebsiteDialog()` 在沒有協定時補 `https://`，以 `DataManager.incrementedID/incrementedOrder` 建立 `WebApp`，再呼叫 `applySettingsForNewWebApp()` 與 `addWebsite()`（`MainActivity.kt:149-175`）。`WebApp` 是新站的核心資料模型，目前已有網址、標題、cookie、JS、省流量、圖片、adblock、安全瀏覽、沙盒、獨立程序、指紋鎖、外觀與排序等欄位（`WebApp.kt:18-59`）。新站預設跟隨全域（`WebApp.kt:135-137`）。

**設定與繼承。** `WebAppSettingsActivity.kt:37-76` 以 `INTENT_WEBAPPID` 載入實際生效的 `WebApp`，再複製成可編輯物件。全域模板位於 `GlobalSettings.globalWebApp`，ID 為 `Int.MAX_VALUE`（`GlobalSettings.kt:6-16`）。每站以 `isOverrideGlobalSettings` 表達二元語意：自訂時保留自己的值；跟隨時由 `DataManager.getWebAppIgnoringGlobalOverride()` 呼叫 `copySettings()` 套入全域模板（`DataManager.java:250-270`、`WebApp.kt:85-121`）。UI 也以同一語意禁用細節區（`WebApp.kt:292-295`），不建立「逐欄繼承／覆寫／預設」三態模型。

**存儲。** `DataManager` 是單例入口，底層為 `SharedPreferences("WEBSITEDATA")`；整份 `websites` 清單與 `GlobalSettings` 都以 Gson JSON 存回同一個 preference 檔，並保存 `MAX_ID`、`NEXT_CONTAINER`（`DataManager.java:43-56`、`98-108`、`157-195`、`207-217`）。這是現階段最容易嫁接新欄位的位置，也意味著任何新模型都必須考慮舊 JSON 的缺欄位、型別與版本轉換。

**啟動與沙盒。** `WebViewLauncher` 依 `containerId` 反射啟動 `.__WebViewActivity_<id>`，未用 container 時啟動一般 `WebViewActivity`；兩者都靠 `INTENT_WEBAPPID` 找回資料。主 Manifest 目前是 `documentLaunchMode="intoExisting"`（`AndroidManifest.xml:41-52`）；`app/build.gradle:169-191` 生成 8 個 `.__WebViewActivity_0..7`，各自使用 `:web_sandbox_i` 與同一模式，`:204-220` 再從原始 Activity 複製出 8 份類別。這是原生架構的編譯期生成鏈，修改 `WebViewActivity` 或 Manifest 時要同步考慮生成物。

**WebView 執行期。** `WebViewActivity.onCreate()` 取得 WebApp 並設定內容（`:122-132`）；沙盒路徑會呼叫 `WebView.setDataDirectorySuffix()`（`:145-163`）；接著設定 cookie、第三方 cookie、JS、圖片與其他 WebView 能力（`:210-249`）。省流量已有兩個底層訊號：`isSendSavedataRequest` 會加入 `Save-Data: on`（`:593-598`），`isBlockImages` 會呼叫 `setBlockNetworkImage(true)`（`:227-228`）。頁面進度由 `onProgressChanged()` 更新進度條（`:842-854`）。指紋鎖沿用 `BiometricPromptHelper` 與 per-app `isBiometricProtection`，不另做第二套認證。

**載入與外部 Intent。** 主動載入集中於 `loadURL()`（`:602-622`），站內導航集中於 `CustomBrowser.shouldOverrideUrlLoading()`（`:987-1015`）。目前 `tel:`、`mailto:` 會直接交給外部 App；`isOpenUrlExternal` 開啟後，以 `url.contains(host)` 判斷站外。此處也適合接入 tracking parameter 清理與更細緻的 `allow_app_launch`／`allow_app_installs`，但應改成比較正規化後的 host/origin，不能沿用子字串判斷。

**安全瀏覽與內容阻擋。** `WebApp.isSafeBrowsing` 已存在（`WebApp.kt:45`），但 `setupWebView()` 目前無條件呼叫 `setSafeBrowsingEnabled(false)`（`WebViewActivity.java:214`）；因此 Privacy 的 `block_malware` 必須先修補這條漏接。既有 adblock 由 per-app `isUseAdblock`、`adBlockSettings` 與 `shouldInterceptRequest()` 構成（`WebApp.kt:30,59`、`WebViewActivity.java:912-925`），是內容阻擋的基礎。

**排序、刪除與圖示。** 主畫面目前使用 `LinearLayoutManager` 與垂直拖拽清單（`WebAppListFragment.kt:28-38`）；adapter 目前只綁定標題與點擊開站（`WebAppListAdapter.kt:34-40`）。左滑會呼叫 `markInactive()` 軟刪除並刪除捷徑（`WebAppListFragment.kt:49-67`、`WebApp.kt:139-145`），拖放後依顯示順序寫回 `order`（`WebAppListFragment.kt:82-100`）。`ShortcutDialogFragment.onDestroy()` 與 `onCreateDialog()` 管理 favicon 抓取畫面（`ShortcutDialogFragment.java:90-159`），`loadBitmap()` 只把解碼後的 `Bitmap` 留在對話框內（`:162-176`）。主列表若要直接顯示網站圖示，必須把縮小後的 bitmap 存成 app 私有資產並把可攜識別寫進 `WebApp`；不能依賴系統 shortcut 圖示。

**備份。** `SettingsActivity.kt:58-93,109-151` 以 SAF 匯出／匯入；`DataManager.saveSharedPreferencesToFile()` 與 `loadSharedPreferencesFromFile()` 目前使用 Base64 包裝的 Java `ObjectOutputStream/ObjectInputStream`（`DataManager.java:289-346`）。checksum 只能驗證內容一致，不是簽章或信任來源；新格式應改為版本化 JSON，逐鍵白名單解析，失敗時不套用半份資料。

### 2. Hermit 決策與痛點對照

| Hermit 決策 | 解決的痛點 | Native Alpha 的融合方式 |
|---|---|---|
| 具名 sandbox 與顏色身份 | 多個網站身分容易混淆，不知道當前落在哪個隔離容器 | 保留 Native 的 8 個 container；每格顯示 container 色圓點，顏色只代表視覺身份與隔離邊界，不代表已證實獨立 CookieStore |
| 主畫面以圖示呈現 | 長名稱清單難掃描，常用站要快速點擊 | 改為 2–4 列自適應網格，favicon 為主、名稱為輔，搜尋仍能精確篩選 |
| per-app 主題色 | 網頁不只是一個無差別 WebView，使用者需要快速辨認當前網站 | `WebApp.themeColor` 驅動工具列、狀態欄、進度條與彩色捷徑 |
| 21 種命名色／Material 色 | 色相是可辨識的身份語言，但過多顏色會失去穩定映射 | 固定 8 個 container 顏色，不因排序、主題或網站 favicon 改變 |
| Behavior / Privacy / Theme 分區 | 設定太多、同名開關混在一起，使用者不知道風險落點 | Native 單 App 收斂為 Behavior / Privacy / Appearance；Global Settings 收斂為三個產品層級分區 |
| 省流量與阻擋圖片 | 行動網路昂貴，單純送 `Save-Data` 又不能保證伺服器遵守 | 用一個 `data_saver_mode` UI 主開關，底層同步寫入既有 `isSendSavedataRequest` 與 `isBlockImages` |
| frameless 與下拉刷新 | 常駐網站需要更像獨立 App，也需要快速重整 | frameless 切換 WebView 外框；`SwipeRefreshLayout` 包住主 WebView，不重寫導航 |
| tracking parameter 移除 | 分享連結常帶追蹤碼，但完整攔截器成本與誤傷風險高 | 僅清理 7 個明確查詢鍵，預設關閉，OAuth／簽名 URL 整段略過 |
| Monitors | 使用者需要知道非工具型網站是否更新，又不願每天手動巡站 | 用 WorkManager 最低 15 分鐘週期、Jsoup selector 與 hash 比對，先驗證單站更新通知 |
| userscripts | 少數進階使用者需要局部改頁，但任意腳本會擴大攻擊面與支援成本 | 最後做 per-app 本地腳本，只在頁面載入後 `evaluateJavascript`，禁止外部 `@require/@resource` 匯入 |

由此得到的架構原則是：**主畫面負責找網站，color/container 負責辨識身分，單 App 設定負責風險隔離，Global Settings 負責預設與批量操作，Monitors 只做可驗證的背景差異，userscripts 是最後才開的擴充面。**

## 階段二：融合版設計

### 2.1 產品定義

這不是通用瀏覽器，也不是網站行銷頁。它是一個**以 WebApp 為單位的個人網站工作台**：使用者用圖示網格管理常用網站，用 container 顏色辨認隔離身分，用 per-app 設定處理行為與隱私，用 Monitors 追蹤少數重要頁面，再以本地 JSON 備份整個工作區。核心承諾是「網站像獨立 App 一樣被啟動、配置、隔離與辨識」，不是「取代 Chrome 的全部功能」。

### 2.2 信息架構

```text
Home / WebApp Grid
├─ Search（只過濾本地 WebApp）
├─ Create（URL → favicon → container）
├─ WebApp tile → WebViewActivity
├─ Long press / drag → 重排
└─ Hamburger → Global Settings

WebApp Settings
├─ Behavior：frameless、pull-to-refresh、data saver、JS、外部開啟等
├─ Privacy：cookies、adblock、block_malware、tracking_protection、App launch/install
└─ Appearance：themeColor、dark mode、其他視覺顯示

Global Settings
├─ Website Presets：global WebApp、adblock、追蹤參數預設
├─ Interface & Gestures：toolbar position、swipe_gestures、search engine、default browser
└─ Data & Backup：JSON export/import、clear cache/cookies
```

### 2.3 三個關鍵界面

#### A. 主屏：WebApp Grid

- 頂部是一列固定的 Material Top App Bar：左側漢堡按鈕直接進 Global Settings，中間是**藥丸搜尋框**，右側不塞次要動作。搜尋即時比對 `title`、host 與可讀的 base URL；無結果時顯示「沒有符合的 WebApp」與清除搜尋。
- 清單改為 `DragDropSwipeRecyclerView` 的 `GRID_WITH_HORIZONTAL_DRAGGING`，以最小格寬 104dp 自適應：手機常規寬度 2 列、中寬 3 列、平板 4 列；保留拖拽排序、左滑刪除與右滑設定。格與格使用 12–16dp 間距，避免 favicon 與文字擠在一起。
- 每格結構固定為：置中的方形 favicon、單行名稱、名稱下方的小圓點。圓點取 `containerId 0..7` 對應色；`NO_CONTAINER` 使用中性空心點。圓點旁以 content description 宣讀「Container 3, Teal」之類的語意，不以顏色作唯一訊息。
- 按下圖示直接啟動；長按或拖拽握把進入重排；右滑進設定，左滑軟刪除並保留 Snackbar Undo。網站名稱從 `ShortcutDialogFragment` 取得的標題可覆寫，但網域仍保留在 accessibility 描述與搜尋資料中。
- favicon 應在捷徑建立或抓取成功後正規化為小尺寸 PNG，存入 app 私有目錄，並在 `WebApp` 保存相對檔名。`ShortcutDialogFragment` 目前的 in-memory bitmap 必須在此時落盤；JSON 備份以縮小圖示的 base64 內容一併攜帶，還原時重建私有檔，避免把主機絕對路徑寫進資料。
- 右下為帶文字的 `ExtendedFloatingActionButton`，label 固定「+ Create」。新增流程沿用現有 URL 對話框，建立後預設跟隨全域；若網站有 favicon，先顯示在格中，抓取失敗才顯示首字母佔位圖。

**八個 container 色。** 使用 Hermit 色彩語彙中的 Blue、Green、Orange、Purple、Teal、Pink、Amber、Indigo，分別固定映射 0–7，放入本地 Material 色資源。實作時保留 Hermit 的命名語彙（Amber、Aquamarine、Blue、Brick、Brown、Crimson、Cyan、Green、Indigo、Lavender、Lime、Olive、Orange、Pink、Purple、Red、Tangerine、Teal、Violet、Yellow），但只公開這 8 個可選 container 色。文字與圖標依底色亮度切換深／淺色，圓點要有內描邊，確保在淺色與深色主題上都可辨識。

#### B. 單 App 配置

頁首顯示 favicon、網站名稱、host 與 container 色點；下方保留「跟隨全域／此 App 自訂」開關。關閉自訂時，顯示實際全域值並禁用細節控制，沿用 `Utility.setViewAndChildrenEnabled()` 的既有互動，不新增逐欄三態。

- **Behavior**：frameless、pull-to-refresh、data saver（單一主開關串聯 `isSendSavedataRequest` 與 `isBlockImages`）、JavaScript、desktop UA、頁面還原。frameless 僅控制 WebView 的外框與系統列；下拉刷新只處理頂層頁面，表單正在輸入或有 overlay 時不可搶手勢。
- **Privacy**：cookie、第三方 cookie、adblock、`block_malware`、`tracking_protection`、相機／麥克風／位置權限、`allow_app_launch`、`allow_app_installs`。`block_malware` 直接接既有 `isSafeBrowsing` 並修補「欄位存在但 WebView 永遠關閉」的漏接，不新增同義欄位。
- **Appearance**：`themeColor`、深色模式、時段深色、進度條顯示。色票選擇後即時預覽 toolbar/status bar；另提供「跟隨 container 色」或「自訂網站色」兩種來源，`themeColor` 本身仍保存在 WebApp，因此跟隨全域時可被複製。
- `allow_app_launch` 只允許 `tel:`、`mailto:`、地圖等外部處理器，`allow_app_installs` 另行控制 `market:`、安裝器與 APK 下載流程。兩者預設謹慎，任何外部意圖都顯示目標 App，不把「允許開站外連結」與「允許安裝」混成同一開關。

#### C. Global Settings

- **Website Presets**：全域 WebApp 預設、adblock 清單入口、tracking_protection 預設值、容器色與 favicon 抓取策略。這裡改的是「跟隨全域」的 App 所看到的預設。
- **Interface & Gestures**：`toolbar_position`（Top / Off）、`swipe_gestures`（前後導航、刷新、切站）、自定義搜索引擎、`set_as_default_browser`。搜索引擎保存名稱、搜尋 URL 模板與測試結果；不得把搜尋查詢送到本地主屏搜尋框以外的地方。
- **Data & Backup**：`backup_and_sync` 在 v1 僅做本機 JSON 匯出／匯入與 Android SAF 選擇位置；不建立帳號或雲端後端。匯入前顯示 schema 版本、App 數量與圖示數量；解析失敗保留舊資料，成功後一次提交並清快取。

### 2.4 tracking_protection、Monitors、userscripts 的優先序

三者依風險與驗證成本排序，而不是依展示效果排序。

| 優先 | 功能 | 最小落地法 | 驗收門檻 |
|---|---|---|---|
| 1 | `tracking_protection` | 在本機新增純函式 URL policy，只移除 7 鍵：`utm_source`、`utm_medium`、`utm_campaign`、`utm_term`、`utm_content`、`fbclid`、`gclid`。接在 `loadURL()` 與最上層 `shouldOverrideUrlLoading()`，不改 `baseUrl`，預設關閉。 | 未知參數、fragment、重複未知鍵保留；遇到 OAuth `state/code/access_token/id_token` 或 `sig/signature/X-Amz-Signature` 整段略過；可一鍵重載原始 URL；不處理所有子資源與 POST。 |
| 2 | Monitors MVP | 每筆 monitor 存 `webAppId`、URL、CSS selector、`lastHash`、`lastCheckedAt`、`failureCount`；既有 `work-runtime:2.10.0` 建週期 Worker，既有 `jsoup:1.14.1` 抓頁與取 selector 文字；正規化後算 SHA-256，首次只建基準，變更才用 `NotificationUtils` 通知。 | 單站可建立、可停用、可刪除；沒有變更不通知；抓取失敗指數退避並保留舊 hash；通知可開回對應 WebApp；最低週期 15 分鐘。 |
| 3 | userscripts | 每站保存一份本地腳本字串，頁面進入 `onPageFinished()` 後以 `evaluateJavascript()` 注入；明確禁止 `@grant`、`@require`、`@resource` 與外部來源 URL，不暴露 native bridge。 | 只接受使用者直接貼上或本地檔案；限制大小與執行次數；停用後下一頁立即不注入；UI 明示腳本與網站同頁執行，不是強沙盒。 |

`tracking_protection` 先做，因為它只需一個可單測的 URL 轉換與兩個既有 WebView 接點。Monitors 是新旗艦，但涉及排程、網路、HTML 容錯與通知去重，必須先完成設定、持久化與穩定 ID 的基礎。userscripts 最後做，因為 `evaluateJavascript` 並沒有真正隔離 DOM、cookie 或同源網路能力；把限制寫在 UI 文案並不足以形成安全邊界。

### 2.5 刻意不做

1. **不做通用瀏覽器分頁、歷史同步與書籤漫遊。** 這些會把 WebApp 工作台拉回瀏覽器競品，並引入大量導航狀態。
2. **不做雲端帳號、跨裝置即時同步。** v1 的 sync 只指可攜 JSON；沒有後端、衝突合併與帳號隱私面。
3. **不做可訂閱的完整 content blocker 規則引擎。** 保留 Native Alpha 現有 adblock 與 `isSafeBrowsing` 修補，不複製 Hermit 的更新清單、OkHttp 攔截器與誤擋治理。
4. **不做 userscript 全兼容層。** 不支援 `GM_*`、`@require/@resource`、跨站自動更新或任意 bundle；v1 只接受少量、可見、可停用的本地腳本。
5. **不宣稱已做完整 Cookie/storage 隔離。** 8 個 container 是既定進程／資料目錄與視覺身份；若日後要升級為 partition，需另做 Android WebView 能力與跨版本驗證。

## 階段三：優化建議

以下正好對應藍圖 0→6，共 7 條；每條都先列可落地接點，再說明對應學到的模塊與排序理由。

### 0. 先重做主屏外殼與圖示資料

- **改動點（精確接點）**：`MainActivity.kt` 的 `MainActivity.onCreate()` `30-44`、`personalizeToolbar()` `76-82`、`onCreateOptionsMenu()/onOptionsItemSelected()` `84-108`、`buildAddWebsiteDialog()` `149-175`；`WebAppListFragment.onViewCreated()` `28-38`；`WebAppListAdapter.onBindViewHolder()` `34-40`；`ShortcutDialogFragment.onCreateDialog()` `120-159` 與 `loadBitmap()` `162-176`；`WebApp.kt:18-59` 增加 favicon 識別與 theme 欄位；`DataManager.saveWebAppData()` `98-108` 負責新欄位的持久化。將清單 layout manager／orientation 改為 grid 與 `GRID_WITH_HORIZONTAL_DRAGGING`，把頂部改成搜尋藥丸、漢堡與 `ExtendedFloatingActionButton`，並在 favicon 取得後落盤。
- **學到什麼**：`MainActivity` 是入口與新增流程，`WebAppListFragment` 是拖拽／滑動控制器，`WebAppListAdapter` 是資料到格子的綁定點，`ShortcutDialogFragment` 是 favicon 的唯一候選來源，`DataManager` 是讓圖示跨重啟存在的持久化層。這五段合起來才是一條完整主屏鏈路。
- **為什麼先做它**：所有後續功能都需要「找到並打開正確 WebApp」；先建立穩定主屏、favicon、container 色點與本地搜尋，才不會讓後面的設定與 Monitors 漂在空中。這也是最能直接改善日常使用感的部分。

### 1. 嫁接色彩身份與獨立 Recents

- **改動點（精確接點）**：`WebApp.kt:18-59` 新增 `themeColor`，`WebApp.copySettings()` `85-121` 同步複製；`WebViewActivity.onCreate()` `122-132`、`setupWebView()` `145-180`、`onProgressChanged()` `842-854` 套用狀態欄、工具列與進度色；`ShortcutDialogFragment.onCreateDialog()` `120-159` 及 `addShortcutToHomeScreen()` 生成彩色捷徑；`AndroidManifest.xml:41-52` 與 `app/build.gradle:178-191` 把 `documentLaunchMode` 改為 `always`。八色資源由 `containerId` 解出，不新增第二套身分欄位。
- **學到什麼**：`WebApp` 是跨 Activity、全域模板、SharedPreferences 與捷徑的共同資料模型；`copySettings()` 漏掉欄位便會產生「跟隨全域卻沒有主題」的假象。`WebViewActivity` 的 setup/progress 是動態套色點，`documentLaunchMode` 只控制 Recents 文件任務語意，不能當 Cookie 隔離證據。
- **為什麼先做它**：主屏完成後，使用者可立即找到站；色彩讓 8 個 container 從抽象 ID 變成可辨識的視覺身份。這一步同時為 Monitors 通知與快捷啟動提供一致的顏色語彙。

### 2. 重組設定並接通 Behavior/Privacy 管線

- **改動點（精確接點）**：`WebAppSettingsActivity.kt:37-76` 保持「取實際值→複製→編輯→儲存」流程；`webapp_settings.xml` 的現有 `sectionSecurity`、`sectionCookies`、`sectionDarkmode`、`sectionDatasaving` 等區塊重排成 Behavior / Privacy / Appearance；`WebApp.kt:292-295` 保留 `sectionWebAppDetailSettings` 的啟用規則；`global_settings.xml:40-163` 將 `sectionGeneral`、`sectionBackup` 等選項分成網站預設／介面與操作／資料與備份。新增或改接欄位時同步更新 `WebApp.kt:18-121`。`WebViewActivity.setupWebView()` `210-249` 把無條件 `setSafeBrowsingEnabled(false)` 改接 `isSafeBrowsing`，由 `data_saver_mode` 原子寫入 `isSendSavedataRequest` 與 `isBlockImages`；`hideSystemBars()` `623-656` 實作 frameless；主 WebView 外層用 `SwipeRefreshLayout` 並在 `onPageFinished()` `895-902` 結束刷新；`shouldOverrideUrlLoading()` `987-1015` 加入 `allow_app_launch`／`allow_app_installs`，分流 `tel:`、`mailto:`、`market:`。`GlobalSettings.kt:6-16` 僅擴充模板與全域開關，不改變 `isOverrideGlobalSettings` 語意。
- **學到什麼**：Native 的設定由 `WebAppSettingsActivity`、`WebApp` 欄位、GlobalSettings 模板與 Data Binding 共同組成；只改 XML 分區不會讓功能生效。`WebViewActivity.setupWebView()` 是欄位落地的門戶，`setSafeBrowsingEnabled(false)` 是「欄位有、管線未接」的例子；`initCustomHeaders()` `593-598`、`setBlockNetworkImage()` `227-228`、`hideSystemBars()` 與 `shouldOverrideUrlLoading()` 分別對應省流量、外框及外部 Intent 的真實邊界。
- **為什麼先做它**：新設定若直接塞進舊頁會失去可發現性，欄位若只停在模型中又會形成假 UI。先固定三分區並把 Behavior/Privacy 接到 WebView，可直接改善安全、資料量與外站行為，也是後續 tracking、Monitors 與 userscripts 的必要前置。

### 3. 先落 tracking_protection 的最小切片

- **改動點（精確接點）**：新增 `TrackingUrlPolicy` 純函式與 URL parser；在 `WebViewActivity.loadURL()` `602-622` 及 `CustomBrowser.shouldOverrideUrlLoading()` `987-1015` 的最上層 HTTP(S) 導航套用。`WebApp.kt:18-59` 加入 `isTrackingProtection`，`copySettings()` `85-121` 複製它；`global_settings.xml:40-163` 放開關與測試預覽。7 鍵固定為 `utm_source/utm_medium/utm_campaign/utm_term/utm_content/fbclid/gclid`，其他 query 與 fragment 逐字保留。
- **學到什麼**：Native 已有集中載入點，不需要照搬 Hermit 的 OkHttp interceptor。`loadURL()` 管主動載入，`shouldOverrideUrlLoading()` 管導航；但 WebView 不會把所有 POST、redirect、subresource 都交給這兩個方法，因此只能承諾「已知導航 URL 的保守清理」。原始 `baseUrl` 不可被覆寫，OAuth `state/code/token` 與簽章參數必須整段略過。
- **為什麼先做它**：輸入與輸出都是 URL，無背景工作、無 HTML parser、無腳本執行面；先用少量明確鍵換取使用者可見的隱私價值，也為之後設定語意與備份欄位建立先例。

### 4. 一次完成小改動組

- **改動點（精確接點）**：`MainActivity.personalizeToolbar()` `76-82` 與選單處理 `84-99` 加入 `toolbar_position`、自定義引擎與 default-browser 入口；`SettingsActivity.kt:58-93,109-151` 沿用 SAF 匯出／匯入，但把 `DataManager.java:289-346` 的 `ObjectOutputStream/ObjectInputStream` 換成版本化 JSON schema；`WebApp.kt:18-121` 增加 swipe gesture 與 engine 關聯欄位，確實進 `copySettings()`。`AndroidManifest.xml:33-40` 視需要在 launcher intent-filter 外提供 VIEW intent；`backup_and_sync` 的 UI 只提供本機匯出／匯入。
- **學到什麼**：備份風險不在檔案選取器，而在 `DataManager` 的序列化格式與全域偏好清單的信任邊界。`MainActivity` 是 top-level 操作入口，`WebApp` 是 per-site 行為資料，`GlobalSettings` 則適合保存搜尋引擎清單與手勢預設。JSON 必須以 schema version、白名單型別與原子提交保護舊資料。
- **為什麼先做它**：這些功能彼此共享 UI 與資料欄位，且不會擴大 WebView 攻擊面；在 Monitors 前把 schema 與備份定穩，可避免未來的 monitor、favicon、腳本資料再次被舊 ObjectStream 鎖住。

### 5. Monitors 最小驗證 MVP

- **改動點（精確接點）**：利用 `app/build.gradle:126` 的 `org.jsoup:jsoup:1.14.1` 與 `:158` 的 `androidx.work:work-runtime:2.10.0`；新增 `Monitor` model、`MonitorDao`（若採輕量 JSON，先在 `DataManager.java:98-108,157-195` 的 JSON 中加入版本化陣列）、`MonitorWorker` 與 `MonitorScheduler`。Worker 使用 Android WorkManager `PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS` 的 **15 分鐘**最小週期，用 Jsoup 載入 URL、`Document.select(cssSelector)`、取文字並 hash；變更時呼叫既有 `NotificationUtils`，通知 action 帶 `INTENT_WEBAPPID` 開啟對應站。抓取要設定 timeout、User-Agent、重試與 `lastHash`，首次結果只建基準。
- **學到什麼**：Native 本身已有 WorkManager 與 NotificationUtils 的基礎，但沒有 Monitors 資料模型；`DataManager` 的整份 JSON 是目前最小持久化路徑，適合先驗證，不適合長久承載大量歷史。Jsoup 已有 `ShortcutDialogFragment.fetchWebappData()` `211-260` 的抓頁與 selector 範例，可借它的連線、redirect 與 Parser 習慣，但 Monitor 不可沿用 UI thread 或一次性 Bitmap 邏輯。
- **為什麼先做它**：它是藍圖的殺手功能，但最小版本可只做單站、單 selector、一次比較與一則通知；先驗證通知是否真有用，再擴充多站、diff view、歷史與自訂頻率。不要在 MVP 同時做 server push 或 AI 摘要。

### 6. userscripts 最後落地

- **改動點（精確接點）**：在 `WebApp.kt:18-121` 增加 `userScriptEnabled` 與本地腳本識別；在 `DataManager.java:98-108,157-195` 版本化保存腳本字串或私有檔名；在 `WebViewActivity.CustomBrowser.onPageFinished()` `895-902` 且 error page 分支之外注入已驗證腳本；沿用 `evaluateJavascript()`，不暴露 bridge。匯入時拒絕含 `@grant`、`@require`、`@resource` 或外部 URL 的 metadata，限制大小、數量與執行次數，並顯示安全提示。`WebViewActivity.setupWebView()` `210-249` 不因腳本而放寬其他 WebSettings。
- **學到什麼**：`onPageFinished()` 已會在 error page 執行原生 JS，是現成注入點；`WebApp` 與 `DataManager` 可以保存 per-site 設定，但 `evaluateJavascript()` 只是在網站同頁執行，不能提供真正 sandbox，因此限制 metadata 只能降低意外依賴，不能消除網站與腳本互相讀取的風險。
- **為什麼最後做它**：它需要檔案選擇、版本、除錯、錯誤提示與安全政策，且沒有 Monitors 的通知價值直接。先完成 0–5 的穩定資料模型與 WebView 邊界，才能在不放寬預設安全的情況下讓使用者選擇性啟用。

### 技術債處理方向

1. **先修 ID 與 schema。** `DataManager.replaceWebApp()` `273-277` 把 WebApp ID 當 list index；軟刪除目前不縮短清單，但未來匯入、同步或硬刪除容易錯位。新功能應以明確 ID lookup／map 取代索引假設，並為 JSON 加 schema 與 migration。
2. **消除 shallow copy。** `WebApp.copySettings()` 的 `adBlockSettings` 目前共享同一個 mutable list（`WebApp.kt:121`）；新增 `themeColor`、tracking、monitor、腳本欄位時必須同步複製或深拷貝，否則跟隨全域的 App 會互相污染。
3. **修正安全語意。** `setupWebView()` 的 `setSafeBrowsingEnabled(false)`（`WebViewActivity.java:214`）必須接 `isSafeBrowsing`；`shouldOverrideUrlLoading()` 的 `url.contains(host)`（`:1003-1014`）要改成正規化 host/origin 比較，遇到非 HTTP(S) scheme 先分類再決定外部 App。
4. **用 JSON 取代 Java 物件序列化。** 現有備份（`DataManager.java:289-346`）不應繼續擴張。新格式必須有版本、白名單欄位、大小上限、原子寫入／還原、checksum 與使用者確認；checksum 只做完整性，不冒充簽章。
5. **降低資料管理成本。** `DataManager.getWebAppIgnoringGlobalOverride()` 每次呼叫 `loadAppData()`（`:255-263`），背景 Worker 與多個 Activity 會放大 I/O；在持久化邊界建立明確 load/reload 時機，Worker 讀取改走不可變 snapshot。
6. **管理生成式沙盒代碼。** `app/build.gradle:169-220` 會複製 Activity 並改寫 Manifest；任何 WebView 修改都要確認生成流程與 `documentLaunchMode`、`process` 欄位一致。保留 GPL-3.0 授權、源碼可得性與第三方依賴清單。
7. **為後三者畫權限線。** Monitors 是背景網路；userscripts 是使用者在頁面同源的任意 JS；tracking_protection 是 URL 字串轉換。三者不能用同一個「啟用進階功能」開關偷渡權限，且都應可獨立停用與備份。

### 0→5 學什麼清單

- **0 主屏**：RecyclerView/Adapter、grid drag/swipe、favicon 抓取與落盤、搜尋狀態、empty/error state。
- **1 身份**：`WebApp` 欄位與 `copySettings()`、container 色資源、動態 status/toolbar/progress、shortcut 與 `documentLaunchMode`。
- **2 設定 IA**：Data Binding、GlobalSettings 模板、`isOverrideGlobalSettings` 二元語意、頁面分區與實際生效值。
- **3 WebView 邊界**：Safe Browsing、cookies、圖片與 Save-Data、frameless、SwipeRefresh、外部 scheme 與 host 判斷。
- **4 小改動組**：toolbar/gesture 設定、搜尋 URL 模板、default-browser intent、SAF、版本化 JSON 備份與匯入失敗保護。
- **5 Monitors**：WorkManager 排程／約束、Jsoup selector、時間與 timeout、hash 去重、NotificationUtils、失敗退避與通知深連結。

這份定稿的實作順序是：**先讓 WebApp 被看見（0）並被辨識（1），再把設定放對位置（2）並接通安全邊界（3），接著加入低風險隱私與可攜資料（4），最後才引入背景監控（5）與任意頁面腳本（6）；完整落地順序為 0–6。**
