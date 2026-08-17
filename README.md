# LyricCaptioner — Android 本地双语字幕工具

> 本地优先（Local-first）的 Android 双语字幕工作流：导入短视频 → 本地语音识别生成英文字幕 → 中英翻译/歌词纠错（本地模型或云端增强）→ 视频画面内逐段编辑 → 预览 → 导出带双语硬字幕的 MP4。

- 平台：Android-only（`minSdk 26` / `targetSdk 35` / `compileSdk 36`，ABI 仅 `arm64-v8a` + `x86_64`）
- 语言与 UI：Kotlin + Jetpack Compose + Material 3（单 Activity 单模块 `:app`）
- 状态：V3 开发中；活动状态以 `docs/` 三份活动文档为准（见下文「项目状态」）

---

## 1. 项目目的

LyricCaptioner 解决一个具体问题：在 Android 手机上为**短视频**（默认不超过 5 分钟）快速生成并导出**中英双语字幕视频**，同时保持**本地优先**——视频导入、音频处理、语音识别、预览和导出全部在设备本地完成，不依赖账号或云端处理用户媒体。

用户可以选择四种字幕来源：

1. 导入已有 **SRT** 字幕；
2. 导入**歌词**并自动按时间轴对齐（`LyricLineAligner`）；
3. **手动**录入字幕；
4. 真实**本地语音识别**（whisper.cpp JNI）生成英文草稿。

生成草稿后可以编辑英文与中文内容、逐段调整样式与位置，用 Media3 预览，最后经 FFmpegKit 把双语字幕**烧录进视频**并保存到系统相册。

## 2. 功能特性

- **系统媒体导入**：默认使用系统 Photo Picker / 媒体选择器，提供 SAF「选择其他位置」入口；支持失效 URI 重绑与项目级视频关联。
- **本地音频处理**：从视频提取音轨 → 重采样为 16 kHz 单声道 PCM16（`LinearPcm16Resampler` / `Pcm16ChannelMixer` / `Pcm16ToMono16kProcessor`）→ 交给 ASR。
- **本地语音识别（ASR）**：whisper.cpp JNI（`lyriccaptioner_whisper`），默认模型 `ggml-small.en-q5_1.bin`（约 190 MB）；进程级 Whisper 上下文缓存（`WhisperSessionRuntime`），串行识别、可取消、内存压力自动释放。
- **中英翻译与歌词纠错**：
  - 本地回退：OPUS-MT en→zh（ONNX Runtime）逐条翻译；
  - 云端增强（V3）：DeepSeek **BYOK**（Bring Your Own Key）批量纠错英文 + 中文翻译，严格 JSON Schema 校验、按 `cue_id` 原子回填，失败/低置信度自动回退本地翻译。
- **字幕编辑**：时间轴（`cue_id + start_ms + end_ms`）、逐段样式与位置覆盖、视频画面内直接操作（拖动/缩放/删除/字号）。
- **统一坐标系统**：预览（普通/全屏）与 ASS 导出共用同一套「源视频像素 + 归一化布局」解析规则，保证所见即所得。
- **预览与导出**：Media3 预览与字幕叠加；FFmpegKit（LTS 6.1.4，minimal-gpl 16kb AAR）烧录双语 ASS 字幕；MediaStore 默认保存到系统相册 `Movies/LyricCaptioner`。
- **项目持久化**：`.lcp` 工程归档（`ProjectArchive`）保存/恢复视频引用、字幕、样式与布局；字幕文本变化会使旧导出失效。

## 3. 技术栈

| 领域 | 选型 |
|---|---|
| 语言 | Kotlin 2.0.21（JVM target 17） |
| 构建 | AGP 8.7.3、Gradle 8.9+、Kotlin Compose 插件 |
| UI | Jetpack Compose（BOM 2024.12.01）、Material 3 |
| 架构 | 单模块 MVVM：`MainActivity` → `MainViewModel` → 领域管线（`CaptionPipeline` / `AsrModule` / `ExportEngine`） |
| 媒体 | AndroidX Media3 1.10.1（ExoPlayer / Transformer / Effect） |
| 原生 | FFmpegKit LTS 6.1.4（`app/libs` 内置 AAR）；whisper.cpp JNI（可选 Native 构建）；NDK 27.3.13750724、CMake 3.22.1 |
| 模型推理 | ONNX Runtime 1.20.0（OPUS-MT 本地翻译） |
| 并发 | kotlinx-coroutines 1.9.0 |
| 安全 | Android Keystore AES-256-GCM（DeepSeek Key 加密存储） |
| 测试 | JUnit 4 + JVM 单测、`lintDebug`、AndroidTest（设备诊断/插桩） |

