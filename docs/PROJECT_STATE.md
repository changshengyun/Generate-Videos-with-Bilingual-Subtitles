# LyricCaptioner Project State

- `STATE_REV: 2026-08-30.007`
- Repository: `D:\DevEnv\Projects\lyric-captioner-android`
- Branch: `dev`（跟踪 `origin/dev`;由精简基线 `716f2f4` 建立）
- Release: `v5.0.0` 已发布到开发分支（`versionName 5.0.0 / versionCode 5000`）
- Current task: `V5-RELEASE-001 / PASS / BUILD_VERIFIED / PUSHED`
- Product status: `V5_DEV_PUBLISHED`
- Evidence ceiling: `BUILD_VERIFIED`（本阶段不操作设备;V4.4 既有设备证据不改写）
- Last state sync: 2026-08-30

## 当前决定

- 当前生效治理体制为 AGENTS.md §9 阶段契约；§7 Multi-Agent 体制仅在用户显式启用时生效，其引用的治理文档（`MULTI_AGENT_WORKFLOW.md`、`DECISIONS.md`、`governance/*` 等）已只读归档于 `docs-BK/`，启用前须先恢复或重新获得用户批准。
- 用户已决定把精简后的 V4.4 基线作为 V5.0.0 发布到远端 `dev`;V3/V4 作为历史阶段保留。
- V3 未完成的生产 base 验证固定记录为 `V3-ASR-DIAG-001 / PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`，不补做、不改写为 PASS，也不阻塞 V4。
- 唯一产品主链路为：相册导入 → 一次点击开始识别 → 本地 Whisper → 自动 AI 增强 → 自动进入字幕编辑 → 添加或编辑字幕 → 预览并导出。
- V4 已完成 `V4-FLOW-001`、`V4-EDITOR-001`、`V4-UI-001` 与发布前设备验收；后续任何新的真机操作仍需单独获得明确授权。
- `V5-RELEASE-001` 获得明确 push 授权,仅限新建 `origin/dev`;不得 push main、创建标签或 force push。
- `V4-SIMP-001` 是插入的代码简化阶段：仅拆分 `ui/EditorScreen.kt` 为同包多文件并修复 4 处乱码文案，纯机械搬移、行为不变；不改变任何架构、依赖、模型或合同。
- 不新增依赖，不更换模型，不修改 AI Prompt、歌词检索、响应合同或 cue 时间戳合同。
- 简单变更按 S0 只检查精确 diff；普通功能按 S1 聚焦验证；复杂故障按 S2 证据优先。三次修复失败后冻结修改并只运行一个最小判别实验。
- `CODEX-HYGIENE-001` 已引入针对最终 diff、注释和变更说明的项目级规则与 `.agents/skills/final-diff-hygiene`；未恢复 V4.4 已迁出的 Multi-Agent 角色体系。该阶段当时尚未启动 V5，现已由 `V5-RELEASE-001` 进入 V5 发布基线。
- `V4-SIMP-002` 已完成:只删除已证明无生产消费者的仓库负担、测试遗留、死代码、直接依赖和完全失效文档；未改变产品行为、技术路线、持久化、安全或运行时合同。
- `V5-RELEASE-001` 只升级版本身份与发布文档,不新增 V5 功能,不改变应用包名、业务源码或运行时合同。

## 当前验收门禁

