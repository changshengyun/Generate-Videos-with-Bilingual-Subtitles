# LyricCaptioner V4 开发路线

- `ROADMAP_REV: 2026-08-25.008`
- 当前任务：`V4-CAPTION-QUALITY-001 / MATRIX_DEFINED / IN_PROGRESS`
- V3 历史摘要：[`archive/v3/V3_STAGE_HISTORY_2026-08-12.md`](archive/v3/V3_STAGE_HISTORY_2026-08-12.md)

## 文档职责

本文件只维护 V4 产品目标、阶段顺序、依赖和总体验收。唯一活动任务见 `CURRENT_TASK.md`，实时门禁见 `PROJECT_STATE.md`。V3 保留为历史证据，不参与 V4 当前调度。

## V4 产品目标

唯一产品主链路为：

```text
相册导入视频
→ 一次点击“开始识别”
→ 本地 Whisper 识别
→ 自动执行 AI 增强
→ 自动进入字幕编辑
→ 添加或编辑字幕
→ 预览并导出最终视频
```

V4 保持 Whisper 模型、Media3、FFmpegKit、存储架构和导出技术路线不变。用户批准的 `V4-CAPTION-QUALITY-001` 仅在 enhancement 边界内升级 Prompt、响应合同和 cue 拆分时间策略，以支持标准英文纠错、双句拆 cue 和唯一最终字幕批次。

## V3 历史边界

- `V3-ASR-DIAG-001` 固定为 `PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`。
- 诊断入口的 base 证据继续保留；未执行的新 APK 生产 base 验证不得改写为 PASS。
- 用户已明确延期该验证，因此它不阻塞 V4，但仍属于完整生产验收的已知缺口。

## V4 阶段顺序

| 阶段 | 当前状态 | 目标/依赖 |
|---|---|---|
| `V4-PLAN-001` | `PASS / CHECKPOINT_CREATED` | 初始化 S0/S1/S2 规则、V4 路线和活动状态，创建 checkpoint |
| `V4-FLOW-001` | `PASS / COMPONENT_VERIFIED` | 一次点击串联本地 ASR、AI 增强和自动进入编辑器 |
| `V4-EDITOR-001` | `PASS / COMPONENT_VERIFIED` | 按当前播放位置在空档新增双语字幕，并保持编辑/恢复/导出一致 |
| `V4-UI-001` | `PARTIAL_PASS / COMPONENT_VERIFIED / SIMULATOR_BLOCKED` | 普通与全屏独立控制行已通过组件验证；Pixel 8 因已有 snapshot operation pending 无法启动，未取得截图或 instrumentation 证据 |
| `V4-E2E-001` | `PARTIAL_PASS / DEVICE_VALIDATION_DEFERRED_BY_USER` | 用户接管真机验收；未取得真实 AI、导出、回放或截图证据，不得写成 PASS |
| `V4-CAPTION-QUALITY-001` | `IN_PROGRESS / MATRIX_DEFINED` | 唯一最终批次、已验证英文纠错、双句拆 cue、长字幕复核和主页面编辑器整合 |

## 执行和提交顺序

1. `V4-PLAN-001` 文档与规则 checkpoint。
2. `V4-FLOW-001` 独立功能提交。
3. `V4-EDITOR-001` 独立功能提交。
4. `V4-UI-001` 独立功能提交。
5. `V4-E2E-001` 因用户接管真机测试保留为部分通过。
6. `V4-CAPTION-QUALITY-001` 按新冻结矩阵实现并完成组件/构建验证；真实设备证据由用户终验补齐。

提交信息使用中文，默认不 push。每次只精确暂存当前阶段文件；所有进入 V4 前的未跟踪或脏内容必须保留。

## V4 组件验收快照（2026-08-24）

- `python tools\asr_evaluate_test.py`：6/6 通过。
- `testDebugUnitTest`：58 suites、352 tests、0 failures、0 errors、0 skipped。
- `lintDebug`、普通 `assembleDebug`、`-PenableWhisperNative=true assembleDebug`、普通与 Native `assembleDebugAndroidTest`：全部成功。
- Native Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，383,243,785 bytes。
- AndroidTest APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`，133,289 bytes。
- Pixel 8 模拟器三次启动均在安装前失败，原始错误为 `A snapshot operation for 'Pixel_8' is pending and timeout has expired`；按三次失败规则冻结，未删除 AVD 快照或锁文件。
- 未连接、安装或操作真机；未执行真实 AI、真实导出或完整产品 E2E。

## V4 总体验收

正式 V4 PASS 需要真实设备从系统相册入口完成：一次点击识别、本地 ASR、真实 AI 增强、标准英文纠错、融合双句拆 cue、主页面内联编辑、保存恢复、MediaStore 导出和 Media3 回放。缺少真实 AI、真实设备、真实导出或截图证据时，只能标记对应的 `PARTIAL_PASS`。
