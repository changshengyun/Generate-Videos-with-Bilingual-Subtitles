# LyricCaptioner V4 Project State

- `STATE_REV: 2026-08-30.001`
- Repository: `D:\DevEnv\Projects\lyric-captioner-android`
- Branch: `main`（V4.4 收尾后与 `feature/ai-enhancement-search-scheduler` 快进合并）
- Release: `v4.4.0`（V4 系列功能全部完成并通过验收的正式发布）
- Current task: `CODEX-HYGIENE-001 / MATRIX_DEFINED / IN_PROGRESS`（发布后治理配置，V5 阶段未启动）
- Product status: `V4_RELEASED`
- Evidence ceiling: `DEVICE_VERIFIED`（真机识别、真实 API 增强、三视频沙箱验证证据留存于本地 `test-artifacts/`，V4.4 起不入库）
- Last state sync: 2026-08-30

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
- `CODEX-HYGIENE-001` 只允许引入针对最终 diff、注释和变更说明的项目级规则与一个 repo Skill；不恢复 V4.4 已迁出的 Multi-Agent 角色体系，也不启动 V5。

## 当前验收门禁

- `V4-FLOW-001` 与 `V4-EDITOR-001` 已达到 `PASS / COMPONENT_VERIFIED`。
- `V4-UI-001` 达到 `PARTIAL_PASS / COMPONENT_VERIFIED / SIMULATOR_BLOCKED`；Pixel 8 因已有 snapshot pending 无法启动，未取得新截图或 instrumentation 证据。
- 收尾矩阵：ASR Python 6/6；JVM 352/352；lint、普通/Native Debug、普通/Native AndroidTest 构建全部成功。
- `V4-SIMP-001` 已完成并达到 `PASS / COMPONENT_VERIFIED`：`EditorScreen.kt` 拆分为同包 8 文件（2365 → 290 行，主入口保留）并修复 4 处乱码文案；41 处 `private` → `internal`；2 个源码契约测试改为读取 ui 目录拼接文本，断言强度不降低；JVM 352/352、lint、普通/Native Debug 与 AndroidTest 构建、ASR Python 6/6 全部通过；纯机械搬移，行为与语义契约完全不变。
- `docs/CURRENT_TASK.md` 已冻结 `V4-SIMP-001` 验收矩阵；阶段内不操作真机或模拟器。
- 当前禁止真机操作，最高只能记录 `COMPONENT_VERIFIED`；真实 AI、真实设备、真实导出与回放证据仍未获得。
- 阶段实现与构建成功不等于完整 V4 产品 PASS。

## 受保护工作树

保留所有进入 V4 前的未跟踪或脏本地内容，包括 `.env`、`dist/`、`tools/opus-mt-en-zh/` 和未知内容（`.emulator-test-assets/` 与 `docs/debug/` 已于 V4.4 移出仓库，本地副本仍受保护）。不得 reset、clean、覆盖、批量暂存或 push。

## V4.4 收尾记录（2026-08-29）

- V4 全部功能（V4.1 编辑套件、V4.2 AI 增强链路与 SRT 导出、V4.3 一致性恢复、V4.3.x 修复与 SearchScheduler 双路检索）已完成并通过验收，验收证据留存于本地 `test-artifacts/`（V4.4 起不入库）。
- 临时调试脚本、截图、UI dump、trace 已从 `tools/` 抽离至 `test-artifacts/debug-session-v4.3/`；超大原始 logcat 移入 `test-artifacts/device-capture/raw-logs/` 并加入 `.gitignore` 仅本地保留。
- 版本号升至 `versionName 4.4.0 / versionCode 4400`，README 重写为项目入口与发布说明，打标签 `v4.4.0` 并推送。
- 仓库精简：`.agents/` 与 `.codex/skills/` 迁入 DEV-SKILL 仓库；`deliverables/`、`docs-v2/`、`docs/archive/`、`docs/debug/`、`.kotlin/`、`.emulator-test-assets/` 移出仓库（均可从 git 历史找回）；相关文档引用已同步更新。
- 二次精简：`app/src/test/`、`app/src/androidTest/` 测试代码移出仓库；`test-artifacts/` 改为仅本地保留并加入 `.gitignore` 不入库；历史测试与证据均可从 git 历史（`v4.4.0`）找回。
- 后续若进入 V5 新阶段，另行立项；当前不提前声明任何 V5 内容。


## 权威资料

- 路线：`docs/DEVELOPMENT_ROADMAP.md`
- 唯一活动任务与冻结矩阵：`docs/CURRENT_TASK.md`
- 既有 AI 需求与路线背景：`docs/REQUIREMENTS.md`、`docs/TECH_OPTIONS.md`、`docs/ENVIRONMENT_REPORT.md`、`docs/SPIKE_PLAN.md`
- V3 历史证据：已于 V4.4 移出仓库（原 `docs/archive/v3/`），需要时从 git 历史（`v4.4.0` 及更早）找回
