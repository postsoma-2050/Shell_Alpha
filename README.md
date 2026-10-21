<p align="center">
  <img src="graphics/logo.png" width="128" height="128" alt="ShellAlpha Logo" />
</p>

<h1 align="center">ShellAlpha</h1>

<p align="center">
  <strong>安全、轻量、多进程沙盒隔离的 Android 轻应用容器</strong><br>
  <em>Privacy-First, Multi-Process Sandboxed Lite Apps Container for Android</em>
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

**ShellAlpha** 是一款专为 Android 平台打造的轻应用（Lite Apps）容器与隐私浏览器。它可以将网页封装为拥有原生质感、独立数据沙盒与后台持续播放能力的独立应用，减少原生应用对系统内存的长期占用与权限索取。

---

## ✨ 核心功能

### 1. 📱 双模式主页 (Grid & List)
- **宫格与列表切换**：支持紧凑四列宫格与清晰的单列横向列表一键切换。
- **独立设置与管理**：列表模式下提供快捷设置入口与操作菜单，直达轻应用配置。
- **沙盒色彩标识**：通过彩色圆点直观呈现站点所属的沙盒容器编号。

### 2. 🛡️ 多进程物理沙盒隔离
- **OS 级独立多进程**：内置 8 个独立沙盒进程（`:web_sandbox_0` ~ `:web_sandbox_7`），运行于独立内存空间。
- **数据物理分立**：每个沙盒拥有独立的 WebView 数据目录、Cookies 与本地存储，防止跨站追踪，支持同站点多账号并存。
- **会话持久化**：具备沙盒会话自动恢复与状态保护机制。

### 3. 🎵 后台媒体持续播放
- **媒体前台服务**：各沙盒均支持独立的媒体前台服务与唤醒锁。
- **切后台与熄屏播放**：支持音频、视频、播客在切出后台或屏幕熄灭时无缝继续播放。
- **系统媒体控制器**：完整支持 Android 锁屏面板与通知栏媒体控制。

### 4. ⚙️ 轻应用设置与自定义
- **快速编辑应用信息**：点击顶部卡片即可快捷修改轻应用名称与起始网址，即时更新图标预览。
- **四维分类配置**：提供行为（Behavior）、隐私（Privacy）、外观（Theme）、集成（Integration）清晰分类。
- **顺畅的输入交互**：优化页面滚动与焦点避让，软键盘弹起时自动平滑滚动，避免遮挡输入内容。
- **实时数据联动**：表单输入与顶部身份卡片实时同步。

### 5. 📖 沉浸阅读与双语翻译
- **正文纯净提取**：自动过滤网页广告与冗余排版，提取正文纯净呈现。
- **多档护眼排版**：支持字体切换、字号无级缩放与多种阅读背景配色。
- **双语对照翻译**：支持网页即时翻译与段落级双语对照展示。

### 6. 🎨 深色模式与主题自适应
- **算法深色反转 (Force Dark)**：智能反转网页色彩，与系统深色模式保持同步。
- **系统栏色彩自适应**：状态栏与导航栏根据网页主色调实时动态融合。

### 7. 🔒 隐私防护与实用工具
- **URL 追踪参数清洗**：自动去除链接中的 `utm_*`、`fbclid` 等追踪信标。
- **退出清理机制**：支持退出时自动清空沙盒外共享 Cookie 与缓存。
- **页面搜索与导出**：内置页面关键字查找（Find in Page），支持将网页导出为 PDF 或调用系统打印。
- **广告拦截与脚本注入**：内置规则拦截网页广告，支持站点自定义 JavaScript 脚本。

---

## 🏗️ 技术架构与质量指标

- **开发语言**：Kotlin + Modern Java (Java 17)
- **构建系统**：Gradle 8.10.2 + Android Gradle Plugin 8.7.2
- **系统兼容**：最低支持 Android 9.0 (API Level 28)，适配 Android 15 (Target SDK 35) 及 Android 16
- **测试覆盖**：**470+ 项全自动单元测试 100% 通过**，保证核心链路稳定可靠
- **代码健康**：0 Android Lint Errors，保持代码结构清晰

---

## 📦 产品变体 (Flavors)

| 变体 Flavor | 特性描述 | 适用场景 |
| :--- | :--- | :--- |
| **`standard`** | 纯净单进程容器，资源占用极简 | 日常基础使用与轻度浏览 |
| **`extendedGithub`** | 完整内置 8 容器多进程物理沙盒与扩展功能 | 多账号分身与高隐私需求场景 |

---

## 🛠️ 构建与测试

```bash
# 1. 运行全套自动化单元测试 (470+ tests)
./gradlew testStandardDebugUnitTest

# 2. 运行设置与布局相关测试
./gradlew testStandardDebugUnitTest --tests com.cylonid.nativealpha.SettingsLayoutTest

# 3. 运行 Lint 静态代码检查
./gradlew lintStandardDebug

# 4. 编译 Standard 渠道安装包
./gradlew assembleStandardDebug

# 5. 编译 ExtendedGithub 渠道安装包（含 8 沙盒特性）
./gradlew assembleExtendedGithubDebug
```

编译产物位于 `app/build/outputs/apk/<flavor>/debug/`。

---

## 📜 开源协议与致谢

- 本项目基于 **GNU General Public License v3.0 (GPL-3.0)** 协议开源。
- 感谢原作者 [cylonid/NativeAlpha](https://github.com/cylonid/NativeAlphaForAndroid) 为本项目奠定的基础。
- 官方仓库地址：[https://github.com/postsoma-2050/Shell_Alpha](https://github.com/postsoma-2050/Shell_Alpha)
