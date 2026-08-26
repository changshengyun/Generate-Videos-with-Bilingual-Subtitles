# Current Task: V4-SIMP-001

- `STATE_REV: 2026-08-26.013`
- `TASK_REV: V4-SIMP-001.002`
- Stage state: `PASS / COMPONENT_VERIFIED`
- Product status: `V4_COMPONENTS_IMPLEMENTED_E2E_PENDING`
- Evidence ceiling: `COMPONENT_VERIFIED`
- Device gate: `EXPLICIT_PHYSICAL_DEVICE_AUTHORIZATION_REQUIRED`

## 1. 阶段目标

在等待 `V4-E2E-001` 真机授权的窗口期，把 `app/src/main/java/com/example/lyriccaptioner/ui/EditorScreen.kt`（2365 行、35 个顶层声明）按职责拆分为同包多个文件，修复其中 4 个乱码按钮文案，并同步适配 2 个源码契约测试。纯机械搬移，行为与语义契约完全不变。

## 2. 冻结验收矩阵

| 类别 | 冻结内容 |
|---|---|
| 主链路 | 从既有代码基线出发：拆分后工程编译成功 → 全量 JVM 测试通过 → lint 通过 → 普通/Native Debug 与 AndroidTest 构建成功 → 2 个 UI 源码契约测试在多文件形态下继续守护同等断言强度。 |
| 必须证据 | `testDebugUnitTest` 实际测试计数（基线 58 suites / 352 tests / 0 failures）；`lintDebug` 成功输出；普通与 `-PenableWhisperNative=true` Debug 构建及普通与 Native AndroidTest 构建成功；ASR Python 6/6；拆分前后 ui 包 Kotlin 源文件清单与行数对比；乱码字符串修复后的精确 diff。 |
| 禁止事项 | 不改任何函数体逻辑、参数、调用点与 semantics contentDescription 契约；不改 MainViewModel、ProjectArchive 及其他业务模块；不动受保护工作树（`.emulator-test-assets/`、`.env`、`dist/`、`tools/opus-mt-en-zh/`、`third_party/`、`._cache_adb.exe` 等既有脏内容）；不操作真机或模拟器破坏性恢复；不 reset、clean、批量暂存或 push。 |
| 退出状态 | 全部验证命令通过且契约测试断言强度不低于拆分前，标记 `PASS / COMPONENT_VERIFIED`；`V4-E2E-001` 恢复为下一等待授权事项。 |
| 未完成状态 | 任一验证失败为 `PARTIAL_PASS` 并记录失败项；模拟器证据因既有 snapshot pending 不可获得时按 `SIMULATOR_BLOCKED` 降级说明（本次为无行为变化搬移，不强制 UI 截图）；契约断言无法在多文件形态下保持同等强度时冻结并返回 `HUMAN_DECISION`。 |

## 3. 拆分方案摘要

同包 `com.example.lyriccaptioner.ui` 下新建 8 个文件：`DeepSeekKeySettingsPanel.kt`、`WorkbenchPanels.kt`、`CaptionStyleControls.kt`、`EditorSupport.kt`、`VideoPreviewPlayer.kt`、`SubtitlePreviewOverlay.kt`、`DirectCaptionEditPanel.kt`、`CaptionListPanel.kt`；`EditorScreen.kt` 仅保留主入口与 `uniqueDocumentName`。搬移函数原样保留，仅 `private` → `internal`（Kotlin 文件级私有语义）并按文件裁剪 imports。`DirectEditPanelTab` 枚举与 `DirectCaptionEditPanel`、`DirectStyleGroupTitle` 保持原有相对顺序。

## 4. 已完成实现

- `V4-PLAN-001`：`758cfa1`。
- `V4-FLOW-001`：`990207b`，`PASS / COMPONENT_VERIFIED`。
- `V4-EDITOR-001`：`a342db9`，`PASS / COMPONENT_VERIFIED`。
- `V4-UI-001`：`d4ef61d`，`PARTIAL_PASS / COMPONENT_VERIFIED / SIMULATOR_BLOCKED`。
- `V4-SIMP-001`：本阶段功能 commit，`PASS / COMPONENT_VERIFIED`。证据：
  - 拆分结果：`EditorScreen.kt` 从 2365 行/35 个顶层声明变为 290 行（仅主入口 + `uniqueDocumentName`），同包新建 8 文件：`CaptionListPanel.kt`(322)、`CaptionStyleControls.kt`(359)、`DeepSeekKeySettingsPanel.kt`(171)、`DirectCaptionEditPanel.kt`(228)、`EditorSupport.kt`(62)、`SubtitlePreviewOverlay.kt`(350)、`VideoPreviewPlayer.kt`(444)、`WorkbenchPanels.kt`(314)；41 处 `private` → `internal`；纯机械搬移（完整性校验：拆分前后代码行 2177/2177 精确匹配，差异仅为可见性声明与乱码修复）。
  - 乱码修复：`DefaultCaptionStyleControls` 中 4 个按钮文案修复为「取消粗体/粗体/取消斜体/斜体」。
  - 契约测试适配：2 个测试的源码读取改为 ui 目录全部 .kt 按文件名排序拼接（断言范围实际扩大到整个 ui 包）；4 处断言字符串同步 `private` → `internal`；`useController = false` 计数仍为 2、`PlayerControlRow(` 计数减 1 仍为 2、`DirectEditPanelTab` 边界提取逻辑在拼接文本中仍精确命中。
  - 验证证据：`testDebugUnitTest` 58 suites / 352 tests / 0 failures（与基线一致）；`lintDebug`、普通 `assembleDebug`、`-PenableWhisperNative=true assembleDebug`、`-PenableWhisperNative=true assembleDebugAndroidTest` 全部 BUILD SUCCESSFUL；`python tools\asr_evaluate_test.py` 6/6 OK。
  - UI instrumentation/截图：模拟器维持 `SIMULATOR_BLOCKED`（Pixel 8 snapshot pending，同 V4-UI-001 降级原因），本次为无行为变化纯搬移，按矩阵约定不强制 UI 证据。
- V3 缺口继续固定为 `V3-ASR-DIAG-001 / PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`。

## 5. 下一允许动作

阶段完成。恢复等待 `V4-E2E-001` 真机授权；授权前不得连接、安装或操作真机。
