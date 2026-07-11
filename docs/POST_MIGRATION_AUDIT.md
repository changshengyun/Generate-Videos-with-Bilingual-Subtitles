# LyricCaptioner 迁移后恢复审计

审计日期：2026-07-12（Asia/Shanghai）
审计对象：`D:\DevEnv\Projects\lyric-captioner-android`
审计方式：只读检查 Git、文档、代码结构和嵌套仓库；未构建、未运行测试、未修改业务代码或技术路线。

## 最终判断

**C — 需要处理代码状态。当前不能直接进入 SPIKE 阶段。**

独立仓库边界已经建立，架构资料和技术探针计划基本具备，但迁移快照尚未形成可审查、可回滚的干净 Git 基线：项目级治理文件和主要权威文档仍未跟踪，`third_party/ffmpeg-kit` 仍保留 5 个未提交实验修改。先完成迁移状态收口，不需要重新评估架构，也不应开始功能开发。

## 审计证据基线

- 新 Git 根：`D:\DevEnv\Projects\lyric-captioner-android`
- 分支：`migration/lyric-captioner-history`
- HEAD：`05dbae7`（`chore: capture current project state`）
- 提交数：1
- remote：未配置
- 父仓库归档源：`D:\DevEnv\Work\lyric-captioner-android`
- 迁移 staging：`D:\DevEnv\Projects\.migration-staging\source-20260711-235821`
- 当前工作树：dirty

本报告只确认当前文件系统事实。已有文档中记录的构建和设备结果没有在本轮重跑，因此仍属于历史证据，不升级为本轮验证事实。

## 1. Git 边界

### 结论

**独立边界已建立，但迁移提交尚未闭合。**

已确认：

- 在新目录执行 `git rev-parse --show-toplevel` 返回新项目目录，不再返回 `D:\DevEnv\Work`。
- 新仓库只承载 LyricCaptioner 项目；父仓库中的 `health-assistant`、`codex-debug-log` 不在新仓库范围内。
- 当前 HEAD 保留项目代码快照，主应用源码与父仓库归档源的已跟踪文件抽样/哈希核对未发现内容差异；18 个现有 docs 文件与来源对应文件核对无差异。
- `third_party/ffmpeg-kit` 仍是模式 `160000` 的 gitlink；`third_party/whisper.cpp` 是独立嵌套仓库，但不在父索引中作为 gitlink 跟踪。

未满足项：

- `AGENTS.md`、`.agents/` 和 17 个迁移后的审计/架构文档路径仍为 untracked，故新 HEAD 不能独立恢复当前治理与项目状态。
- 仓库没有 remote；这不阻止本地 SPIKE，但当前没有异机恢复或远端审查能力。
- 当前仅有一个快照提交，无法提供迁移后治理文档与 ffmpeg-kit 实验状态的提交级审查边界。

## 2. 文档完整性

### 结论

**内容覆盖基本完整，持久化状态不完整，且部分迁移元数据已过期。**

已存在的门禁文档包括：

- `PROJECT_BRIEF.md`
- `REQUIREMENTS.md`
- `ENVIRONMENT_REPORT.md`
- `CURRENT_SYSTEM_MAP.md`
- `CURRENT_TECH_STACK.md`
- `FEATURE_STATUS.md`
- `TECH_OPTIONS.md`
- `SPIKE_PLAN.md`
- `ARCHITECTURE_DECISION.md`
- `PROJECT_STATE.md`
- `NEXT_TASK.md`
- 审计与 handoff 文档

主要问题：

- `PROJECT_STATE.md` 仍把 Git 根、分支和 HEAD 写为迁移前的 `D:\DevEnv\Work`、`chore/adopt-codex-workflow`、`618f36e`。
- `GIT_MIGRATION_PLAN.md` 仍标记 `NOT EXECUTED`，`GIT_MIGRATION_APPROVAL.md` 仍标记 `AWAITING USER APPROVAL`，与新独立仓库已经存在的事实冲突。
- `PROJECT_STATE.md` 将唯一下一步写成直接等待批准 Media3 证据复现，但没有插入“迁移状态收口”这一前置门禁。
- 上述关键文档目前大多未被 Git 跟踪，不能作为可重复恢复的仓库事实来源。

因此文档内容不足不是首要分级原因；首要阻断是这些文档尚未形成迁移后提交基线。

## 3. 当前代码状态

### 主应用

- Kotlin/Compose 单应用结构完整，核心目录包括 `ui`、`processing`、`captions`、`audio`、`project`、`model` 和可选 JNI/CMake 层。
- 主应用已跟踪文件相对 HEAD 无普通文本 diff。
- 本轮未运行 Gradle、单元测试、APK 构建或设备验收，避免生成状态或越过只读审计范围。
- 历史文档记录 Debug/Release 单元测试及普通/native Debug 构建曾通过；这些结果的基线仍是迁移前路径和 2026-07-11 环境。

