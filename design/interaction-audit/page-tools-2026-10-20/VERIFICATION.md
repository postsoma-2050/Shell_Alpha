# 复制网址与页内查找

## 基准依据（先对比，再实现）

- NativeAlphaForAndroid-orignal/app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java：cmItemCopyUrl 使用 ClipboardManager + ClipData，复制 WebView 当前 URL；原版仅在长按菜单的“更多”中提供。
- hermit broswer/decompiled_hermit/resources/res/layout/fragment_browser.xml:137：browser_find_in_page 是独立的原生工具条，包含查询输入、下一项、上一项；网页约束在额外工具条下方。
- Hermit BrowserFragment.java:316–329：findNext(true/false)；:1280–1281 绑定查询变化与关闭。
- Hermit defpackage/tp.java:45–60：关闭工具条并 clearMatches。参考工程反编译部分存在缺失，本次仅依据可核验代码，不假定完整源码可编译复用。

## 实现

- 有栏模式的快捷面板直接提供“复制网址”“页内查找”；原有长按菜单复用同一逻辑。
- 复制当前页面（包括跳转后的路径、查询参数、片段），空 URL 回退轻应用初始网址。Android 13+ 使用系统剪贴板提示，旧版本使用 Toast。
- WebView 原生 findAllAsync/findNext，支持计数、前后跳转、无匹配禁用导航；180ms 输入防抖，不注入或遍历网页 DOM。
- 查找条占用独立布局空间，不覆盖网站；保留键盘避让。返回先关闭查找；导航、弹窗切换、媒体全屏、切到无边框、销毁时清理高亮、监听和待执行搜索。
- 按用户确定的规则，无边框不提供页内设置/工具入口。

## 验证

加入 Reader 后的前一轮构建成功：267 项单元测试，0 失败、0 错误、1 跳过。Pixel 7a 上 PageToolsInteractionTest 与 FullscreenInteractionTest 通过（/tmp/reader-device-final.log）。Yahoo News 实际点按查找 News 得到 9 个匹配，前后切换与复制入口通过（/tmp/page-tools-live-check.log；yahoo-find.png、yahoo-copy.png）。自动真机夹具使用离线 HTML 验证实际 WebView 匹配，与真实 Yahoo 验证分开记录。无边框工具入口的新增规则见 frameless-tools-2026-10-20。
