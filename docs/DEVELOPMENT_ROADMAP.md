# LyricCaptioner V2 开发路线

## 文档职责

本文件只维护长期模块顺序、模块目标和总体验收门槛。当前执行合同见 `CURRENT_TASK.md`，实时事实见 `PROJECT_STATE.md`。`docs-BK/`、`docs/archive/` 和其他证据文档不参与当前任务调度。

## V2 产品目标

V2 面向个人 ARM64 手机使用，形成稳定的真实工作流：

1. 从手机本地选择真实视频并导出带双语字幕的有效 MP4。
2. 移除产品路径中的 Demo、历史替代实现、一次性维护代码和重复测试，降低复杂度，但不牺牲已验证能力。
3. 提升英文歌曲、英文单词识别与中文翻译质量，并用固定数据集和可重复指标证明提升。
4. 完成中文界面、全屏预览、字幕样式编辑和第二轮移动端 UI 视觉/交互精修。
5. 保证用户可以直接从手机系统媒体选择器选择本地视频并进入真实编辑链路；该能力未获得真机证据时必须继续保留为后续门禁，不能因模拟器通过而关闭。

“大幅提升”必须相对固定基线给出数据；构建通过、模拟输出或主观感觉不能代替验收。

## 模块顺序

| 模块 | 状态 | 目标 |
|---|---|---|
| V1-MVP | `ARM64_E2E_VERIFIED` | 五分钟真实视频完整闭环已通过，作为回归基线 |
| V2-ASR-001 | `PAUSED_WITH_ASSETS` | 模型目录、校验、原子导入和评测工具已完成；缺合格英文歌曲样本，不丢弃现有改动 |
| V2-PROD-001 | `ARM64_PRODUCT_PATH_VERIFIED` | Product routing, cleanup hardening, tests, lint, both Debug builds, and the ARM64 import → Local ASR → translation → save/restore → FFmpegKit export → Media3 playback path verified |
| V2-CLEAN-001 | `CODE_VERIFIED / E2E_DEFERRED` | 安全删除与合并、测试和双构建已通过；完整模拟器链转交本地模型模块收口 |
| V2-LOCAL-AI-001 | `LOCAL_AI_SIMULATOR_VERIFIED` | 集成可手动准备的 Whisper 与 OPUS-MT ONNX 模型，移除产品运行时模型下载依赖 |
| V2-ASR-002 | `FIXTURE_REQUIRED` | 使用固定英文歌曲语料比较候选模型，改善歌词和单词识别；当前缺人工准确歌曲 fixture |
| V2-TRN-002 | `PLANNED` | 使用固定英中歌词语料评估并改善中文翻译 |
| V2-UI-001 | `UI_SIMULATOR_VERIFIED` | 中文化并整理主要界面 |
| V2-PREVIEW-001 | `IMPLEMENTING` | 全屏播放和字幕位置、大小、字体、颜色编辑 |
| V2-UI-002 | `PLANNED` | 使用项目级 UI 技能进行第二轮移动端视觉系统、信息层级、触控交互和状态反馈优化 |
| V2-IMPORT-002 | `PLANNED / DEVICE_GATE_DEFERRED` | 从手机系统媒体选择器直接选择本地视频，持久化访问权限并进入预览、保存恢复和处理链路 |
| V2-E2E-002 | `DEFERRED_DEVICE_GATE` | 用户重新授权真机测试后，在个人 ARM64 手机完成 V2 全流程最终验收 |

一次只实施一个模块。开发窗口可自行处理模块内普通 Bug；只有架构/技术栈变更、新大型依赖、破坏性数据操作、需求冲突或无法证明安全的删除，才交由决策窗口处理。

## 临时测试模式

自 2026-07-30 起，后续模块暂时执行 `SIMULATOR_ONLY_TEMPORARY`：禁止真机安装、启动和验收，功能模块使用 Android 模拟器完成测试。该模式不降低单测、Lint、构建、完整功能链和 Review 标准；0 instrumentation 用例不计作功能通过。ARM64 最终验收保留为 `V2-E2E-002 / DEFERRED_DEVICE_GATE`，等待用户重新授权。

