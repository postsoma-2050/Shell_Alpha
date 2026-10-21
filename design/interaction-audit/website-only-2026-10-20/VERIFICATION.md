# 无边框：网站独占模式（取代之前常驻恢复条方案）

用户明确要求：无边框时只显示网站，配置只能回 ShellAlpha 主页面修改；同时要像 Hermit 一样使用镜头区域。

## 先查基准后的实现依据
- Hermit `decompiled_hermit/sources/defpackage/ho0.java:10`：cutout mode = 1 (SHORT_EDGES)。
- Hermit `decompiled_hermit/sources/defpackage/jo0.java:11`：cutout mode = 3 (ALWAYS)。
- Hermit `decompiled_hermit/sources/defpackage/fo0.java:55`：应用窗口 cutout helper；BrowserActivity.java:153 调用适配入口。
- Hermit `decompiled_hermit/sources/defpackage/gp.java:197`：全屏窗口标志与隐藏系统栏。
- NativeAlpha 原始版 `WebViewActivity.java:623`：全屏不让系统装饰缩小窗口，隐藏状态/导航栏；原始 `full_webview.xml` 网页使用 match_parent。
- ShellAlpha 的额外留白来自 browserWindowRoot 对 systemBars|displayCutout 的统一 padding；常驻恢复条则是此前本地新增设计。本轮撤掉无边框的两处额外占位。

## 本轮最终行为
- 无边框自动隐藏系统栏、应用工具栏、底部 Browser controls；网页进入镜头区域。API30+ 使用 ALWAYS，API28–29 使用 SHORT_EDGES。
- 无边框不再统一为状态栏或镜头留 padding；键盘弹出时仍为输入保留 IME 高度。
- 禁用无边框的设置抽屉、设置手势、MENU 打开设置、应用自己的长按菜单；保留网页正常长按行为。
- 返回键有网页历史时后退，无历史时启动 MainActivity 并关闭网页 Activity。媒体全屏和弹窗仍按既有层级退出。
- 普通有栏模式继续避让系统栏/镜头并保留快捷抽屉。
- “无边框不能页内改设置”来自用户明确要求，并非声称 Hermit 的入口策略完全一致。

## 验证
最终构建成功（/tmp/cutout-release-check.log），239 项单元测试，0 失败、0 错误、1 跳过。Pixel 7a（33081JEHN21726）安装成功，FullscreenInteractionTest 真机通过（/tmp/cutout-device-final.log）。断言网页左上角为屏幕 (0,0)、尺寸等于完整显示区域，系统栏与恢复条隐藏，网页底部按钮可点击；MENU 不弹出设置，根页面返回主页面。
