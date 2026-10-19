# UI Refinement 最终 QA 与静态审阅报告 (2026-10-19)

- **执行代理与模型策略**: 实现与验证代理使用 Gemini 3.8 Flash 与免费 DeepSeek（WorkBuddy Free，实际服务 `deepseek-v4.1-flash`），主代理负责全局方向与结果验收。由 Gemini 3.8 Flash 代理独占执行最终 QA 验证、关键兼容性修复与全量代码审阅。
- **状态与备份**: 首页、设置、浏览、多语言翻译及预设种子代理均已全部关闭并停止写入，所有源码与资源实现处于完全冻结状态。初始修改前快照备份位于 `backups/ui-refinement-20261019-134449`。所有缺陷修复、统一构建、单元测试、打包语言核对与 Lint 分析已全部执行完毕。

---

## 一、 设计方向与 Hermit 参考资源对标

项目 UI/UX 与布局以 `/Users/grangerfdad/Desktop/hermit broswer`（Hermit 反编译资源）为主要设计对标方向。基于现有真实存在的参考布局文件进行静态核对，主要对标维度如下：

1. **首页图标网格**:
   - **Hermit 真实参考**: `decompiled_hermit/resources/res/layout/item_lite_app_grid.xml`，采用统一卡片间距、居中图标与紧凑标签结构。
   - **本项目对齐**: 首页采用自适应网格，在窄屏与宽屏 (`w480dp`) 配置下独立处理，网格每行由屏幕宽度进行静态约束，单列或多列时内容居中，真实排版与换行待实机设备验证。
2. **药丸搜索框 (Pill Search)**:
   - **Hermit 真实参考**: `decompiled_hermit/resources/res/layout/view_search_query_editor.xml`，采用胶囊药丸轮廓与内嵌功能按钮。
   - **本项目对齐**: 首页与主操作栏采用药丸造型搜索框，集成搜索引擎占位符解析及输入清洗，符合 Material Design 规范。
3. **大标题分区设置 (Sectioned Settings)**:
   - **Hermit 真实参考**: `decompiled_hermit/resources/res/layout/view_settings_header.xml`、`quick_settings_top_header.xml` 与 `fragment_quick_settings.xml`，采用大字号主标题、功能卡片与清晰分隔线。
   - **本项目对齐**: `SettingsActivity` 与 `WebAppSettingsActivity` 采用大标题与功能分组卡片，抽离硬编码英文字符串至双语资源，强化分区层级。
4. **侧边快捷抽屉 (Quick Settings Drawer)**:
   - **Hermit 真实参考**: `decompiled_hermit/resources/res/layout/fragment_quick_settings.xml`、`item_drawer.xml`，抽屉内嵌紧凑快捷操作开关。
   - **本项目对齐**: 浏览器侧边抽屉采用固定与动态宽度适配，快捷操作项具有统一行高与状态回显。
5. **色盘与 48dp 触控热区保底**:
   - **对标规范**: Android / Material Design 触控目标尺寸基准（最小 48dp × 48dp）。
   - **本项目对齐**: `WebAppSettingsActivity` 色盘动态计算列数时消除了 4dp 末列间距溢出；`add_website_dialogue.xml` 与 `shortcut_dialog.xml` 根容器包裹 `<ScrollView>` 并显式声明 `minHeight="48dp"` 保底；`CircularProgressBar` 解除硬编码黑色，自适应 Material 动态主色。
6. **视觉风格一致性**:
   - 简体中文（`zh-rCN`）文案与预设应用（PostSoma，`https://postsoma-2050.website/`）完全继承既有 Material Design 3 / Hermit 主题规范与颜色系统，未引入相斥的视觉或排版样式。
   - **审阅约束**: 不做任何未经实机证实的高保真像素重合或视觉动效断言，结论均基于 XML 布局树与属性定义的静态分析。

---

## 二、 构建输出与打包语言验证

使用 OpenJDK 17 (`JAVA_HOME=/opt/homebrew/opt/openjdk@17`) 统一执行 `./gradlew assembleStandardDebug` 成功。

### 1. APK 产物路径与规格
- **通用架构包 (Universal APK)**:
  `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/build/outputs/apk/standard/debug/ShellAlpha-standard-universal-debug.apk`
  - 文件大小: 25,474,370 字节
  - 时间戳: 2026-10-19 14:22:51
- **架构分包**:
  - arm64-v8a: `app/build/outputs/apk/standard/debug/ShellAlpha-standard-arm64-v8a-debug.apk` (21,833,264 字节)
  - armeabi-v7a: `app/build/outputs/apk/standard/debug/ShellAlpha-standard-armeabi-v7a-debug.apk` (20,976,826 字节)
  - x86_64: `app/build/outputs/apk/standard/debug/ShellAlpha-standard-x86_64-debug.apk` (21,911,369 字节)
  - x86: `app/build/outputs/apk/standard/debug/ShellAlpha-standard-x86-debug.apk` (21,552,578 字节)