## V2-PROD-001 execution record (2026-07-28)

- Implementation complete for the code and local test/build scope: Product routing is Local Whisper + real translation + FFmpegKit export + Media3 playback only; missing model/JNI fails explicitly; fixed Demo processing was removed.
- Verification: 84 unit tests passed with 0 failures/errors/skips; `lintDebug`, normal Debug, and Native Debug passed; Native package installed on the ARM64 target.
- ARM64 gate passed on `fcf4b0cb / 25098PN5AC` (`arm64-v8a`): real SAF video import, 48-caption Local Whisper ASR, 48-caption real translation, project save/restore, FFmpegKit export, and Media3 preview playback all completed.
- Device measurements: ASR approximately 113 seconds wall time, observed peak RSS 566,820 kB, peak sampled temperature 36.3 C; translation 1,451 ms; export approximately 10.79 seconds; final RSS 248,016 kB and temperature 30.9 C.
- Output: `v2-prod-output.mp4`, 46,276,534 bytes, 299.792 seconds, H.264/AAC, with visibly burned-in English and Chinese subtitles. FFmpegKit return code was 0; no Demo fallback occurred. Failure/cancel/temp cleanup is covered by the passing unit/regression suite.
- Cleanup inventory counts: KEEP 10, MERGE 2, DELETE 1, DEFER 5. No batch cleanup, FFmpeg/AAR change, model switch, Git commit, or GitHub sync was performed.
- Final status: `ARM64_PRODUCT_PATH_VERIFIED`.

## V2-PROD-001：真实产品路径

### 范围

- 使用 Android SAF 选择手机本地视频、字幕、模型和输出位置。
- 使用 Local Whisper、翻译模块、FFmpegKit 烧录与 Media3 回放真实文件。
- 产品路由不得生成固定 Demo 字幕或把缺失模型伪装为 Local 成功；缺少前置条件时显示明确不可用原因。
- 在当前分支重新完成一次 ARM64 真机导入与导出，不能用 V1 历史文件替代。
- 在清理开始前建立代码、测试、脚本的 `KEEP / MERGE / DELETE / DEFER` 清单及 Review 标准。

### 验收

- 真机选取本地视频成功，源 URI 与时长有效。
- 真实 Local ASR 和翻译产生非空、有序字幕，不发生 Demo 回退。
- 输出 MP4 大于 0，包含 H.264/AAC，时长合理，双语字幕可见且 Media3 可播放。
- 取消和失败不留下临时文件、空目标或误报成功。
- 单测、Lint、普通 Debug 和 Native Debug 全部通过。

## V2-CLEAN-001：安全精简与重构

### 原则

- 先证明无引用、无运行时发现、无外部契约、无迁移/回滚用途，再删除代码。
- 不以“测试数量少”为目标；以产品不变量覆盖充分、重复维护成本降低为目标。
- 每个重构批次只处理一个边界，先跑相关测试，再跑完整测试与构建。
- 优先移除已退出产品路径的 Demo/历史实现、重复 helper、一次性诊断逻辑和被新契约测试完全覆盖的重复测试。
- `MainViewModel` 和 `EditorScreen` 只按已有业务边界拆分，不做全项目重写；模块对外暴露最小稳定接口，内部细节保持封装。

### 测试删除规则

下列测试原则上必须保留：SAF 导入/重绑、项目保存恢复、SRT、字幕时间轴、派生输出失效、Local ASR 路由、模型校验与原子导入、翻译原子性、ASS/FFmpeg 导出、取消与失败清理。

只有满足以下任一条件才可删除测试：

- 被测生产代码在同一批次安全删除；
- 与另一测试验证同一不变量且没有额外边界价值，合并后覆盖不降低；
- 仅验证已废弃实现细节，并已有更高层契约测试替代。

禁止删除失败测试来获得绿色结果。每个删除项必须记录原覆盖点和替代证据。

### 验收

- 给出删除/合并文件清单和理由。
- 给出主代码行数、测试行数、测试数量、APK 大小、Lint 警告的前后对比。
- 产品公开行为和模拟器完整导入/导出回归保持通过。
- 不修改或清理 `third_party/ffmpeg-kit` 的既有脏状态。

