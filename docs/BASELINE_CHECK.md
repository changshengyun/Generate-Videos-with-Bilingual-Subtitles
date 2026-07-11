# Post-migration baseline check

检查日期：2026-07-12（Asia/Shanghai）
仓库：`D:\DevEnv\Projects\lyric-captioner-android`

## 结论

主应用源码相对 `05dbae7a2134de5e8af4765654586921a9ee9779` 无文本修改。本轮基线应提交项目治理文件、门禁 Skill、权威文档、迁移记录和 handoff，使独立仓库能够恢复当前工程约束与项目状态。

`third_party/ffmpeg-kit` 是无 `.gitmodules` 映射的历史 gitlink，内部保留 5 个未提交实验修改。其完整状态和 diff 已冻结在 `docs/FFMPEG_KIT_CHANGE_ANALYSIS.md`；嵌套工作树本轮保持原样，不纳入主应用技术路线。

## 应提交文件

- `AGENTS.md`
- `.agents/skills/evidence-first-debugging/**`
- `.agents/skills/project-architecture-gate/**`
- `.agents/skills/mvp-implementation-gate/**`
- `docs/PROJECT_BRIEF.md`
- `docs/REQUIREMENTS.md`
- `docs/ENVIRONMENT_REPORT.md`
- `docs/CURRENT_SYSTEM_MAP.md`
- `docs/CURRENT_TECH_STACK.md`
- `docs/FEATURE_STATUS.md`
- `docs/ARCHITECTURE_DECISION.md`
- `docs/TECH_OPTIONS.md`
- `docs/SPIKE_PLAN.md`
- `docs/PROJECT_STATE.md`
- `docs/NEXT_TASK.md`
- `docs/MID_PROJECT_AUDIT.md`
- `docs/POST_MIGRATION_AUDIT.md`
- `docs/GIT_MIGRATION_PLAN.md`
- `docs/GIT_MIGRATION_APPROVAL.md`
- `docs/handoffs/AUDIT_HANDOFF.md`
- `docs/handoffs/LEGACY_HANDOFF.md`
- `docs/reviews/REVIEW-UNSPECIFIED.md`
- `docs/BASELINE_CHECK.md`
- `docs/FFMPEG_KIT_CHANGE_ANALYSIS.md`

其中迁移计划、审批、旧 handoff 和未指定任务 review 是历史审计证据，不代表当前待执行动作；保留它们用于追溯，不删除历史文件。

## 不应提交文件

- `local.properties`：本机 Android SDK 路径。
- `.gradle/`、`app/build/`、`build/`、`.cxx/`、`.kotlin/`：Gradle/Kotlin/CMake 缓存或构建诊断产物。
- `.tool-downloads/`：本机下载缓存。
- `third_party/whisper.cpp/`：按现有 `.gitignore` 由脚本恢复的下载依赖。
- `models/`、`app/src/main/assets/models/`、`ggml-*.bin`、`*.gguf`：大型本地模型。
- IDE 配置、安装包和 capture 目录：按现有 `.gitignore` 排除。

## 临时文件与构建产物

- 当前被忽略：`.gradle/`、`.tool-downloads/`、`app/build/`、`local.properties`。
- `.kotlin/errors/*.log` 已被迁移快照错误地跟踪。它们属于构建诊断产物，不应成为新提交内容；本轮禁止删除历史文件，因此保持 HEAD 原状，后续如需清理必须另行批准并保留回滚点。
- 根目录及 `deliverables/` 中的 PNG、MP4、SRT、ASS 已被迁移快照跟踪。它们是历史设备/交付证据，不是本轮生成的临时文件；本轮不删除、不修改，也不据此提升产品验收状态。

## 迁移残留

- `third_party/ffmpeg-kit`：索引模式 `160000`，gitlink 为 `d6be56d7aec286eb3c292d6b23ff07a6b70d8693`，但根仓库没有 `.gitmodules`。嵌套仓库为 detached HEAD，并有 5 个未提交修改。
- 历史文档中存在迁移前路径 `D:\DevEnv\Work`、分支 `chore/adopt-codex-workflow` 和 HEAD `618f36e...`。这些内容在迁移计划、旧审计和 handoff 中作为历史事实保留；当前权威状态以 `docs/PROJECT_STATE.md` 为准。
- 新仓库未配置 remote。它不阻止本地 baseline，但异机恢复和远端审查能力仍缺失。

## Baseline commit 准入检查

- [x] 当前仓库根、分支和 HEAD 已核验。
- [x] 项目治理文件与权威文档已列入提交范围。
- [x] `docs/PROJECT_STATE.md` 已更新到独立仓库路径和迁移后门禁。
- [x] `ffmpeg-kit` 的 HEAD、5 个 diff、分类和隔离方案已形成可恢复文档证据。
- [x] 主仓库其余状态可解释；未修改业务代码、技术路线或历史产物。
- [x] 本清单及关联文档将由 `chore: establish post migration baseline` 提交形成 checkpoint。
