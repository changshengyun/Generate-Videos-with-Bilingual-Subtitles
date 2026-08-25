# LyricCaptioner V4 Project State

- `STATE_REV: 2026-08-25.012`
- Repository: `D:\DevEnv\Projects\lyric-captioner-android`
- Branch: `migration/lyric-captioner-history`
- V4 baseline HEAD: `b225f0b7364d17f8935a3a2854cf853fe849a14e`
- Current task: `V4-E2E-001`
- Stage state: `MATRIX_DEFINED / IN_PROGRESS`
- Product status: `V4_E2E_DEVICE_VALIDATION_IN_PROGRESS`
- Current gate: `PHYSICAL_DEVICE_AUTHORIZED / fcf4b0cb`
- Evidence ceiling: `COMPONENT_VERIFIED`
- Last state sync: 2026-08-25

## 当前决定

- V4 是新的当前产品版本；V3 只保留历史证据。
- V3 未完成的生产 base 验证固定记录为 `V3-ASR-DIAG-001 / PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`，不补做、不改写为 PASS，也不阻塞 V4。
- 唯一产品主链路为：相册导入 → 一次点击开始识别 → 本地 Whisper → 自动 AI 增强 → 自动进入字幕编辑 → 添加或编辑字幕 → 预览并导出。
- V4 依次执行 `V4-FLOW-001`、`V4-EDITOR-001`、`V4-UI-001`；`V4-E2E-001` 只有获得明确设备授权后才能执行。
- 用户已于 2026-08-25 授权在 `fcf4b0cb` 上无损安装并执行 `V4-E2E-001`；不授权清除 App/设备数据、破坏性 Git/文件操作、架构或依赖变更。
- 不新增依赖，不更换模型，不修改 AI Prompt、歌词检索、响应合同或 cue 时间戳合同。
- 简单变更按 S0 只检查精确 diff；普通功能按 S1 聚焦验证；复杂故障按 S2 证据优先。三次修复失败后冻结修改并只运行一个最小判别实验。

## 当前验收门禁

- `V4-FLOW-001` 与 `V4-EDITOR-001` 已达到 `PASS / COMPONENT_VERIFIED`。
- `V4-UI-001` 达到 `PARTIAL_PASS / COMPONENT_VERIFIED / SIMULATOR_BLOCKED`；Pixel 8 因已有 snapshot pending 无法启动，未取得新截图或 instrumentation 证据。
- 收尾矩阵：ASR Python 6/6；JVM 352/352；lint、普通/Native Debug、普通/Native AndroidTest 构建全部成功。
- `docs/CURRENT_TASK.md` 已冻结 `V4-E2E-001` 验收矩阵；目标真机 `fcf4b0cb` 已获执行授权，验收正在进行。
- 在取得完整主链路证据前仍保持 `COMPONENT_VERIFIED` 证据上限；真实 AI、真实设备、真实导出与回放结果必须按实际执行提升或保留为未完成。
- 阶段实现与构建成功不等于完整 V4 产品 PASS。

## 受保护工作树

保留所有进入 V4 前的未跟踪或脏内容，包括 `.emulator-test-assets/`、`.env`、`dist/`、`docs/debug/ASR_SMALL_BASE_VERSION_COMPARISON.md`、`tools/opus-mt-en-zh/` 和未知内容。不得 reset、clean、覆盖、批量暂存或 push。

## 下一允许动作

在 `fcf4b0cb` 上以 `install -r` 保留现有 App/Key/媒体状态，选择 base 模型并执行冻结的 V4 真实产品主链路、取消与失败边界验收；仅对已证明的普通缺陷做最小修复。

## 权威资料

- 路线：`docs/DEVELOPMENT_ROADMAP.md`
- 唯一活动任务与冻结矩阵：`docs/CURRENT_TASK.md`
- 既有 AI 需求与路线背景：`docs/REQUIREMENTS.md`、`docs/TECH_OPTIONS.md`、`docs/ENVIRONMENT_REPORT.md`、`docs/SPIKE_PLAN.md`
- V3 历史证据：`docs/archive/v3/`