## V2-CLEAN-001 execution record (2026-07-30)

- Implemented `SAFE_DELETE` for the unused `processing/LocalModelManagers.kt` and one obsolete `SpeechMode.DEMO` compatibility test. Implemented `SAFE_MERGE` for Local ASR state resolution and FFmpegKit temporary/destination cleanup ownership.
- Preserved the V2-ASR model catalog/import/validator/tests and `tools/asr-evaluate.py`; `third_party/ffmpeg-kit` was not modified. No batch deletion, model switch, AAR change, Git commit, or GitHub sync was performed.
- Review inventory for this module: `SAFE_DELETE 1 production file + 1 replaced test`, `SAFE_MERGE 2`, `LEFT_FOR_REVIEW 4 groups`, `KEEP all product-invariant tests and ASR assets`. The review items are Media3 exporter/observability, historical one-off maintenance logic, large UI/ViewModel boundary splits, and the ML Kit-used `LocalModelManager` contract.
- Metrics: main Kotlin `39 files / 4209 lines -> 38 / 4173`; test Kotlin `23 / 1415 -> 23 / 1393`; unit tests `84 -> 83`; Native Debug APK `138394427 -> 138394427` bytes; current Lint `0 errors / 3 warnings` (baseline warning count was not recorded).
- Verification: 83 unit tests passed with 0 failures/errors/skips; `connectedDebugAndroidTest` completed with 0 cases and is not functional evidence; `lintDebug`, normal Debug, and Native Debug passed. Native simulator launch reached `ASR: LOCAL`, `Model: ready`, `JNI: ready`, without Demo fallback or crash.
- Simulator full path is not verified. On Pixel_8 the app can be launched on `displayId=2`, but `uiautomator` intermittently exposes Launcher and Import taps do not reliably open DocumentsUI. The real video import therefore did not reach Local ASR, translation, save/restore, FFmpegKit export, or Media3 playback. Final status: `BLOCKED`.

## V2-LOCAL-AI-001：双本地模型集成

- 英文识别使用已批准的 `ggml-small.en-q5_1.bin`。
- 英译中使用本地量化 OPUS-MT ONNX 模型，保持 `LocalTranslator` 接口并替换产品 ML Kit 下载路径。
- 模型从项目本地资产经校验后复制到 App 私有目录，运行时不访问网络、不回退云端或 Demo。
- 在模拟器断网条件下完成识别、翻译、保存恢复、导出和回放。
- 该模块通过后再分别进入 ASR 与翻译质量评测，不把“能运行”误称为“质量显著提升”。

## V2-ASR-002：英文歌曲识别质量

- 固定至少 3 段有人工准确歌词和时间范围的英文歌曲样本，另设短单词/短句样本。
- 在同一设备、同一音频预处理条件下比较当前模型与候选模型。
- 记录 WER、关键词召回、漏词/重复词、时间戳有效性、耗时、峰值内存和模型大小。
- 只有质量显著改善且资源可接受，才切换默认模型；不得针对单一歌曲硬编码修正。

## V2-TRN-002：中文翻译质量

- 建立固定英文歌词与人工认可中文参考集，冻结输入和评分规则。
- 同时评价语义准确、流畅度、歌词语气、术语一致性、漏译和字幕长度。
- 自动指标只作辅助，最终以盲评或明确人工评分表为准。
- 云端服务、新模型或大依赖属于关键决策，必须先提交成本、隐私、离线能力和回滚方案。

## V2-UI-002：移动端视觉系统与交互精修

