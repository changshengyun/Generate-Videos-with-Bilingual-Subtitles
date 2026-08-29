# LyricCaptioner Android

一个**本地优先**的 Android 双语字幕工具：导入一段不超过 5 分钟的视频，一键完成「本地语音识别（ASR，Automatic Speech Recognition，自动语音识别）→ AI 歌词纠错与中文翻译 → 字幕编辑 → 导出带双语字幕的 MP4」。

- **当前版本**：`V4.4.0`（V4 系列收官版本，V4 全部功能已完成并通过验收）
- **应用包名**：`com.example.lyriccaptioner`
- **平台要求**：Android 8.0+（minSdk 26，targetSdk 35）

## 当前版本已完成的核心能力

| 能力 | 说明 |
|---|---|
| 一键识别 | 相册导入视频后一次点击完成本地 Whisper 识别，自动衔接 AI 增强 |
| AI 增强双语字幕 | DeepSeek 歌曲识别 + 歌词检索 + 本地 DP（Dynamic Programming，动态规划）校验 + 双语生成 + 本地校验落屏 |
| 双路歌词检索 | LRCLIB 数据库检索为主，DeepSeek Responses API 联网搜索（web_search）为辅，本地 DP 对齐做最终裁决 |
| 字幕编辑套件 | 按播放位置新增双语字幕、拆分/合并、逐条样式编辑、样式锁、布局锁、全屏直编 |
| 导出 | FFmpegKit 烧录字幕导出 MP4，SRT 字幕文件导出，MediaStore 发布 |
| 本地降级 | 云端增强失败（离线/超时/服务错误）自动回退本地翻译，识别结果永远可用 |
| 项目保存/恢复 | 工程文件（.lcp）保存与恢复，识别结果可手动落盘 |
| 安全密钥管理 | DeepSeek Key（BYOK，Bring Your Own Key，自带密钥）仅在 App 内输入，Android Keystore AES-256-GCM 加密保存 |

---

## 快速运行说明

### 环境准备

```text
克隆仓库
  │
  ├─→ 安装 Android Studio（含 Android SDK，Platform 35）
  │
  ├─→ 配置 local.properties（写入 sdk.dir=<本机 SDK 路径>）
  │
  └─→ 恢复原生依赖：.\tools\setup-whisper-native.ps1
        （下载/恢复 third_party/whisper.cpp，构建时由 CMake 集成）
```

### 构建与安装

```powershell
# Debug 包（含 Whisper 原生库，必须带 -PenableWhisperNative=true，否则识别不可用）
.\gradlew.bat -PenableWhisperNative=true assembleDebug

# 产物位置
app\build\outputs\apk\debug\app-debug.apk

# 安装到已连接设备
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

> **重要**：默认构建不带 `-PenableWhisperNative=true` 时，APK 缺少 `liblyriccaptioner_whisper.so`，识别功能整体不可用。发布构建务必显式传参。

### 首次运行

首次启动需在设置面板输入 DeepSeek API Key（仅用于 AI 增强；不输入也能完成本地识别与编辑导出）。识别前需确保本机已安装 Whisper 模型文件（应用内引导安装，模型不随仓库分发）。

---

## 下载与安装说明

- **当前发布包**：`dist/LyricCaptioner-v4.4.0-debug.apk`（Debug 签名，由 `-PenableWhisperNative=true assembleDebug` 构建）。
- **为什么仓库里没有下载链接**：完整 APK 约 300 MB 以上，超出 Git 与 GitHub 单文件 100 MB 限制，无法随源码提交。安装包的获取方式如下：

```text
方式一：自行构建（推荐，始终与源码一致）
  git clone → setup-whisper-native.ps1 → gradlew -PenableWhisperNative=true assembleDebug

方式二：本地已有产物
  dist/LyricCaptioner-v4.4.0-debug.apk（构建后由收尾流程拷贝至此，本地分发）

方式三：后续发布渠道（待确认）
  如需公开下载链接，建议将 APK 上传至 GitHub Releases 页面后在本文档补充链接
