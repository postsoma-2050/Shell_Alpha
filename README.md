<p align="center">
  <img src="graphics/logo.png" width="128" height="128" alt="ShellAlpha Logo" />
</p>

<h1 align="center">ShellAlpha（寄居蟹）</h1>

<p align="center">
  <strong>安全、轻量、多进程沙盒隔离的 Android 轻应用容器与现代隐私浏览器</strong><br>
  <em>Privacy-First, Multi-Process Sandboxed Lite Apps Container & Modern Browser for Android</em>
</p>

<p align="center">
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android_9.0+_--_15/16-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Platform"></a>
  <a href="https://developer.android.com/about/versions/15"><img src="https://img.shields.io/badge/Target_SDK-35-blue.svg?style=flat-square" alt="Target SDK"></a>
  <a href="https://github.com/postsoma-2050/Shell_Alpha/actions"><img src="https://img.shields.io/badge/Tests-470+_Passing-brightgreen.svg?style=flat-square" alt="Unit Tests"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-orange.svg?style=flat-square" alt="License"></a>
  <a href="https://github.com/postsoma-2050/Shell_Alpha"><img src="https://img.shields.io/badge/GitHub-postsoma--2050%2FShell__Alpha-141F5A?style=flat-square&logo=github" alt="Repository"></a>
</p>

---

## 💡 项目简介

**ShellAlpha（寄居蟹）** 是一款专为 Android 平台打造的高性能轻应用（Lite Apps）容器与隐私浏览器。在原生应用体积日益膨胀、频繁索取通讯录等敏感权限的背景下，ShellAlpha 遵循 **“轻量、可以长期使用、稳定输出，小步迭代、零破坏性”** 的架构升级原则，将 Web 站点封装为兼具原生质感、数据物理沙盒隔离、后台媒体持续播放与沉浸式阅读能力的现代化轻应用。

如同为脆弱的 Web 视图披上一层坚固的保护壳，ShellAlpha 赋予每个网页专属的进程空间、主题定制与隐私围栏。

---

## ✨ 核心特性

### 1. 🐚 Hermit 风格双模式主页
- **宫格与列表双模态**：支持四列紧凑宫格（Grid）与单列横向列表（List）一键平滑切换。
- **列表独立快捷设置**：列表模式下提供专属设置齿轮与快捷菜单，无需长按即可直达站点配置。
- **沙盒容器色彩标识**：通过彩色圆点与高亮徽标直观呈现站点所属沙盒编号，运行边界一目了然。

### 2. 🛡️ 8 容器多进程物理沙盒隔离
- **OS 级独立多进程体系**：内置 8 个独立沙盒进程（`:web_sandbox_0` ~ `:web_sandbox_7`），运行于系统隔离的独立内存地址空间。
- **数据与存储物理分立**：每个沙盒拥有专属的 WebView 数据目录与 Cookie 管理域，跨站追踪彻底切断，完美支持同站点多账号并发无干扰登录。
- **沙盒持久化与互操作保障**：提供沙盒会话自动恢复机制与配置深层校验，保证长期运行稳定不丢失状态。

### 3. 🎵 全进程后台媒体持续播放 (Background Media Playback)
- **全沙盒前台服务守候**：主进程与全部 8 个沙盒进程均配备专属媒体前台服务与唤醒锁管理。
- **熄屏切后台持续播放**：支持 YouTube、Bilibili、播客及各类 HTML5 音视频切出后台或屏幕熄灭时无缝继续播放。
- **系统媒体控制器集成**：完整对接 Android 锁屏面板、通知栏媒体控制器与蓝牙耳机线控操作。

### 4. 📖 沉浸式阅读模式与双语对照翻译引擎
- **纯净内容提取引擎**：算法去除侧边栏、横幅广告与冗余标签，呈现书籍般的沉浸式纯净排版。
- **多档排版与深色护眼调节**：支持系统字体/无衬线字体切换、无级字号调节及多种阅读背景色（纸墨、羊皮纸、夜间暗色）。
- **双语对照翻译引擎**：集成段落级双语对照翻译，支持整页即时翻译与多语言无缝切换。

### 5. 🎨 深度解耦的主题色与深色模式引擎
- **算法深色反转 (Force Dark)**：支持全网页智能色彩反转与原生深色模式同步，保护暗光环境下的视觉体验。
- **状态栏与导航栏自适应**：系统状态栏与导航栏色彩根据网页主色调实时动态融合，告别割裂色块。

### 6. ⚙️ 对标 Hermit 的 Lite App 设置页全新交互体验
- **一键快捷修改应用身份**：顶部卡片支持 Material 3 涟漪点击，直接弹出修改对话框，解决抓取临时标题（如 Cloudflare `Just a moment...`）无法修改的痛点，即时同步刷新 Favicon。
- **彻底杜绝软键盘遮挡**：
  - 核心概览卡片与四大分类 Tab（Behavior、Privacy、Theme、Integration）全部融入主滚动视图，释放 70% 可视空间；
  - 软键盘弹起时输入框**智能焦点避让自动滚动**（附加 84dp 安全避让高度），彻底跨越底部操作栏；
  - 标签页切换防走火设计，杜绝输入法意外抢焦弹起。
