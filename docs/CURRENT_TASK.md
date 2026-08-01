# 当前任务：V2-UI-002

## 状态

- Revision: `4`
- Status: `UI2_SIMULATOR_VERIFIED`
- Owner: `Development Agent`
- Product Gate: `V2_UI2_SIMULATOR_ACCEPTANCE`
- Verification Mode: `SIMULATOR_ONLY_TEMPORARY`

## V2-UI-002 Current Execution Contract

- The only completed active task is `V2-UI-002`; the next planned task is `V2-IMPORT-002`.
- Previous verified stages remain preserved: `V2-UI-001 / UI_SIMULATOR_VERIFIED` and `V2-PREVIEW-001 / PREVIEW_SIMULATOR_VERIFIED`.
- Scope: second-round Compose/Material 3 mobile workbench refinement; compact header and status, preview-first layout, clearer import → recognition/translation → subtitle editing → export hierarchy, denser subtitle editing, consistent states, touch targets, accessibility semantics, and safe-area validation.
- Preserve existing local video picker, Media3 fullscreen preview and controls, bilingual subtitle overlay, subtitle style persistence, project/archive semantics, FFmpegKit export, ViewModel behavior, model/runtime selection, and navigation.
- Explicitly out of scope: ASR, translation, FFmpegKit, Media3 business logic, archive semantics, architecture/navigation migration, new dependencies, timeline, trimming, filters, effects, stickers, transitions, speed changes, and physical-device import acceptance.
- Verification mode: only `emulator-5554` / Pixel_8; no physical device. Preserve existing dirty `third_party/ffmpeg-kit` and unrelated untracked content.

## V2-PREVIEW-001 previous verification record

- Historical stage record: `V2-PREVIEW-001` is already verified and is not the active task.
- Preserved stage states: `V2-UI-001 / UI_SIMULATOR_VERIFIED`, `V2-ASR-002 / FIXTURE_REQUIRED`, and `V2-TRN-002 / PLANNED`.
- Scope: full-screen Media3 preview, play/pause/seek/back handling, live bilingual subtitle overlay, subtitle font/size/position/color editing, and persistence through `SubtitleStyle`, `ExportProfile`, project archives, and FFmpegKit output.
- Explicitly out of scope: timeline, multi-track editing, filters, stickers, transitions, effects, trimming, speed changes, cover editing, and new dependencies.
- Verification mode: only `emulator-5554` / Pixel_8; no physical device.

## V2-PREVIEW-001 verification record (2026-08-01)

- Implemented fullscreen Media3 preview using one ExoPlayer instance per imported URI; normal/fullscreen transitions preserve playback position and play state, with Play/Pause, seekbar tap, exit button, and BackHandler coverage.
- Live bilingual subtitle overlay remains driven by `CaptionTimeline`; subtitle font family, size, bottom position, English/Chinese/outline colors are editable and persisted through `SubtitleStyle`, `ExportProfile`, v1/v2 project archives, and ASS/Media3 rendering mappings.
- Added style validation/default compatibility tests and real UI instrumentation semantics for import, SRT import, fullscreen, Media3 controls, Back, no Demo content, and screenshot capture.
- Pixel_8 `emulator-5554`: `1080x2400`, `420 dpi`; real flow imported `/sdcard/Download/v2-preview-test.mp4` and `/sdcard/Download/v2-preview-test.srt`, rendered 2 bilingual cues, entered/exited fullscreen, and returned `INSTRUMENTATION_CODE: -1` with `media3Controls=play_pause_seekbar_tap`.
- New screenshots generated 2026-08-01 outside the repository: `D:\DevEnv\Projects\preview-v2-native-normal-final.png`, `D:\DevEnv\Projects\preview-v2-native-fullscreen-final.png`, and `D:\DevEnv\Projects\preview-v2-native-restored-final.png`.
- Verification passed: 96 JVM tests with 0 failures/errors/skips, `python tools\asr_evaluate_test.py` (6 tests), `lintDebug`, normal Debug, Native Debug (arm64-v8a/x86_64), `assembleDebugAndroidTest`, and real UI instrumentation.
- Final status: `PREVIEW_SIMULATOR_VERIFIED`.

