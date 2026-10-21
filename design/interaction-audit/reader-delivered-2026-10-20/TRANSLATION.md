# Reader 專屬劃線翻譯

2026-10-20。取代先前 ACTION_PROCESS_TEXT 全域文字處理入口。

## FreshRSS 參考與連線差異

參考 `/Users/grangerfdad/Desktop/FreshRSS-edge/frontend/src/components/reader-app.tsx`：翻譯開關在閱讀工具列，選字限定閱讀正文，手機選好文字後再按翻譯，退出／變更選取會取消請求。

`frontend/app/api/reader/translate/route.ts` 則在伺服器呼叫 Google。這才是它與 Android 手機直接請求 Google 的出口差異；把按鈕移入 Reader 本身不會改變 IP。

FreshRSS API 有 gate cookie、同來源 Origin、部署環境與限流檢查，不能假設 Android 能匿名沿用。這次沒有修改 FreshRSS 或部署後端，也沒有加入代理、API 金鑰、離線模型。Android 保留直接請求 Google 免費端點；後續同手機對照已確認可透過修正 User-Agent 取得譯文，不需要為這個已重現的問題新增後端。

## 互動與生命週期

- 翻譯開關只在閱讀工具列；普通網站及 Lite App 設定沒有新增翻譯選項。
- 已移除 ReaderTranslateActivity 及兩個主 manifest 的 ACTION_PROCESS_TEXT 註冊，不再充當其他 App 的系統文字處理器。
- 只有 ReaderWebView 包裝原生選字回呼。劃線後讀取選字，顯示獨立「Translate selection」按鈕；原生選字選單保留同名入口與 Copy 等動作。
- 選字本身不發翻譯請求。明確點按後才建立閱讀翻譯對話框，顯示載入，隨後顯示譯文或具體錯誤；目標語言、重試、複製與官方 Google 網頁續譯均在此對話框內。
- 關閉翻譯、改變閱讀樣式、退出閱讀或 Activity 銷毀，會清除入口／選字狀態、取消請求並關閉對話框。只傳所選文字及目標語言，不傳整篇文章、網站 Cookie 或頁面 URL。
- 最多 2,000 字；請求有連線／讀取／整體超時、回應大小限制及取消控制。沒有背景輪詢、常駐服務、模型下載。

## 「點了沒反應」修正

舊流程把 Reader WebView 的 getUrl 精確相等當作點擊前提。但 loadDataWithBaseURL 未指定 history URL 時，本機文件可能回報 about:blank，造成入口直接 return。已移除這個錯誤前提，並明確設定 Reader history URL。

原生選字選單銷毀也不應取消使用者已點下的單次讀取；真正取消邊界是關閉 Reader／翻譯、重新渲染及後續請求取代。新增真機測試涵蓋舊 history URL 載入方式及選單先關閉的情境。

## 閱讀文件限制

文章仍經 ReaderDocument 的 Jsoup safelist 清理，移除腳本、事件、iframe 及不安全連結。Reader WebView 禁止網路、檔案及 content provider 存取；文章 CSP 禁止頁面腳本。宿主僅在原生選字互動時執行讀取，沒有對原網站注入翻譯監聽器或新增 JS bridge。

## 原生 HTTP User-Agent 差異：已找到可修正原因

先前 Pixel 7a 合成句子實測遭 HTTP 302 導向 Google /sorry 驗證頁。當時把連線失敗往 IP／後端出口推論，證據不足。使用者提供同手機 Selah Translate Google 引擎可用的證據後，改為受控請求比較。

參考 Selah Translate 倉庫 commit `c2da6ef9de1eb68eea7e9ba15cde4a520917a8be` 的 `entrypoints/service/google.ts` 與 `entrypoints/background.ts`：同樣使用 translate.googleapis.com/translate_a/single、client=gtx，另外帶 strip=1/nonced=1，並由瀏覽器直接 fetch，未走自建後端。僅參考程式邏輯，沒有複製擴展套件或加入額外依賴。

Pixel 7a 上使用同一固定合成句子、zh-CN、同一端點的對照結果：

| 請求設定 | HTTP | 譯文 |
|---|---|---|
| 原生預設 User-Agent + Accept: application/json | 302 → www.google.com/sorry | 無 |
| 原生預設 User-Agent + Selah 額外參數 | 302 → www.google.com/sorry | 無 |
| WebView User-Agent + Selah 額外參數 | 200 | 有 |
| WebView User-Agent + 原始參數 | 200 | 有 |
| 原生預設 User-Agent + 預設 Accept | 302 → www.google.com/sorry | 無 |
| WebView User-Agent + Accept: application/json + 原始參數 | 200 | 有 |

後兩項將 Accept 與額外參數排除，確認這次的有效修正是使用安裝中 WebView 的 User-Agent。正式 Request 現在由 Context 讀取 `WebSettings.getDefaultUserAgent()` 並明確設定請求標頭；不硬編碼 Chrome 版本、不讀 Cookie、不改 VPN／DNS／代理、不新增伺服器或模型。

對照測試為 GoogleTranslationTransportDeviceTest，正式請求 live probe 為 ReaderTranslationDeviceTest#googleTranslatesSyntheticPhraseOnDevice，實際對話框結果及複製按鈕驗證為 ReaderTranslationDialogDeviceTest。這些外部服務測試只使用固定合成文字；正式單元測試不依賴 Google 網路。

先前 translation-provider-rejected.png、translation-google-web.png 為修正前的歷史證據。修正後的結果須以新 APK 真機驗證為準。

## 修正後最終驗證

- JDK 17：`testStandardDebugUnitTest assembleStandardDebug assembleStandardDebugAndroidTest` 成功；287 項單元測試，0 失敗、0 錯誤、1 跳過。
- 最新 APK 與測試 APK 均以 `adb install -r --user 0` 安裝 Pixel 7a（33081JEHN21726）。
- 正式 Request 的即時 Google 翻譯與 ReaderTranslationDialogDeviceTest 共 2 項真機測試通過（1.7 秒）：固定英文句子返回譯文，閱讀浮層顯示中文，取得結果後複製按鈕才可用，關閉後對話框消失。
- 真機 CNN 實際長按選字，閱讀正文出現獨立 Translate selection 按鈕；實際點按後浮層顯示 `authorities → 当局`，複製按鈕可用。截圖 translation-reader-selection-action.png、translation-reader-success.png。此操作驗證使用已修正 User-Agent 的版本；之後補上對話框首幀按鈕禁用，再安裝最終版本並通過上述 2 項測試。
- 較早的 4 項閱讀作用域／腳本限制／退出生命週期／選單銷毀前後點擊回歸測試亦已通過；此次後續改動集中在 HTTP User-Agent 與對話框初始按鈕狀態。

保留僅屬於 Reader 的翻譯功能。沒有新增翻譯後端、代理、離線模型或常駐服務。
