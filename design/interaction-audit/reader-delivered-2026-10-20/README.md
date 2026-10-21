# Reader Mode：已下發正文與 CNN 相容性修正

2026-10-20。

## 已確認的失敗原因

原提取入口把 `document.body.textContent.length > 1500000` 當成整頁不可解析。這包含大型 hydration／廣告腳本，與真正的正文大小不同。

對當天 CNN 公開回應的量測：

| 頁面 | body.textContent 字元 | body 腳本字元 | 可見文字字元 | 原判斷 |
|---|---:|---:|---:|---|
| CNN 首頁 | 2,363,309 | 2,242,845 | 7,029 | 解析前拒絕 |
| CNN 新聞文章 | 2,340,218 | 2,292,423 | 7,683 | 解析前拒絕 |

參考文章：[CNN 原頁](https://www.cnn.com/2026/10/20/americas/alberta-rejects-independence-referendum-canada-intl-hnk)。量測對象是這次下載的公開 HTML，在獨立無頭 Chrome 中停止頁面腳本及外部資源後解析；不是手機當前頁的 DOM，也不是所有 CNN 頁面的固定數字。

修正後，該份文章回應可提取出 16,137 字元的正文 HTML，包含末段；原 DOM 不變。首頁也能提取頁面文字，但首頁是新聞列表，不把它稱為一篇完整文章。

## Hermit 參考依據

參考 `/Users/grangerfdad/Desktop/hermit broswer/decompiled_hermit`：

- `sources/core/webview/processors/PageSourceProcessor.java`：取得當前 URL 與頁面 HTML，再以原生 Jsoup 解析，交給後續處理。
- `sources/com/chimbori/hermitcrab/reader/ReaderFragment.java`、`sources/defpackage/y90.java`：獨立 Reader 畫面與載入／閱讀設定流程。
- `sources/defpackage/q33.java`：明確區分 LOADING、ARTICLE_AVAILABLE、ERROR_NOT_AN_ARTICLE、ERROR_FETCH_FAILED、ERROR_PARSE_FAILED。
- `resources/res/layout/fragment_reader.xml`：載入、正文及錯誤區塊分開；錯誤時保留回到瀏覽器的入口。

這是反編譯資料，若干核心方法缺失或反編譯錯誤；只能引用已能確認的流程，不能聲稱完整還原 Hermit 的演算法或所有 fallback 行為。此次沿用我們現有 Mozilla Readability 與 ReaderDocument 清理／顯示流程，沒有複製反編譯類別。

## 本次實作

入口仍為原來的 Reader mode 按鈕，正常與無邊框模式共用。

1. Readability 在副本中解析，先移除 script／style／noscript／template，再計算正文大小。保留 20,000 元素及正文／輸出大小限制，不靠無限制放大上限解決 CNN 問題。
2. 補充檢查已下發資料：與當前文章 URL 相符的 JSON-LD articleBody、明確的 NEXT_DATA `props.pageProps.article.body` 純文字欄位，以及主標題所在 article 內唯一的 `itemprop=articleBody` 區塊。
3. 正文資料有衝突、網址不符、只有 description 或資料格式不明時，保留原 Readability 結果。補充來源只在沒有正常結果、或明顯比正常結果長時採用；長度不等於全文完整性證明。
4. 所有輸出沿用 ReaderDocument 的 Jsoup safelist；閱讀 WebView 關閉網路與檔案存取；後續劃線翻譯需要宿主讀取選字，因此啟用宿主 JavaScript 求值，並由清理器及 CSP `default-src none` 阻止文章腳本執行。原頁不刪節點、不清 Cookie、不重新請求全文。

## 驗證

- `app/src/test/javascript/reader-extraction.cjs`：19 項通過，包含大型腳本／少量正文、結構化與隱藏正文、錯誤文章、摘要、衝突資料及原 DOM 不變。
- JDK 17 下 `./gradlew testStandardDebugUnitTest assembleStandardDebug assembleStandardDebugAndroidTest` 通過；273 項單元測試，0 失敗、0 錯誤、1 跳過。
- 已在 Pixel 7a（33081JEHN21726）以 `--user 0` 更新 APK，透過 adb 點按現有 CNN 輕應用的 Reader mode 按鈕，成功開啟手機上當前的 CNN 文章並顯示正文。截圖為 `cnn-live-reader.png`。這與上述公開下載樣本是不同文章。
- 原有三項觸控 instrumentation 在 Android Studio 螢幕分享環境遇到 targeted input injection 的窗口／顯示器錯誤，未通過，不能宣稱整套真機自動化綠燈；因此改以真機 adb 點按核驗 Reader 入口。未為此放寬正式程式的輸入或安全限制。

JavaScript 驗證需 Node、Playwright 與 Chrome。本機重跑：

```sh
NODE_PATH=/Users/grangerfdad/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules \
CHROME_BIN='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' \
/Users/grangerfdad/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node app/src/test/javascript/reader-extraction.cjs
```

仍需區分其他失敗原因：網站 JS 關閉、正文尚未載入、只有影片／無足夠文字、文章主要在跨來源 iframe、超過資源限制。此次已重現並修復 CNN 的大小誤判，沒有據此宣稱所有上述情況均已解決。
