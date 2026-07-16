# 当前任务

- 任务编号：`ASR-001`
- 修订号：`1`
- 任务名称：真实本地 Whisper ASR
- 工作状态：`COMPONENT_VERIFIED / HUMAN_DECISION`
- 产品门禁：`ASR-001 / COMPONENT_VERIFIED / HUMAN_DECISION`
- 负责范围：音频提取、模型检查、Whisper JNI、英文字幕转换、取消与清理

## 一、交付目标

在不改变 FFmpeg/AAR、字幕烧录、Media3 或 ARC-001 逻辑的前提下，完成真实本地 Whisper ASR 闭环：

1. 复用现有 `AndroidAudioExtractor`、`LocalSpeechRecognizer` 和 Whisper JNI，不创建平行音频或推理架构。
2. 对外提供稳定 ASR 接口，封装音频提取、模型检查、JNI 调用、英文 cue 转换、取消和临时音频清理。
3. 显式区分 `Local`、`Demo`、`Unavailable`；Demo 结果不得伪装为 Local 成功。
4. 支持用户导入兼容模型；不自动下载、不提交模型或构建产物。
5. 普通 Debug 可构建；`enableWhisperNative` Native Debug 必须打入 Whisper JNI。
6. ASR 只生成英文带时间戳字幕，不承担 TRN-001 翻译。

## 二、允许修改范围

- `app/src/main/java/com/example/lyriccaptioner/processing/`
- `app/src/main/java/com/example/lyriccaptioner/MainViewModel.kt`
- `app/src/main/java/com/example/lyriccaptioner/model/EditorState.kt`
- `app/src/main/java/com/example/lyriccaptioner/ui/EditorScreen.kt`
- 对应 `app/src/test/` 测试及必要的固定测试输入
- 本任务活动文档：`CURRENT_TASK.md`、`DEVELOPMENT_ROADMAP.md`、`PROJECT_STATE.md`

## 三、明确不包含

- 不修改 FFmpegKit/AAR、字幕烧录路线、Media3 或 ARC-001 归档逻辑。
- 不自动下载或提交 Whisper 模型；不提交构建产物。
- 不卸载应用，不清除数据，不重置或删除 AVD。
- 不修改 `docs-BK`，不修改全局环境、工具链或 Git 历史。

## 四、验收门槛

- 模型状态、模式选择、JNI 错误、字幕转换、取消和失败清理单测通过。
- 模块完成后全量 `:app:testDebugUnitTest` 通过。
- 普通 `:app:assembleDebug` 与 `:app:assembleDebug -PenableWhisperNative=true` 分别通过。
- Native APK 至少包含 `arm64-v8a/lib/arm64-v8a/liblyriccaptioner_whisper.so`。
- 若获得授权 ARM64 设备、兼容模型和固定媒体，执行一次真实 Local 识别并记录 JNI、字幕、时间戳和清理证据。

## 五、后续门禁

最终状态只能是 `COMPONENT_VERIFIED / HUMAN_DECISION`、`ARM64_DEVICE_VERIFIED` 或 `BLOCKED`。

## 六、本轮验收结果

- 单元测试：全量 `:app:testDebugUnitTest` 共 51 项，Failures=0、Errors=0、Skipped=0；ASR-001 定向测试 7 项通过。
- 普通 Debug：`:app:assembleDebug` 通过。
- Native Debug：`:app:assembleDebug -PenableWhisperNative=true` 通过；APK 包含 `lib/arm64-v8a/liblyriccaptioner_whisper.so` 和 `lib/x86_64/liblyriccaptioner_whisper.so`。构建仅有 Android SDK XML v4 与当前 CMake 版本的兼容性警告。
- 路由：`WhisperRuntimeStatusResolver`、`AsrModuleTest` 和界面状态明确区分 Local、Demo、Unavailable；Local 不可用时保留具体原因，Demo 成功提示明确写为 Demo，不能伪装为 Local。
- JNI：Native Debug 已打包真实 `whisper_jni.cpp` 产物并加入可审计的开始、完成、失败日志；尚未在 ARM64 设备上执行运行时 JNI 识别。
- 设备与模型：ADB 当前仅有 x86_64 模拟器 `emulator-5554`，没有可用 ARM64 真机；项目内没有获授权的兼容 Whisper 模型。`third_party/whisper.cpp/models` 中的测试占位文件不作为模型使用或提交。
- 未完成：需要产品方提供/授权 ARM64 设备、兼容模型和固定测试媒体，才能完成真实 Local 识别、英文字幕非空、时间戳顺序及成功/失败/取消清理的设备验收。
