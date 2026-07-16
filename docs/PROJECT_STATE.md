# 项目当前状态

## 一、当前门禁

`ASR-001 / COMPONENT_VERIFIED / HUMAN_DECISION`

- 项目根目录：`D:\DevEnv\Projects\lyric-captioner-android`
- 当前阶段：真实本地 Whisper ASR
- 当前任务：`ASR-001`，修订号 `1`
- 当前状态：`COMPONENT_VERIFIED / HUMAN_DECISION`
- 验收设备：ARM64 `25098PN5AC`（ADB `fcf4b0cb`）；既有 x86_64 模拟器 `emulator-5554` 仅保留为历史验证设备

## 二、已确认事实

- 归档边界统一为 `ProjectRepository`；Android 实现集中处理 SAF 读写和媒体访问。
- `ProjectArchive` 写入 `# LyricCaptionerProject v2`，字段使用安全编码；读取兼容 v1。
- 归档保存视频 URI、视频时长、字幕 cue（含候选和确认状态）、样式及导出配置；不复制媒体字节。
- 视频选择使用 `OpenDocument`，并尝试 `takePersistableUriPermission`。
- 媒体访问分类：`Persisted`、`SessionOnly`、`ProviderUnsupported`、`Unavailable`。
- 失效视频 URI 保留在已恢复项目中，但预览不会把失效 URI 交给 Media3；界面提供 `Relink Video`。
- 归档读写错误按输入、读取、写入、格式、权限、媒体和未知错误分类，并转换为用户可见状态。
- JVM 回归测试共 44 项通过；`assembleDebug` 通过；最终 Debug APK 已安装到既有模拟器。

## 三、模拟器验收记录

| 场景 | 结果 | 证据 |
|---|---|---|
| SAF 视频导入 | 通过 | `video_import_completed mediaState=Persisted durationMs=4000` |
| 双语 SRT 导入并保存 | 通过 | `project_save_completed captionCount=2` |
| 强制停止/重启后打开归档 | 通过 | `project_load_completed mediaState=PERSISTED captionCount=2` |
| 失效媒体恢复 | 通过 | `project_load_completed mediaState=UNAVAILABLE captionCount=1`，UI 显示 `Relink Video` |
| 腐坏归档 | 通过 | `project_load_failed kind=FORMAT`，原项目状态仍保留 |
| 失效媒体重绑 | 通过 | `video_import_completed mediaState=Persisted durationMs=4000 captionCount=1` |

## 四、边界与风险

- ARC-001 的既有结果是模拟器验证，不代表产品发布接受；ASR-001 已连接 ARM64 设备但尚未完成真实模型推理验收。
- `SessionOnly` 和 `ProviderUnsupported` 的出现取决于具体文档提供器；分类和提示路径已实现，但本轮固定输入命中了 `Persisted`。
- 归档只保存 URI 引用，不保存媒体内容；媒体提供器撤销授权或删除源文件后必须走重绑。
- EXP-001 的导出/AAR 结果属于已完成历史事实；本轮未修改该路线。

## 五、ASR-001 初始盘点

- 已存在 `AndroidAudioExtractor`、`LocalSpeechRecognizer`、`WhisperLocalSpeechRecognizer`、`WhisperModelStore` 和 `whisper_jni.cpp`，本任务应在其上收敛稳定 ASR 边界。
- 当前 `AppPipelineFactory.createDefault()` 在 Local 不可用时直接选择 Demo；`EditorScreen` 因此允许按钮显示 `Generate Demo`，但生成状态没有完整记录路由。
- 当前 `CaptionPipeline.generateDraft()` 还串联 Demo 校正器和 ML Kit 翻译，不符合 ASR 只生成英文字幕的边界。
- 当前 `WhisperModelStore` 的不可用提示使用“Demo recognition active”，但没有独立的 `Local/Demo/Unavailable` 模式模型。
- 当前 Native JNI 已有 `nativeTranscribe`，但需要补充可审计的 JNI 路由/完成日志、单元测试和 Native APK ABI 检查。

## 六、ASR-001 验证事实

- ASR 组件已收敛为稳定接口，复用现有音频提取、LocalSpeechRecognizer 和 Whisper JNI；音频临时文件在成功、失败和取消路径执行清理。
- ASR 只输出英文字幕 cue，转换层校验非空文本、非负且有序时间戳、置信度范围，并拒绝无效结果。
- 模型状态与路由显式区分 `LOCAL`、`DEMO`、`UNAVAILABLE`；Local 不可用时显示具体原因，Demo 结果不会标记为 Local。
- 代码与全量 JVM 单测通过：51 项通过，0 失败、0 错误、0 跳过；普通 Debug 与 Native Debug 均组装通过。
- Native APK 已确认包含 arm64-v8a 的 `liblyriccaptioner_whisper.so`；JNI 源码记录 transcribe 开始、完成和失败事件。

## 七、当前阻断与下一步

- ARM64 设备 `fcf4b0cb` 已连接，Native APK 已安装，UI 显示 `JNI: ready`；固定媒体 `/sdcard/Download/source-test-video.mp4` 已存在。
- 设备共享存储、应用目录和当前项目均没有兼容 Whisper 模型；需要提供/授权模型后，继续验收真实 JNI transcribe、Local 路由、非空英文字幕、时间戳顺序及成功/失败/取消清理。
- 当前最终状态：`COMPONENT_VERIFIED / HUMAN_DECISION`。
