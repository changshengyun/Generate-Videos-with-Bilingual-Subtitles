# LyricCaptioner V4 开发路线

- `ROADMAP_REV: 2026-08-30.002`
- 当前任务：无活动任务（`CODEX-HYGIENE-001 / PASS / REPO_CONFIG_VERIFIED`，未启动 V5）
- V3 历史摘要：原 `archive/v3/V3_STAGE_HISTORY_2026-08-12.md`（V4.4 已移出仓库，可从 git 历史找回）

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
| `V4-PLAN-001` | `PASS / CHECKPOINT_CREATED` | 初始化 S0/S1/S2 规则、V4 路线和活动状态，创建 checkpoint |
| `V4-FLOW-001` | `PASS / COMPONENT_VERIFIED` | 一次点击串联本地 ASR、AI 增强和自动进入编辑器 |
| `V4-EDITOR-001` | `PASS / COMPONENT_VERIFIED` | 按当前播放位置在空档新增双语字幕，并保持编辑/恢复/导出一致 |
| `V4-UI-001` | `PARTIAL_PASS / COMPONENT_VERIFIED / SIMULATOR_BLOCKED` | 普通与全屏独立控制行已通过组件验证；Pixel 8 因已有 snapshot operation pending 无法启动，未取得截图或 instrumentation 证据 |
| `V4-SIMP-001` | `PASS / COMPONENT_VERIFIED` | 代码简化已完成：`EditorScreen.kt` 拆分为同包 8 文件（2365 → 290 行）并修复 4 处乱码文案，41 处 `private` → `internal`，契约测试改为 ui 目录拼接文本且断言强度不降低；行为不变，不影响后续阶段合同 |
| `V4-E2E-001` | `WAITING_DEVICE_AUTHORIZATION` | 真实相册导入、ASR、AI、编辑、恢复、导出与 Media3 回放验收 |

发布后的 `CODEX-HYGIENE-001` 已完成，只调整 Codex 项目规则与 repo Skill，不改变 V4 阶段顺序、产品实现或 V5 路线。

## 执行和提交顺序

1. `V4-PLAN-001` 文档与规则 checkpoint。
2. `V4-FLOW-001` 独立功能提交。
3. `V4-EDITOR-001` 独立功能提交。
4. `V4-UI-001` 独立功能提交。
5. `V4-SIMP-001` 独立功能提交（无行为变化的拆分与乱码修复，不改变任何阶段依赖）。
6. `V4-E2E-001` 获得设备授权后执行验收并提交状态。

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

正式 V4 PASS 需要在获得设备授权后，使用真实设备从系统相册入口完成：一次点击识别、本地 ASR、真实 AI 增强、自动进入编辑器、开头/中间/结尾新增双语字幕、修改已有字幕、保存恢复、MediaStore 导出和 Media3 回放。缺少真实 AI、真实设备或真实导出证据时，只能标记对应的 `PARTIAL_PASS`。
