# Current Task: V3-ASR-DIAG-001

- `STATE_REV: 2026-08-22.001`
- `TASK_REV: V3-ASR-DIAG-001.001`
- Stage state: `MATRIX_DEFINED / IN_PROGRESS`
- Product status: `DIAGNOSIS_IN_PROGRESS`
- Evidence ceiling: `DEVICE_DIAGNOSTIC_ONLY`
- Baseline HEAD: `9a798ccb3890128565a12c924c11e6468908a2b9`
- Device gate: `ARM64 device fcf4b0cb authorized for this diagnostic only`

## 1. 阶段目标

使用同一份只提取一次的 16 kHz 单声道 PCM16 WAV，在同一 ARM64 设备上分别以 small 和 base 模型各执行一次：每次新建 Whisper Context、`no_context=true`、调用 `whisper_full()`、采集 segment 与 token 原始观测、随后立即释放 Context。把异常压缩到 Context 复用、Native/编译环境、Whisper 参数三类之一；本阶段不修复产品行为。

## 2. 冻结验收矩阵

| 类别 | 冻结内容 |
|---|---|
| 主链路 | 一个既有设备视频只执行一次音频提取并冻结 WAV；small 与 base 串行读取完全相同的 WAV，各自 fresh context -> `no_context=true` -> `whisper_full()` -> 原始诊断输出 -> free context。 |
| 必须证据 | App commit；实际编译的 whisper.cpp version/commit；两个 model SHA256；WAV SHA256/时长；fresh/reuse/no_context/language/threads；`whisper_full` rc/耗时/segment count；每个 segment 的 start/end/text/no_speech_prob/平均 token probability 和 token id/text/probability。 |
| 禁止事项 | 不怀疑或重新验证视频是否有声音；不重复走视频流程；不改 UI、字幕后处理、DeepSeek、翻译、导出或业务识别策略；不回滚提交；不调 threshold；不并发运行 small/base；不清除 App/用户数据；不 push。 |
| 退出状态 | 只有 small/base 都在设备 `fcf4b0cb` 对同一 WAV 完成一次诊断，且所需字段可得或明确标为 `Unavailable`，才可标记 `PASS / DEVICE_DIAGNOSTIC_VERIFIED`。 |
| 未完成状态 | 缺少任一模型或固定 WAV为 `BLOCKED_INPUT`；Native 诊断入口不能构建为 `BLOCKED_BUILD`；任一设备推理未完成为 `PARTIAL_PASS`；三类原因仍不能区分为 `D / DATA_INSUFFICIENT`。 |

## 3. 允许范围

- 允许修改 Debug/instrumentation 入口、JNI 诊断日志、Native 构建 provenance 常量和必要测试。
- 允许构建并安装 `-PenableWhisperNative=true` 的 Debug 与 AndroidTest APK，向设备增加实验模型/WAV，并运行本任务 instrumentation。
- 允许更新三份活动文档与 `docs/debug/DEBUG_REPORT.md`。
- 所有既有脏内容必须保留；尤其不得暂存或清理与本任务无关的 UI、编辑、导出、AI、测试资产和 `third_party/ffmpeg-kit` 状态。

## 4. 当前已验证快照

- Git 根目录：`D:\DevEnv\Projects\lyric-captioner-android`
- Branch：`migration/lyric-captioner-history`
- HEAD：`9a798ccb3890128565a12c924c11e6468908a2b9`
- Device：`fcf4b0cb / ARM64`，本阶段开始取证时 App 进程未运行。
- 设备 small：`ggml-small.en-q5_1.bin`，SHA256 `bfdff4894dcb76bbf647d56263ea2a96645423f1669176f4844a1bf8e478ad30`。
- 源码 whisper.cpp：`v1.9.1 / f049fff95a089aa9969deb009cdd4892b3e74916`；实际编译产物 provenance 尚待本阶段输出确认。
- 设备没有可复用 WAV；必须从既有输入只提取一次后冻结。

## 5. 下一动作

建立阶段 checkpoint；实现最小 Debug-only 诊断入口；固定 WAV；small/base 串行 A/B；仅汇总用户指定字段。