```

---

## 版本发布说明（V4.4.0）

- **版本定位**：`V4.4.0` 是 V4 系列的**收官正式发布**，代表 V4 版本全部功能已经开发完成并通过验收。
- **为什么不叫 V5.0**：V5 代表新的产品阶段；在明确启动 V5 之前，本次收尾沿用 V4 系列版本号。
- **发布内容**：相对 V4.3.3 无新功能，仅包含版本收尾——仓库文件整理、版本号升级（`4.4.0 / 4400`）、README 重写、状态文档同步、打标签 `v4.4.0`。

### V4.3 验收情况（本次发布的验收基础）

| 验收项 | 结果 |
|---|---|
| JVM 全量回归（截至 `a1e0486` 一致性修复） | 402 条 / 0 失败（直播网络测试自动跳过） |
| SearchScheduler 接入后新增测试（`233cb04`） | DeviceRealApiTraceTest（3 组真机数据集真实 API）、DeviceAsrWalkthroughTest、ThreeVideoEnhancementSandboxTest 等 |
| 三视频本地沙箱重跑（2026-08-29） | 三个真实视频走完整流程 0–4，逐阶段 prompt/输入/输出/拦截策略记录于本地证据 `test-artifacts/ai-enhancement/three-video-local-sandbox-rerun.md`（V4.4 起不入库） |
| 真机识别与真实 API 增强 | 真机抓取识别文本与 AI trace，证据在本地 `test-artifacts/ai-enhancement/device-real-api-trace.md`（V4.4 起不入库） |
| APK 原生库完整性 | 13 个原生库齐全（含 `liblyriccaptioner_whisper.so`） |
| 真机装机冒烟 | 进程存活，无 FATAL / crash |

> 说明：验收当时全部回归测试通过；V4.4 精简时测试代码（`app/src/test/`、`app/src/androidTest/`）与沙箱代码已从仓库移出，可从 git 历史（`v4.4.0`）找回。

---

## 开发流程回顾（V1 → V4.4）

整体演进脉络：

```text
V1 原型                    V2 移动端编辑台               V3 云端增强与产品化
┌─────────────┐          ┌──────────────────┐        ┌────────────────────────┐
│ 桌面脚本验证  │ ───────→ │ Compose 编辑器     │ ─────→ │ BYOK 密钥安全            │
│ FFmpeg 烧录  │          │ 全屏字幕预览       │        │ Whisper 会话缓存          │
│ 双语字幕跑通  │          │ 视频导入加固       │        │ 逐条样式/渲染修复 r1-r4    │
└─────────────┘          └──────────────────┘        │ 系统相册导入导出            │
                                                      │ DeepSeek 增强链路          │
                                                      │ 整首歌词检索生成双语字幕      │
                                                      └───────────┬────────────────┘
                                                                  ▼
        V4.4 收官发布（本次）←─ V4.3.x 修复群 ←─ V4.3 ←─ V4.2 ←─ V4.1 ←─ V4 主链路
        ┌──────────────┐    ┌──────────┐  ┌────────────────┐  ┌────────────┐  ┌──────────────┐  ┌─────────────────┐
        │仓库整理/版本号 │    │错误提示门控│  │恢复 V4.1 编辑套件│  │AI2 六项根因修复│  │字幕质量/置信度 │  │一键识别→增强→编辑 │
        │README/标签发布 │    │识别策略放宽 │  │中文保留/拆分对齐 │  │SRT 导出接线    │  │拆分合并/样式锁  │  │按播放位置加字幕   │
        └──────────────┘    │深色主题适配 │  │全量回归 398→402 │  │全屏控制栏      │  │发布基线 47c7077│  │字幕/播放控制分离   │
                            │SearchScheduler│ └────────────────┘  └────────────┘  └──────────────┘  │EditorScreen 拆分 │
                            │自动滚动修复   │                                                        └─────────────────┘
                            └──────────┘
