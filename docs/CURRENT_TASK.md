# Current Task: V4-E2E-001

- `STATE_REV: 2026-08-25.012`
- `TASK_REV: V4-E2E-001.001`
- Stage state: `MATRIX_DEFINED / IN_PROGRESS`
- Product status: `V4_E2E_DEVICE_VALIDATION_IN_PROGRESS`
- Evidence ceiling: `COMPONENT_VERIFIED`
- Device gate: `PHYSICAL_DEVICE_AUTHORIZED / fcf4b0cb`

## 1. 阶段目标

在用户明确授权目标设备后，通过真实产品入口完成相册导入、一次点击识别、本地 ASR、真实 AI 增强、自动进入编辑器、开头/中间/结尾新增双语字幕、编辑、保存恢复、相册导出和 Media3 回放。

## 2. 冻结验收矩阵

| 类别 | 冻结内容 |
|---|---|
| 主链路 | 系统相册导入真实视频 → 一次点击“开始识别” → 本地 Whisper → DeepSeek 增强或合同允许的本地回退 → 自动进入编辑器 → 开头/中间/结尾新增双语字幕 → 修改已有字幕文本、时间、位置、宽度和字号 → 保存并重新打开 → MediaStore 导出到相册 → Media3 回放。 |
| 必须证据 | 目标设备标识；真实视频和模型入口；ASR 与 AI 调用/来源状态；三个插入槽位和一次已有 cue 编辑；保存恢复一致；导出 URI、文件大小/时长/编码；Media3 回放；普通与全屏播放器无遮挡截图；取消和失败边界；不得用 mock、Demo 或组件测试替代。 |
| 禁止事项 | 未获授权前不得连接、安装或操作真机；不修改模型、AI Prompt、歌词检索、响应合同、cue 时间戳或架构；不清 App/设备数据；不 reset、clean、批量暂存或 push。 |
| 退出状态 | 全部真实主链路与边界证据在授权设备上通过，才能标记 `PASS / DEVICE_VERIFIED`。 |
| 未完成状态 | 缺少设备授权为 `WAITING_DEVICE_AUTHORIZATION`；缺少真实 AI、真实设备、真实导出/回放或 UI 截图任一证据为对应 `PARTIAL_PASS`；外部凭据或 fixture 无法提供时为 `HUMAN_DECISION`。 |

矩阵保持冻结。用户于 2026-08-25 明确授权在已连接目标设备 `fcf4b0cb` 上执行本任务范围内的无损安装、真实产品主链路验证、普通缺陷修复和必要测试；仍禁止清除 App/设备数据、破坏性 Git/文件操作、架构或依赖变更。

## 3. 已完成实现

- `V4-PLAN-001`：`758cfa1`。
- `V4-FLOW-001`：`990207b`，`PASS / COMPONENT_VERIFIED`。
- `V4-EDITOR-001`：`a342db9`，`PASS / COMPONENT_VERIFIED`。
- `V4-UI-001`：`d4ef61d`，`PARTIAL_PASS / COMPONENT_VERIFIED / SIMULATOR_BLOCKED`。
- V3 缺口继续固定为 `V3-ASR-DIAG-001 / PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`。

## 4. 收尾验证证据

- ASR Python：6/6 通过。
- JVM：58 suites、352 tests、0 failures、0 errors、0 skipped。
- lint、普通 Debug、Native Debug、普通 AndroidTest 和 Native AndroidTest 构建全部成功。
- 当前 Native Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，383,243,785 bytes。
- AndroidTest APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`，133,289 bytes。
- Pixel 8 模拟器三次均因已有 `snapshot operation pending` 在启动阶段退出；没有安装 App，因此没有 UI instrumentation 或新截图证据。按 S2 三次失败规则冻结，未执行删除快照/锁文件等破坏性恢复。
- 真机、真实 DeepSeek、真实导出与 Media3 回放：`Unavailable / not authorized`。

## 5. 下一允许动作

在 `fcf4b0cb` 上使用 `install -r` 保留现有 App 数据与 DeepSeek Key，选择 base 模型并从系统相册入口执行冻结的 V4 真实主链路和边界验收；只在已证明的普通故障范围内实施最小修复。
