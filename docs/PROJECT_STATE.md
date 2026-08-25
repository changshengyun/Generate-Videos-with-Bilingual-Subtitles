# LyricCaptioner V4 Project State

- `STATE_REV: 2026-08-25.013`
- Repository: `D:\DevData\Codex\.codex\worktrees\c3dc\lyric-captioner-android`
- Branch: `codex/v4-caption-quality-001`
- V4 baseline HEAD: `97fc26f87761c1844429d5366e0b3dff18dcf21e`
- Current task: `V4-CAPTION-QUALITY-001`
- Stage state: `MATRIX_DEFINED / IN_PROGRESS`
- Product status: `CAPTION_QUALITY_AND_EDITOR_INTEGRATION_IN_PROGRESS`
- Current gate: `USER_LED_DEVICE_VALIDATION / NO_AGENT_DEVICE_ACTION`
- Evidence ceiling: `COMPONENT_VERIFIED`
- Last state sync: 2026-08-25

## 当前决定

- V4 是新的当前产品版本；V3 只保留历史证据。
- V3 未完成的生产 base 验证固定记录为 `V3-ASR-DIAG-001 / PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`，不补做、不改写为 PASS，也不阻塞 V4。
- 唯一产品主链路为：相册导入 → 一次点击开始识别 → 本地 Whisper → 自动 AI 增强 → 自动进入字幕编辑 → 添加或编辑字幕 → 预览并导出。
- `V4-E2E-001` 因用户接管真机测试固定为 `PARTIAL_PASS / DEVICE_VALIDATION_DEFERRED_BY_USER`；没有真实 AI、导出、回放或截图证据。
- 用户批准 `V4-CAPTION-QUALITY-001` 修改 enhancement Prompt、响应合同和 cue 拆分策略；仍不授权更换模型、新增依赖、改变媒体/存储架构或破坏性操作。
- RAW_ASR 只作为内部输入；云端或显式标注的本地回退原子提交唯一最终批次，预览、保存和导出不得混合来源。
- 真机终验改由用户执行；本阶段 Agent 不连接、安装或操作设备。
- 简单变更按 S0 只检查精确 diff；普通功能按 S1 聚焦验证；复杂故障按 S2 证据优先。三次修复失败后冻结修改并只运行一个最小判别实验。

## 当前验收门禁

- `V4-FLOW-001` 与 `V4-EDITOR-001` 已达到 `PASS / COMPONENT_VERIFIED`。
- `V4-UI-001` 达到 `PARTIAL_PASS / COMPONENT_VERIFIED / SIMULATOR_BLOCKED`；Pixel 8 因已有 snapshot pending 无法启动，未取得新截图或 instrumentation 证据。
- 收尾矩阵：ASR Python 6/6；JVM 352/352；lint、普通/Native Debug、普通/Native AndroidTest 构建全部成功。
- `docs/CURRENT_TASK.md` 已冻结 `V4-CAPTION-QUALITY-001` 验收矩阵；实施范围包含标准英文纠错、双句拆 cue、长字幕复核和主页面编辑器整合。
- 在取得完整主链路证据前仍保持 `COMPONENT_VERIFIED` 证据上限；真实 AI、真实设备、真实导出与回放结果必须按实际执行提升或保留为未完成。
- 阶段实现与构建成功不等于完整 V4 产品 PASS。

## 受保护工作树

保留所有进入 V4 前的未跟踪或脏内容，包括 `.emulator-test-assets/`、`.env`、`dist/`、`docs/debug/ASR_SMALL_BASE_VERSION_COMPARISON.md`、`tools/opus-mt-en-zh/` 和未知内容。不得 reset、clean、覆盖、批量暂存或 push。

## 下一允许动作

建立 `V4-CAPTION-QUALITY-001` checkpoint，随后实现 enhancement v4 合同、确定性 cue 拆分、唯一最终批次和主页面字幕编辑器；完成聚焦与冻结回归后保持 `PARTIAL_PASS`，等待用户真机终验。

## 权威资料

- 路线：`docs/DEVELOPMENT_ROADMAP.md`
- 唯一活动任务与冻结矩阵：`docs/CURRENT_TASK.md`
- 既有 AI 需求与路线背景：`docs/REQUIREMENTS.md`、`docs/TECH_OPTIONS.md`、`docs/ENVIRONMENT_REPORT.md`、`docs/SPIKE_PLAN.md`
- V3 历史证据：`docs/archive/v3/`