## 4. 架构

### 4.1 主链路数据流

```text
系统相册/媒体选择器导入视频（≤5 min）
  → AndroidAudioExtractor 提取音频
  → PCM16 重采样为 16 kHz 单声道 WAV
  → 本地 Whisper ASR（whisper.cpp JNI，进程级上下文缓存）
  → Raw CaptionCue（英文草稿 + 时间区间）
  → 增强链路（二选一）：
      ├─ 云端 DeepSeek BYOK：整批英文 → 严格 JSON Schema → 按 cue_id 原子回填
      └─ 本地 OPUS-MT（ONNX）：逐条英译中回退
  → 字幕编辑（时间轴 + 视频画面内直接操作 + 逐段样式/位置）
  → Media3 普通/全屏预览（统一坐标解析）
  → FFmpegKit 烧录双语 ASS 字幕
  → MediaStore 导出到系统相册（Movies/LyricCaptioner）
```

项目可随时保存为 `.lcp` 归档并恢复（`ProjectArchive`）。

### 4.2 代码分层（`app/src/main/java/com/example/lyriccaptioner/`）

| 包 | 职责 | 代表类型 |
|---|---|---|
| 根包 | 入口与状态 | `MainActivity`、`MainViewModel` |
| `audio` | PCM16 音频处理 | `LinearPcm16Resampler`、`Pcm16ChannelMixer`、`Pcm16ToMono16kProcessor`、`Pcm16WavWriter` |
| `processing` | 管线编排与实现 | `CaptionPipeline`、`AsrModule`、`WhisperProcessSession`、`WhisperSessionRuntime`、`OnnxLocalTranslator`、`FfmpegKitSubtitleExporter`、`MediaStoreExportGateway`、`ExportEngine`、`ExportLifecycle` |
| `processing/enhancement` | 云端 AI 增强 | `CaptionEnhancementCoordinator`、`DeepSeekCaptionEnhancementProvider`、`StrictJson`、`SongLyricsSearchTool` |
| `processing/enhancement/byok` | BYOK 安全存储与认证 | `DeepSeekByokManager`、`AndroidKeystoreDeepSeekKeyStore`、`DeepSeekModelsAuthenticationProbe` |
| `captions` | 字幕模型与格式 | `CaptionTimeline`、`CaptionTimingEditor`、`SrtParser`/`SrtWriter`、`LyricLineAligner` |
| `model` | 领域模型与策略 | `CaptionCue`、`CaptionStyleModel`、`CaptionGeometryModel`、`EditorState`、`ExportProfile`、各 `*Policy`（导入/提交/派生输出/直接编辑…） |
| `project` | 项目持久化 | `ProjectRepository`、`AndroidProjectRepository`、`ProjectArchive`（`.lcp`） |
| `ui` | Compose UI 与契约 | `EditorScreen`、`EditorUiPolicy`、`ProductUiContract`、`Theme` |
| `cpp`（Native 构建） | whisper.cpp JNI 桥 | `whisper_jni.cpp`、`CMakeLists.txt` |

设计要点：

- **本地优先**：识别、翻译回退、预览、导出全本地；仅 DeepSeek 增强需要网络，且只发送 cue ID、时间戳与英文文本。
- **进程级 Whisper 缓存**：`WhisperSessionRuntime` 单例持有唯一 native 上下文，串行推理、可取消、模型指纹（路径/大小/SHA-256）变化或内存压力时安全释放。
- **策略与实现分离**：大量领域规则以纯 Kotlin 策略对象（`*Policy`）表达，便于 JVM 单测。
- **统一渲染解析**：Compose 预览与 ASS 导出共用 `CaptionRenderResolver`，避免两套坐标/样式常量漂移。

## 5. 目录结构

