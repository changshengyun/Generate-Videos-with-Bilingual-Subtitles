# Current Task: V4-EDITOR-001

- `STATE_REV: 2026-08-24.009`
- `TASK_REV: V4-EDITOR-001.001`
- Stage state: `MATRIX_DEFINED / IN_PROGRESS`
- Product status: `V4_PLAYHEAD_CAPTION_INSERTION_IMPLEMENTATION`
- Evidence ceiling: `COMPONENT_VERIFIED`
- Stage checkpoint: `V4-FLOW-001 feature commit`
- Device gate: `NO_PHYSICAL_DEVICE_ACTIONS / V4-E2E-001_REQUIRES_EXPLICIT_AUTHORIZATION`

## 1. 阶段目标

新增 `addCaptionAt(playheadMs)`，只允许在已有字幕之间的有效空档、第一条之前或最后一条之后新增一条双语可编辑字幕。新增 cue 占满对应空档、使用独立 ID、自动选中并按时间排序。

## 2. 冻结验收矩阵

| 类别 | 冻结内容 |
|---|---|
| 主链路 | 在字幕编辑区以播放器当前时间调用 `addCaptionAt(playheadMs)`；开头使用 `0 → first.startMs`，中间使用 `previous.endMs → next.startMs`，结尾使用 `last.endMs → videoDurationMs`；创建后立即进入该 cue 的英文/中文编辑。 |
| 必须证据 | 聚焦单元测试覆盖开头、中间、结尾、cue 内拒绝、相邻重叠、空档不足 100ms、未知时长、空列表、边界播放位置、独立 ID、排序、选中和派生产物失效；既有双语文本、时间、位置、宽度、字号编辑与项目保存恢复测试保持通过。 |
| 禁止事项 | 不新增批量编辑、动画、复杂模板、自动拆分、自动合并或再次调用 AI；不改变既有 cue 的内容和时间；不引入依赖；不操作真机；不 reset、clean、批量暂存或 push。 |
| 退出状态 | 插入策略与 ViewModel 写入路径的聚焦测试通过，保存/恢复/预览/导出继续共享 `CaptionCue` 数据，标记 `IMPLEMENTED / FOCUSED_TEST_VERIFIED`；完整矩阵在 V4 收尾统一执行。 |
| 未完成状态 | 代码完成但聚焦证据不全为 `PARTIAL_PASS`；普通故障按 S1/S2 自主闭环；命中架构、依赖、需求冲突或无法证明安全时为 `HUMAN_DECISION`。 |

## 3. 允许范围

- 新增纯 Kotlin 插入策略、修改 `MainViewModel.addCaptionAt`、从播放器上报当前播放位置、更新字幕编辑区按钮及聚焦测试。
- 复用既有 `CaptionCue`、`DerivedOutputPolicy`、项目快照、预览和导出解析链路。
- 阶段完成后把活动任务切换到已批准的 `V4-UI-001`，并在 UI 编码前冻结其矩阵。

## 4. 已完成上游

- `V4-PLAN-001` checkpoint：`758cfa1`。
- `V4-FLOW-001` 聚焦测试：`CompleteCaptionWorkflowTest` 与 `EditorScreenV3UiContractTest` 通过；完整回归矩阵尚未执行。
- `V3-ASR-DIAG-001` 继续保持 `PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`。

## 5. 下一动作

精确检查并提交 `V4-FLOW-001`；随后实现和聚焦验证播放位置插入策略。
