<p align="center">
  <img src="graphics/logo.png" width="128" height="128" alt="ShellAlpha Logo" />
</p>

<h1 align="center">ShellAlpha</h1>

<p align="center">
  <strong>安全、轻量、多进程沙盒隔离的 Android 轻应用容器</strong><br>
  <em>Privacy-First, Multi-Process Sandboxed Lite Apps Container for Android</em>
</p>

<p align="center">
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Platform"></a>
  <a href="https://developer.android.com/about/versions/15"><img src="https://img.shields.io/badge/Target_SDK-35-blue.svg?style=flat-square" alt="Target SDK"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-orange.svg?style=flat-square" alt="License"></a>
  <a href="https://github.com/postsoma-2050/Shell_Alpha"><img src="https://img.shields.io/badge/GitHub-postsoma--2050%2FShell__Alpha-141F5A?style=flat-square&logo=github" alt="Repository"></a>
</p>

---

## 💡 项目简介

**ShellAlpha（寄居蟹）** 是一款面向 Android 平台的轻应用（Lite Apps）容器与隐私浏览器。在原生应用动辄占用数百兆内存并索取通讯录等敏感权限的背景下，许多日常网络服务只需纯粹的 Web 视图即可满足需求。

ShellAlpha 如同为脆弱的 Web 视图披上一层坚固的保护壳，将网页封装为兼具原生质感、独立数据沙盒与后台媒体播放能力的轻应用。

---

## ✨ 核心特性

### 1. 🐚 Hermit 风格双模式主页
- **宫格与列表双模态**：支持四列紧凑宫格（Grid）与单列横向列表（List）一键无缝切换。
- **列表独立设置入口**：在横向列表模式中，每个轻应用右侧提供专属**设置齿轮**与操作菜单，无需长按即可一步直达站点参数配置。
- **沙盒颜色直观标识**：通过彩色圆点直观呈现站点所属的沙盒容器编号，运行边界一目了然。

### 2. 🛡️ 8 个多进程沙盒物理隔离
- **OS 级独立多进程**：内置 8 个完全独立的沙盒进程（`:web_sandbox_0` ~ `:web_sandbox_7`），运行于独立的系统内存空间。
- **存储数据物理分立**：每个沙盒拥有独立的 WebView 数据目录，Cookies、LocalStorage 与缓存互不可见，切断跨站点追踪，支持同站点多账号互不串扰并存。

### 3. 🎵 全进程后台媒体播放 (Background Media Playback)
- **全沙盒前台服务守候**：默认主进程及全部 8 个沙盒进程均配备专属媒体前台服务。
- **锁屏熄屏不中断**：支持 YouTube、Bilibili、网易云、播客及通用 HTML5 音视频切出后台或屏幕熄灭时无缝继续播放。
- **系统媒体控制器集成**：无缝对接 Android 系统锁屏面板与通知栏媒体控件。

### 4. 🔒 深度隐私治理与运行加固
- **URL 追踪参数自动清洗**：自动剥离链接中的 `utm_*`、`fbclid`、`gclid` 等追踪信标。
- **退出清除 Cookie**：接入 Activity 销毁生命周期（`onDestroy`），彻底清空 Cookie 与本地持久化，临时切后台不丢失会话。
- **防死循环机制**：Favicon 抓取引入 60 秒失败冷却负缓存，杜绝重复空转；定时刷新增加最低 5 秒安全下限；修复非连续 ID 寻址越界。
- **内置 Adblock 与 UserScripts**：集成规则拦截引擎去除侵入式广告，支持为特定站点注入自定义 JavaScript 脚本。

---

## 🏗️ 架构与工程规范

- **开发语言**：Kotlin + Modern Java (Java 17)
- **构建系统**：Gradle 8.10.2 + Android Gradle Plugin 8.7.2
- **系统兼容**：最低支持 Android 9.0 (API Level 28)，深度适配 Android 15 (Target SDK 35)
- **健康度指标**：**0 Android Lint Errors**，**89 项单元测试通过**，彻底清理历史孤立代码与废弃资产。

### 产品变体 (Flavors)

| 变体 Flavor | 特性描述 | 适用场景 |
| :--- | :--- | :--- |
| **`standard`** | 纯净单进程容器，保持极简资源占用 | 基础浏览与日常轻度使用 |
| **`extendedGithub`** | 完整包含 8 容器多进程沙盒物理隔离与扩展特性 | 深度多账号隔离、隐私安全极客首选 |

---

## 🛠️ 构建与测试

```bash
# 1. 运行自动化单元测试 (89 tests)
./gradlew testStandardDebugUnitTest

# 2. 运行 Lint 静态代码质量检查 (0 Errors)
./gradlew lintStandardDebug

# 3. 编译 Standard 渠道 Debug 安装包
./gradlew assembleStandardDebug

# 4. 编译 ExtendedGithub 渠道 Debug 安装包（含多进程沙盒特性）
./gradlew assembleExtendedGithubDebug
```
编译产物位于 `app/build/outputs/apk/<flavor>/debug/`。

---

## 📜 开源协议与致谢

- 本项目依据 **GNU General Public License v3.0 (GPL-3.0)** 协议开源。
- 感谢原作者 [cylonid/NativeAlpha](https://github.com/cylonid/NativeAlphaForAndroid) 为本项目奠定的基础，以及 Hermit 带来的交互与体验灵感。
- 官方仓库地址：[https://github.com/postsoma-2050/Shell_Alpha](https://github.com/postsoma-2050/Shell_Alpha)
