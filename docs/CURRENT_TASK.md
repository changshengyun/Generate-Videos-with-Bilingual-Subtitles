# Current Task: V4-EDITOR-CONTROL-001

- `STATE_REV: 2026-08-26.017`
- `TASK_REV: V4-EDITOR-CONTROL-001.001`
- Stage state: `MATRIX_DEFINED / IN_PROGRESS`
- Product status: `EDITOR_SCOPE_LOCKS_PERSISTENT_PANEL_AND_MERGE_IN_PROGRESS`
- Evidence ceiling: `COMPONENT_VERIFIED`
- Device gate: `NOT_INSTALLED / USER_DEVICE_VALIDATION_PENDING / NO_AGENT_DEVICE_ACTION`

## 1. 阶段目标

在现有 Compose 编辑器、共享 ExoPlayer、项目默认样式与 cue override 边界内，实现普通/全屏共享的布局锁、样式面板独立整体锁、固定式非模态样式面板，以及与上一条/下一条字幕原子合并。

## 2. 冻结验收矩阵

| 类别 | 冻结内容 |
|---|---|
| 主链路 | 打开已有最终字幕 → 在普通或全屏预览切换布局锁 → 统一或单条调整位置、宽度和字号 → 打开固定样式面板 → 切换样式锁并统一或单条修改完整样式 → 主页面保持可滚动、可选字幕、可播放和 Seek → 与上一条或下一条合并 → 保存恢复并使旧导出失效。 |
| 必须证据 | 两种锁切换不修改数据、单条/全局分流、按属性清除 override、普通/全屏锁状态共享；固定面板仅收起箭头关闭、拖动只改高度、主页面保持交互、Insets/IME/48dp；合并方向、首尾边界、ID、文本、时间、置信度、样式继承、AI 临时状态与导出失效；聚焦 JVM、UI 合同、ASR、全量 JVM、lint、普通/Native Debug 和 AndroidTest 构建、独立复审。 |
| 禁止事项 | 不修改歌曲匹配透明化、`SongMatch`、AI Prompt、DeepSeek/LRCLIB/canonical 流程、处理等级、Whisper、Media3/FFmpegKit/MediaStore 架构或依赖；保留 `3fa18cc` 的 30% 最终置信度门槛；不安装或操作手机，不清数据，不 reset、clean、批量暂存或 push。 |
| 退出状态 | 代码、聚焦验证、冻结回归、Native APK 和独立复审全部完成后，最高标记 `PARTIAL_PASS / COMPONENT_VERIFIED / NOT_INSTALLED`；只有后续用户明确授权并提供真机交互证据，才允许提升为 `DEVICE_VERIFIED`。 |
| 未完成状态 | 组件或构建证据缺失时保持 `PARTIAL_PASS` 或 `BLOCKED`；需要架构、依赖、模型、AI 合同或破坏性操作时返回 `HUMAN_DECISION`。 |

矩阵在业务代码修改前冻结。当前实现沿用 `CaptionLayout`、`DefaultCaptionStyle`、每 cue `CaptionLayoutOverride/CaptionStyleOverride` 和共享播放器，不新增依赖。

## 3. 已验证基线

- 基线 HEAD：`3fa18cc0b696f81aaea52ea44f230272952b71db`（`降低标准歌词匹配置信度门槛`）。
- `V4-CAPTION-REPAIR-001` 保持 `PARTIAL_PASS / COMPONENT_VERIFIED / USER_DEVICE_VALIDATION_PENDING`。
- 当前普通/全屏直接编辑固定写入单 cue override；当前样式面板使用 `ModalBottomSheet`，会形成模态交互并可外部关闭；当前只有拆分，没有相邻 cue 合并。
- `V3-ASR-DIAG-001` 继续固定为 `PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`。

## 4. 冻结实现决策

- 布局锁和样式锁互相独立；切换锁本身不修改字幕，只决定后续写入范围。
- 锁状态属于编辑会话，普通/全屏和旋转共享，但不写入项目归档。
- 全局写入更新项目默认值，并只清除对应属性的 cue override；其他覆盖必须保留。
- 固定样式面板使用同页底部非模态 `Surface`；点击外部、主页面操作、系统返回和拖动均不关闭，只有左上角收起箭头关闭。
- 合并按时间顺序；英文用单空格连接，中文直接按顺序连接；兄弟 `parent:1/parent:2` 恢复 `parent`，其他使用较早 cue ID；样式与布局采用用户发起合并时选中的 cue。

## 5. 当前执行状态

- 验收矩阵已冻结；下一步创建阶段 checkpoint，再实现纯策略、ViewModel 分流和 Compose UI。

## 6. 下一允许动作

创建 `V4-EDITOR-CONTROL-001` checkpoint，依次实现编辑范围策略、合并策略、普通/全屏锁、固定样式面板和聚焦验证。最终只生成并交付 Native APK，不执行 `adb install`。
