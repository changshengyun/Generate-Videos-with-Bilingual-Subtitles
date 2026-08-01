# LyricCaptioner 当前状态

## 当前门禁

- 当前阶段：`V2_PREVIEW_IMPLEMENTING`
- 当前任务：`V2-PREVIEW-001 / IMPLEMENTING`
- 当前目标：在现有产品链路中完成全屏 Media3 预览、实时字幕叠加和字幕样式持久化
- 下一任务：完成模拟器全链路验收后进入 `PREVIEW_SIMULATOR_VERIFIED`

## 仓库快照

- 仓库：`D:\DevEnv\Projects\lyric-captioner-android`
- 分支：`migration/lyric-captioner-history`
- 核对时 HEAD：`b7830710c64660083521708f533ac3a25fa7ec70`
- 工作树存在未提交的 V2-ASR 模型校验、目录、测试、评测工具和三份活动文档修改。
- `third_party/ffmpeg-kit` 保持既有脏状态，不得清理、重置或并入本轮。

开始开发时必须重新核对 Git 状态；以上快照只说明本次规划依据，不覆盖后续变化。

## 已验证基线

V1 已在个人 ARM64 设备完成真实五分钟工作流：

- 手机本地视频导入；
- Local Whisper 识别；
- 离线中文翻译与人工确认；
- 保存、重启和恢复；
- FFmpegKit 导出 H.264/AAC MP4；
- 双语字幕可见且 Media3 回放正常；
- 取消/失败清理通过。

该结果证明架构可行，但不能代替当前 V2 代码的重新验收。

## 当前实现事实

- FFmpegKit 是当前产品烧录后端，Media3用于播放/回放。
- 产品工厂和 UI 已只允许 Local Whisper；固定 Demo 处理文件已删除，缺少模型/JNI 时明确失败。
- 当前有 38 个主 Kotlin 文件、23 个 JVM 测试文件、无有效 `androidTest` 用例；数量不是质量目标。
- `MainViewModel.kt` 与 `EditorScreen.kt` 较大，后续只按业务边界渐进拆分，不整体重写。
- 历史 Media3 导出实现、Demo 处理、一次性诊断代码和重复测试是清理候选，不是自动删除项。

## V2-ASR 资产状态

- 模型目录、精确校验、原子导入和评测脚本已进入当前工作树。
- 候选模型准备和构建测试已有证据，但缺少合格英文歌曲音频、准确歌词/时间标注与稳定设备条件。
- `V2-ASR-001` 暂停为 `PAUSED_WITH_ASSETS`；相关文件不得因当前任务而删除。
- `V2-ASR-002` 已进入，但因缺少合格 fixture 停在 `FIXTURE_REQUIRED`；不得主观选择模型。
- 后续 `V2-ASR-002` 将用固定数据集比较 WER、关键词召回、时间戳、耗时、内存和模型大小。

## 已批准开发顺序

1. `V2-PROD-001`：`ARM64_PRODUCT_PATH_VERIFIED`，当前版本真实产品路径和测试/Review 基线已通过。
2. `V2-CLEAN-001`：安全删除/合并和代码级验证已完成，完整链路转交本地模型模块收口。
3. `V2-LOCAL-AI-001`：`LOCAL_AI_SIMULATOR_VERIFIED`，双本地模型模拟器闭环已通过。
4. `V2-ASR-002`：当前活动任务，英文歌曲和英文单词识别提质；等待固定英文歌曲 fixture。
5. `V2-TRN-002`：中文翻译提质。
6. `V2-UI-001`：`UI_SIMULATOR_VERIFIED`，中文界面与主流程层级整理已通过。
7. `V2-PREVIEW-001`：`IMPLEMENTING`，全屏预览和字幕样式编辑。
8. `V2-E2E-002`：用户重新授权后的 ARM64 最终验收。

## 质量与删除决策

- 先定义产品不变量和测试覆盖，再删除测试或生产代码。
- 无关测试是指没有独立边界价值、仅覆盖已删除实现或已被更高层契约测试完整替代；不是“运行慢”或“数量多”。
- 失败测试不得直接删除，必须先判断是产品缺陷、过期契约还是测试自身错误。
- 普通 Bug由开发窗口自行完成最小复现、根因修复和回归；不因连续失败自动换技术栈或降低验收标准。
- 重大依赖、架构、数据破坏或范围冲突由决策窗口批准。

## 临时设备测试决策（2026-07-30）

- 用户要求后续开发暂时跳过所有真机测试，改用 Android 模拟器。
- 最近一轮已通过 84 项单测、Lint、双 Debug 构建和模拟器 Native 启动冒烟；`connectedDebugAndroidTest` 为 0 用例。
- 该结果没有实施 `V2-CLEAN-001` 的安全删除/合并，也没有运行模拟器完整产品链，因此此前的 `BLOCKED` 不转为完成，而是解除真机阻断后回到 `READY_FOR_IMPLEMENTATION`。
- 当前模块必须完成实际精简和模拟器完整链路后，才能标记 `V2_CLEAN_SIMULATOR_VERIFIED`。
- 本轮已完成安全删除/合并和全量代码验证，但 Pixel_8 的多显示 Launcher/无障碍焦点问题阻止真实 Import 进入 DocumentsUI；因此当前最终状态为 `BLOCKED`，不得把启动冒烟或 0 用例 instrumentation 当作完整链路通过。
- `V2-E2E-002` 的 ARM64 最终验收延后，等待用户重新授权。

