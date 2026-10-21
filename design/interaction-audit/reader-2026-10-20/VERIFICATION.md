# Reader Mode

## 参考基准

先检查 Hermit 的 ReaderFragment、fragment_reader.xml 与 dialog_reader_settings.xml：独立阅读界面、正文标题与来源、返回原网页、字号、Light / Sepia / Dark / Black 配色。反编译 y90/c0 的正文提取部分不完整，不能声称直接复用了 Hermit 完整解析器。

提取使用 Mozilla Readability 原始代码，固定提交及授权位于 app/src/main/assets/reader/UPSTREAM.md；LICENSE.md、NOTICE 同目录保留。上游 https://github.com/mozilla/readability 使用 Apache-2.0，并明确建议对 document 的克隆解析、对输出另行消毒。

## 实现与边界

- 有栏模式快捷面板新增 Reader mode。后续按用户确认，无边框通过右下边缘滑入的独立网页工具面板访问阅读模式，默认仍只显示网站。
- 用户点击时才运行正文提取；克隆当前已载入页面，不额外向服务器重抓文章，不重置登录或原网页。
- DOM / 输出大小限制；后台 Jsoup 消毒后在独立本地 WebView 呈现正文，禁用 JavaScript、网络、文件和内容访问。此版以文字阅读为主，不加载远程图片/视频。
- 支持 A− / A+（16–32px），亮色、羊皮纸、深色、纯黑循环，保存阅读偏好；调整字号配色按相对位置恢复阅读进度。
- 原页面隐藏并调用 onPause，退出时恢复同一 WebView，保留会话与滚动位置；原网址仍可复制和分享。
- 页内查找在阅读模式中搜索提取后的正文。返回先关闭查找，再退出阅读；普通文章链接回到原网站 WebView，沿用该沙盒策略。
- 切换页面、弹窗、媒体全屏、无边框或 Activity 销毁时清理；没有轮询、定时提取或常驻后台任务。12 秒超时只用于单次提取。
- 无可提取正文或网页关闭 JavaScript 时提示原因，不篡改网站或强行启用 JavaScript。

## 验证

Reader 初始实现已通过 267 项单元测试（0 失败、0 错误、1 跳过）。Pixel 7a 的原生正文提取、配色/字号、阅读文档内原生查找、退出恢复原网页，以及无边框视口回归均通过（/tmp/reader-device-final.log，2 tests）。本轮无边框工具入口变更见 frameless-tools-2026-10-20；真实新闻文章阅读的视觉验收未在该初始自动夹具中覆盖。
