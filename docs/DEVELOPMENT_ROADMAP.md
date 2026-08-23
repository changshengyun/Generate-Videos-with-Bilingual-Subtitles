# LyricCaptioner V4 开发路线

- `ROADMAP_REV: 2026-08-24.005`
- 当前任务：`V4-FLOW-001 / MATRIX_DEFINED / IN_PROGRESS`
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

V4 不改变 Whisper 模型、DeepSeek Prompt、歌词检索、AI 响应合同、cue 时间戳合同、存储架构或导出技术路线。

## V3 历史边界

- `V3-ASR-DIAG-001` 固定为 `PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`。
- 诊断入口的 base 证据继续保留；未执行的新 APK 生产 base 验证不得改写为 PASS。
- 用户已明确延期该验证，因此它不阻塞 V4，但仍属于完整生产验收的已知缺口。

## V4 阶段顺序

| 阶段 | 当前状态 | 目标/依赖 |
|---|---|---|
| `V4-PLAN-001` | `IN_PROGRESS` | 初始化 S0/S1/S2 规则、V4 路线和活动状态，创建 checkpoint |
| `V4-FLOW-001` | `MATRIX_DEFINED / IN_PROGRESS` | 一次点击串联本地 ASR、AI 增强和自动进入编辑器 |
| `V4-EDITOR-001` | `PLANNED` | 按当前播放位置在空档新增双语字幕，并保持编辑/恢复/导出一致 |
| `V4-UI-001` | `PLANNED` | 普通与全屏预览统一使用视频画面下方的独立播放器控制行 |
| `V4-E2E-001` | `WAITING_DEVICE_AUTHORIZATION` | 真实相册导入、ASR、AI、编辑、恢复、导出与 Media3 回放验收 |

## 执行和提交顺序

1. `V4-PLAN-001` 文档与规则 checkpoint。
2. `V4-FLOW-001` 独立功能提交。
3. `V4-EDITOR-001` 独立功能提交。
4. `V4-UI-001` 独立功能提交。
5. `V4-E2E-001` 获得设备授权后执行验收并提交状态。

提交信息使用中文，默认不 push。每次只精确暂存当前阶段文件；所有进入 V4 前的未跟踪或脏内容必须保留。

## V4 总体验收

正式 V4 PASS 需要在获得设备授权后，使用真实设备从系统相册入口完成：一次点击识别、本地 ASR、真实 AI 增强、自动进入编辑器、开头/中间/结尾新增双语字幕、修改已有字幕、保存恢复、MediaStore 导出和 Media3 回放。缺少真实 AI、真实设备或真实导出证据时，只能标记对应的 `PARTIAL_PASS`。