```

各阶段做了什么、为什么这么做：

| 阶段 | 目标 | 完成内容与结果 |
|---|---|---|
| V1 | 验证「给视频加双语字幕」技术可行性 | 桌面侧用 FFmpeg 脚本跑通双语字幕烧录（样例产物曾留存于 `deliverables/`，V4.4 精简时移出仓库，可在 git 历史中找回） |
| V2 | 把能力搬到 Android 移动端 | Compose 紧凑编辑工作台、全屏字幕预览、导入导出加固（V2-IMPORT-002 / V2-UI-002），打标 `v2.0.0` |
| V3 | 产品化：云端增强 + 安全 + 渲染质量 | 安全 BYOK（Keystore 加密、异常消息脱敏）、字幕增强合同、Whisper 进程级会话缓存、逐字幕样式卡片、Media3 渲染语义修复 r1–r4、系统相册统一导入导出、产品级 UI、DeepSeek 增强生产链路、整首歌词检索；每个子阶段都有冻结验收矩阵（V4.4 精简时移出仓库，见 git 历史） |
| V4 主链路 | 一次点击走完全流程 | `V4-FLOW-001` 串联本地 ASR + AI 增强 + 自动进编辑器；`V4-EDITOR-001` 按播放位置新增双语字幕；`V4-UI-001` 分离字幕与播放控制；`V4-SIMP-001` 把 2365 行 EditorScreen 拆为同包 8 模块（行为不变） |
| V4.1 | 字幕质量与编辑能力成型 | 字幕质量整合、置信度门槛、样式锁/布局锁、拆分合并、逐条 AI 建议；发布基线提交 `47c7077`，回归 379/382 |
| V4.2 | AI 增强链路修根因 + 导出补齐 | AI2 增强链路六项根因修复（请求合同、Prompt、歌词检索、验证器等）并接线 SRT 导出，打标 `v4.2`；单测 361/361 |
| V4.3 | 恢复重构中丢失的编辑能力并双向对齐 | EditorScreen 重构曾丢失 V4.1 编辑套件，逐个恢复（样式锁面板、拆分合并、逐条 AI 建议、布局锁、编辑页直编），再按需求驱动测试与 V4.1 双向比对，修复「编辑英文清空中文」「拆分批提交合同」两处差异；全量回归 63 套件 / 398 条 / 0 失败，后升至 402 条 |
| V4.3.x | 真机问题修复群 | 分段增强错误提示被 running 门控吞没（修复）、歌曲识别弃权导致低命中（放宽为必须给候选）、深色主题黑底黑字（修复）、SearchScheduler 双路检索（web_search + 本地 DP）、字幕列表自动跳转干扰编辑（V4.3.3 修复） |
| **V4.4** | **版本收官** | 文件整理归档、版本号升级、README 重写、`v4.4.0` 标签发布 |

---

## 项目架构说明

### 产品主链路

```text
相册导入视频（≤5 分钟）
   ▼
一次点击「开始识别」
   ▼
流程0：Whisper 本地识别（JNI → whisper.cpp，纯本地不联网）
   ▼  原始英文 cue（含错词/错误断句）
流程1：DeepSeek 第 1 次调用 —— 猜歌名（最多 2 候选 + 净化歌词行）
   ▼
流程2：歌词检索（LRCLIB / web_search 双路）+ 本地 DP 对齐校验
   ▼  CONFIRMED（≥0.82）/ UNCONFIRMED / NOT_FOUND
流程3：DeepSeek 第 2 次调用 —— 按权威歌词纠错英文 + 整首上下文翻译中文
   ▼
流程4：本地校验（条数/id/时间戳 1:1）→ 原子写入编辑器（CLOUD_APPLIED）
   ▼
