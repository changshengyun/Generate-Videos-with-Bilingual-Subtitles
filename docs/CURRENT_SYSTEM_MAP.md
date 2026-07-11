# LyricCaptioner 当前系统地图

## 审计边界

本图只描述 `lyric-captioner-android/`。根仓库中的 `health-assistant`、工程 Skill 和中央 debug log 不属于该产品运行系统。

## 应用入口与状态中心

- Android 入口：`MainActivity`，创建 `MainViewModel` 并渲染 Compose `EditorScreen`。
- UI：`EditorScreen.kt`，负责 SAF 文件选择/创建、视频播放器、字幕编辑控件、状态展示。
- 状态中心：`MainViewModel.kt` + `EditorState`，保存视频 URI、时长、字幕、模型状态、导出状态和样式。
- 处理编排：`CaptionPipeline`，串联音频抽取、英文识别、纠错、翻译和导出。

## 主要模块

| 模块 | 责任 | 当前实现 |
|---|---|---|
| `ui/` | Compose 编辑器、ExoPlayer 预览、字幕叠层 | 正式代码存在，设备集成未验收 |
| `captions/` | SRT、时间轴、歌词匹配、时间边界 | JVM 组件测试通过 |
| `audio/` | PCM 混音、重采样、WAV 写入 | JVM 组件测试通过 |
| `processing/` | Demo/Local 管线、MediaCodec、Whisper、ML Kit、Media3 导出 | 构建通过，关键设备集成未验收 |
| `project/` | 文本项目归档读写 | JVM 组件测试通过 |
| `model/` | cue、编辑器状态、导出配置 | 编译和组件间使用已验证 |
| `cpp/` | whisper.cpp JNI 桥 | 双 ABI 构建/打包验证，运行未验证 |

## 输入到输出的真实链路

### 手工可用链路

`OpenDocument(video)` → 读取时长并限制 5 分钟 → ExoPlayer 预览 → 导入 SRT / 粘贴歌词 / 手工 cue → 编辑文字和时间 → Compose 预览 → SRT、项目归档或 Media3 MP4 导出。

该链路的代码和构建成立；SRT/时间/归档核心逻辑有组件测试。整条链路尚无本轮目标设备证据，且末端 Media3 MP4 导出在历史指定模拟器上重复失败。

### Local 自动生成链路

视频 `content://` URI → `AndroidAudioExtractor` 使用 `MediaExtractor/MediaCodec` 解码 → PCM 混音、线性重采样 → 缓存目录 16 kHz mono PCM16 WAV → `WhisperLocalSpeechRecognizer` → JNI → whisper.cpp → cue → `DemoCaptionCorrector` 原样返回 → `MlKitLocalTranslator` → 双语 cue → 编辑/预览 → Media3 Transformer/OverlayEffect → 临时 MP4 → 检查文件大小、音视频轨和时长 → 复制到用户选择的 URI。

这条链路只有代码、组件和构建证据，没有真实 ASR 或 ARM64 真机端到端验证。历史模拟器导出失败，仓库 MP4 样例没有 App 内生成溯源。

## 运行时分支与演示路径

- `AppPipelineFactory.createDefault()` 只有在模型文件存在且 Whisper JNI 加载成功时选择 Local；否则整个生成管线选择 Demo。
- Demo 音频抽取只延迟 450 ms，不读取媒体。
- Demo ASR 固定返回三条英文字幕和固定时间轴。
- Demo 翻译只对三个固定英文句子返回固定中文，其他内容返回“待翻译”。
- `DemoCaptionCorrector` 仅延迟后原样返回；它在 Demo 和 Local 管线中都被使用。因此“上下文自动纠错”目前不是正式实现，只有歌词候选匹配和人工选择是真实代码。
- “Paste Lyrics” 按视频总时长平均分配 cue，不是语音对齐结果。
- 独立 `Translate` 操作直接使用真实 `MlKitLocalTranslator`，不依赖当前生成管线是否为 Demo。

## 外部依赖和数据边界

- Android SDK/MediaCodec/SAF；Media3 ExoPlayer、Transformer、Effect；ML Kit Translate；whisper.cpp 本地原生库和用户导入模型。
- 核心媒体和 Whisper 推理设计为本地处理；ML Kit 首次模型准备可能需要网络，之后离线能力尚待真机验证。
- 项目归档保存视频 URI，而不是复制媒体；跨重启 URI 权限有效性尚未验证。

## 状态不明边界

- `AudioChunker` 有组件测试，但当前 Whisper 调用路径未使用它拆分完整 WAV；5 分钟输入的内存/时延风险未知。
- 仓库保留 `third_party/ffmpeg-kit` gitlink，但 app 构建依赖和当前代码链路没有使用 FFmpegKit。
- 历史模拟器截图支持导入和预览的部分证据；历史运行记录同时明确记载导出失败。仓库 MP4 样例缺少与 App 导出绑定的命令、日志或哈希链，不能算集成验收。