## V2-UI-002 阶段入口记录（2026-08-01）

- 阶段入口时已将唯一活动任务从已验证的 `V2-PREVIEW-001` 切换为 `V2-UI-002 / IN_PROGRESS`；该阶段现已完成并更新为 `UI2_SIMULATOR_VERIFIED`。
- 阶段入口 checkpoint 将只包含三份活动文档；`third_party/ffmpeg-kit` 与既有未跟踪内容不纳入提交。
- 视觉审查基线：`D:\DevEnv\Projects\preview-v2-native-normal-final.png`、`D:\DevEnv\Projects\preview-v2-native-fullscreen-final.png`、`D:\DevEnv\Projects\preview-v2-native-restored-final.png`。初步发现工作台纵向卡片堆叠、状态区占高、字幕编辑区被挤压、主次操作不够集中；全屏预览能力保持不变。

## V2-UI-002 verification record (2026-08-01)

- 完成阶段 checkpoint `6741fc5`，最终功能提交为本阶段独立提交；既有 `third_party/ffmpeg-kit` 脏状态及未跟踪内容未纳入。
- Compose 工作台改为紧凑标题、预览优先、状态条和四段工作区标签；导入、识别/翻译、字幕编辑、样式控制与导出按阶段分区，保留现有 ViewModel、Local AI、FFmpegKit、Media3 和导航行为。
- 新增可访问语义与触控边界检查，覆盖工作区标签、视频导入、字幕列表、样式控制和导出入口；继续使用状态栏/导航栏安全区。
- Pixel_8 `emulator-5554`（`1080x2400` / `420 dpi`）完成真实 DocumentsUI 视频选择与返回、字幕导入、样式控制、导出操作区和 Media3 全屏/退出回放；instrumentation 返回 `INSTRUMENTATION_CODE: -1`，状态栏 inset `132`，安全内容高度 `2400`。
- 测试通过：`asr_evaluate_test.py` 6 项、JVM 96 项、Lint、普通 Debug、Native Debug、AndroidTest 构建及真实 UI instrumentation。
- 新截图（2026-08-01 13:53:42）位于 `D:\DevEnv\Projects\preview-v2-ui2-ui2-initial.png`、`preview-v2-ui2-ui2-imported.png`、`preview-v2-ui2-ui2-subtitles.png`、`preview-v2-ui2-ui2-export.png`、`preview-v2-ui2-preview-fullscreen.png`、`preview-v2-ui2-preview-restored.png`；相较基线，工作流层级更清晰、重复大卡片减少、预览和编辑区可用空间增加。
- 阶段最终状态：`UI2_SIMULATOR_VERIFIED`。不声明 `DEVICE_VERIFIED`；手机本地视频设备门禁延续到 `V2-IMPORT-002` / `V2-E2E-002`。

## 已批准后续阶段（当前任务已完成）

- `V2-UI-002 / UI2_SIMULATOR_VERIFIED`：第二轮移动端视觉与交互精修已完成，仅模拟器证据通过。
- `V2-IMPORT-002 / PLANNED / DEVICE_GATE_DEFERRED`：完成 Android 系统媒体选择器到真实本地视频预览、权限持久化、保存恢复和处理链路。当前禁止真机，因此模拟器通过后仍必须把真机导入证据延续至 `V2-E2E-002`。
- 当前 Developer 不得提前实施这两个模块，也不得因本路线补充扩大 `V2-PREVIEW-001` 范围。

## V2-UI-001 实施记录（2026-07-31）

