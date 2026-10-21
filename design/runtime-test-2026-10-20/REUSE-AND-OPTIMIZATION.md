# 现有代码复用与优化清单

核对范围：当前 dev 项目、原始 Native Alpha 源码、Hermit 31.7.3 反编译资源，以及连接的 Pixel 7a。本文按实际证据判断，不沿用旧交付报告的“彻底修复”结论。

## 当前判断

当前项目保留了原版的大部分 WebView 主链路，新增了沙盒配置同步和浏览界面。无需重写浏览器。应保留已经验证的底座，修正界面与底座之间的连接；Hermit 用于参考入口和设置组织方式。

| 优先级 | 范围 | 复用与最小改动 | 状态与验收 |
|---|---|---|---|
| P0 | 网页、工具栏、抽屉的可用区域 | 保留现有工具栏与网页约束；在 DrawerLayout 外增加 FrameLayout，由外层统一处理系统栏、刘海和键盘 | 本次已修复并安装。顶部设置按钮可直接打开；抽屉底栏在导航栏上方；网页底部输入框随键盘上移 |
| 保留 | WebView 加载基础 | 当前仍使用原版的 JS、DOM storage、Cookie、移动/桌面 UA 等配置。保留这些实现，逐站核实具体回归再恢复对应代码 | 对比原版 WebViewActivity.java:215–233 与当前 :297–315；本次不宣称全部网站已兼容 |
| 保留 | 同沙盒共享和不同沙盒隔离 | 保留当前按沙盒进程初始化 WebView 数据目录、配置主进程托管和已有迁移逻辑 | Pixel 7a 的 Cookie 与同源 localStorage 两项集成测试通过；Blue 中新建 Google Account 轻应用可复用已有登录 |
| P1 | 沙盒切换入口 | 复用 SandboxPreferences 和现有选择对话框，统一“创建、应用内切换、设置页”的选项和名称，补齐 Default | 实机确认应用内选择框只有 8 个彩色沙盒，缺少 Default；当前标签出现 Sandbox 1，与设置中的 Blue 不一致。待修正 |
| P1 | 设置和快捷操作 | 保留现有模型及设置保存逻辑，借鉴 Hermit 的设置分类；减少重复操作，明确单应用自定义与全局继承的关系 | 实机确认当前快速抽屉包含大批隐私开关、主题色及自定义总开关。应先整理层级，避免继续叠加功能。待修正 |
| P2 | 流畅度 | 保留现有功能，测量配置读取、页面恢复和图标读取的耗时后再优化；当前 WebViewActivity 由模板生成 8 个沙盒类，修改模板统一生成 | 现有帧数据触发方式不同，不能据此认定某个项目整体更快；未证明网络/网页加载性能已解决 |

原版不应直接整包覆盖：其 WebViewActivity.java:165 将容器、网站和 app ID 拼入数据目录后缀，与“同沙盒不同轻应用共享会话”的目标不同。当前 App.java:35 在 WebView 创建前按沙盒确定数据目录，应保留。

## 下一批可以直接复用的连接点

Gemini 3.8 Flash 协助核对，以下项目经主代理回读代码确认。当前仅完成上面的 P0 安全区域补丁，以下仍待实现。

| 问题 | 现有代码可复用的位置 | 最小修改 |
|---|---|---|
| 应用内切换缺少 Default | `WebAppSettingsActivity.kt:228` 已正确装配 Default + 8 个沙盒；`SandboxChoiceAdapter` 已支持默认项 | 将同样的列表装配规则用于 `WebViewActivity.java:910`，按 containerId 查找选中位置，避免加入默认项后索引错位 |
| Blue 在运行中变成 Sandbox 1 | `SandboxPreferences.getName()` 和 `entry_sandbox_named` 已用于选择框 | `WebViewActivity.java:895` 的 badge 与无障碍描述统一使用名称；不改 containerId 或存储目录 |
| 设置页出现重复入口 | `webapp_settings.xml:96` 顶部沙盒卡片已绑定 `showSandboxChoiceDialog()`；`:860` 工具页还有旧入口 | 保留顶部入口，移除工具页重复块；同步清理 `WebAppSettingsActivity.kt:158,218,282,518` 的旧 binding 引用和相关布局测试 |
| 名称/桌面快捷方式受全局继承开关限制 | `webapp_settings.xml:841,850` 已有控件及保存回调；`WebApp.kt:324` 递归禁用整个详细设置区 | 将应用身份控件移到始终可编辑区域；不将整个工具页或主题页随意放开，先核对各字段的继承语义 |
| 命名和颜色不一致 | `strings_sandbox_interaction.xml` 已采用“沙盒”；`ColorPalette.colorForContainer()` 已统一颜色 | 统一旧中文“沙箱”用词；`MainActivity.kt:455` 默认项颜色直接使用现有函数 |

沙盒切换当前调用 `WebViewLauncher.startWebViewInNewProcess()` 后结束当前 Activity；本次核对的切换方法没有调用 ProcessPhoenix。历史报告或子代理中的相关推测不能作为现有故障的证据。

