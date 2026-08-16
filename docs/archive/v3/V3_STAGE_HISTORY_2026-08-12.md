# LyricCaptioner V3 阶段历史摘要

本文件由 2026-08-12 活动文档压缩产生，只保存已关闭或已被当前任务取代的产品开发事实。废弃角色和治理规则未归档。

- Snapshot HEAD: `421dc9cd3a158c0c9894e398df070c96a691dd12`
- Source documents: `DEVELOPMENT_ROADMAP.md`, `CURRENT_TASK.md`, `PROJECT_STATE.md`
- Current task excluded from archive: `V3-CLEAN-001`

## V3-DEC-001

状态：`PASS`。交互、样式、媒体入口、Whisper 缓存生命周期、云端增强、本地回退、密钥边界和清理范围已确认。

## V3-AI-CONTRACT-001

状态：`PARTIAL_PASS / SECURE_BYOK_VERIFIED / DEEPSEEK_AUTH_VERIFIED / LIVE_LYRICS_FLOW_DEFERRED`。真机完成 Keystore BYOK 保存、masked 恢复、连接测试、同 Key 轮换、失败替换保留旧 Key 和删除；真实在线歌词、歌曲匹配和逐 cue 产品链路当时未验证。

## V3-ASR-SESSION-001

状态：`PARTIAL_PASS / WHISPER_SESSION_COMPONENT_VERIFIED / PHYSICAL_DEVICE_RUNTIME_DEFERRED_BY_USER`。组件 focused 14/14、完整 JVM 169/169、ASR 6/6、lint 0 error/33 warning，以及普通、native 和 AndroidTest 构建通过。真实连续识别、handle 热复用和真机性能数据进入最终设备积压。

## V3-EDITOR-001 与 V3-EDITOR-002

`V3-EDITOR-001` 完成共享样式与布局模型，但产品交互需要返工。`V3-EDITOR-002` 关闭为 `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`：canonical ratio、v6 编辑恢复、Media3 FIT/PAR、Compose Stroke/Fill 与 ASS Outline 组件路径通过；物理 UI 未测量。

最终组件回归记录包括完整 JVM 241/241、ASR 6/6、lint 0 error/33 warning，以及普通、native 和 AndroidTest 构建通过。历史 R1–R4 过程已压缩为本摘要，不再参与当前调度。

## V3-MEDIA-001

状态：`PARTIAL_PASS / MEDIA_COMPONENT_VERIFIED / PHYSICAL_DEVICE_MEDIA_FLOW_WAIVED_BY_USER`。产品视频导入统一为 Photo Picker `VideoOnly`，成品视频导出统一为 MediaStore，覆盖 API 26–36 权限、pending publish/rollback、取消竞态和源文件安全。focused JVM 21/21、完整 JVM 262/262、ASR 6/6、lint 和普通/native/AndroidTest 构建通过；真实设备和相册媒体流未验证。

## V3-UI-001

状态：`PARTIAL_PASS / PRODUCT_UI_COMPONENT_VERIFIED / FINAL_PHYSICAL_UI_ACCEPTANCE_DEFERRED_BY_USER`。移除 App 顶栏、V2 标签和运行时诊断展示，保留系统 Insets、产品导航、识别成功提示、用户主动进入编辑和隐私安全状态反馈。focused UI 11/11、完整 JVM 273/273、ASR 6/6、lint 和普通/native/AndroidTest 构建通过；真机视觉和完整产品链路延期。

## V3-AI-001

状态：`COMMITTED / ACCEPTED / LYRICS_ACCURACY_SRT_DEVICE_VERIFIED`，阶段提交 `9a798ccb3890128565a12c924c11e6468908a2b9`。实现 AI 候选歌曲识别、LRCLIB 完整英文歌词检索、多 cue span 对齐验证和基于整首歌词上下文的中英对照生成。正常与错位两份 8-cue SRT 在 ARM64/API36 真机通过生产增强入口，cue ID、顺序和时间轴保持，输出可回读。专项 66/66、全量 JVM 291/291、ASR 6/6、lint、普通/native Debug 和 AndroidTest 构建通过。证据上限为单歌曲 SRT 专项，不代表视频、Whisper 或完整端到端流程通过。

## 持续有效的历史边界

- 模拟器、组件和构建证据不得写成真机或正式产品 PASS。
- 真机积压只表示证据未测量，不表示失败或通过。
- `third_party/ffmpeg-kit`、模拟器证据、模型工具和其他进入任务前的工作树状态必须保留。
