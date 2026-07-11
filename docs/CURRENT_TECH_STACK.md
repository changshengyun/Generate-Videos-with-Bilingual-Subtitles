# LyricCaptioner 当前真实技术栈

## 生效栈

| 层 | 当前实际技术 | 版本/配置 | 证据 |
|---|---|---|---|
| 语言 | Kotlin；JNI 层 C++17 | Kotlin 2.0.21 | Gradle plugin、`.kt`、CMake/cpp |
| UI | Jetpack Compose + Material3 | Compose BOM 2024.12.01 | `EditorScreen`、依赖表 |
| 状态/并发 | AndroidX ViewModel/StateFlow + coroutines | Lifecycle 2.8.7、coroutines 1.9.0 | import 与调用路径 |
| 构建 | Gradle Wrapper + Kotlin DSL + AGP | Gradle 8.9、AGP 8.7.3、JDK 17 | wrapper/build files、本轮构建 |
| Android | 单 application module | compileSdk 36、targetSdk 35、minSdk 26 | `app/build.gradle.kts` |
| 播放/导出 | Media3 ExoPlayer/UI/Transformer/Effect | 1.10.1 | 依赖与正式调用路径 |
| 音频抽取 | Android MediaExtractor + MediaCodec | 平台 API | `AndroidAudioExtractor` |
| DSP | 自有 Kotlin PCM16 混音、16 kHz 线性重采样、WAV | 项目代码 | 24 项 JVM 测试中的相关组件 |
| 本地 ASR | whisper.cpp JNI | 目标源码 v1.9.1；NDK 27.3.13750724；CMake 3.22.1 | CMake/JNI、双 ABI APK |
| 翻译 | Google ML Kit Translate | 17.0.3 | 依赖与 `MlKitLocalTranslator` |
| 文件访问 | Android SAF / `content://` | 平台 API | Activity Result contracts |
| 测试 | JUnit 4 | 4.13.2 | 本轮强制重跑：Debug 24/24、Release 24/24 通过 |

## 实际构建变体

- 默认 `assembleDebug`：不启用 `externalNativeBuild`，APK 没有项目 Whisper `.so`，运行时必然不能进入 Local ASR。
- `assembleDebug -PenableWhisperNative=true`：构建 ARM64/x86_64 Whisper JNI；本轮 APK 内容已核验。
- 模型文件不打包，由用户导入 `files/models/ggml-base.en.bin`；“native APK 可构建”不等于模型已安装或推理可运行。

## 历史路线残留

- `third_party/ffmpeg-kit`：gitlink 存在且工作树 modified，但当前 app 的 Gradle dependencies 和 import/调用链没有 FFmpegKit；判定为残留，不是生效栈。
- `DemoProcessing.kt`：不是纯历史文件，而是当前正式默认降级路径；在模型/JNI 未就绪时真实生效。
- `LocalModelManagers.kt` 中 Demo 状态管理类仍存在；是否被当前 UI 使用需与具体调用进一步区分，不能据文件存在认定生效。

## 兼容性和版本状态

- AGP 8.7.3 的已测试 compileSdk 上限为 35，而项目使用 36；本轮构建成功但存在未来兼容风险。
- CMake 报 SDK XML v3/v4 工具版本漂移警告；本轮不影响 native 构建。
- `minSdk 26` 是配置事实，不是已在 API 26 设备验证的兼容事实。
- Media3 真实导出类会检查输出大小、音视频轨和时长；字幕视觉、编码器兼容和跨播放器播放仍需设备探针。
- 当前 Media3/OverlayEffect 路径在历史指定 x86_64 模拟器上存在可重复的 frame-processing 实现故障；该故障不等价于 Media3 架构被否定。

## 状态不明组件

- Whisper 模型的实际文件版本、哈希、许可、内存占用和推理质量。
- ML Kit 模型是否已在目标设备下载、离线缓存是否可靠。
- 厂商 MediaCodec 对代表性输入/输出格式的支持。
- 项目归档中 `content://` URI 的持久访问权限。
