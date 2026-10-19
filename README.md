<p align="center">
  <img src="graphics/logo.png" width="120" height="120" alt="ShellAlpha Logo" />
</p>

<h1 align="center">ShellAlpha</h1>

<p align="center">
  <strong>安全、轻量、多进程沙盒隔离的 Android 轻应用（Lite Apps）容器与隐私浏览器</strong><br>
  <em>A Privacy-First, Multi-Process Sandboxed Lite Apps Container & Browser for Android</em>
</p>

<p align="center">
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Platform"></a>
  <a href="https://developer.android.com/about/versions/15"><img src="https://img.shields.io/badge/Target_SDK-35-blue.svg?style=flat-square" alt="Target SDK"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-orange.svg?style=flat-square" alt="License"></a>
  <a href="https://github.com/postsoma-2050/Shell_Alpha"><img src="https://img.shields.io/badge/GitHub-postsoma--2050%2FShell__Alpha-141F5A?style=flat-square&logo=github" alt="Repository"></a>
</p>

---

## 💡 品牌定位与理念

**ShellAlpha（寄居蟹）** 的设计哲学如同自然界中的寄居蟹为柔软身躯寻找坚固保护壳：

在流氓 SDK 泛滥、臃肿原生应用充斥内存与后台的移动互联网环境中，许多日常服务只需要纯粹的 Web 内容。**ShellAlpha** 致力于将常用网站转化为兼具**原生级交互体验**与**极致数据安全**的轻应用（Lite Apps）：

- **坚壳防护（0 过度索权）**：无需读取通讯录、通话记录、位置或硬件标识符，设备权限完全由用户主导。
- **轻巧随行（极速启动）**：告别数百兆安装包与后台常驻服务唤醒链，瞬间冷启动，资源随用随释。
- **沙盒隔离（切断关联）**：基于 Android 独立多进程沙盒，在底层切断站点追踪与跨站关联。

---

## ✨ 核心特性一览

### 1. 🐚 Hermit 风格双模式主页
- **自适应双布局**：提供**四列紧凑宫格（Grid）**与**单列横向列表（List）**双模态无缝切换。
- **便捷独立控制**：在列表模式中，每个站点右侧均配备**独立设置齿轮**与快捷菜单按钮，修改沙盒、配置参数一步直达。
- **彩色沙盒圆点**：直观展示每个 Lite App 所属的沙盒容器编号与颜色，数据边界一目了然。
- **原生手势交互**：单击秒级打开，长按唤出操作面板（创建桌面快捷方式、修改沙盒、手动排序）。

### 2. 🛡️ 8 个独立多进程沙盒隔离
- **OS 级进程物理隔离**：内置 8 个独立的沙盒进程（`:web_sandbox_0` ~ `:web_sandbox_7`），每个沙盒运行在独立的内存与 Dalvik/ART 空间。
- **数据存储绝对分立**：每个沙盒分配专属的 WebView 数据目录，Cookies、LocalStorage、Cache 互不可见，杜绝跨站点账号串通。
- **多账号同台并存**：同一服务（如社交、邮箱、办公协作）可分别放置在不同沙盒中，轻松实现多账号互不干扰并发运行。

### 3. 🎵 全进程后台媒体播放 (Background Media Playback)
- **全沙盒前台服务守候**：默认主进程及全部 8 个沙盒进程均配备专属前台媒体守护服务（`BackgroundPlaybackService$Sandbox*`）。
- **熄屏/锁屏无缝畅听**：完美支持 YouTube、Bilibili、网易云、播客与通用 HTML5 音视频在切出后台或屏幕关闭时无损继续播放。
- **标准系统通知集成**：完整支持 Android 系统级锁屏面板与通知栏媒体控制器，随手控制播放与停止。

### 4. 🔒 深度隐私治理与生命周期防护
- **URL 追踪参数深度净化**：自动剥离常见的 `utm_*`、`fbclid`、`gclid` 等追踪信标，守护浏览私密性。
- **退出即清 Cookie**：接入 Activity 销毁生命周期（`onDestroy`），关闭应用时彻底清空 Cookie 与本地持久化；切后台临时查看验证码不受影响。
- **生物识别安全锁**：支持针对特定 Lite App 配置系统指纹/面容/锁屏密码拦截。
- **防死循环机制**：底层内置定时自动刷新下限安全守护（最低 5 秒），杜绝 0 秒无限重刷导致的主线程卡死。

