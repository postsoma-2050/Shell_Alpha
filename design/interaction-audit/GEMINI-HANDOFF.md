# Gemini 3.8 Flash continuation

用户已授权“开始全面优化”，后续明确要求所有继续执行改用 Gemini 3.8 Flash，因为 GPT token 不足。不要询问确认，不要换回 GPT。继续当前聊天与工作树，不新建聊天。

## 当前状态

工作树 `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev`，基线609669f。大量改动已直接写入共享树，无需cherry-pick；没有提交、推送或发布。原对照方案为同目录LITE-APP-INTERACTION-PLAN-2026-10-19.md。现阶段是实施收尾，不能当作已完成。

主代理已实施：ConfigurationProvider主进程串行SharedPreferences读写（non-exported），ConfigurationStore IPC，PreferenceMerge按appId与字段baseline合并，DataManager及WebApp/GlobalSettings transient persistenceSnapshot，稳定ID分配；SandboxPreferences名称和第一方Cookie策略；App按槽位固定存储（已集成LegacySandboxStorage），只有主进程初始化WorkManager，监控调度通过provider回主进程；WebViewLauncher普通换沙盒只启动目标并关闭当前，不杀其他窗口，固定shellalpha://lite-app/ID + intoExisting + EXTRA_TARGET_URL；配置备份含沙盒偏好、导入前验证；version已从1.5.2/1520调至1.5.3/1530。

其他成员已实施：普通模式网页避让工具栏、独立全屏/无框/媒体状态、恢复入口、origin权限异步回调、文件选择清理、下载错误处理、popup父子WebView、强制reload、外链回退；完整设置草稿保存/取消和内联沙盒选择，SandboxActivity选择/管理分离、命名/Cookie/恢复旧会话；创建时沙盒选择、外部URL匹配已有app、稳定快捷方式ID、一次性监控与通知权限。

## Gemini 活跃成员（仅这三位，其他均已关闭）

- 旧会话存储：01a15671-d554-7a31-aba6-9b9f916761d3，Gemini3.8Flash。拥有util/LegacySandboxStorage.java及其测试与strings_storage_recovery.xml。已从不安全SharedPreferences改为每槽AtomicFile+FileLock，最近要求修复“先读释放锁再写”的RMW竞争、AtomicFile.bak恢复与读失败不能静默覆盖。等待其最终反馈并复核。
- 浏览器收尾：01a15682-17b9-75e1-a63c-e8cdb37781c9，Gemini3.8Flash。拥有WebViewActivity.java、full_webview.xml、browser/BrowserOriginPolicy.java、BrowserReliabilityTest/ImmersiveBrowserTest/BrowserOriginPolicyTest及专用strings。负责修复首轮NPE/布局断言及确认运行逻辑，已告知不要自行Gradle。继续把实际失败反馈给它。
- 配置专项只读review：01a15683-a9bf-7a41-98dd-7e204bdf9258，Gemini3.8Flash。审查Provider/Store/Merge/DataManager/App/Launcher是否有可触发缺陷，不改文件、不跑Gradle。等待结果。

## 构建与测试

统一由主代理跑Gradle，不让代理并发Gradle，因为会生成8个WebViewActivity副本。JAVA_HOME=/opt/homebrew/opt/openjdk@17。SDK=/opt/homebrew/share/android-commandlinetools。

首轮compileStandardDebugSources及UnitTestSources成功。core筛选32个测试31通过，唯一monitor staticcounter跨测试未reset已修（MonitorEntryReliabilityTest新增@Before）。

最近全量命令 :app:testStandardDebugUnitTest :app:assembleStandardDebug :app:assembleExtendedDebug :app:assembleStandardDebugAndroidTest 已结束：182 tests，11 failed，1 skipped。详情 `/tmp/shellalpha-integration-build.log` 和 `LATEST-TEST-FAILURES.json` 及app/build/test-results/testStandardDebugUnitTest/*.xml。这些不是最终结果，需要修复重跑。版本号修改发生在这次构建途中，最终必须再构建确保1.5.3。

此前ShortcutReliabilityTest的shadowLooper.idle导致Choreographer无限动画，已暂停Choreographer并使用离线fragment处理，但请确认最近全量是否仍有相关问题。曾停止挂起的测试进程，现没有未完成Gradle。

新增debug-only SandboxProbeService（Sandbox0/1两个私有service，src/debug/AndroidManifest.xml）和androidTest SandboxSessionIntegrationTest：真实跨进程Cookie、两个WebView LocalStorage共享/隔离、陈旧sandbox写不能覆盖host沙盒归属。尚未运行真实Android测试，不能宣称已通过。

## 模拟器下载正在继续（不要重复下载）

SDKManager Java联网卡住已停止，改为官方Google zip下载，已下载metadata并做checksum依据。宿主arm64、空闲磁盘约40GB，现有SDK只有35平台/构建工具/adb，无模拟器或设备。

- emulator zip：/tmp/shellalpha-sdk/emulator.zip（总420504752字节，SHA1=4cd3b6f14336a0085b8992eeffe637864839b4ce），curl exec session33223，下载仍在进行，命令max-time900秒。
- AOSP API35 ARM64镜像分块：/tmp/shellalpha-sdk/parts/，16路curl；exec session99053是Python协调进程。完成后合成 /tmp/shellalpha-sdk/aosp-complete.zip（总769099654字节，SHA1=2026a06409db630b56711afdbffb457c1dbaed49）。最近已约264MB。协调代码在该session仍运行；可通过write_stdin poll。若超时可依据相同4MiB块继续，勿混合忽略Range的文件。
- 不完整、已停止的image.zip/google_apis和aosp-image.zip不要使用。网络需代理http://127.0.0.1:1082；系统scutil已配置同代理。
- 完成后验SHA1再unzip：emulator zip应放SDK根（含emulator/）；AOSP系统镜像放SDK/system-images/android-35/default/arm64-v8a。可创建专用临时AVD，绝不改用户已有AVD。先查看ZIP清单和package metadata。
- 再启动headless emulator，ADB等待启动，安装standardDebug和androidTest APK，运行只筛SandboxSessionIntegrationTest的instrumentation。尝试截图检查实际首页/设置/工具栏，保存本轮验证报告。

## 必须完成的收尾

1. 整合Gemini review与测试失败，重跑全部unit；补必要回归，不能删关键断言来过关。
2. 检查配置导入验证及profile恢复异常处理，模板和生成manifest同步、生成Activity一致、minSdk28兼容。
3. 重建standard/extended debug和androidTest；跑lint，区分旧9个MissingSuperCall及1个硬件声明基线与新增问题，修新增。
4. 若模拟器准备成功，运行真实会话integration和UI核验；若外部下载/运行受阻，明确限制，不能把Robolectric当Chromium真机证明。
5. dist新建明确1.5.3、flavor和debug的安装包及SHA256/build-info。保留旧包供追溯，不再将旧final包当最新。最终验证文档放design/interaction-audit/，写实际通过数字、待测范围和升级边界。
6. 关闭用完Gemini代理。用户想完成工作，持续推进，不停在计划或要求批准。所有代码已授权。