- 已建立 UI 前 checkpoint：`30b4238 chore: checkpoint local AI and product path before UI`。
- `EditorScreen.kt` 现在使用深色沉浸式编辑背景，以视频预览为视觉中心；导入/项目、识别/翻译、字幕编辑、导出/分享按四个面板分区。
- 用户可见文案已中文化；主操作改为图标符号加短标签，状态、字幕列表和字幕样式均独立成卡片，不再集中堆叠在主 `FlowRow`。
- `Theme.kt` 改为 Material 3 深色配色，使用黑灰层级、白色文字和少量荧光绿强调色；未新增依赖、未改变 ViewModel、媒体处理或导出链路。
- `LocalAiInstrumentation` 保留原本地 AI 链路入口，并增加无输入的生产 Activity UI smoke：验证 Compose 根视图已布局、截图非空且窗口为 `1080x2400`。
- Pixel_8 模拟器 `emulator-5554` 已安装最终 Debug/Native APK；UI smoke 返回 `INSTRUMENTATION_CODE: -1`，窗口和截图均为 `1080x2400`，当前焦点为 `com.example.lyriccaptioner/.MainActivity`。
- 最终截图：`D:\DevEnv\Projects\lyric-captioner-ui-review.png`；滚动后的分区截图：`D:\DevEnv\Projects\ui-flow-scroll.png`；DocumentsUI 视频选择器截图：`D:\DevEnv\Projects\documentsui-video-picker.png`。
- 直接 DocumentsUI 验证显示 `com.google.android.documentsui/com.android.documentsui.picker.PickActivity` 在 display 0 可用，并显示模拟器中的测试视频；源视频未修改。
- 本轮未切换模型、未修改 FFmpegKit/Media3、未连接真机；`third_party/ffmpeg-kit` 的既有脏状态保留。
- 验证：92 JVM tests 通过；`lintDebug` 通过（0 error / 0 warning）；普通 Debug、Native Debug 和 `assembleDebugAndroidTest` 通过。
- 本阶段最终状态：`UI_SIMULATOR_VERIFIED`。

## V2-ASR-002 质量阶段记录（2026-07-31，非当前任务）

- 已按活动文档进入下一阶段 `V2-ASR-002`：英文歌曲和英文单词识别提质。
- 仓库当前没有合格的英文歌曲质量评测 fixture：缺少至少 3 段 30-60 秒英文歌曲音频、人工准确歌词、时间范围/标注和统一采样条件。
- 因缺少人工准确参考歌词，本轮不得运行主观模型选择，也不得把 beep/合成视频或普通短句测试当作歌曲识别质量证据。
- 当前默认模型保持 `ggml-small.en-q5_1.bin`；未切换识别模型。

## V2-ASR-002 已完成的可交付项

- 保留现有 V2-ASR 模型校验、目录、原子导入和 `tools/asr-evaluate.py`。
- 修正 ASR 评测工具的英文引号归一化。
- 评测工具现在只接受已批准本地 Whisper 模型名：
  - `ggml-base.bin`
  - `ggml-base.en.bin`
  - `ggml-small.en-q5_1.bin`
- 评测工具会拒绝：
  - 未批准模型名；
  - 少于 3 段 fixture；
  - 非 30-60 秒 fixture；
  - 缺少准确 reference 的 fixture；
  - 多模型之间 fixture 名称、reference 或时长不一致；
  - 空 cue、无效时间戳、崩溃、空结果和 Demo fallback 会进入指标输出，不会被静默吞掉。
- 新增 `tools/asr_evaluate_test.py`，使用合成非歌曲文本验证评测工具契约；该测试不能作为模型质量样本。
- `.gitignore` 增加本地 ASR 质量 fixture 与评测输出目录：
  - `tools/asr-fixtures/`
  - `tools/asr-results/`

## 验证

- `python tools\asr_evaluate_test.py`：6 tests passed。
- `.\gradlew.bat testDebugUnitTest --tests "com.example.lyriccaptioner.processing.WhisperModelValidatorTest" --tests "com.example.lyriccaptioner.processing.WhisperModelImporterTest"`：BUILD SUCCESSFUL。

## V2-ASR-002 人工复核视频生成记录（2026-07-31）

