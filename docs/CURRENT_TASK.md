# Current Task: V3-ASR-DIAG-001

- `STATE_REV: 2026-08-24.007`
- `TASK_REV: V3-ASR-DIAG-001.007`
- Stage state: `PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_PENDING`
- Product status: `A / NO_CONTEXT_FALSE_CAUSES_NATIVE_STALL_FIXED_PROD_PENDING`
- Evidence ceiling: `DEVICE_DIAGNOSTIC_VERIFIED_PRODUCTION_PENDING`
- Baseline HEAD: `9a798ccb3890128565a12c924c11e6468908a2b9`
- Diagnostic implementation: `50fd1407ad9e63c34b27292bf36adc81db7b062e`
- Device gate: `ARM64 device fcf4b0cb authorized for this diagnostic only`

## 1. 阶段目标

用户授权唯一单变量对照：保持既有 App HEAD、Native、base、固定 WAV、fresh context、`language=auto` 和 4 threads 不变，只把 Debug 参数由 `no_context=true` 改为 `no_context=false` 并运行一次。本阶段不修复产品行为。

## 2. 冻结验收矩阵

| 类别 | 冻结内容 |
|---|---|
| 主链路 | 直接读取同一固定 WAV；base fresh context -> `no_context=false` -> `whisper_full()` -> segment text/no-speech 摘要 -> free context。 |
| 必须证据 | Git HEAD、Native version/commit、base SHA256、WAV SHA256、fresh/reuse/no_context/language/threads、`whisper_full` rc/耗时/segment count、各 segment text/no_speech_prob，以及与上一轮 true 结果的 segment count/首段/末段/music marker 对比。 |
| 禁止事项 | 不运行 small；不复用 Context；不重新提取视频；不改除 Debug `params.no_context` 外任何代码或参数；不输出完整 token；不改业务调用链；不清数据；不 push。 |
| 退出状态 | `fcf4b0cb` 上完成唯一一次 `no_context=false` 对照并取得全部摘要字段，标记 `PASS / NO_CONTEXT_CONTROL_DEVICE_VERIFIED`。 |
| 未完成状态 | 构建失败为 `BLOCKED_BUILD`；推理未完成为 `PARTIAL_PASS`；结果不能按用户规则判断为 `D / NEEDS_FURTHER_VALIDATION`。 |

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

唯一一次 `no_context=false` 对照已完成；不得执行其他实验。

## 6. 验证结果

- Device：`fcf4b0cb`；Native：`whisper.cpp 1.9.1 / f049fff`；GGML：`0.15.1`。
- base SHA256：`a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002`。
- WAV SHA256：`cd76904fc36ac08de32da432a4a6c14c48bf34f267c082cb74d6a1ec5c692d1d`；`31,602 ms / 505,638 samples`。
- 配置：fresh context、无复用、`no_context=true`、`language=auto`、4 threads。
- `whisper_full=0`；context init `97 ms`；inference `8,580 ms`；9 segments；结束后 Context 已释放。
- Segment 0–7 为歌词，`no_speech_prob=0.475166`；Segment 8 为 `(upbeat music)`，`no_speech_prob=0.881964`，由 `(`、`up`、`beat`、` music`、`)` token 直接解码产生。
- 报告：设备 `files/asr-diagnostics/base.json`，SHA256 `69e44af46a24299921e9ad420dd13f4041419870226ffe33993aa0e1646762fc`。
- 结论：Native/编译环境并非“只能产生音乐标记”的全局故障；本次恢复不能在 Context 复用与 `no_context` 参数之间唯一归因，因此选择 `D`。

## 7. `no_context=false` 单变量对照

- Git HEAD 保持 `fe05a6dc4d76e705e98461c68a957417a01d78c3`；实验 APK SHA256 `6045b74f91bdf4a6b7ad0f8a9b4ab60452bb19077b9d491ec46f70db74018d52`。
- 运行时：`whisper.cpp 1.9.1 / f049fff`；fresh context；无复用；`no_context=false`；`language=auto`；4 threads；base/WAV SHA256 与上一轮一致。
- `whisper_full` 在 10 分钟观察窗内未返回；return code、segment count、segment text/no-speech 均为 `Unavailable`。
- 四个 Whisper 工作线程最初各累计约 2 秒 CPU，随后连续数分钟不增长；无 `whisper_full_exited` 或 `context_freed`。
- 为结束残留实验进程执行 App force-stop；未清数据，base 与固定 WAV hash 未变化；未重跑。
- 对比：`no_context=true` 在 `8,580 ms` 返回 9 segments；`false` 在同一其他条件下 stalled。结论选 `A. no_context 导致`，表现为 Native non-return，而不是本轮重新观察到 `[MUSIC]`。

## 8. 修复后验证

- 仅修改 `app/src/main/cpp/whisper_jni.cpp`：业务 `make_full_params()` 现在设置 `params.no_context = true`，并记录 ARM64 stall 证据。
- Debug APK：`assembleDebug -PenableWhisperNative=true` 成功；AndroidTest APK 同样成功构建。
- 设备：`fcf4b0cb` ARM64；同一 base/WAV；fresh context；无 context reuse；`language=auto`；4 threads。
- `whisper_full` return：`0`；inference：`10,319 ms`；segment count：`9`。
- Segment 0：` I have to live without you`；LastSegment：` (upbeat music)`。
- 结论：`FIXED / NO_CONTEXT_FIX_DEVICE_VERIFIED`。

## 9. 真实 App 流程收尾验证

- 代码确认：`app/src/main/cpp/whisper_jni.cpp` 的业务 `params.no_context = true` 保持不变。
- 已删除 `WhisperModelStore.ensureBundledModel()` 及其 App/AndroidTest 调用；App 不再启动时强制复制并选择 `ggml-small.en-q5_1.bin`。
- 新 APK 尚未重新安装到设备，真实 App base 流程需在安装本次构建产物后重新验证；当前不宣称生产 PASS。
- 状态：诊断入口 `DEVICE_VERIFIED`；真实 App base 流程 `PENDING_REINSTALL`。

## 10. V3 阶段总结

- 阶段报告：[`archive/v3/V3_DEVELOPMENT_PHASE_SUMMARY_2026-08-24.md`](archive/v3/V3_DEVELOPMENT_PHASE_SUMMARY_2026-08-24.md)。
- 本阶段可归档为开发阶段候选版本，但不得宣称完整 V3 生产 PASS。
- 已验证：Native ASR 修复、固定 base/WAV 真机诊断、Debug 与 AndroidTest 构建、335 条 JVM 单元测试。
- 未完成：新 APK 安装后的真实 App base 入口，以及导入→ASR→AI→编辑→导出→回放完整真机链路。
