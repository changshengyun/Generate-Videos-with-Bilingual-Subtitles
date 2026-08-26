# LyricCaptioner V4 Project State

- `STATE_REV: 2026-08-26.013`
- Repository: `D:\DevEnv\Projects\lyric-captioner-android`
- Branch: `migration/lyric-captioner-history`
- V4 baseline HEAD: `daf38c884b5b8b9f6b7f1b0517232871f9113417`
- Current task: `V4-E2E-001`
- Stage state: `WAITING_DEVICE_AUTHORIZATION`
- Product status: `V4_COMPONENTS_IMPLEMENTED_E2E_PENDING`
- Current gate: `PHYSICAL_DEVICE_AUTHORIZATION_REQUIRED`（E2E 专用；不阻塞无行为变化的代码简化）
- Evidence ceiling: `COMPONENT_VERIFIED`
- Last state sync: 2026-08-26

## 当前决定

- 当前生效治理体制为 AGENTS.md §9 阶段契约；§7 Multi-Agent 体制仅在用户显式启用时生效，其引用的治理文档（`MULTI_AGENT_WORKFLOW.md`、`DECISIONS.md`、`governance/*` 等）已只读归档于 `docs-BK/`，启用前须先恢复或重新获得用户批准。
- V4 是新的当前产品版本；V3 只保留历史证据。
- V3 未完成的生产 base 验证固定记录为 `V3-ASR-DIAG-001 / PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`，不补做、不改写为 PASS，也不阻塞 V4。
- 唯一产品主链路为：相册导入 → 一次点击开始识别 → 本地 Whisper → 自动 AI 增强 → 自动进入字幕编辑 → 添加或编辑字幕 → 预览并导出。
- V4 依次执行 `V4-FLOW-001`、`V4-EDITOR-001`、`V4-UI-001`；`V4-E2E-001` 只有获得明确设备授权后才能执行。
- 本地分支相对 origin 领先 11 / 落后 4（2026-08-26 记录）；是否 rebase/merge/push 由用户决定，默认不 push。
- `V4-SIMP-001` 是插入的代码简化阶段：仅拆分 `ui/EditorScreen.kt` 为同包多文件并修复 4 处乱码文案，纯机械搬移、行为不变；不改变任何架构、依赖、模型或合同。
- 不新增依赖，不更换模型，不修改 AI Prompt、歌词检索、响应合同或 cue 时间戳合同。
- 简单变更按 S0 只检查精确 diff；普通功能按 S1 聚焦验证；复杂故障按 S2 证据优先。三次修复失败后冻结修改并只运行一个最小判别实验。

## 当前验收门禁

- `V4-FLOW-001` 与 `V4-EDITOR-001` 已达到 `PASS / COMPONENT_VERIFIED`。
- `V4-UI-001` 达到 `PARTIAL_PASS / COMPONENT_VERIFIED / SIMULATOR_BLOCKED`；Pixel 8 因已有 snapshot pending 无法启动，未取得新截图或 instrumentation 证据。
- 收尾矩阵：ASR Python 6/6；JVM 352/352；lint、普通/Native Debug、普通/Native AndroidTest 构建全部成功。
- `V4-SIMP-001` 已完成并达到 `PASS / COMPONENT_VERIFIED`：`EditorScreen.kt` 拆分为同包 8 文件（2365 → 290 行，主入口保留）并修复 4 处乱码文案；41 处 `private` → `internal`；2 个源码契约测试改为读取 ui 目录拼接文本，断言强度不降低；JVM 352/352、lint、普通/Native Debug 与 AndroidTest 构建、ASR Python 6/6 全部通过；纯机械搬移，行为与语义契约完全不变。
- `docs/CURRENT_TASK.md` 已冻结 `V4-SIMP-001` 验收矩阵；阶段内不操作真机或模拟器。
- 当前禁止真机操作，最高只能记录 `COMPONENT_VERIFIED`；真实 AI、真实设备、真实导出与回放证据仍未获得。
- 阶段实现与构建成功不等于完整 V4 产品 PASS。

## 受保护工作树

保留所有进入 V4 前的未跟踪或脏内容，包括 `.emulator-test-assets/`、`.env`、`dist/`、`docs/debug/ASR_SMALL_BASE_VERSION_COMPARISON.md`、`tools/opus-mt-en-zh/` 和未知内容。不得 reset、clean、覆盖、批量暂存或 push。

## 下一允许动作

等待 `V4-E2E-001` 真机授权。授权前不得连接、安装或操作真机。

## 权威资料

- 路线：`docs/DEVELOPMENT_ROADMAP.md`
- 唯一活动任务与冻结矩阵：`docs/CURRENT_TASK.md`
- 既有 AI 需求与路线背景：`docs/REQUIREMENTS.md`、`docs/TECH_OPTIONS.md`、`docs/ENVIRONMENT_REPORT.md`、`docs/SPIKE_PLAN.md`
- V3 历史证据：`docs/archive/v3/`
