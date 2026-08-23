# LyricCaptioner V4 Project State

- `STATE_REV: 2026-08-24.010`
- Repository: `D:\DevEnv\Projects\lyric-captioner-android`
- Branch: `migration/lyric-captioner-history`
- V4 baseline HEAD: `daf38c884b5b8b9f6b7f1b0517232871f9113417`
- Current task: `V4-UI-001`
- Stage state: `MATRIX_DEFINED / IN_PROGRESS`
- Product status: `V4_SEPARATE_PLAYER_CONTROLS_IMPLEMENTATION`
- Current gate: `IMPLEMENTATION_AUTHORIZED`
- Evidence ceiling: `COMPONENT_VERIFIED`
- Last state sync: 2026-08-24

## 当前决定

- V4 是新的当前产品版本；V3 只保留历史证据。
- V3 未完成的生产 base 验证固定记录为 `V3-ASR-DIAG-001 / PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`，不补做、不改写为 PASS，也不阻塞 V4。
- 唯一产品主链路为：相册导入 → 一次点击开始识别 → 本地 Whisper → 自动 AI 增强 → 自动进入字幕编辑 → 添加或编辑字幕 → 预览并导出。
- V4 依次执行 `V4-FLOW-001`、`V4-EDITOR-001`、`V4-UI-001`；`V4-E2E-001` 只有获得明确设备授权后才能执行。
- 不新增依赖，不更换模型，不修改 AI Prompt、歌词检索、响应合同或 cue 时间戳合同。
- 简单变更按 S0 只检查精确 diff；普通功能按 S1 聚焦验证；复杂故障按 S2 证据优先。三次修复失败后冻结修改并只运行一个最小判别实验。

## 当前验收门禁

- `V4-FLOW-001` 已实现并通过聚焦测试；完整回归矩阵延至 V4 收尾统一执行。
- `V4-EDITOR-001` 已实现并通过插入、直接编辑和项目归档聚焦测试；完整回归矩阵延至 V4 收尾统一执行。
- `docs/CURRENT_TASK.md` 已冻结 `V4-UI-001` 验收矩阵，允许进入播放器布局实现。
- 当前禁止真机操作，最高只能记录 `COMPONENT_VERIFIED`；真实 AI、真实设备和真实导出证据归入 `V4-E2E-001`。
- 阶段实现与构建成功不等于完整 V4 产品 PASS。

## 受保护工作树

保留所有进入 V4 前的未跟踪或脏内容，包括 `.emulator-test-assets/`、`.env`、`dist/`、`docs/debug/ASR_SMALL_BASE_VERSION_COMPARISON.md`、`tools/opus-mt-en-zh/` 和未知内容。不得 reset、clean、覆盖、批量暂存或 push。

## 下一允许动作

精确检查并提交 `V4-EDITOR-001`；随后只在 `V4-UI-001` 冻结矩阵范围内实现独立播放器控制行和聚焦测试。

## 权威资料

- 路线：`docs/DEVELOPMENT_ROADMAP.md`
- 唯一活动任务与冻结矩阵：`docs/CURRENT_TASK.md`
- 既有 AI 需求与路线背景：`docs/REQUIREMENTS.md`、`docs/TECH_OPTIONS.md`、`docs/ENVIRONMENT_REPORT.md`、`docs/SPIKE_PLAN.md`
- V3 历史证据：`docs/archive/v3/`