### 嵌套依赖

- `third_party/whisper.cpp`：detached HEAD，工作树干净。
- `third_party/ffmpeg-kit`：detached HEAD，父索引指向 `d6be56d7...`，内部有 5 个修改文件，共 11 行增加、9 行删除。

当前代码状态不适合直接开展 SPIKE，因为新增探针证据会与迁移遗留的未提交状态混合，破坏 checkpoint、diff 和回滚边界。

## 4. 技术路线状态

### 结论

**无需重新选型；路线 A“保守诊断并延续”仍是当前唯一推荐路线。**

当前生效路线为 Kotlin/Compose + Media3 + Android MediaCodec + ML Kit + 可选 whisper.cpp JNI。FFmpegKit 未接入 app 的 Gradle 依赖或运行调用链。现有资料已经：

- 给出硬约束和否决项；
- 比较保守延续、平衡加固、实验替代三条路线；
- 说明实验替代当前违反“不直接换栈”的约束；
- 将 Media3 导出失败界定为实现/集成故障，尚无证据上升为架构不可行。

因此不选 D。迁移本身没有产生需要更换语言、框架、构建系统或核心处理链路的新证据。

## 5. ffmpeg-kit 修改状态

`third_party/ffmpeg-kit` 当前修改如下：

| 文件 | 状态/意图线索 |
|---|---|
| `android/ffmpeg-kit-android-lib/build.gradle` | `versionCode` 从 240600 改为 260600，并有空行变化 |
| `scripts/android/cpu-features.sh` | 已修改 |
| `scripts/function-android.sh` | 已修改 |
| `scripts/function.sh` | 已修改 |
| `scripts/main-android.sh` | 已修改 |

审计判断：

- 这些修改已随工作树复制到新仓库，但没有形成独立提交、补丁清单或正式 submodule 配置。
- 它们属于未完成的 FFmpegKit 构建实验，不属于当前 app 生效技术栈，也不是可用 fallback。
- 不应在 SPIKE 前删除、继续修改、重新初始化或接入 app。
- 应先以可审查方式冻结其 HEAD、完整 diff、用途和处置决定；可以保留为隔离实验状态，但不能继续只依赖 dirty detached 工作树保存。

## 6. 当前风险

| 风险 | 等级 | 影响 |
|---|---|---|
| 迁移治理文件和权威 docs 未跟踪 | 高 | 新仓库 HEAD 无法恢复当前门禁、约束和下一步 |
| ffmpeg-kit 5 个实验修改未持久化 | 高 | checkout、清理或错误操作可能丢失实验；后续 diff 被污染 |
| `PROJECT_STATE` 与迁移后 Git 基线不一致 | 高 | 后续 Agent 可能回到父仓库或跳过迁移收口 |
| Media3 指定模拟器导出已知失败 | 高 | 核心 MP4 硬边界尚未满足，但属于待探针定位的实现风险 |
| 无当前连接设备和新 logcat | 高 | 无法确定 codec/Surface/OpenGL/input/environment 最早失败层 |
| 真机 Whisper、ML Kit 离线、SAF、5 分钟资源和许可未验 | 高/中 | MVP 与发布可行性仍未证明 |
| 无 remote | 中 | 不影响本地实验，但降低灾难恢复与协作审查能力 |
| AGP 8.7.3/compileSdk 36 与 SDK XML 漂移 | 中 | 当前历史构建成功，未来重复构建存在兼容风险 |

## 7. 是否可以进入 SPIKE 阶段

**当前不可以。**

进入 SPIKE 前必须满足以下最小前置条件：

1. 在新独立仓库中形成明确的迁移收口 checkpoint，使 `AGENTS.md`、`.agents/`、权威 docs 和迁移后项目状态可由 Git 恢复。
2. 更新 `PROJECT_STATE.md`、迁移计划/审批状态中的 Git 根、分支、HEAD、当前门禁和唯一下一步，消除迁移前陈述。
3. 对 ffmpeg-kit 的 5 个修改形成可恢复证据和明确隔离决定；不得在本步骤继续开发或接入。
4. 收口后确认主仓库工作树只剩被明确接受的状态，并建立 SPIKE 前 checkpoint。
5. 用户批准首个探针范围。建议首个动作仍是 `NEXT_TASK.md` 所述的 Media3 指定模拟器单次证据复现；只采集证据，不修复、不换栈。

满足 1-4 后，项目可进入架构审批下的 SPIKE 执行准备；满足第 5 项后才可实际运行探针。

## 分类排除

- **不选 A**：迁移后 Git/ffmpeg-kit 状态尚未收口。
- **不选 B**：虽然文档需要更新，但还存在未提交嵌套代码状态，问题超出纯文档补充。
- **选择 C**：需要先处理仓库内代码/依赖工作树状态与迁移 checkpoint。
- **不选 D**：没有新证据否定现有技术路线；当前故障仍应先通过最小探针定位。
