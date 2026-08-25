# Current Task: V4-CAPTION-REPAIR-001

- `STATE_REV: 2026-08-25.015`
- `TASK_REV: V4-CAPTION-REPAIR-001.001`
- Stage state: `MATRIX_DEFINED / IN_PROGRESS`
- Product status: `TWO_PASS_CAPTION_REPAIR_AND_EDITOR_UX_IN_PROGRESS`
- Evidence ceiling: `COMPONENT_VERIFIED`
- Device gate: `USER_LED_DEVICE_VALIDATION / NO_AGENT_DEVICE_ACTION`

## 1. 阶段目标

保持一次点击本地 Whisper → AI → 编辑主链路，在现有 Kotlin、Compose、Media3、FFmpegKit 和存储边界内完成双阶段 AI 字幕增强、人工安全拆分与单 cue AI 建议、样式底部面板及全屏字幕直接编辑。

## 2. 冻结验收矩阵

| 类别 | 冻结内容 |
|---|---|
| 主链路 | 系统相册导入视频 → 一次点击本地 Whisper → 歌曲候选识别、LRCLIB 检索与本地 canonical 验证 → 第一次整批增强和 1→2 自动拆分 → 所有自动子 cue 一次整批局部修复 → 整批校验并原子提交唯一最终列表 → 人工编辑、安全拆分、单 cue AI 建议预览/应用 → 样式 Bottom Sheet 与普通/全屏直接预览编辑 → 保存恢复 → MediaStore 导出与 Media3 回放。 |
| 必须证据 | enhancement v5、局部修复合同/validator、canonical 区间、二次失败保留首轮完整结果、取消不提交中间结果、处理等级持久化测试；英文编辑保留中文、安全拆分、单 cue 建议仅应用目标 cue、旧导出失效测试；Bottom Sheet、全屏控制和直接编辑 UI 合同/instrumentation；冻结 Android 回归。真实 DeepSeek、普通/全屏截图、导出与回放由用户终验。 |
| 禁止事项 | 不更换或下载模型，不新增依赖，不改变 Kotlin/Compose/Media3/FFmpegKit/存储架构，不使用模型记忆冒充 canonical 歌词，不清 App/设备数据，不运行 Agent 真机操作，不 reset、clean、批量暂存或 push；不得发布部分二次修复或覆盖未确认的人工内容。 |
| 退出状态 | 所有代码、聚焦测试和冻结回归通过，且用户提供真实 DeepSeek、真机普通/全屏截图、MediaStore 导出和 Media3 回放证据后，才能标记 `PASS / DEVICE_VERIFIED`。 |
| 未完成状态 | 组件与构建通过但缺少用户真机证据时为 `PARTIAL_PASS / COMPONENT_VERIFIED / USER_DEVICE_VALIDATION_PENDING`；真实 Key、歌词命中或指定视频证据缺失时保持对应 `PARTIAL_PASS`；架构、依赖、模型或破坏性操作需求才进入 `HUMAN_DECISION`。 |

矩阵在业务代码修改前冻结。当前授权允许升级 enhancement Prompt/合同、增加现有 Provider 内的第二次整批请求、增加人工单 cue 建议接口，以及修改当前编辑器 Compose UI；不授权新增依赖或改变核心媒体、模型和存储路线。

## 3. 已验证基线与用户反馈

- 基线 HEAD：`d410a8da3b57ff7de32d754213b5cc896db34c55`（`实现字幕质量与主页面编辑器整合`）。
- `V4-CAPTION-QUALITY-001` 保持 `PARTIAL_PASS / COMPONENT_VERIFIED`；用户真机确认英文编辑会清空中文、人工拆分边界不可靠、全屏底部控制不可见、全屏字幕不可编辑，且真实 AI 英文修复质量不足。
- 当前实现只有 `caption-enhancement.v4` 一次整批提交；自动拆分后的子 cue 不再执行 AI 局部修复，人工阶段也没有单 cue AI 建议接口。
- 当前 `CueEditingPolicy.updateEnglish` 明确清空中文；全屏 `SubtitlePreviewOverlay` 明确使用 `directEditMode = false`，属于已定位的实现缺陷。
- `V3-ASR-DIAG-001` 继续固定为 `PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`，不补做、不运行 small、不改写为 PASS。

## 4. 实施与失败边界

- canonical 英文由 LRCLIB 检索和本地多 cue 对齐决定；DeepSeek 只生成中文并在未验证情况下做保守纠错。
- 第二次增强逻辑上逐 cue、物理上一次整批请求；响应不能修改 ID、数量、顺序或时间。
- 第二次增强失败时原子发布完整第一阶段结果并标记 `FIRST_PASS_REVIEW_REQUIRED`；用户取消时不发布任何中间结果。
- 人工单 cue AI 结果必须先预览，确认后只替换目标 cue；失败或取消保持原文。
- UI 继续复用同一个 ExoPlayer；全屏控制条位于导航栏之上，字幕手势不得拦截播放或 Seek。

## 5. 当前执行状态

- 验收矩阵已冻结，下一步创建阶段 checkpoint 后实施聚焦代码和测试。

## 6. 下一允许动作

建立 `V4-CAPTION-REPAIR-001` checkpoint，依次实现双阶段 enhancement、人工编辑能力、样式 Bottom Sheet 和全屏直接编辑；完成聚焦验证后运行冻结回归。真机仍由用户使用 `D:\DevEnv\Projects\sorce\5e4c3cd7073a9e9b03df1fbf8af6d928.mp4` 终验。