## 双本地模型资产与决定（2026-07-30）

- 用户明确要求识别和翻译均使用可手动下载的本地模型，并授权替换 ML Kit 动态翻译模型路径。
- `tools/ggml-small.en-q5_1.bin` 已核验：190098681 bytes，SHA-1 `20F54878D608F94E4A8EE3AE56016571D47CBA34`。
- `tools/opus-mt-en-zh` 最小运行包已核验齐全，总大小 122854406 bytes；包含量化 encoder、merged decoder、SentencePiece、tokenizer 和生成配置。
- ONNX 文件当前位于模型目录根部，后续加载器按实际布局读取；不得运行时访问项目工具目录，而应校验后复制到 App 私有目录。
- 当前 ML Kit `NEEDS_DOWNLOAD` 阻断由 `V2-LOCAL-AI-001` 取代处理；新后端通过前不删除对照实现，通过后移除产品引用和无用依赖。

## V2-PROD-001 verification snapshot (2026-07-28)

- Code path: Demo processing file removed; factory and UI permit only Local Whisper; unavailable model/JNI is explicit. Translation, FFmpegKit export, SAF import/relink, derived-output invalidation, and Media3 playback boundaries remain in place.
- Regression/build evidence: 84 JVM unit tests passed; lint, normal Debug, and Native Debug passed. The final Native APK installed successfully with `primaryCpuAbi=arm64-v8a`.
- ARM64 acceptance passed on `fcf4b0cb / 25098PN5AC` (`arm64-v8a`): real SAF video import, Local Whisper, real translation, project save/restore, FFmpegKit export, and Media3 preview playback completed. ASR was approximately 113 seconds with observed peak RSS 566,820 kB and peak sampled temperature 36.3 C; translation was 1,451 ms; export was approximately 10.79 seconds.
- Final output was 46,276,534 bytes, 299.792 seconds, H.264/AAC, with visible English/Chinese burned-in subtitles and FFmpegKit return code 0. Media3 preview video-region comparison changed 568 sampled points across 3 seconds. No Demo fallback occurred and no device user data was deleted or reset. Failure/cancel/temp cleanup remains covered by passing tests.
- Review inventory: KEEP 10 / MERGE 2 / DELETE 1 / DEFER 5. Existing V2-ASR model catalog/import/validator/tests and `tools/asr-evaluate.py` remain preserved; `third_party/ffmpeg-kit` remains dirty and untouched.
- Final state: `ARM64_PRODUCT_PATH_VERIFIED`.

## V2-CLEAN-001 verification snapshot (2026-07-30)

- Safe cleanup implemented: deleted unused `processing/LocalModelManagers.kt` and the obsolete Demo compatibility test; merged Local ASR state handling and FFmpegKit temporary/destination cleanup ownership. Product-invariant tests, model catalog/import/validator, and `tools/asr-evaluate.py` remain.
- Before/after metrics: main Kotlin `39/4209 -> 38/4173`; test Kotlin `23/1415 -> 23/1393`; unit tests `84 -> 83`; Native Debug APK `138394427 -> 138394427` bytes; current lint `0` errors and `3` warnings, baseline warning count unavailable.
- Code verification: all 83 JVM unit tests passed; `lintDebug`, normal Debug, and Native Debug passed; connected instrumentation reported 0 test cases. Native simulator launch showed Local ASR, ready model, and ready JNI with no Demo fallback.
- Full simulator product path remains unverified because `uiautomator`/Launcher focus is inconsistent across Pixel_8 displays and Import cannot reliably open the SAF picker. No claim is made for real video import, Local ASR inference, translation, save/restore, FFmpegKit output, or Media3 playback in this cleanup run.
- LEFT_FOR_REVIEW: Media3 exporter/observability, historical one-off maintenance logic, large `MainViewModel`/`EditorScreen` split boundaries, and the ML Kit-used `LocalModelManager` contract. `third_party/ffmpeg-kit` remains dirty and untouched. Final state: `BLOCKED`.

## 当前唯一下一步

为 `V2-ASR-002` 准备固定英文歌曲质量评测 fixture：至少 3 段 30-60 秒英文歌曲音频、人工准确英文歌词、时间范围/裁剪方式和统一预处理条件。缺少这些 fixture 时，不能运行模型优选或安全切换。

## V2-UI-001 verification snapshot (2026-07-31)

