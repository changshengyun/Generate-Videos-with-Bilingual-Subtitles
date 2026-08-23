# Current Task: V4-UI-001

- `STATE_REV: 2026-08-24.010`
- `TASK_REV: V4-UI-001.001`
- Stage state: `MATRIX_DEFINED / IN_PROGRESS`
- Product status: `V4_SEPARATE_PLAYER_CONTROLS_IMPLEMENTATION`
- Evidence ceiling: `COMPONENT_VERIFIED`；没有新的模拟器截图时不得声明 `SIMULATOR_VERIFIED`
- Stage checkpoint: `V4-EDITOR-001 feature commit`
- Device gate: `NO_PHYSICAL_DEVICE_ACTIONS / V4-E2E-001_REQUIRES_EXPLICIT_AUTHORIZATION`

## 1. 阶段目标

普通与全屏预览统一为“视频有效画面与字幕覆盖层 → 独立播放器控制行”。禁用 `PlayerView` 内部控制器，使播放/暂停、当前时间、Seek、总时长始终位于视频画面之外。

## 2. 冻结验收矩阵

| 类别 | 冻结内容 |
|---|---|
| 主链路 | 普通和全屏预览使用同一个 ExoPlayer 实例；视频与字幕只占画面区域，画面下方单独显示播放/暂停、当前时间、进度条和总时长；字幕编辑区域继续位于普通控制行之后。 |
| 必须证据 | 聚焦 UI/source contract 证明两个 `PlayerView.useController = false`、普通和全屏均复用独立控制行、控制行与同一 player 绑定、进度条不在视频/字幕 Box 内、最小触控尺寸 48dp、Insets 与 IME 约束保留；完整矩阵在 V4 收尾统一执行；只有能安全使用 Pixel 8 模拟器时补充截图。 |
| 禁止事项 | 不更换 Media3/ExoPlayer，不改变字幕渲染坐标、手势含义、导出布局、主题或编辑功能；不引入依赖；不操作真机；不 reset、clean、批量暂存或 push。 |
| 退出状态 | 聚焦合同和既有直接编辑测试通过，普通与全屏代码结构均保证控件位于视频区域之外，标记 `IMPLEMENTED / COMPONENT_VERIFIED`；若取得 Pixel 8 可复核截图再提升为 `SIMULATOR_VERIFIED`。 |
| 未完成状态 | 代码完成但聚焦证据不全为 `PARTIAL_PASS`；缺少模拟器截图不阻止组件级状态但不得声明模拟器通过；普通故障按 S1/S2 自主闭环。 |

## 3. 允许范围

- 只修改 `EditorScreen.kt` 的播放器布局、独立控制行和直接相关测试。
- 使用现有 Media3 `Player`/`ExoPlayer`，使用 Material 3 `Slider`，不新增依赖。
- UI 完成后执行一次 V4 收尾完整矩阵，并同步三份活动文档。

## 4. 已完成上游

- `V4-PLAN-001` checkpoint：`758cfa1`。
- `V4-FLOW-001` 功能提交：`990207b`，聚焦测试通过。
- `V4-EDITOR-001`：插入策略及直接编辑/归档相关聚焦测试通过，待当前提交生成 commit id。
- `V3-ASR-DIAG-001` 保持 `PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`。

## 5. 下一动作

精确检查并提交 `V4-EDITOR-001`；随后实现普通与全屏共享的独立播放器控制行和聚焦 UI 验证。
