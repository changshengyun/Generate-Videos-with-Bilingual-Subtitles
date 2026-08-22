# Current Task: V3-ASR-DIAG-001

- `STATE_REV: 2026-08-23.003`
- `TASK_REV: V3-ASR-DIAG-001.003`
- Stage state: `PASS / BASE_DEVICE_DIAGNOSTIC_VERIFIED`
- Product status: `D / DATA_INSUFFICIENT_FOR_CONTEXT_VS_PARAMETER`
- Evidence ceiling: `DEVICE_DIAGNOSTIC_ONLY`
- Baseline HEAD: `9a798ccb3890128565a12c924c11e6468908a2b9`
- Diagnostic implementation: `50fd1407ad9e63c34b27292bf36adc81db7b062e`
- Device gate: `ARM64 device fcf4b0cb authorized for this diagnostic only`

## 1. 阶段目标

用户取消 small 验证。本任务只使用已经冻结的 16 kHz 单声道 PCM16 WAV，以 base 模型执行一次：新建 Whisper Context、`no_context=true`、调用 `whisper_full()`、采集 segment 与 token 原始观测、随后立即释放 Context。本阶段不修复产品行为。

## 2. 冻结验收矩阵

| 类别 | 冻结内容 |
|---|---|
| 主链路 | 直接读取已经冻结的同一 WAV；base fresh context -> `no_context=true` -> `whisper_full()` -> 原始诊断输出 -> free context。不得重新提取视频。 |
| 必须证据 | App commit；实际编译的 whisper.cpp version/commit；base model SHA256；WAV SHA256/时长；fresh/reuse/no_context/language/threads；`whisper_full` rc/耗时/segment count；每个 segment 的 start/end/text/no_speech_prob/平均 token probability 和 token id/text/probability。 |
| 禁止事项 | 不运行 small；不怀疑或重新验证视频是否有声音；不重复走视频流程；不改 UI、字幕后处理、DeepSeek、翻译、导出或业务识别策略；不回滚提交；不调 threshold；不清除 App/用户数据；不 push。 |
| 退出状态 | base 在设备 `fcf4b0cb` 对冻结 WAV 完成一次诊断，且所需字段可得或明确标为 `Unavailable`，才可标记 `PASS / BASE_DEVICE_DIAGNOSTIC_VERIFIED`。 |
| 未完成状态 | 缺少 base 或固定 WAV 为 `BLOCKED_INPUT`；Native 诊断入口不能构建为 `BLOCKED_BUILD`；base 推理未完成为 `PARTIAL_PASS`；原因仍不能区分为 `D / DATA_INSUFFICIENT`。 |

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
- 固定 WAV：SHA256 `cd76904fc36ac08de32da432a4a6c14c48bf34f267c082cb74d6a1ec5c692d1d`，`16 kHz / mono / PCM16`，不得重新提取。
- base：`ggml-base.en.bin`，SHA256 `a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002`。
- Native Debug APK 已构建并安装；运行时 provenance 待 base 诊断输出确认。

## 5. 下一动作

base 单次诊断已完成，small 未运行。若需要区分 Context 复用与参数因素，唯一最小对照是同一 WAV/base/fresh context 下仅改 `no_context=false`；本任务未授权也未运行该对照。

## 6. 验证结果

- Device：`fcf4b0cb`；Native：`whisper.cpp 1.9.1 / f049fff`；GGML：`0.15.1`。
- base SHA256：`a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002`。
- WAV SHA256：`cd76904fc36ac08de32da432a4a6c14c48bf34f267c082cb74d6a1ec5c692d1d`；`31,602 ms / 505,638 samples`。
- 配置：fresh context、无复用、`no_context=true`、`language=auto`、4 threads。
- `whisper_full=0`；context init `97 ms`；inference `8,580 ms`；9 segments；结束后 Context 已释放。
- Segment 0–7 为歌词，`no_speech_prob=0.475166`；Segment 8 为 `(upbeat music)`，`no_speech_prob=0.881964`，由 `(`、`up`、`beat`、` music`、`)` token 直接解码产生。
- 报告：设备 `files/asr-diagnostics/base.json`，SHA256 `69e44af46a24299921e9ad420dd13f4041419870226ffe33993aa0e1646762fc`。
- 结论：Native/编译环境并非“只能产生音乐标记”的全局故障；本次恢复不能在 Context 复用与 `no_context` 参数之间唯一归因，因此选择 `D`。
