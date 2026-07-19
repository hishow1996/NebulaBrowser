# 星云浏览器 · NebulaBrowser

> 原生 Android 浏览器应用，集成插件系统、油猴脚本、视频悬浮播放、小说/漫画阅读器、AI 聊天、多搜索引擎。
> 白色现代化设计 + 日夜模式切换（默认白天）。

## 特性总览

| 模块 | 说明 |
|---|---|
| 🌐 浏览器核心 | 多标签、地址栏、独立进程 WebView、广告拦截、Cookie 管理 |
| 🐵 油猴脚本 | 解析 + 注入 + GM_* API；支持 GreasyFork / OpenUserJS / 本地导入 / 推荐脚本 |
| 🧩 插件系统 | Kiwi 风格 Chrome WebExtension（manifest v2/v3）+ Chrome Store 一键安装 + chrome.* API 模拟 |
| 🎬 视频 URL 拦截 | shouldInterceptRequest 自动识别 m3u8/mp4/ts/flv 等 |
| 📺 悬浮播放器 | 全局悬浮（桌面 + 任意 App）、右下拖拽柄任意缩放、3s 自动隐藏、拖拽柄常驻 |
| 🎞 内置播放器 | 画质选择 + CC 字幕（含自动翻译）+ 速度 + 全屏；画质同步到悬浮窗 |
| 🌐 视频下载 | newpipe-extractor + yt-dlp（assets 二进制）+ ffmpeg-kit 合并降质 |
| 🔤 CC 字幕 | 内嵌轨/外挂/在线搜索/ASR 4 种来源；Google 非官方 + DeepSeek 兜底翻译 |
| 📖 小说/漫画阅读器 | legado 兼容书源、Readability.js 注入提取、自有 RuleEngine |
| 📚 书架 | Compose 网格书架、分组、阅读进度、置顶 |
| 🤖 AI 助手 | 默认 DeepSeek-V3 沉默用户模式 + 多 Profile + SSE 流式 + Markwon |
| 🔍 多搜索引擎 | 16 个内置（Google/Bing/百度/DDG/搜狗/360/Brave/Yandex/知乎/B站等） + 自定义 |
| 🌗 主题 | 默认白天，可切换“跟随系统/白天/夜间”；夜间不影响视频与代码视区 |
| 💎 UI | Material You + 大留白 + 圆角胶囊 + 蓝紫渐变 + 思源黑体 |

## 技术栈

- Kotlin 1.9 + Coroutines + Flow
- Android System WebView（渲染）+ Media3 ExoPlayer（视频）
- Jetpack Compose + XML ViewBinding（混合）
- Room + OkHttp + Retrofit + Moshi + kotlinx.serialization
- Media3（HLS/DASH）+ newpipe-extractor + yt-dlp binary + ffmpeg-kit-full
- Mozilla Readability.js + legado 兼容书源协议
- OpenAI 兼容 API + SSE + Markwon + prism4j
- QuickJS-Android（书源 JS 规则扩展）
- DataStore + EncryptedSharedPreferences

## 工程结构