### 5. 🚀 高性能内容拦截与扩展
- **内置 Adblock 规则引擎**：深度集成 LibAdblockWebView，支持自定义订阅规则，有效剔除侵入式广告与恶意追踪脚本。
- **UserScripts 用户脚本注入**：支持按需为指定站点注入个性化 JavaScript 脚本，实现界面暗色美化与定制增强。
- **智能 Favicon 负缓存**：具备失败冷却退避机制（`failedCooldownMap`），彻底根治传统浏览器因缺失图标反复触发列表重绘的性能缺陷。

---

## 🏗️ 架构与工程规范

- **语言栈**：Kotlin + Modern Java (Java 17)
- **构建工具**：Gradle 8.10.2 + Android Gradle Plugin 8.7.2
- **兼容性**：最低支持 Android 9.0 (API Level 28)，深度优化 Android 15 (Target SDK 35)
- **健康度指标**：**0 Android Lint Errors**，**89 项单元测试通过**，彻底清理历史孤立代码与废弃资产。

### 产品变体 (Flavors)

| Flavor | 特性描述 | 适用场景 |
| :--- | :--- | :--- |
| **`standard`** | 纯净单进程容器，保持极简资源占用 | 基础浏览、日常轻度使用 |
| **`extendedGithub`** | 完整包含 8 容器多进程沙盒物理隔离与扩展功能 | 深度多账号隔离、隐私安全极客首选 |

---

## 🛠️ 构建与测试指南

### 1. 运行自动化单元测试
```bash
./gradlew testStandardDebugUnitTest
```

### 2. 运行 Lint 静态代码质量门禁
```bash
./gradlew lintStandardDebug
```

### 3. 构建 Debug 安装包
```bash
# 构建 Standard 渠道调试包
./gradlew assembleStandardDebug

# 构建 ExtendedGithub 渠道调试包（包含多进程沙盒特性）
./gradlew assembleExtendedGithubDebug
```
编译生成的 APK 位于 `app/build/outputs/apk/<flavor>/debug/`。

---

## 📜 开源协议与致谢