- 必须使用项目级 `.codex/skills/ui-ux-pro-max`、`.codex/skills/mobile-android-design` 和 `.codex/skills/edge-to-edge` 作为设计与 Review 输入，但活动任务和本路线的边界优先于 Skill 建议。
- 在不改变 Kotlin、Jetpack Compose、Material 3、ViewModel、Media3、FFmpegKit 和本地模型路线的前提下，重新审视视觉主次、留白、密度、字体层级、颜色对比、图标一致性、触控区域、空状态、加载状态、错误反馈和操作可发现性。
- 以手机单手操作为主，核心流程必须保持清楚：本地视频导入 → 识别/翻译 → 字幕编辑 → 预览 → 导出。不得为了外观增加与产品无关的卡片、装饰或功能。
- 先形成视觉方向和可测量的 UI 验收清单，再修改 Compose；必须提供 Pixel_8 初始页、导入后、字幕编辑、全屏预览和导出状态截图，并进行对照 Review。
- 不引入 Web/React/Tailwind、Compose alpha、Navigation 迁移、大型 UI 库、在线字体或新运行时依赖。

## V2-IMPORT-002：手机本地视频直接导入

- 用户从 Android 系统媒体/文件选择器直接选择手机本地视频；不得要求用户先把视频复制到应用专用目录或通过开发工具推送文件。
- 正确处理 SAF URI、临时/持久读取权限、文件类型、时长与可读性校验、取消选择、失效 URI、重启恢复和重新绑定。
- 成功选择后必须显示真实视频预览和有效元数据，并能进入字幕生成/导入、项目保存恢复、FFmpegKit 导出和 Media3 回放；不得使用固定路径、Demo 视频或历史产物代替。
- 普通实现失败时继续在 `V2-IMPORT-002` 内执行最小复现、根因修复和完整回归，不能跳过该功能或降低标准进入 PASS。
- 当前 `SIMULATOR_ONLY_TEMPORARY` 下可取得的最高状态为 `IMPORT_SIMULATOR_VERIFIED / DEVICE_DEFERRED`。在用户重新授权真机前，不得声称“手机本地视频导入已完成”。未完成的真机证据必须自动延续到 `V2-E2E-002`。
- 真机门禁至少要求：个人 ARM64 手机打开系统选择器、选择真实本地 MP4、返回 App、预览播放、保存并重启恢复、完成一次有效字幕导出且源视频未被覆盖或删除。

## 统一 Review 门槛

- 需求与范围：实现仅覆盖当前模块，无隐式换栈或顺手扩张。
- 正确性：不得遗留 P0/P1；P2 必须有明确处置或记录。
- 测试：新增行为有对应测试，失败缺陷先形成最小复现和回归测试，再修复。
- 真实能力：产品路径不得使用 Demo、占位输出或历史产物冒充成功。
- 安全与隐私：日志不记录媒体内容、模型内容或用户私有路径全文。
- 可维护性：模块边界清楚、公开接口最小、重复实现减少，但不为抽象而抽象。

## 标准调试循环

模块验收失败时，开发窗口自行执行：定位最早失败点 → 建立最小复现/回归测试 → 根因分析 → 最小修复 → 相关测试 → 全量测试与模块验收。普通错误不得因尝试次数增加而绕过、换栈或降低验收标准。

## V2-LOCAL-AI-001 execution record (2026-07-30)

- Implemented local Whisper `ggml-small.en-q5_1.bin` selection and local OPUS-MT ONNX translation through the existing `LocalTranslator` contract.
- Added approved OPUS-MT artifact catalog, SHA-256/size validation, atomic private install, tokenizer, deterministic greedy decoder, and emulator-only instrumentation chain.
- Removed product ML Kit translation implementation, ML Kit dependency, Play Services coroutine dependency, and `INTERNET` permission after verification.
- Verification passed: 92 JVM unit tests, `lintDebug`, normal Debug, Native Debug, `assembleDebugAndroidTest`, and offline emulator full chain.
- Offline emulator evidence: `emulator-5554`, display `1080x2400 / 420 dpi`, H.264/AAC MP4 68730 bytes / 4011 ms, ASR 226032 ms, translation 1635 ms, export 428 ms, temperature 25.0 C.
- Final status: `LOCAL_AI_SIMULATOR_VERIFIED`.

## V2-ASR-002 execution record (2026-07-31)

