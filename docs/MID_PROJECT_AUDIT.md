# LyricCaptioner 独立中途审计报告

## 结论

架构分类为 **C：存在明确实现故障，但架构尚未被否定**。

项目不是空壳：SRT、字幕时间、歌词匹配、PCM/WAV、项目归档具有组件测试，普通与 native APK 可以构建，双 ABI Whisper JNI 已打包。但产品硬边界之一——由 App 烧录双语字幕并导出真实 MP4——在历史指定 Pixel_8 API 36.1 x86_64 模拟器上重复失败，错误为 `Video frame processing error`，目标文件为 0 bytes。

本轮 ADB 无设备，因此没有把历史交接直接升级为“本轮复现事实”。独立复核确认了 exporter、依赖版本、历史失败状态文件、未集成的 FFmpegKit 残留和仓库产物缺少 App 生成溯源。现有证据足以确认存在实现/集成故障，但不足以证明当前架构违反硬边界或必须替换。

## 审计基线与范围

- Git root：`D:\DevEnv\Work`
- 项目：`lyric-captioner-android/`
- Branch：`chore/adopt-codex-workflow`
- HEAD：`618f36ee50ecfef7302faa974d6b0e9e494614b9`
- 工作树：审计开始前已 dirty；未清理或覆盖非审计代码。
- 只运行只读检查、现有单测和 Debug/native 构建；只修改审计/状态文档。

## 产品目标与硬边界

Android 本地优先的 <=5 分钟短视频双语字幕生成、人工校对、预览和烧录导出工具。第一版不保证 100% 歌词还原；Demo、固定返回、桌面生成产物、代码存在和编译通过不能算验收；真实 MP4 必须由 App 在约定设备/模拟器产生并可播放。

## 当前真正生效的系统

Compose `EditorScreen` → `MainViewModel`/`EditorState` → `CaptionPipeline` → Android MediaCodec 音频抽取 → 可选 whisper.cpp JNI → ML Kit → Media3 ExoPlayer/Transformer/OverlayEffect → SAF 输出。

模型或 JNI 未就绪时，`AppPipelineFactory` 选择 Demo：不读取音频，固定返回三条英文 cue 和固定翻译。Local 管线也使用原样返回的 `DemoCaptionCorrector`。FFmpegKit 源码实验未形成 AAR，未被 Gradle 或 app 代码引用。

## 证据等级

### 已验证事实

- Debug/Release 单测本轮各 24/24 强制重跑通过。
- 普通和 native Debug APK 构建成功；native APK 含 ARM64/x86_64 Whisper JNI。
- 当前生效依赖包括 Media3 1.10.1、ML Kit Translate 17.0.3；compileSdk 36/targetSdk 35/minSdk 26。
- AGP 8.7.3 对 compileSdk 36 发出兼容警告，但未导致本轮构建失败。
- ADB 37.0.0 可执行，设备列表为空。

### 有证据支持但不完整

- 项目内截图和交接记录支持指定模拟器曾完成视频/SRT 导入与双语预览。
- 项目内当前状态/交接一致记录 Media3 导出重复失败；本轮没有原始 logcat 和设备，具体根因未知。
- 输出检查代码会验证大小、音视频轨和时长，但没有成功走到验收证据。

### 模拟、固定或占位

- Demo ASR 固定三条 cue；Demo 翻译固定映射；Demo audio extractor 只延迟。
- `DemoCaptionCorrector` 原样返回，并在 Local 管线使用。
- 粘贴歌词按总时长均分 cue，不是语音对齐。

### 已失败

- 当前集成 Media3 烧录路径在指定 x86_64 模拟器重复 `Video frame processing error`。
- ML Kit 历史模拟器下载/翻译未完成；证据仅来自交接，根因未在本轮复核。
- FFmpegKit 实验构建未完成、未集成，不能作为 fallback。

### 未知

- Media3 exporter 是否能在 ARM64 真机成功。
- 真实 Whisper 的正确性、性能、内存和 5 分钟稳定性。
- ML Kit 离线复用、SAF 持久 URI、失败恢复、API 26 兼容和发布许可。
- `final-bilingual-subtitle.mp4` 是否、以及如何由 App 生成；目前无可追溯证据。

## 当前首要阻断

已知 Media3 导出实现故障尚未定位，而本轮没有可连接的原模拟器/设备以采集完整 Transformer、codec、Surface 和输入元数据证据。

## 分类论证

- A 不成立：核心闭环已知失败。
- B 不成立：这不是单纯缺少关键证据，已有明确实现故障。
- C 成立：故障存在，但组件/构建/部分运行证据表明架构仍可能通过实现级修正满足边界。
- D 不成立：没有证明平台能力缺失、硬约束不可满足，或合理实现级修正后仍失败。

## 推荐下一步

推荐 Skill：`evidence-first-debugging`。

推荐最小任务：经批准后，在同一 Pixel_8 API 36.1 x86_64 模拟器上，用当前 native Debug APK、固定 `emulator-h264-test.mp4` 和 `emulator-bilingual-test.srt` 只复现一次导出，保存完整 logcat、Transformer exception chain、codec/Surface 信息、输入哈希和输出大小；停止在证据冻结，不修改代码。

完成本报告后停止，不进入实现。