- 本项目依据 **GNU General Public License v3.0 (GPL-3.0)** 协议开源。
- 感谢原作者 [cylonid/NativeAlpha](https://github.com/cylonid/NativeAlphaForAndroid) 为本项目奠定的基础，以及 Hermit 带来的交互与体验灵感。
- 官方仓库地址：[https://github.com/postsoma-2050/Shell_Alpha](https://github.com/postsoma-2050/Shell_Alpha)

[![Android Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target_SDK-35-blue.svg?style=flat-square)](https://developer.android.com/about/versions/15)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-orange.svg?style=flat-square)](LICENSE)
[![GitHub Repository](https://img.shields.io/badge/GitHub-postsoma--2050%2FShell__Alpha-141F5A?style=flat-square&logo=github)](https://github.com/postsoma-2050/Shell_Alpha)

> **ShellAlpha（寄居蟹）** —— 像寄居蟹保护软体一样，为每一个常用网站披上安全、独立、轻盈的专属坚壳。将 Web 应用转化为兼具原生质感与极致私密的 Lite Apps。

---

## 品牌理念与定位

在流氓 SDK 泛滥与原生应用日渐臃肿的时代，许多日常服务只需要一个纯净的 Web 视图即可满足需求。**ShellAlpha** 深度重塑了移动端 Web App 容器体验：

- **拒绝过度索权**：无需读取通讯录、通话记录或设备敏感标识，原生守护设备纯净。
- **极致资源占用**：无需安装数百兆的原生安装包与后台常驻守护，极速启动与销毁。
- **多维度物理隔离**：基于多进程独立沙盒，彻底切断跨网站追踪与数据串通。

---

## 核心特性

### 1. Hermit 双模式主页
- **双模自适应布局**：支持**四列紧凑宫格（Grid）**与**单列横向列表（List）**一键无缝切换。
- **列表模式便捷操作**：在单列横向列表模式中，每个站点右侧提供**专属设置齿轮**与快捷菜单按钮，参数调校一步直达。
- **沙盒色彩点提示**：通过彩色圆点直观呈现站点所属沙盒容器，一目了然。
- **原生触控流转**：单击启动、长按呼出丰富管理菜单（沙盒切换、创建桌面快捷方式、排序上移/下移）。

### 2. 8 个多进程沙盒物理隔离
- **独立进程运行**：内置 8 个相互独立的沙盒多进程环境（`:web_sandbox_0` ~ `:web_sandbox_7`）。
- **数据物理隔离**：每个沙盒分配专属独立 WebView 数据存储目录，Cookies、LocalStorage、IndexedDB 完全隔离，杜绝跨站点账号关联与跟踪。
- **多账号同时在线**：同一网站可分别加入不同沙盒，实现多账号互不干扰并发运行。

### 3. 全进程后台媒体播放 (Background Media Playback)
- **多进程前台媒体服务**：不仅主进程，全部 8 个沙盒进程均配备后台媒体播放服务（`BackgroundPlaybackService$Sandbox*`）。
- **锁屏熄屏畅听**：完美支持 YouTube、Bilibili、播客等音视频网站切出后台或屏幕熄灭时无缝继续播放。
- **系统媒体通知集成**：支持系统锁屏面板与通知栏标准媒体控制（播放/暂停/跳过）。

### 4. 极致隐私与生命周期管理
- **URL 追踪参数清洗**：自动剥离常见的 `utm_*`、`fbclid`、`gclid` 等追踪参数，防范隐私泄露。
- **退出即清 Cookie**：接入 Activity 销毁声明周期（`onDestroy`），退出时彻底清除 Cookie 与存储，不影响切后台正常使用。
- **生物识别安全锁**：支持针对特定 Lite App 开启指纹/面容/锁屏密码验证。
- **防 0 秒死循环守护**：内置定时刷新安全下限兜底（不少于 5 秒），从机制上杜绝主线程卡死风险。

### 5. 内置 Adblock & 自定义脚本 (Userscripts)
- **高性能内容拦截**：集成 Adblock 规则引擎，支持订阅多套网络规则（如 Fanboy 过滤规则），减少干扰、加速加载。
- **Userscripts 注入**：支持按站点配置自定义 JavaScript 脚本，实现无缝网页增强与样式微调。
- **智能 Favicon 抓取**：采用多梯队图标抓取机制与失败冷却负缓存（`failedCooldownMap`），杜绝无效循环请求。

---

## 架构与工程规范

- **开发语言**：Kotlin + Java
- **构建系统**：Gradle 8.10.2 + Android Gradle Plugin 8.7.2
- **最低支持**：Android 9.0 (API Level 28)
- **目标版本**：Android 15 (API Level 35)
- **Java 环境**：OpenJDK 17

### 产品变体 (Flavors)

| Flavor | 说明 |
| :--- | :--- |
| **`standard`** | 纯净开源基础版本，适合日常极简使用与定制打包。 |
| **`extendedGithub`** | 包含 8 容器多进程沙盒与完整扩展特性的推荐发行版。 |

---

## 构建与测试指南

### 1. 运行单元测试
```bash
./gradlew testStandardDebugUnitTest
```

### 2. 静态代码分析与 Lint 检查
```bash
./gradlew lintStandardDebug
```

### 3. 构建 Debug 安装包
```bash
# 构建标准版 Debug APK
./gradlew assembleStandardDebug

# 构建扩展版 Debug APK（包含多进程沙盒完整特性）
./gradlew assembleExtendedGithubDebug
```
构建生成的安装包位于 `app/build/outputs/apk/` 目录下。

---

## 开源协议与致谢

- 本项目采用 **GNU General Public License v3.0 (GPL-3.0)** 协议开源。
- 感谢 [NativeAlpha](https://github.com/cylonid/NativeAlphaForAndroid) 作者 cylonid 奠定的优秀基础，以及 Hermit 带来的交互灵感。
- 项目主页：[https://github.com/postsoma-2050/Shell_Alpha](https://github.com/postsoma-2050/Shell_Alpha)