- Entered the ASR quality stage after `LOCAL_AI_SIMULATOR_VERIFIED`.
- Current repository lacks the required quality fixtures: at least three 30-60 second English song clips with accurate human lyrics, timing ranges, and fixed preprocessing conditions.
- No subjective model choice was made; default ASR remains `ggml-small.en-q5_1.bin`.
- Hardened the independent evaluator in `tools/asr-evaluate.py`: approved model names only, comparable fixture sets across models, accurate reference requirement, 30-60 second fixture gate, WER/CER, missing words, repeated text, timestamp validity, elapsed time, peak RSS, temperature, crash/empty/Demo fallback reporting.
- Added `tools/asr_evaluate_test.py` with 6 synthetic non-song contract tests. These tests verify the evaluator only and are not quality fixtures.
- Verification passed: `python tools\asr_evaluate_test.py` and targeted Whisper model validator/importer unit tests.
- Generated three simulator-only review MP4 files from user-provided source videos in `D:\DevEnv\Projects\sorce\` for manual viewing. Each ran through Local Whisper, local OPUS-MT, FFmpegKit export, and Media3 playback with network disabled. These outputs do not include reference lyrics, so they remain manual review artifacts and do not change the quality gate.
- Final status: `FIXTURE_REQUIRED`.

## V2-UI-001 execution record (2026-07-31)

- Established checkpoint `30b4238` before UI work, preserving the prior local-AI/product-path changes and excluding models, media, generated files, `docs/archive/`, and the dirty `third_party/ffmpeg-kit` entry.
- Reworked the Compose editor into four visible workflow panels: import/project, local recognition/translation, subtitle editing, and export/share. The video preview remains the primary visual region.
- Changed the app theme to a dark Material 3 palette with neutral black/gray surfaces, white text, and a limited lime accent. User-visible labels and runtime states are Chinese; no new dependency or processing-path change was introduced.
- Preserved the existing `LocalAiInstrumentation` chain and added a no-input production Activity UI smoke check for a laid-out Compose root and non-empty screenshot.
- Pixel_8 / `emulator-5554` evidence: UI instrumentation passed with `1080x2400` window and screenshot; final activity focus was `com.example.lyriccaptioner/.MainActivity`; a display-0 DocumentsUI video picker opened and showed emulator test videos.
- Review screenshots remain outside the repository at `D:\DevEnv\Projects\lyric-captioner-ui-review.png`, `D:\DevEnv\Projects\ui-flow-scroll.png`, and `D:\DevEnv\Projects\documentsui-video-picker.png`.
- Verification passed: 92 JVM tests, `lintDebug` with 0 errors/0 warnings, normal Debug, Native Debug, and `assembleDebugAndroidTest`.
- Final status: `UI_SIMULATOR_VERIFIED`.

## V2-UI-001 Insets fix verification (2026-08-01)

- Root cause: `MainActivity` did not apply Window Insets while the target SDK edge-to-edge window allowed the Compose root column to start at screen top; the previous ordinary padding did not protect the status bar.
- Minimal fix: applied Compose `statusBarsPadding()` and `navigationBarsPadding()` to the editor root content. No ViewModel, model, FFmpegKit, Media3, export path, or dependency changes.
- `LocalAiInstrumentation` now reads the status-bar inset and the accessibility bounds of the `歌词字幕工作台` node, asserting `titleTop >= statusBarInset`.
- Pixel_8 / `emulator-5554` / `1080x2400` / `420 dpi`: instrumentation passed with `statusBarInset=132`, `titleTop=164`, `titleBottom=256`, screenshot `1080x2400`, and `INSTRUMENTATION_CODE: -1`.
- New repair evidence screenshots, generated 2026-08-01: `D:\DevEnv\Projects\v2-ui-insets-initial-20260801-115158.png`, `D:\DevEnv\Projects\v2-ui-insets-imported-20260801-115158.png`, and `D:\DevEnv\Projects\v2-ui-insets-scroll-20260801-115314.png`. The 2026-07-31 UI screenshots remain baseline-only.
- Verification passed: 92 JVM tests, `lintDebug`, normal Debug, Native Debug, `assembleDebugAndroidTest`, and real UI instrumentation.
- Final status: `UI_SIMULATOR_VERIFIED`.