```
app/src/main/java/com/nebula/browser/
├── App.kt                        入口 Application
├── MainActivity.kt               单 Activity 壳 + 底部导航
├── common/                       AppContext / 权限 / 文件 / URL 工具
├── browser/                       浏览器主模块
│   ├── BrowserFragment            主页
│   ├── BrowserViewModel
│   ├── tab/TabManager             多标签管理
│   ├── webview/NebulaWebView     自定义 WebView + WebViewClient/ChromeClient
│   ├── menu/BrowserMenuSheet      底部 Sheet 菜单
│   └── downloader/DownloadService 前台下载服务
├── userscript/                   油猴脚本
│   ├── model/UserScript           元数据 + 脚本
│   ├── parser/UserScriptParser    ==UserScript== 块解析器
│   ├── matcher/UrlMatcher        @match / @include URL 匹配
│   ├── injector/UserScriptInjector 三种注入时机 + GM API 包装
│   ├── gmbridge/GmBridge          addJavascriptInterface Java 桥
│   ├── store/UserScriptStore      脚本存储 + 一键安装
│   ├── repo/GreasyForkRepository  GreasyFork / OpenUserJS
│   └── ui/UserScriptManagerActivity 已安装列表
├── plugin/                       Kiwi 风格 Chrome 扩展系统
│   ├── model/WebExtensionManifest  manifest.json v2/v3 解析
│   ├── model/ExtensionPackage      已安装扩展包
│   ├── model/ExtensionId           Chrome 算法生成扩展 ID
│   ├── core/ExtensionRegistry      已安装扩展注册表 + 持久化
│   ├── core/ExtensionInstaller     统一安装入口（CRX/ZIP/目录/WebStore）
│   ├── core/ChromeWebStoreClient   Chrome Web Store 客户端
│   ├── crx/CrxUnpacker             CRX3/ZIP 解包器
│   ├── inject/ContentScriptInjector content_scripts 注入到 WebView
│   ├── inject/BackgroundScriptRunner 隐藏 WebView 运行后台脚本
│   ├── api/ChromeApiBridge         chrome.* API 模拟（runtime/storage/tabs/cookies/...）
│   ├── ui/ExtensionsManagerActivity chrome://extensions 风格管理页
│   ├── ui/ChromeWebStoreActivity   内置 Chrome Web Store 浏览页
│   ├── ui/ExtensionPopupActivity   action.default_popup 弹窗
│   └── ui/ExtensionOptionsActivity  options_ui.page 选项页
├── media/
│   ├── detector/VideoDetectorInterceptor 视频 URL 拦截
│   ├── floating/FloatingVideoService 悬浮窗 Service + 任意缩放
│   ├── player/ExoPlayerHolder 单例 ExoPlayer
│   ├── player/MediaSessionService 长通知栏
│   ├── quality/QualityManager + QualityBus 画质同步
│   ├── subtitle/SubtitleManager + Translator Google→DeepSeek 兜底
│   ├── extractor/VideoExtractClient newpipe-extractor
│   ├── ytdlp/YtDlpBridge         调用内置 yt-dlp 二进制
│   └── ffmpeg/FfmpegHelper        合并/降质
├── reader/
│   ├── readability/ReadabilityExtractor Mozilla Readability JS 桥
│   ├── ruleengine/RuleEngine       legado 兼容
│   ├── ruleengine/BookSourceImporter 书源 JSON 导入
│   ├── novel/NovelReaderActivity   小说阅读器入口
│   ├── novel/MangaReaderActivity   漫画阅读器入口
│   ├── shelf/BookRepository        书架 Repository
│   ├── shelf/ShelfFragment         书架主页（Fragment）
│   └── shelf/AddBookActivity
├── ai/
│   ├── client/OpenAiClient         OpenAI 兼容 + SSE
│   ├── profiles/AiProfileRepository 默认配置载入
│   ├── store/AiMessageRepository   对话历史
│   ├── markdown/MarkdownRenderer   Markwon
│   └── ui/AiChatFragment + AiChatActivity
├── search/
│   ├── EngineRegistry             16 个内置引擎 + 自定义
│   └── suggestion/SearchSuggestionManager 防抖 + 并发取消
├── store/
│   ├── AppDatabase                Room 数据库 + 所有 DAO
│   └── SettingsManager            DataStore 偏好
└── settings/
    ├── SettingsActivity + SettingsFragment
    └── SettingItem                列表项 + Switch/Click

assets/
├── default_engines.json           内置搜索引擎
├── default_ai_profiles.json        默认 AI 端点（DeepSeek/Grok/GLM/Qwen 沉默模式）
├── default_book_sources.json       默认 legado 书源占位
├── recommended_scripts.json        推荐油猴脚本
├── readability.js                  Mozilla Readability 精简版
├── nebula_boot.js                  油猴脚本 boot loader
└── ytdlp/                          yt-dlp 二进制（README 说明）

res/
├── values/ + values-night/         强制白天主题 + 夜间覆盖
├── drawable/                       80+ Material Symbols 矢量图标 + 背景
├── layout/                         浏览器/AI/书架/悬浮窗/播放器布局
└── mipmap/                         应用图标 adaptive icon
```

## 构建与运行

### 在 Termux 安卓直接编译（推荐）

```bash
pkg install openjdk-17 gradle
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
cd 浏览器-Nebula
gradle wrapper --gradle-version 8.5
./gradlew assembleRelease
# APK 位置: app/build/outputs/apk/release/app-release-unsigned.apk
# 可在 termux 内一键签名安装：
apksigner sign --ks ~/.android/debug.keystore app/build/outputs/apk/release/app-release-unsigned.apk
```

> 注：原始 outputDir 可通过修改 `app/build.gradle.kts` 中 `applicationVariants` 的输出路径，将 APK 输出到 `/storage/emulated/0/a2/termux/软件-Nebula浏览器/`。

### 在 Linux 工作站编译

```bash
git clone <仓库地址>
cd NebulaBrowser
./gradlew assembleDebug
```

### yt-dlp 二进制集成

应用首次启动时会从 GitHub Release 热更新下载对应 ABI 的 yt-dlp 二进制到 `filesDir/ytdlp_bin`。
如需自编译二进制，参考 `app/src/main/assets/ytdlp/README.txt` 中的 PyInstaller 路线。

## 设计细节（与原需求对应）

### 悬浮窗

- 右上角关闭按钮（24dp）
- 中部三按钮：快退 10s / 播放暂停 / 快进 10s
- 底部进度条 + 时间显示
- 右下角拖拽柄（24dp），触摸区 48dp，任意方向拖拽 → 修改 width/height
  - 最小 160dp × 120dp
  - 最大 屏幕物理可用空间（上下大幅拉长）
- 3s 无操作淡出控件，拖拽柄永不隐藏
- 全局悬浮：`TYPE_APPLICATION_OVERLAY`，前台服务 + 悬浮播放通知

### 画质同步

- 内置播放器档位：Auto / 240P / 360P / 480P / 720P / 1080P / Source
- Auto 模式开启网速自适应（带宽阈值分级降档）
- 单例 `QualityBus` 跨内置与悬浮窗同步 `trackSelectionParameters`
- 省流模式（限 480P）开关