字幕编辑（新增/拆分/合并/样式/直编）→ 预览 → 导出（MP4 烧录 / SRT）
```

任何一步云端失败（离线、超时、服务错误），`CaptionEnhancementCoordinator` 自动降级为本地翻译（`TranslationModule`），保证识别结果永远可编辑、可导出。

### 模块结构

```text
app/src/main/java/com/example/lyriccaptioner/
├── MainActivity.kt / MainViewModel.kt     # Compose 入口 + 全流程状态编排
├── audio/                                 # 音频抽取与预处理
├── processing/
│   ├── AsrModule.kt                       # 本地识别调度
│   ├── WhisperProcessSession.kt           # whisper.cpp JNI 会话（进程级缓存）
│   ├── FfmpegKitSubtitleExporter.kt       # FFmpegKit 烧录导出
│   └── enhancement/                       # ★ AI 增强子系统（见下文重点介绍）
├── model/                                 # EditorState、CaptionCueSplitPolicy 等数据模型
├── captions/                              # cue 时间与文本合同
├── project/                               # 工程保存/恢复（.lcp）
└── ui/                                    # EditorScreen 主入口 + 8 个拆分面板模块
```

### 技术栈与对应模块

| 技术 | 用途 | 对应位置 |
|---|---|---|
| Kotlin + Jetpack Compose + Material 3 | 全部 UI 与状态管理 | `ui/`、`MainViewModel` |
| whisper.cpp（JNI/CMake） | 本地英文语音识别 | `WhisperProcessSession`、`third_party/whisper.cpp` |
| DeepSeek Chat Completions API | 歌曲识别、双语生成（流程1/3） | `DeepSeekCaptionEnhancementProvider` |
| DeepSeek Responses API + web_search | 联网歌词检索兜底（流程2 双路之一） | `ResponsesApiClient`、`SearchScheduler` |
| LRCLIB 公开接口 | 权威歌词数据库检索（流程2 主路） | `LrclibSongLyricsSearchTool` |
| 本地 DP 对齐算法 | 识别结果与权威歌词逐句对齐，唯一「确认歌曲」的裁决者 | `SongLyricsCandidateVerifier` |
| FFmpegKit | 字幕烧录导出 MP4 | `FfmpegKitSubtitleExporter` |
| Media3 (ExoPlayer) | 视频预览与字幕渲染 | `ui/VideoPreviewPlayer.kt` |
| MediaStore | 导出文件发布到系统相册 | `MediaStoreExportSession` |
| Android Keystore（AES-256-GCM） | DeepSeek Key 加密存储 | 密钥设置面板 + 存储层 |
| Gradle 8.9 + Version Catalog | 构建与依赖 | `gradle/libs.versions.toml` |

---

## 关键技术说明

| 技术能力 | 作用 | 关键位置 |
|---|---|---|
| 本地 ASR（自动语音识别） | 不联网、零成本地把歌声转成带时间轴的英文 cue | `AsrModule`、`WhisperProcessSession` |
| 信任边界设计 | 歌曲是否「确认」只由本地 DP 置信度决定，模型自报的 `song_match` 字段被解析器直接丢弃，防止模型幻觉造成错误确认 | `SongLyricsCandidateVerifier`、`DeepSeekCaptionEnhancementProvider.parseSongCandidates()` |
| 确定性输出合同 | 输入输出严格 1:1（条数、id、时间戳不可变），任何偏差整体拒绝，杜绝时间轴漂移 | `CaptionEnhancementResponseValidator`、`CaptionEnhancementContract` |
| 分类降级策略 | 按错误类型白名单（离线/连接/超时/可重试/无效响应）决定是否本地翻译降级；认证失败不降级 | `CaptionEnhancementCoordinator` |
| 异常消息脱敏 | 异常链中自动剥离 Authorization、Bearer、sk-* 等敏感内容 | `CaptionEnhancementException.sanitizeExceptionMessage` |
| 按播放位置加字幕 | 在播放时刻的空档插入双语字幕并维持时间轴一致 | `MainViewModel` + `model/` |
| 拆分/合并政策 | 一条 cue 拆两条并保持父边界与不重叠，编辑状态按时间顺序原子更新 | `CaptionCueSplitPolicy`、`MainViewModel` |
| 状态化错误处理 | UI 不接收异常，所有错误写入 `EditorState` / `ExportState` 等状态流 | `MainViewModel` |

---

## AI 增强部分（重点介绍）

### 它是什么

AI 增强是一条「把 Whisper 识别出的带错英文歌词，变成准确英文 + 自然中文的双语字幕」的流水线。核心代码位于 `app/src/main/java/com/example/lyriccaptioner/processing/enhancement/`。

### 它是怎么搭建的

设计遵循三条原则：

```text
原则一：本地验证说了算
   └─ 歌曲是否确认，只信本地 DP 对齐置信度，不信模型自我判断

原则二：纠错有据可依
   └─ 确认歌曲后，模型只能按权威歌词对齐表纠错，禁止凭记忆补写

原则三：确定性合同
   └─ 输入输出 1:1，由流程4 本地校验器强制执行，偏差即整体拒绝
```

### 工作流与调用链

```text
入口：MainViewModel.generateCompleteCaptions()
  │
  ├─ AsrModule ── WhisperProcessSession（JNI）──→ 原始 cue 批
  │
  └─ CaptionEnhancementCoordinator.enhance()
        │
        ├─ 映射：cue 批 → 增强请求
        │
        ├─ DeepSeekCaptionEnhancementProvider.enhance()
        │     ├─ 流程1：IDENTIFICATION_SYSTEM_PROMPT（prompt v5）
        │     │     └─ cue 数 ≥ 3 才启动；禁止弃权，必须返回最可能候选
        │     ├─ 流程2：findVerifiedLyrics()
        │     │     ├─ 主路：LrclibSongLyricsSearchTool（按歌名/歌手）
        │     │     ├─ 新路：SearchScheduler 三路线并行检索（web_search）
        │     │     │     └─ 三区间分支：CONFIRMED ≥0.82 / 中间地带 0.50–0.82 / 待查 <0.50
        │     │     ├─ 兜底：歌词原文文本检索（≤300 字符）
        │     │     └─ 裁决：SongLyricsCandidateVerifier（本地 DP 对齐）
        │     ├─ 流程3：已验证模式（VERIFIED_LYRICS_SYSTEM_PROMPT）
        │     │     或保守模式（UNCONFIRMED_SYSTEM_PROMPT，禁止编造）
        │     └─ 流程4：CaptionEnhancementResponseValidator 逐条核对
        │
        ├─ 成功 → 原子替换编辑器字幕（CLOUD_APPLIED）
        └─ 失败 → 白名单错误走 TranslationModule 本地降级（LOCAL_FALLBACK_APPLIED）