- UI checkpoint: `30b4238`。
- Production UI now uses a dark Material 3 editor surface, preview-first layout, four workflow panels, short Chinese labels with icon symbols, and separate runtime/subtitle/style cards. Existing processing callbacks and product routing are unchanged.
- Pixel_8 `emulator-5554` passed the real UI smoke instrumentation: Compose root laid out, screenshot non-empty, window/screenshot `1080x2400`, and final focused activity `com.example.lyriccaptioner/.MainActivity`.
- Review screenshots are outside the repository: `D:\DevEnv\Projects\lyric-captioner-ui-review.png`, `D:\DevEnv\Projects\ui-flow-scroll.png`, and `D:\DevEnv\Projects\documentsui-video-picker.png`.
- Direct display-0 DocumentsUI verification opened the video picker and showed the emulator test videos; user source files were not modified. No true device was used.
- Verification passed: 92 JVM tests, `lintDebug` 0 errors/0 warnings, normal Debug, Native Debug, and AndroidTest APK build. Existing local-AI chain instrumentation remains registered.
- Final state: `UI_SIMULATOR_VERIFIED`.

## V2-LOCAL-AI-001 verification snapshot (2026-07-30)

- Local models: Whisper `ggml-small.en-q5_1.bin` verified at 190098681 bytes / SHA-1 `20F54878D608F94E4A8EE3AE56016571D47CBA34`; OPUS-MT approved runtime package verified at 122854406 bytes across 7 SHA-256 checked artifacts.
- Product path: `OnnxLocalTranslator` replaces product ML Kit translation; ML Kit implementation/dependencies and app `INTERNET` permission were removed. Runtime model preparation is local asset -> validated private install only.
- Verification: 92 JVM unit tests passed with 0 failures/errors/skips; `lintDebug`, normal Debug, Native Debug, and `assembleDebugAndroidTest` passed.
- Offline emulator full chain passed on `emulator-5554`: local video -> Local Whisper -> local OPUS-MT translation -> project save/restore -> FFmpegKit export -> Media3 playback.
- Output evidence: H.264 `video/avc`, AAC `audio/mp4a-latm`, 68730 bytes, 4011 ms; bilingual cue `(beep)` -> `(哔哔声)`; fixed translation probe `hello world` -> `世间喜悦`.
- Resource data: ASR 226032 ms, fixed translation probe 2521 ms, ASR-cue translation 1635 ms, export 428 ms, battery 100, temperature 25.0 C.
- Final state: `LOCAL_AI_SIMULATOR_VERIFIED`.

## V2-ASR-002 fixture gate snapshot (2026-07-31)

- Entry evidence: active roadmap lists `V2-ASR-002` after `V2-LOCAL-AI-001`; current repository has no approved English song quality fixture set.
- Tooling update: `tools/asr-evaluate.py` now rejects unapproved model names, non-comparable fixture sets, missing accurate references, too few fixtures, and out-of-range durations. It reports WER, CER, missing words, repeated tokens, timestamp validity, elapsed time, peak RSS, temperature, crash, empty result, and Demo fallback.
- Added evaluator regression tests in `tools/asr_evaluate_test.py`; tests use synthetic non-song text and cannot be used for model selection.
- Ignored local-only ASR quality data directories: `tools/asr-fixtures/` and `tools/asr-results/`.
- Verification: 6 Python evaluator tests passed; targeted Whisper validator/importer JVM tests passed.
- Default model remains `ggml-small.en-q5_1.bin`; no model switch, no true-device operation, no FFmpegKit/Media3/third_party changes.
- User review artifacts: three MP4 outputs were generated in `D:\DevEnv\Projects\sorce\` through simulator-only Local Whisper -> OPUS-MT -> FFmpegKit -> Media3 playback. They are for manual viewing only; without accurate reference lyrics they do not produce WER/CER and do not prove quality improvement.
- Current next step remains human review of the three output videos plus preparation of accurate lyric fixtures.
- Final state: `FIXTURE_REQUIRED`.

## V2-UI-001 Insets fix snapshot (2026-08-01)

- Root cause confirmed: no Compose Window Insets were applied to the edge-to-edge activity content, so the title could occupy the status-bar region.
- `EditorScreen` now applies `statusBarsPadding()` and `navigationBarsPadding()` at the root content boundary. Product processing and all non-UI modules are unchanged.
- The production UI instrumentation asserts the actual accessibility title bounds against the actual status-bar inset. On `emulator-5554`, `1080x2400 / 420 dpi`, it passed with `statusBarInset=132`, `titleTop=164`, `titleBottom=256`, and `INSTRUMENTATION_CODE: -1`.
- New screenshots generated 2026-08-01: `D:\DevEnv\Projects\v2-ui-insets-initial-20260801-115158.png`, `D:\DevEnv\Projects\v2-ui-insets-imported-20260801-115158.png`, `D:\DevEnv\Projects\v2-ui-insets-scroll-20260801-115314.png`. Previous screenshots are not repair evidence.
- Verification passed: 92 JVM tests, `lintDebug`, normal Debug, Native Debug, `assembleDebugAndroidTest`, and real UI instrumentation.
- Current gate remains `V2_UI_SIMULATOR_ACCEPTANCE`; final state: `UI_SIMULATOR_VERIFIED`.
