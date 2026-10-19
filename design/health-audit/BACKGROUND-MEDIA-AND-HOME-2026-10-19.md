# 后台媒体播放与 Hermit 首页布局

这是健康审查后，按用户追加要求实现的两项功能。最初审查快照见 [HEALTH-2026-10-19.md](/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/design/health-audit/HEALTH-2026-10-19.md)。

## 首页：四列宫格与横向列表

以用户截图及本地 Hermit 的 `apktool_out/res/layout/item_lite_app_grid.xml`、`item_lite_app_linear.xml` 为参照，沿用 ShellAlpha 的 Material 主题和品牌。

- 默认每行四个图标；图标下最多两行名称，点按打开，长按显示菜单。
- 顶部切换按钮切换到真正的横向列表：左侧图标，中间名称和网址，右侧独立设置齿轮与竖向更多菜单。
- 设置齿轮直接打开对应 Lite App 设置；更多菜单保留切换沙盒、快捷方式、删除、上移和下移等操作。
- 列表中的两个操作按钮均为 48dp 触控区域；条目随文字高度增长。复用条目时重置方向、宽高、间距、标题对齐和按钮显示状态。
- 保留搜索、空状态、删除撤销、沙盒标记和旋转后的选择。原偏好 `grid_span_count=1` 迁移为列表，其他旧值迁移为四列；切换结果继续保存。
- 英文和简体中文同步；仅清理两项被新模式替代且已无引用的旧列数字符串。

主要实现：[WebAppListFragment.kt](/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/kotlin/com/cylonid/nativealpha/fragments/webapplist/WebAppListFragment.kt)、[WebAppListAdapter.kt](/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/kotlin/com/cylonid/nativealpha/fragments/webapplist/WebAppListAdapter.kt)、[web_app_list_item.xml](/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/res/layout/web_app_list_item.xml)。

## 后台媒体播放

入口：**Lite App 设置 → 行为 → 允许在后台播放媒体**。若当前应用继承全局设置，可在全局 Lite App 默认设置中启用，或先打开该应用的自定义设置。功能默认关闭。

- 接通现有 `isAllowMediaPlaybackInBackground` 字段，不再只保存而没有消费者。
- 播放中的应用在启用后离开前台时保留 WebView 播放，不调用会中断当前播放的暂停操作；关闭选项后按原有行为暂停。
- 监听顶层网页的 HTML `audio/video` 状态，只有实际报告播放且应用可见、用户已启用功能时，才请求前台媒体服务；普通浏览页不启动空服务，不强制自动播放。
- 默认进程和八个沙盒进程各有对应的 `mediaPlayback` 服务，媒体不会转移到默认 WebView 数据目录。
- 使用通知提供停止入口；Android 13 及以上在首次播放时请求通知权限，拒绝通知权限不阻止媒体前台服务本身运行。
- 播放结束、通知停止、关闭页面或移除任务时释放客户端、前台状态和运行锁。多个窗口按对象身份登记，避免同一个应用的第二个窗口覆盖第一个窗口。
- WebView 的全局计时器只在没有其他前台 WebView 或有效后台播放器时暂停，避免停止一页而暂停另一页。
- 取消过快的播放也会先完成前台服务启动契约再退出，不会直接取消一个仍等待前台提升的启动请求。

主要实现：[BackgroundPlaybackController.java](/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/java/com/cylonid/nativealpha/media/BackgroundPlaybackController.java)、[BackgroundPlaybackService.java](/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/java/com/cylonid/nativealpha/media/BackgroundPlaybackService.java)、[WebViewActivity.java](/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java)。Manifest 模板与备用源清单保持同步；沙盒服务由现有 Gradle manifest 生成流程声明。

## 验证方式与适用范围

- 自动化验证包含模式偏好迁移、列表窄屏与大字体几何、搜索、后台生命周期、同 ID 多窗口、通知停止、任务移除、服务声明和取消启动清理。
- `media-capture-script-check.js` 直接从生产代码提取媒体监听脚本，已验证初始状态、播放/暂停、重新注入后的令牌切换，以及事件监听器不会重复安装。
- [独立测试播放器](/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/design/health-audit/background-media-test-player.html) 内嵌音频，不依赖外部音源。可供设备上对照启用/禁用、Home、锁屏、通知停止、播放结束和任务移除行为。
- 构建、JUnit 与 lint 的最终执行记录单独保存；最初健康审查的 63 项测试不能当作新增功能已经验证的证据。

当前没有连接 Android 设备，因此未声称已完成真实锁屏或长时间播放测试。此实现覆盖顶层 HTML 音视频；**跨域 iframe 中的播放器和纯 WebAudio / AudioContext 暂不覆盖**。还需要支持 `WEB_MESSAGE_LISTENER` 的 Android System WebView。网站自己的暂停策略可能影响效果。

现有沙盒架构在同一槽位切换不同 Lite App 时可能重启其进程，旧播放器不会跨进程迁移；这部分保持原有隔离机制。功能没有改为提取媒体流或绕过网站的播放限制。

实现参考：[Android WebView 生命周期与全局计时器](https://developer.android.com/reference/android/webkit/WebView)、[媒体前台服务类型与权限](https://developer.android.com/develop/background-work/services/fgs/service-types#media)、[AndroidX WebView 消息监听器](https://developer.android.com/reference/androidx/webkit/WebViewCompat#addWebMessageListener(android.webkit.WebView,java.lang.String,java.util.Set%3Cjava.lang.String%3E,androidx.webkit.WebViewCompat.WebMessageListener))。前台启动后过早停止的处理另核对了 [AOSP ActiveServices](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/am/ActiveServices.java)。

## 最终构建与交付

- `assembleStandardDebug`、`assembleExtendedGithubDebug` 均通过。
- `testStandardDebugUnitTest`：**89 项，88 通过、1 跳过、0 失败**。媒体控制器在 SDK 28 / 35 运行，包含通知 ID 20000 的过早取消回归断言。
- `lintStandardDebug`：**10 个历史错误、362 个警告**，任务失败；没有新增 lint 错误。详情见 [最终验证报告](/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/design/health-audit/FINAL-VALIDATION.md) 和 [执行日志](/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/design/health-audit/final-verification.log)。
- [安装包](/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/dist/ShellAlpha-1.5.2-hermit-media-debug.apk)：版本 1.5.2，debug 包名 `com.cylonid.nativealpha.debug`，最低 Android 9，目标 Android 15。
- SHA-256：`558a378de0a345e58936c5ec7ebba8e561d731e3eaef14a0b64500d25d346956`；交付副本与最终 Standard universal 构建一致。元数据见 [DELIVERY.json](/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/design/health-audit/DELIVERY.json)。

## 备份记录

- 后台播放改动前：`backups/background-media-20261019-152743/`，含源文件和 SHA-256 清单。
- 首页布局改动前：`backups/home-view-modes-20261019-154306/`，含源文件和 SHA-256 清单。

健康审查所列的图标失败重试、零秒自动刷新、备份 ID 分配和 Cookie 自动清理问题仍是后续修复项；本次只接通用户明确追加的后台播放与首页布局需求。