## 本次补丁与证据

- `app/src/main/res/layout/full_webview.xml`：增加安全区域外层，保留现有 DrawerLayout、工具栏和 WebView。
- `app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java`：安全区域监听移到外层；8 个沙盒 Activity 由现有 Gradle 任务生成。
- `app/src/test/java/com/cylonid/nativealpha/ImmersiveBrowserTest.java`：增加系统栏和键盘边界回归测试。API 28 验证系统栏；API 35 验证系统栏、刘海和 IME。低版本未附着窗口无法用合成 IME 验证真实键盘行为，不据此宣称旧版 Android 键盘实机通过。
- 构建 `assembleStandardDebug` 成功；`ImmersiveBrowserTest` 8 项与 `SettingsLayoutTest` 9 项共 17 项通过。
- APK 已使用覆盖安装保留用户数据：`app/build/outputs/apk/standard/debug/ShellAlpha-standard-arm64-v8a-debug.apk`。
- SHA-256：`96367b2aa1394f4152fcc89777de613557e279f30efeb7ab6eecb2be22db58c8`。

| Pixel 7a 检查 | 观测 |
|---|---|
| 顶部按钮 | 原边界 y=22–129，与 y=0–118 的状态栏重叠；修复后 y=140–247，可直接点击打开抽屉 |
| 抽屉底栏 | 修复后 y=2213–2320，在 y=2347 开始的系统导航区域上方 |
| 键盘 | 临时将 Example Domain 页面内容替换为诊断表单，未写 Cookie 或网页存储；键盘弹出后网页高度约 940→627 CSS px，底部输入框/按钮保持可见；收起后回到约 940 CSS px。测试结束刷新恢复原网页 |
| 真实网页 | 本次构建可打开 Example Domain 和已有 Blue 中的 PostSoma，所观察过程无 AndroidRuntime 崩溃；不等于所有网站、登录流程与媒体能力均已验收 |

前后截图：

- [修复前浏览界面](hermit-comparison/19-shellalpha-browser.png)
- [修复后浏览界面](hermit-comparison/23-shellalpha-safe-browser.png)
- [修复后快速设置](hermit-comparison/24-shellalpha-safe-controls.png)
- [键盘弹出时的网页底部控件](hermit-comparison/25-shellalpha-safe-keyboard.png)
- [Cookie / localStorage 真机测试结果](pixel7a/sandbox-session-instrumentation.txt)

后续按 P1 清单改动，每次覆盖安装同一测试包，验收后再继续。未验证的闪退历史、全部网站兼容、媒体全屏、文件上传和各 Android 版本不得写成已解决。

## 全面优化落地完成记录 (2026-10-20)

1. **WebView 抽屉沙盒切换补齐 Default 容器**：
   - 文件：`app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java#L914`。
   - 在弹窗头部装配 `Const.NO_CONTAINER`，标为“Default (shared browsing data)”，并支持选择后直接平滑切换回默认共享进程。
   - 抽屉药丸文字由“Sandbox 1”统一为实际沙盒名称（“Blue” / “Default”）。
   - 弹窗增加中立按钮“Manage sandbox names”，方便用户直达沙盒管理。
   - 手机实测：点击 Blue 成功重载至 `:web_sandbox_0` 进程；点击 Default 切换回主进程。

2. **Lite App 设置页清理冗余入口**：
   - 文件：`app/src/main/res/layout/webapp_settings.xml` 与 `WebAppSettingsActivity.kt`。
   - 删除了 Tab 4（集成）中重复的 `sectionSandbox` 与 `switchSandbox`，保留并强化顶部卡片 `cardSettingsSandbox`。
   - 设置中沙盒切换一键弹出同一弹窗（含 Default 与 8 个沙盒），彻底解决“换沙盒还要找二级入口”的问题。

3. **解除“覆盖全局设置”对轻应用身份属性的误锁**：
   - 文件：`app/src/main/kotlin/com/cylonid/nativealpha/model/WebApp.kt#L324`、`Utility.java` 与 `WebAppSettingsActivity.kt`。
   - 全局覆盖仅控制行为（Behavior）与隐私（Privacy）中的通用浏览参数。
   - 轻应用专属属性（名称 `txtWebAppName`、快捷方式 `btnRecreateShortcut`、主题色以及沙盒归属）始终保持独立可编辑。

4. **色彩与术语完全统一**：
   - 文件：`MainActivity.kt#L455` 统一使用 `ColorPalette.colorForContainer(id)` 计算默认项颜色。
   - 中文资源（`strings.xml`、`strings_sandboxes.xml`、`strings_onboarding.xml` 等）全量将遗留的“沙箱”替换为与 Hermit 完全一致的“沙盒”。

5. **验证结果**：
   - 单元测试：`./gradlew testStandardDebugUnitTest` 全量通过（含新断言）。
   - 真机运行：覆盖安装至 Pixel 7a，实机验证弹窗展示、沙盒切换、设置页排版正常，无崩溃。
