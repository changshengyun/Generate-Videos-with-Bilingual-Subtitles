# Current Task: V4-EDITOR-CONTROL-001

- `STATE_REV: 2026-08-26.018`
- `TASK_REV: V4-EDITOR-CONTROL-001.002`
- Stage state: `PARTIAL_PASS / COMPONENT_VERIFIED`
- Product status: `EDITOR_CONTROLS_IMPLEMENTED / NOT_INSTALLED`
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

- checkpoint：`2cb1b2d`（`冻结编辑控制改进验收矩阵`）。
- 功能提交：`2a60171`（`实现字幕编辑锁定与合并`）。
- 已实现普通/全屏共享布局锁与独立样式锁；两种锁均只改变后续编辑范围，不持久化到项目归档。
- 全局编辑更新项目默认值并按属性清除 cue override；单条编辑保留其他 cue 和无关属性覆盖。
- 样式区域已由 `ModalBottomSheet` 改为固定非模态 `Surface`：主页面保持可操作，拖动只调整 1/3～1/2 屏高度，系统返回不关闭，只有收起箭头关闭。
- 已实现与上一条/下一条原子合并，包括首尾禁用、兄弟 ID 恢复、冲突回退、文本/时间/置信度/样式继承、AI 临时状态清理和旧导出失效。
- 独立只读代码复审最终 `PASS`，未发现剩余 P1/P2 finding。

## 6. 验证证据

- `python tools\asr_evaluate_test.py`：6/6 通过。
- 聚焦 JVM：24/24 通过，覆盖锁分流、属性级 override 清理、安全布局边界、合并策略和固定面板 UI 源合同。
- 全量 `testDebugUnitTest`：382 项中 379 通过；3 项失败均为隔离 worktree 无法发现原仓库已有 OPUS-MT/Whisper 外部 fixture，不是本阶段代码回归。
- `lintDebug`、普通 Debug、Native Debug、AndroidTest 构建全部成功；最终改动后再次执行 `lintDebug`、`assembleDebugAndroidTest` 与 `-PenableWhisperNative=true assembleDebug` 成功。
- Native APK：`app/build/outputs/apk/debug/app-debug.apk`，116,019,973 bytes（110.65 MiB），SHA-256 `10AFAE4F4B2E05CAC04629A39A87BE2A4D10105CEB2A4F921F65C74AA90CF76D`；包含 arm64-v8a 与 x86_64 Whisper native library。
- 未执行 instrumentation 运行、安装或任何 ADB/手机操作，因此不具备真机 UI、保存恢复或产品链路证据。

## 7. 验收矩阵结论

| 项目 | 结果 |
|---|---|
| 两个独立锁、单条/全局分流、属性级 override 清理 | `PASS / COMPONENT_VERIFIED` |
| 固定非模态面板、48dp 拖动区、IME/Insets 源合同、仅箭头收起 | `PASS / COMPONENT_VERIFIED` |
| 相邻合并方向、边界、ID、文本、时间、置信度、样式、导出失效 | `PASS / COMPONENT_VERIFIED` |
| 冻结回归、Native APK、独立复审 | `PASS` |
| 真机交互、旋转、保存恢复和产品入口验证 | `PENDING / USER_DEVICE_VALIDATION` |

最终状态为 `PARTIAL_PASS / COMPONENT_VERIFIED / NOT_INSTALLED`，未越过冻结证据上限。

## 8. 下一允许动作

交付未安装的 Native APK，由用户自行安装并验证普通/全屏锁同步、固定面板交互、样式全局/单条范围、相邻合并、旋转与保存恢复。Agent 继续禁止安装或操作设备，除非用户后续明确授权新动作。
