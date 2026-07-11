# LyricCaptioner Project State / 迁移后基线状态

## Current gate / 当前门禁

`POST_MIGRATION_CLEANUP`

- 当前阶段：`POST_MIGRATION_CLEANUP`（baseline checkpoint 已完成）
- 当前 Gate：`SPIKE_READY`
- 下一阶段：`SPIKE_READY` 下的已批准技术探针；尚未批准执行
- 架构分类保持：**C — 存在明确实现故障，但架构尚未被否定**。本轮不重新选型。

## Audit baseline / 审计基准

- Git root：`D:\DevEnv\Projects\lyric-captioner-android`
- Branch：`migration/lyric-captioner-history`
- 当前 HEAD（baseline commit 前）：`05dbae7a2134de5e8af4765654586921a9ee9779`（`chore: capture current project state`）
- 独立仓库边界已完成；`health-assistant` 和中央 debug log 不在本仓库范围。
- 主应用源码相对迁移快照 HEAD 无文本修改。本次 baseline commit 收录项目治理、权威文档与迁移后状态。
- `third_party/ffmpeg-kit` 保持 dirty detached 隔离状态；完整分析与恢复证据见 `docs/FFMPEG_KIT_CHANGE_ANALYSIS.md`。

## Canonical documents / 权威文档

- `docs/PROJECT_BRIEF.md`
- `docs/REQUIREMENTS.md`
- `docs/ENVIRONMENT_REPORT.md`
- `docs/CURRENT_SYSTEM_MAP.md`
- `docs/CURRENT_TECH_STACK.md`
- `docs/FEATURE_STATUS.md`
- `docs/MID_PROJECT_AUDIT.md`
- `docs/NEXT_TASK.md`
- `docs/handoffs/AUDIT_HANDOFF.md`
- `docs/POST_MIGRATION_AUDIT.md`
- `docs/BASELINE_CHECK.md`
- `docs/FFMPEG_KIT_CHANGE_ANALYSIS.md`

`lyric-captioner-android/docs/handoffs/LEGACY_HANDOFF.md` 仅为调查线索；项目内旧中文说明中的“已完成”声明不覆盖本审计。

## Verified facts / 已验证事实

- 生效栈为 Kotlin/Compose/Media3/Android MediaCodec/ML Kit/可选 whisper.cpp JNI；FFmpegKit 未接入 app。
- JDK 17.0.19、Gradle 8.9、Android SDK 可用。
- 2026-07-11 迁移前审计记录 Debug 24/24、Release 24/24 单测强制重跑通过，普通与 native Debug 构建成功；本轮未重跑，不将其升级为当前 HEAD 验证事实。
- native APK 含 ARM64/x86_64 的 `liblyriccaptioner_whisper.so`。
- 模型或 JNI 未同时就绪时，默认生成路径使用固定 Demo ASR；Local 管线的 corrector 仍为原样返回的 Demo 实现。
- 当前 ADB 设备列表为空。

## Confirmed failure and evidence boundary / 已确认故障与证据边界

- 历史指定 Pixel_8 API 36.1 x86_64 模拟器上，当前集成的 `Media3SubtitleExporter` 在真实保存流程中重复报 `Video frame processing error`，输出保持 0 bytes。该事实由项目内当前交接和项目状态文件记录，但本轮因无设备未重新采集 logcat。
- 仓库的 `final-bilingual-subtitle.mp4` 没有证据链证明由 Android App/当前 exporter 生成，不能抵消上述失败。
- 最早已定位边界为视频 frame-processing/设备兼容集成层；具体 codec、Surface、OpenGL 或输入因素仍未知。

## Why C / 分类依据

- 不选 A/B：核心硬边界 MP4 烧录在约定运行环境已有明确实现失败，不只是证据缺失。
- 选择 C：构建、组件测试、导入/预览部分证据和正式 exporter 代码均存在；失败尚未被定位到不可修复的架构能力缺失。
- 不选 D：尚无证据证明 Media3 或当前整体架构在所有约定 Android 目标上无法满足硬边界。

## Primary blocker / 当前首要阻断

当前集成的 Media3 字幕烧录无法在历史指定模拟器完成；同时本轮没有连接该模拟器或 ARM64 真机，无法以新 logcat 定位最早失败原因或验证设备差异。

## Major evidence gaps / 主要证据缺口

- 当前 APK 在同一模拟器上的完整 Transformer/codec/Surface logcat。
- ARM64 真机 Media3 导出和真实 Whisper 推理。
- ML Kit 首次下载/离线复用、SAF 跨重启、5 分钟资源基线、许可审计。
- 仓库样例 MP4 的生成命令、来源和与 App 版本绑定关系。

## Current risks / 当前风险

- `third_party/ffmpeg-kit` 的 5 个修改尚未形成嵌套仓库提交，其中 versionCode 修改意图未知；当前仅通过主仓库文档冻结完整 diff。
- 主仓库无 remote，异机恢复和远端审查能力缺失。
- Media3 指定模拟器导出存在历史已知失败；当前没有新设备证据。
- 真机 Whisper、ML Kit 离线、SAF、5 分钟资源和许可仍未验证。
- 已跟踪的 `.kotlin/errors` 和历史媒体/截图属于迁移快照遗留，本轮不删除。

## Next phase / 下一阶段

baseline commit 已完成，主仓库仅剩已记录、已隔离的 `ffmpeg-kit` dirty 状态，工程 Gate 标记为 `SPIKE_READY`。这只表示具备探针准备条件，不等于已批准或已执行 Spike。

实际执行前仍需用户批准 `docs/NEXT_TASK.md`。获批后使用 `evidence-first-debugging`，只复现一次指定模拟器 Media3 导出并冻结完整证据；不修复、不换栈、不继续 FFmpegKit、不继续功能开发。

## Last updated

- Date: 2026-07-12
- Pre-baseline HEAD: `05dbae7a2134de5e8af4765654586921a9ee9779`