- **双向数据实时联动**：在任何子分类或弹窗中修改标题与起始网址，全局视图即时同步。

### 7. 🔍 页面搜索与无边框浮动工具条
- **页面内容实时高亮查找 (Find in Page)**：快速定位关键字，支持顺逆序快速跳转。
- **无边框浮动控制面板**：全屏或无边框模式下随手唤起桌面视图切换、夜间模式、打印与分享工具。
- **系统打印与 PDF 导出解耦**：支持将任意网页直接导出为矢量 PDF 或调用系统打印服务。

### 8. 🔒 深度隐私治理与加固防线
- **URL 追踪参数深度清洗**：自动剥离链接中的 `utm_*`、`fbclid`、`gclid`、`_hsenc` 等数十种商业追踪信标。
- **退出清除 Cookie 策略**：精准接入 Activity 销毁生命周期，彻底清理沙盒外共享 Cookie，避免临时切后台导致会话丢失。
- **防死循环负缓存机制**：Favicon 抓取引入 60 秒冷却机制，杜绝重复空转；定时刷新增加 5 秒安全下限。
- **内置 Adblock 与自定义脚本**：内置规则拦截侵入式广告，支持用户为特定站点注入 UserScripts。

---

## 🏗️ 架构与工程规范

本项目遵循 **高内聚、低耦合** 的重构原则，将历史单体 WebViewActivity 核心能力拆分为 14 个独立控制器：

```
app/src/main/java/com/cylonid/nativealpha/browser/
├── BrowserThemeController.java        # 深色模式与主题色彩自适应引擎
├── BrowserReaderController.java       # 阅读模式提取与字体/字号调节
├── BrowserPrintController.java        # 系统打印与 PDF 矢量导出解耦
├── BrowserPermissionsController.java  # 摄像头、麦克风等运行时权限收敛
├── BrowserContextMenuController.java  # 长按上下文菜单与链接操作收敛
├── BrowserQuickSettingsController.java# 快捷设置面板与实时开关收敛
├── BrowserSearchController.java       # 页面搜索与 FindBar 交互
├── BrowserSecurityPolicy.java         # 协议白名单、SSL 策略与混合内容防御
└── ReaderTranslationPanel.java        # 双语对照翻译面板与网络传输协作
```

### 技术指标

- **开发语言**：Kotlin + Modern Java (Java 17)
- **构建系统**：Gradle 8.10.2 + Android Gradle Plugin 8.7.2
- **系统兼容**：最低支持 Android 9.0 (API Level 28)，深度适配 Android 15 (Target SDK 35) 及 Android 16 预览版
- **测试覆盖**：**470+ 项全自动单元测试 100% 通过**（Robolectric + JUnit 4），确保核心链路 0 破坏性。
- **代码健康**：0 Android Lint Errors，无弃用隐患，历史孤立代码彻底清理。

---

## 📦 产品变体 (Flavors)

| 变体 Flavor | 特性描述 | 适用场景 |
| :--- | :--- | :--- |
| **`standard`** | 纯净极简单进程容器，极低系统资源占用 | 基础网页浏览与日常轻度使用 |
| **`extendedGithub`** | 完整内置 8 容器多进程沙盒物理隔离与扩展功能 | 深度多账号分身、隐私极客、多任务重度用户首选 |

---

## 🛠️ 构建与测试

```bash
# 1. 运行全套自动化单元测试 (470+ tests)
./gradlew testStandardDebugUnitTest

# 2. 运行特定设置交互测试
./gradlew testStandardDebugUnitTest --tests com.cylonid.nativealpha.SettingsLayoutTest

# 3. 运行 Lint 静态代码质量审查
./gradlew lintStandardDebug

# 4. 编译 Standard 渠道 Debug 安装包
./gradlew assembleStandardDebug

# 5. 编译 ExtendedGithub 渠道 Debug 安装包（含多进程沙盒特性）
./gradlew assembleExtendedGithubDebug
```

编译产物位于 `app/build/outputs/apk/<flavor>/debug/`。

---

## 📜 开源协议与致谢

- 本项目遵循 **GNU General Public License v3.0 (GPL-3.0)** 协议开源。
- 感谢原作者 [cylonid/NativeAlpha](https://github.com/cylonid/NativeAlphaForAndroid) 为本项目奠定的坚实基础。
- 感谢 [Hermit (Chimbori)](https://hermit.chimbori.com/) 带来的优秀轻应用交互与设计灵感。
- 官方仓库地址：[https://github.com/postsoma-2050/Shell_Alpha](https://github.com/postsoma-2050/Shell_Alpha)