```text
lyric-captioner-android/
├── app/
│   ├── src/main/                  # 应用代码（见 4.2）、AndroidManifest、res、cpp
│   ├── src/test/                  # JVM 单元测试
│   ├── src/androidTest/           # 插桩测试与设备诊断
│   └── libs/                      # FFmpegKit LTS AAR（已入库）
├── tools/                         # 模型资产 + 开发/评估脚本
│   ├── ggml-small.en-q5_1.bin     # Whisper small.en q5_1（gitignore，需自行放置）
│   ├── opus-mt-en-zh/             # OPUS-MT en→zh ONNX 模型
│   ├── asr-evaluate.py / asr_evaluate_test.py   # ASR 质量评估
│   ├── setup-whisper-native.ps1   # 恢复 whisper.cpp 原生依赖
│   └── validate_multi_agent_architecture.py     # 仓库治理校验
├── docs/                          # 活动文档（路线/当前任务/状态）+ V3 架构方案
├── docs-v2/                       # V2 归档（仅追溯）
├── docs-BK/                       # 早期文档备份（仅追溯）
├── third_party/                   # ffmpeg-kit 源码、whisper.cpp（gitignore，脚本恢复）
├── gradle/                        # Wrapper（Gradle 8.9）
├── AGENTS.md                      # 工程规则（多 Agent 开发协议）
└── README.md
```

## 6. 快速启动

### 6.1 环境要求

- JDK 17
- Android SDK（含 platform 36），`local.properties` 中配置 `sdk.dir`（示例：`sdk.dir=C:/Users/<user>/AppData/Local/Android/Sdk`）
- Gradle 8.9+（项目已带 Wrapper）
- 运行/验证设备：ARM64 真机或 **x86_64** 模拟器（ABI 过滤器仅含 `arm64-v8a`、`x86_64`，不支持 armeabi-v7a）
- Native 构建额外需要：Android NDK `27.3.13750724` 与 CMake 3.22.1

> ⚠️ 本仓库 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 指向本机 zip（`file:///D:/DevEnv/Tools/gradle-8.9-bin.zip`）。新机器请改为官方发行版：`https://services.gradle.org/distributions/gradle-8.9-bin.zip`（AGP 8.7.3 要求 Gradle ≥ 8.9）。

### 6.2 准备模型资产

`ggml-*.bin` 已被 `.gitignore` 排除，克隆/解包后**必须手动放置**：

1. 将 Whisper 模型放入 `tools/`（名称须与 `WhisperModelCatalog` 批准清单及 SHA-1 匹配）：
   - 默认：`ggml-small.en-q5_1.bin`
   - 亦可：`ggml-base.bin` / `ggml-base.en.bin`
2. 确认 `tools/opus-mt-en-zh/` 存在（OPUS-MT 本地翻译回退；缺失则翻译回退不可用）。

构建时 `prepareLocalModelAssets` 任务会自动把上述文件拷贝进 `app` 的 assets（`build/generated/local-model-assets`）。

### 6.3 构建与运行（Windows 示例，macOS/Linux 用 `./gradlew`）

```bash
# 普通 Debug 构建（不含 whisper.cpp 原生库，本地 ASR 不可用）
gradlew.bat :app:assembleDebug

# Native Debug 构建（启用本地 ASR；需先恢复 whisper.cpp，见 6.4）
gradlew.bat :app:assembleDebug -PenableWhisperNative=true
```

用 Android Studio 打开根目录直接运行，或 `adb install` 生成的 APK。

### 6.4 启用本地 Whisper 识别（Native 构建）

本地 ASR 依赖编译进 `lyriccaptioner_whisper` 的 whisper.cpp JNI 库；**普通 Debug 构建不带该库，ASR 会进入 `UNAVAILABLE` 状态**（AppPipelineFactory 路由）。启用步骤：

1. 恢复原生依赖：`powershell -ExecutionPolicy Bypass -File tools\setup-whisper-native.ps1`（还原 `third_party/whisper.cpp`，已被 gitignore）；
2. 使用 `-PenableWhisperNative=true` 构建（触发 `externalNativeBuild`，CMake 编译 `app/src/main/cpp`）。

### 6.5 测试与校验