- `V4-FLOW-001` 与 `V4-EDITOR-001` 已达到 `PASS / COMPONENT_VERIFIED`。
- `V4-UI-001` 达到 `PARTIAL_PASS / COMPONENT_VERIFIED / SIMULATOR_BLOCKED`；Pixel 8 因已有 snapshot pending 无法启动，未取得新截图或 instrumentation 证据。
- 收尾矩阵：ASR Python 6/6；JVM 352/352；lint、普通/Native Debug、普通/Native AndroidTest 构建全部成功。
- `V4-SIMP-001` 已完成并达到 `PASS / COMPONENT_VERIFIED`：`EditorScreen.kt` 拆分为同包 8 文件（2365 → 290 行，主入口保留）并修复 4 处乱码文案；41 处 `private` → `internal`；2 个源码契约测试改为读取 ui 目录拼接文本，断言强度不降低；JVM 352/352、lint、普通/Native Debug 与 AndroidTest 构建、ASR Python 6/6 全部通过；纯机械搬移，行为与语义契约完全不变。
- `CODEX-HYGIENE-001` 达到 `PASS / REPO_CONFIG_VERIFIED`：repo Skill 通过 `quick_validate.py`，`git diff --check` 通过，且 `AGENTS.md` 保持在默认 32 KiB 指令上限以内。
- `V4-SIMP-002` 达到 `PASS / BUILD_VERIFIED`:跟踪项 `126 → 116`,工作树字节 `115,160,329 → 27,124,073`,Kotlin `15,905 → 14,235` 行,Markdown `1,678 → 1,610` 行;ASR Python 6/6、lint、普通/Native Debug 与普通/Native AndroidTest 构建均成功;测试任务如实为 `NO-SOURCE`。
- `V4-SIMP-002` 当前禁止真机操作，因此本阶段最高记录 `BUILD_VERIFIED`；这不改写 V4.4 发布前已经取得并本地留存的设备与真实 API 证据。
- `V5-RELEASE-001` 达到 `PASS / BUILD_VERIFIED / PUSHED`：已同步 `5.0.0 / 5000`；ASR Python 6/6，`testDebugUnitTest` 成功且为 `NO-SOURCE`，lint、普通/Native Debug、普通/Native AndroidTest 构建成功；Native APK 为 397,816,816 bytes，含 arm64-v8a 与 x86_64 Whisper 库；发布提交 `74a2c40` 已推送 `origin/dev`。
- 阶段实现与构建成功不等于完整 V4 产品 PASS。

## 受保护工作树

保留所有既有未跟踪或脏本地内容，包括 `.env`、`dist/`、`tools/opus-mt-en-zh/` 和未知内容（`.emulator-test-assets/` 与 `docs/debug/` 已于 V4.4 移出仓库，本地副本仍受保护）。不得 reset、clean、覆盖或批量暂存;本阶段只允许按用户授权推送 `origin/dev`。

## V4.4 收尾记录（2026-08-29）

- V4 全部功能（V4.1 编辑套件、V4.2 AI 增强链路与 SRT 导出、V4.3 一致性恢复、V4.3.x 修复与 SearchScheduler 双路检索）已完成并通过验收，验收证据留存于本地 `test-artifacts/`（V4.4 起不入库）。
- 临时调试脚本、截图、UI dump、trace 已从 `tools/` 抽离至 `test-artifacts/debug-session-v4.3/`；超大原始 logcat 移入 `test-artifacts/device-capture/raw-logs/` 并加入 `.gitignore` 仅本地保留。
- 版本号升至 `versionName 4.4.0 / versionCode 4400`，README 重写为项目入口与发布说明，打标签 `v4.4.0` 并推送。
- 仓库精简：原 Multi-Agent `.agents/` 与 `.codex/skills/` 迁入 DEV-SKILL 仓库；`CODEX-HYGIENE-001` 后仅重新加入独立的 `.agents/skills/final-diff-hygiene` repo Skill，不恢复角色体系。`deliverables/`、`docs-v2/`、`docs/archive/`、`docs/debug/`、`.kotlin/`、`.emulator-test-assets/` 仍已移出仓库（均可从 git 历史找回）。
- 二次精简：`app/src/test/`、`app/src/androidTest/` 测试代码移出仓库；`test-artifacts/` 改为仅本地保留并加入 `.gitignore` 不入库；历史测试与证据均可从 git 历史（`v4.4.0`）找回。
- 本记录在当时结束 V4；后续已由 `V5-RELEASE-001` 单独立项进入 V5，不改写上述 V4 历史证据。


## 权威资料

- 路线：`docs/DEVELOPMENT_ROADMAP.md`
- 唯一活动任务与冻结矩阵：`docs/CURRENT_TASK.md`
- 既有 AI 需求与路线背景：`docs/REQUIREMENTS.md`、`docs/TECH_OPTIONS.md`、`docs/ENVIRONMENT_REPORT.md`、`docs/SPIKE_PLAN.md`
- V3 历史证据：已于 V4.4 移出仓库（原 `docs/archive/v3/`），需要时从 git 历史（`v4.4.0` 及更早）找回