```

### 关键参数（源码常量）

| 规则 | 值 | 位置 |
|---|---|---|
| 启动门槛（cue 数） | ≥ 3 | `SongLyricsCandidateVerifier.MIN_ELIGIBLE_CUES` |
| 候选歌数上限 | 2 | `DeepSeekCaptionEnhancementProvider.MAX_SONG_CANDIDATES` |
| DP 确认阈值（常规/小批 ≤5 条） | 0.82 / 0.76 | `MIN_CONFIDENCE` / `SMALL_BATCH_MIN_CONFIDENCE` |
| 最少匹配 cue 数（常规/小批） | 3 / 2 | `MIN_MATCHED_CUES` / `SMALL_BATCH_MIN_MATCHED_CUES` |
| 单条相似度下限 | 0.62 | `MIN_CUE_SIMILARITY` |
| 兜底查询长度上限 | 300 字符 | `FALLBACK_QUERY_MAX_CHARS` |

### 与其他模块的关系

- **上游**：`AsrModule` 提供原始 cue；`MainViewModel` 注入 `SearchScheduler` 并观察 `SongMatchStatus` 更新界面状态。
- **下游**：校验通过的字幕原子写入 `EditorState`，进入字幕编辑套件；导出时由 `FfmpegKitSubtitleExporter`（MP4）或 SRT 导出消费。
- **失败路径**：`CaptionEnhancementCoordinator` 按错误类型决定降级或失败，UI 通过状态字段展示错误，不抛异常。
- **沙箱验证**：验收阶段曾用与正式代码共用同一份 Provider/Verifier/Validator 源码的沙箱重跑策略（改策略后重跑沙箱即等同于修改 App 策略）；沙箱与测试代码已在 V4.4 精简时移出仓库（可从 git 历史 `v4.4.0` 找回），验证报告留存于本地 `test-artifacts/ai-enhancement/`。

---

## 仓库目录说明

| 目录 | 内容 | 是否随版本发布 |
|---|---|---|
| `app/` | 应用源码 | 是 |
| `docs/` | 活动文档：路线、任务、状态、架构方案 | 是 |
| `tools/` | 稳定工具：ASR 评估、Whisper 原生依赖恢复脚本 | 是 |
| `test-artifacts/` | 本地验收证据与调试产物（见下表，不入库） | 否（.gitignore） |
| `dist/` | 本地构建的 APK 发布产物 | 否（.gitignore） |
| `docs-BK/`、`.env` 等 | 本地备份/密钥等 | 否（.gitignore） |

> **V4.4 精简说明**：`deliverables/`、`docs-v2/`、`docs/archive/`、`docs/debug/`、`.agents/`、`.codex/`、`.kotlin/`、`.emulator-test-assets/`、`app/src/test/`、`app/src/androidTest/` 已从仓库移除，`test-artifacts/` 改为仅本地保留不入库。其中 `.agents/` 与 `.codex/skills/` 已整理迁入 [DEV-SKILL 仓库](https://github.com/changshengyun/DEV-SKILL)（`projects/lyric-captioner-android/` 与根级 `.codex/skills/`）；其余内容仍可在 git 历史（`v4.4.0` 及更早）中找回。

`test-artifacts/`（仅本地）内部分类：

| 子目录 | 内容 |
|---|---|
| `ai-enhancement/` | AI 增强各轮验证报告、prompt 全文、真机 trace、沙箱重跑记录 |
| `device-capture/` | 真机抓取的 UI dump、截图、AI trace（`raw-logs/` 为超大原始 logcat，仅本地保留） |
| `lyrics-accuracy/` | 歌词准确率评估输出样例 |
| `debug-session-v4.3/` | V4.3 调试会话抽离物：临时脚本、截图、UI dump、trace、日志 |
| `screenshots/` | 早期模拟器验证截图 |

---

## 开发规则摘要

完整治理规则见 `AGENTS.md`。要点：一次只推进一个完整模块；变更按 S0（简单）/S1（普通）/S2（证据优先）分级；同一故障三次修复失败冻结修改转根因分析；真机操作需显式授权；Git 精确暂存。

## 文档索引

- [开发路线](docs/DEVELOPMENT_ROADMAP.md)
- [当前任务](docs/CURRENT_TASK.md)
- [项目状态](docs/PROJECT_STATE.md)（状态唯一权威来源）
- [V4 产品架构](docs/V4_PRODUCT_ARCHITECTURE.md)
- [V3 产品架构](docs/V3_PRODUCT_ARCHITECTURE.md)
- AI 增强流程介绍与 Prompt 全文：本地 `test-artifacts/ai-enhancement/ai-enhancement-flows-intro.md`、`ai-enhancement-prompts.md`（V4.4 起不入库）