### 字幕翻译

- 优先级：Google 非官方 → DeepSeek-V3 兜底 → 用户自配端点
- 双语显示，缓存命中后离线可用
- Room 表 `subtitle_translation` 持久化

### 中文化

- `strings.xml` 仅含简体中文（无英文 fallback）
- 设置项分组中文：外观 / 播放 / 阅读 / AI / 搜索引擎 / 书源 / 油猴 / 插件 / 关于

### 插件系统（参照 Kiwi Browser 重构）

完整 Chrome WebExtension 兼容，支持从 Chrome Web Store 一键安装桌面版扩展：

- 完整 manifest.json 解析（v2 + v3）
- `background.scripts` / `background.page` / `background.service_worker` 后台脚本（隐藏 WebView 加载）
- `content_scripts` 按 `matches`/`exclude_matches`/`include_globs`/`exclude_globs` 规则注入到匹配页面
  - `run_at` 支持 document_start / document_end / document_idle
  - 多 `js`、`css` 文件按 manifest 顺序拼接注入
- `browser_action` / `action.default_popup` 弹窗 UI（独立 Activity 装载 popup HTML）
- `options_ui.page` / `options_page` 扩展选项页（独立 Activity）
- `chrome-extension://<id>/<rel-path>` 资源映射（shouldInterceptRequest 本地文件回写）
- `chrome.*` API 模拟（通过 addJavascriptInterface + JS 包装）：
  - `chrome.runtime`（id / getURL / onMessage / sendMessage / connect）
  - `chrome.storage.local` / `chrome.storage.sync`（SharedPreferences 持久化）
  - `chrome.tabs`（query / create / update / sendMessage）
  - `chrome.cookies`（get / set / remove / getAll）
  - `chrome.notifications`（toast 提示）
  - `chrome.downloads`（接到 DownloadService 启动前台下载）
  - `chrome.contextMenus`（菜单钩子）
  - `chrome.alarms`（定时器钩子）
  - `chrome.webRequest`（head JavaScript 监听钩子）
  - `chrome.scripting.executeScript`（运行时脚本注入）
- 安装入口：
  1. 内置 Chrome Web Store 浏览页（ChromeWebStoreActivity）：拦截 `clients2.google.com/service/update2/crx` 重定向，自动完成 CRX 下载/解包
  2. 导入本地 `.crx` / `.zip` 文件（GetContent 启动器）
  3. 加载已解压的扩展目录（开发者模式）
- `ExtensionRegistry` 持久化已安装扩展元数据到 `filesDir/extensions_meta.json`
- `BackgroundScriptRunner` 单例运行所有已启用扩展的 background，启停跟随扩展状态
- `ExtensionsManagerActivity` 扩展管理界面（chrome://extensions 风格）：列表 + 启用开关 + 点击卸载 + 顶部菜单：商店/导入 CRX

#### 安装流程图

```
[ChromeWebStoreActivity] ──点击"添加到Chrome"─→ 拦截商店 URL
                │
                ↓
        ChromeWebStoreClient.installFromAnyUrl(url)
                │
                ↓
        extractIdFromStoreUrl → buildCrxDownloadUrl(id)
                │
                ↓
        OkHttp 下载 CRX3 字节流
                │
                ↓
        CrxUnpacker.unpack(...)
                │
                ↓ (从 header 中提取 public key → ExtensionId.fromPublicKey)
        解压 .zip payload → filesDir/extensions/<id>/
                │
                ↓
        解析 manifest.json → ExtensionPackage
                │
                ↓
        ExtensionRegistry.add(pkg, fromStore=true) → 持久化 meta.json
                │
                ↓
        BackgroundScriptRunner.start(pkg) → 注入后台脚本到隐藏 WebView
                │
                ↓
        浏览任意 URL 时 ContentScriptInjector.injectOnStarted/Finished
```

#### 限制说明

Android System WebView 不提供真正的扩展子系统，因此本插件系统是 **API 模拟层**：
- Service Worker 不支持真正的离线 Service Worker，仅作后台脚本拼接运行
- `chrome.webRequest` 拦截需要扩展显式注册到 ContentScriptInjector 中，实现可在后续迭代补全
- 沙箱化（isolated world）未严格区分：扩展 content_script 与页面 JS 共享同一 JS 上下文
- 极少数高权限扩展（如 devtools-only、`chrome.debugger`、`chrome.proxy`）暂不支持



- `App.onCreate` 根据 `SettingsManager.themeMode` 调用 `setDefaultNightMode()`
- 三档：跟随系统 / 白天（默认）/ 夜间
- values-night 自动切换系统栏、文字、卡片、悬浮窗等颜色

## 已知边界

- yt-dlp 二进制不在仓库中（按 README 说明部署或热更新）
- QuickJS 仅用作 RuleEngine 兜底（书源 inline JS），未完整集成运行时
- 部分 Activity（如 NovelReaderActivity / MangaReaderActivity）以 Compose 入口占位，
  完整阅读视图在二期补完；本仓库演示其架构位置。

## 开源聆听

欢迎 Issue / PR 协作完善。

