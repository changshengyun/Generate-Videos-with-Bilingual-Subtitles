# Current Task: V4-SIMP-001

- `STATE_REV: 2026-08-26.012`
- `TASK_REV: V4-SIMP-001.001`
- Stage state: `MATRIX_DEFINED / IN_PROGRESS`
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
- V3 缺口继续固定为 `V3-ASR-DIAG-001 / PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`。

## 5. 下一允许动作

执行 V4-SIMP-001 拆分、乱码修复、契约测试适配与全量验证；完成后恢复等待 `V4-E2E-001` 真机授权。