### 2. 打包语言独立验证 (`aapt dump`)
在 `app/build.gradle` 中配置 `resourceConfigurations += ['en', 'zh-rCN']`，经 Android SDK 35.0.0 `aapt` 工具实测解包验证：
- `aapt dump configurations` 实测输出语言配置项仅包含 `zh-rCN` 与无限定默认配置（英文 `en`）。
- `aapt dump badging` 实测输出: `locales: '--_--' 'zh-CN'`。
- 证实德语、西班牙语、意大利语等非目标语言资源已完全被剥离，未打包进最终 APK。

---

## 三、 单元测试准确统计 (Robolectric / JUnit)

执行 `./gradlew testStandardDebugUnitTest` 结果如下：
- **总测试用例数**: **47**
- **通过 (Passed)**: **46**
- **失败 (Failures)**: **0**
- **跳过/忽略 (Skipped)**: **1** (`RoboTests.initializationError`，历史遗留空测试类)

### 详细测试用例分布
| 测试类 | 用例数 | 通过数 | 失败数 | 耗时 | 测试范畴与关键验证项 |
|---|---|---|---|---|---|
| `PresetLiteAppTest` | 6 | 6 | 0 | 0.206s | 预设 PostSoma 种子首次写入、重复加载防重、删除不重生、已有数据保护、**空备份还原保留与数值分型还原 (修复项)** |
| `SearchEngineUrlTest` | 9 | 9 | 0 | 0.020s | 搜索 URL 模板占位符、多占位符拒绝、特殊字符与空格编码、无效 Host 过滤与回退策略 |
| `SettingsLayoutTest` | 7 | 7 | 0 | 2.568s | 小屏手机布局测量、色盘全部色块节点挂载、SDK 28 备份流式读取安全校验 |
| `DateUtilsTest` | 10 | 10 | 0 | 0.013s | 时间日期解析转换与边界处理 |
| `UtilUnitTests` | 7 | 7 | 0 | 19.483s | 桌面快捷方式与 Favicon 解析提取逻辑 |
| `UrlSanitizerTest` | 5 | 5 | 0 | 0.007s | URL 协议清洗与安全性验证 |
| `ImmersiveBrowserTest` | 2 | 2 | 0 | 8.099s | 沉浸式交互及抽屉状态行为测量 |
| `RoboTests` | 1 | 0 | 0 (1 skipped) | 0.000s | 既有预留空类 |

---

## 四、 本轮关键缺陷修复细节

1. **备份 JSON 还原数值分型回归修复 (`DataManager.java`)**:
   - **缺陷现象**: `PresetLiteAppTest.restoredEmptyListIsPreservedWithoutSeed` 出现 `java.lang.ClassCastException: class java.lang.Float cannot be cast to class java.lang.Integer at SharedPreferencesImpl.getInt`。
   - **根因分析**: 在 `loadSharedPreferencesFromJson` 中原代码使用 `value = number == Math.rint(number) ? (long) number : number;`，Java 条件运算符在两分支类型不同（`long` 与 `double`）时会将结果数值自动提升为 `double`，导致装箱为 `Double`；在随后的 `writeSharedPreferences` 中被作为 `Float` 存入 SharedPreferences，后续调用 `getInt(MAX_ID)` 时抛出类型转换异常。
   - **修复实现**:
     1. 在 `loadSharedPreferencesFromJson` 中改用显式类型分支：在整型数值范围内调用 `Integer.valueOf((int) number)`，超出整型范围调用 `Long.valueOf((long) number)`，非整数调用 `Float.valueOf((float) number)`。
     2. 在 `writeSharedPreferences` 中显式定义已知整型键集合 `knownIntKeys`（`MAX_ID`、`NEXT_CONTAINER`、`UITheme`），无论输入何种 `Number` 均强制调用 `prefEdit.putInt(key, ((Number) value).intValue())`，消除类型漂移。
2. **API 28-32 备份文件读取兼容性修复 (`DataManager.java`)**:
   - 将 `inputStream.readAllBytes()` 重构为 8KB 循环缓冲读取至 `ByteArrayOutputStream`，避免在 Android 28~32 设备上导入备份文件时因找不到 API 33 方法抛出 `NoSuchMethodError`。
3. **清除数据提示文案与沙箱隔离边界确认**:
   - 明确将清除确认与成功文案限定为“非沙箱 Lite Apps 共享的 Cookies 与网站存储”，保留原功能边界，不跨进程操作由 `setDataDirectorySuffix` 隔离的各沙箱容器目录与 WebView 独立缓存。