- 在 `SIMULATOR_ONLY_TEMPORARY` 下使用 `emulator-5554` 和当前 `ggml-small.en-q5_1.bin` 生成了 3 个仅供人工观看的 review MP4。
- 三个源视频位于 `D:\DevEnv\Projects\sorce\`，本轮未修改或覆盖源视频。
- 模拟器 Wi-Fi 和 data 均已关闭；产品包无 Demo fallback 路径，运行时使用本地 Whisper JNI 和本地 OPUS-MT。
- 生成链路：本地视频输入 -> Local Whisper -> OPUS-MT -> FFmpegKit 导出 -> Media3 播放。
- 输出文件：
  - `D:\DevEnv\Projects\sorce\5e4c3cd7073a9e9b03df1fbf8af6d928-small-en-review.mp4`
  - `D:\DevEnv\Projects\sorce\6101d9b51a973fcc6bc8432d87851280-small-en-review.mp4`
  - `D:\DevEnv\Projects\sorce\f1764157e6fccc410443c5cbefaecfac-small-en-review.mp4`
- 这些视频没有人工准确参考歌词，不能生成 WER/CER，不能作为模型质量提升证明。
- 当前仍等待用户对三个输出视频进行人工复核；任务状态保持 `FIXTURE_REQUIRED`。

## V2-ASR-002 下一步所需人工输入

进入真正的模型质量比较前，必须提供或确认以下本地私有 fixture，放入已忽略目录，不提交 Git：

1. 至少 3 段 30-60 秒英文歌曲音频。
2. 每段对应人工准确英文歌词。
3. 每段音频的起止时间、裁剪方式和统一预处理条件。
4. 对每个模型在同一设备/同一预处理条件下产生的 cue JSON、耗时、峰值 RSS、温度、崩溃/空结果/Demo fallback 标记。

## V2-ASR-002 边界

- 不提交模型、歌曲、歌词、日志、APK 或生成物。
- 不读取 `docs/archive/`、`docs-BK/` 或旧交接文档。
- 不修改 FFmpegKit、Media3、导出路线或 `third_party/ffmpeg-kit`。
- 不切换识别模型；只有候选模型在固定数据集上明确优于当前模型时，才允许进入安全切换讨论。
- 不提交 Git，不同步 GitHub。

V2-ASR-002 历史阶段状态：`FIXTURE_REQUIRED`；当前活动任务最终状态：`UI_SIMULATOR_VERIFIED`。

## V2-UI-001 Insets 修复记录（2026-08-01）

- 根因：`MainActivity` 未配置 Window Insets，目标 SDK 的 edge-to-edge 窗口使 Compose 根列从屏幕顶端布局；原有普通 padding 不能避开状态栏。
- 最小修复：`EditorScreen` 根内容增加 Compose `statusBarsPadding()` 与 `navigationBarsPadding()`；未修改 ViewModel、模型、FFmpegKit、Media3、导出链路或依赖。
- `LocalAiInstrumentation` 现在读取状态栏 inset 与可访问性树中“歌词字幕工作台”的真实屏幕 bounds，并断言 `titleTop >= statusBarInset`。
- `emulator-5554` / Pixel_8 / `1080x2400` / `420 dpi`：真实 instrumentation 通过，`statusBarInset=132`、`titleTop=164`、`titleBottom=256`、截图 `1080x2400`、`INSTRUMENTATION_CODE: -1`。
- 本轮新截图（旧 UI 截图仅作为修改前基线，不作为本次修复证据）：
  - `D:\DevEnv\Projects\v2-ui-insets-initial-20260801-115158.png`
  - `D:\DevEnv\Projects\v2-ui-insets-imported-20260801-115158.png`
  - `D:\DevEnv\Projects\v2-ui-insets-scroll-20260801-115314.png`
- 全量验证通过：92 JVM tests、`lintDebug`、普通 Debug、Native Debug、`assembleDebugAndroidTest` 和真实 UI instrumentation。
- 本轮最终状态：`UI_SIMULATOR_VERIFIED`。