```bash
# JVM 单元测试
gradlew.bat testDebugUnitTest

# Lint
gradlew.bat lintDebug

# AndroidTest（需连接 ARM64 真机或 x86_64 模拟器）
gradlew.bat connectedDebugAndroidTest

# ASR 质量评估脚本的单元测试
python tools\asr_evaluate_test.py

# 对本地 ASR cue JSON 与人工歌词参考做质量评估（fixture 见脚本注释）
python tools\asr-evaluate.py <result.json>
```

## 7. 项目状态与文档

当前处于 **V3** 阶段，活动状态一律以 `docs/` 三份活动文档为准（Git 分支 `migration/lyric-captioner-history`，HEAD 记录于 `docs/PROJECT_STATE.md`）：

- `docs/DEVELOPMENT_ROADMAP.md` — V3 产品目标、阶段顺序、依赖与总体验收
- `docs/CURRENT_TASK.md` — 当前唯一活动任务、冻结验收矩阵与人工决策门
- `docs/PROJECT_STATE.md` — 实时门禁、已确认事实、风险与下一允许动作
- `docs/V3_PRODUCT_ARCHITECTURE.md` — V3 产品交互、统一坐标系统、模型缓存与云端增强详细方案

**当前门禁**：`V3-SAVE-EXPORT-001 / HUMAN_DECISION / MIUI_PICKER_SELECTION_REQUIRED`（保存/导出故障已定位并构建修复包，等待用户在 MIUI 系统选择器中手动选择 `.lcp` 后完成恢复与导出复验）。V3 总体验收需要目标 ARM64 真机完整通过「相册导入 → 真实识别 → 增强/回退 → 编辑 → 预览 → 导出 → 回放」主链路；在此之前的任何组件级证据不构成产品 PASS。历史归档：`docs-v2/`（V2）、`docs/archive/v3/`（V3 阶段历史）、`docs-BK/`（早期备份）。

## 8. 隐私与安全设计

- **本地优先**：视频、音频、字幕与项目文件默认不出设备；导出仅在本地完成。
- **DeepSeek BYOK**：API Key 只允许在 App 内输入，以 Android Keystore AES-256-GCM 密文保存；「保存并验证」「测试连接」只向固定的 `https://api.deepseek.com/models` 发送最小认证请求，不发送视频、字幕、歌词、媒体路径或其他用户内容；增强请求只包含 cue ID、时间戳与英文文本，并在设备端做严格 JSON Schema 与 `cue_id` 集合一致性校验后原子回填。
- **日志脱敏**：不记录 API Key / Authorization、完整用户歌词或私有媒体路径；崩溃与导出日志只含事件、错误类型、return code、计数/大小/耗时等字段。
- **源文件安全**：导出失败不残留半成品 MediaStore 行，不修改源视频。

## 9. 开发规则

- 一次只推进**一个完整模块**；普通编译错误、局部逻辑错误与测试修复可在已批准范围内自行处理。
- 技术路线、依赖、原生工具链、架构、环境、破坏性操作、设备验证与范围扩大必须先经人工决策。
- 阶段实现期间不逐工作单元提交；仅在被裁决接受并同步活动文档后创建一个中文阶段提交，默认不 push。
- 多 Agent 开发必须完整遵守 `AGENTS.md` 与 `.agents/multi-agent-development.md`（拓扑：root 协调壳 → 唯一内部 Brain → Brain-owned Limbs）。
- 编译成功不等于验收：需区分 `BUILD_VERIFIED`、`COMPONENT_VERIFIED`、`SIMULATOR_VERIFIED`、`DEVICE_VERIFIED` 与正式产品验收，不得用 Demo/mock/固定结果冒充真实链路。

## 10. 许可证与致谢

- 项目自身暂未声明许可证文件。
- 内置 FFmpegKit LTS AAR 为 **GPL** 变体（`ffmpeg-kit-lts-minimal-gpl`）：分发或商用前请评估 GPL 合规义务。
- [whisper.cpp](third_party/whisper.cpp)（MIT）与 [ffmpeg-kit](third_party/ffmpeg-kit) 源码位于 `third_party/`；ONNX Runtime（MIT）与 OPUS-MT 模型由各自许可条款约束。
- 第三方组件许可证详见各 `third_party/` 目录与 AAR 内 `res/raw/license*.txt`。