4. **设置页色盘测量空安全与列间距预算**:
   - 修复 `width?.takeIf { it > 0 }` 避免可空类型直接比较的编译错误；非行末列配置 `marginEnd = gap`，末列设为 0，消除 4dp 边界溢出。
5. **创建对话框基础触控与日夜自适应**:
   - `shortcut_dialog.xml` 与 `add_website_dialogue.xml` 添加 `<ScrollView>` 容器，关键交互项补齐 `minHeight="48dp"`；解绑 `CircularProgressBar` 黑色硬编码，自适应主题主色。

---

## 五、 Lint 静态代码分析与基线对比

执行 `./gradlew lintStandardDebug`，**任务因项目默认 `abortOnError = true` 且存在历史基线错误而退出失败 (FAILED)**。

### 1. 问题分类统计
- **总 Errors**: **10**
- **总 Warnings**: **349**

### 2. 详细错误归属与分析
- **MissingTranslation (0 处)**:
  在 `app/lint.xml` 中配置 `<issue id="MissingTranslation"><option name="locales" value="en,zh-rCN" /></issue>`，并在构建中限定产品语言后，英语与简体中文资源均已完整配齐，本轮无新增多语言缺失错误。
- **NewApi (0 处)**:
  `DataManager` 中 `readAllBytes()` 已彻底重构，新 API 崩溃风险已完全消除。
- **MissingSuperCall (9 处，历史基线错误)**:
  `WebViewActivity.java:1186` 中 `onBackPressed()` 覆盖未调用 `super.onBackPressed`，经构建生成任务复制到 `__WebViewActivity_0` ~ `7`，共 9 处。此为既有代码逻辑，本轮未予扩大化修改。
- **PermissionImpliesUnsupportedChromeOsHardware (1 处，历史基线错误)**:
  Manifest 声明的相机权限在 ChromeOS 硬件特性上的静态警告。

---

## 六、 颜色与对比度实测数据 (静态计算)

使用脚本提取 `values/colors.xml` 与 `values-night/colors.xml` 实际色值并计算相对亮度与对比度，实测数据如下：

### 1. 浅色模式 (Light Theme)
| 色彩配对 | 前景色值 | 背景色值 | 实测对比度 | 达标等级 |
|---|---|---|---|---|
| `onSurface` / `surface` | `#231918` | `#FFF8F7` | **16.36:1** | AA/AAA |
| `onSurfaceVariant` / `surface` | `#534341` | `#FFF8F7` | **8.91:1** | AA/AAA |
| `onPrimary` / `primary` (按钮文字/背景) | `#FFFFFF` | `#904A43` | **6.47:1** | AA |
| `onSecondaryContainer` / `secondaryContainer` | `#5D3F3C` | `#FFDAD6` | **7.25:1** | AA/AAA |

### 2. 深色模式 (Night Theme)
| 色彩配对 | 前景色值 | 背景色值 | 实测对比度 | 达标等级 |
|---|---|---|---|---|
| `onSurface` / `surface` | `#ECE1D4` | `#17130B` | **14.36:1** | AA/AAA |
| `onSurfaceVariant` / `surface` | `#D2C4B4` | `#17130B` | **10.84:1** | AA/AAA |
| `onPrimary` / `primary` (按钮文字/背景) | `#432C00` | `#F0BE6D` | **7.69:1** | AA/AAA |
| `onSecondaryContainer` / `secondaryContainer` | `#F8DFBB` | `#55442A` | **7.24:1** | AA/AAA |

结论：所列主要语义色对比度均达到 AA（部分达 AAA）标准，属于静态资源定义的色彩约束；物理显示屏上的真实感知渲染待设备环境验证。

---

## 七、 运行验证限制与待实机验收清单

- **环境限制说明**: 当前宿主机无物理连接的 ADB 移动设备，无运行中的 Android 模拟器运行时，未安装系统镜像。所有验证均基于无头 JVM、Robolectric 环境与静态分析，不能推断为完整的真实硬件运行证据。
- **静态风险提示（非断言崩溃）**:
  1. **输入法弹起避让**: 对话框与设置页底部控件在较小屏幕软键盘弹起时的实际遮挡与滚动流畅度。
  2. **无障碍焦点流**: TalkBack 模式下药丸搜索框、色盘色块与侧边抽屉按钮的无障碍朗读标签与焦点导航连续性。
  3. **实际设备系统语言切换**: 当系统语言由英文切换为中文时，动态更新 Activity 的文字重绘与字符串占位符渲染。
  4. **首屏冷启动渲染**: 首次启动写入 PostSoma 种子后的首帧刷新耗时与图标异步拉取体验。
