# Git 仓库边界迁移计划

状态：`EXECUTED — HISTORICAL MIGRATION PLAN`

> 迁移已于 2026-07-12 完成，当前独立仓库为 `D:\DevEnv\Projects\lyric-captioner-android`。下文保留迁移前证据、方案和回滚设计；其中旧路径、分支和 HEAD 是历史快照，不代表当前项目状态。

基线时间：2026-07-11（Asia/Shanghai）

## 1. 当前状态

### 1.1 已验证事实

- Git 根目录：`D:\DevEnv\Work`
- 当前分支：`chore/adopt-codex-workflow`
- 当前 HEAD：`618f36ee50ecfef7302faa974d6b0e9e494614b9`
- 父仓库总提交数：1；涉及 `lyric-captioner-android/` 的提交数：1；涉及 `docs/` 的提交数：1。
- 索引共 98 个路径。顶层主要范围为：`lyric-captioner-android/` 73 个、`.idea/` 9 个、`.agents/` 6 个、`docs/` 5 个，以及 `AGENTS.md`、`README.md` 和 3 个 gitlink。
- 当前未提交状态包括 5 个已追踪 docs 修改、多个未追踪审计文档、`health-assistant` 嵌套仓库修改状态、`third_party/ffmpeg-kit` 内 5 个文件修改，以及项目内未追踪交接文档。
- ignore 主要来自 `lyric-captioner-android/.gitignore`、`.idea/.gitignore` 和嵌套仓库自己的 `.gitignore`。只读枚举得到 9,804 个 ignored 路径，主体为 `.gradle/`、`.tool-downloads/`、`app/build/`、IDE 状态、本地模型和 native 构建产物。
- `D:\DevEnv\Projects` 当前不存在。
- 当前环境没有安装 `git filter-repo`。

### 1.2 当前结构

```text
D:\DevEnv\Work                 <- 父 Git 仓库
├── .git
├── AGENTS.md
├── .agents\
├── docs\                       <- 当前主要是 LyricCaptioner 状态文档
├── lyric-captioner-android\    <- 普通目录，不是独立仓库
│   └── third_party\ffmpeg-kit  <- gitlink + 独立工作树，当前 dirty
├── health-assistant\           <- gitlink + 独立 .git，当前状态未完全读取
└── codex-debug-log\            <- gitlink + 独立 .git
```

### 1.3 submodule 异常的最早确认边界

父索引把以下路径记录为模式 `160000` 的 gitlink：

| 路径 | 索引提交 | 工作树事实 |
|---|---|---|
| `codex-debug-log` | `813a30e0...` | 独立仓库，HEAD 与索引一致 |
| `health-assistant` | `369c0594...` | 存在 `.git`；因 Windows ownership / safe.directory 防护，内部状态未完整读取 |
| `lyric-captioner-android/third_party/ffmpeg-kit` | `d6be56d7...` | 独立仓库，HEAD 与索引一致，但有 5 个修改文件 |

根目录没有 `.gitmodules`。Git 能从索引看见 gitlink，却无法把 `codex-debug-log` 路径映射到 submodule 的 URL/name，因此 `git submodule status` 报：

```text
fatal: no submodule mapping found in .gitmodules for path 'codex-debug-log'
```

这是配置/索引不一致，不是 `codex-debug-log` 对象丢失的证据。由于命令在第一个异常路径即停止，不能据此声称其他 gitlink 的 submodule 配置有效。

### 1.4 当前风险

- Agent 从 `Work` 根运行时会同时看到多个产品、中央 debug log、全局工程规则和多个 dirty 边界，容易扩大 diff 与操作范围。
- 父仓库 docs 当前承担 LyricCaptioner 的权威状态，但目录位置不在项目边界内；拆分时若只提取项目目录，会遗漏这些文档。
- 未提交修改不属于任何提交历史。任何只按 HEAD 过滤的方案都会漏掉它们。
- `health-assistant` 的 ownership 异常必须在其独立迁移前单独审计；本计划不修改全局 `safe.directory`。
- `ffmpeg-kit` 是 dirty gitlink 且当前未集成。移动、删除或重新初始化都会有丢失实验修改的风险。
- 直接删除父 `.git` 会同时失去唯一父提交、索引关系和最简单回滚点，当前明确禁止。

### 1.5 为什么不适合 AI Agent 多项目开发

当前边界让一个 Agent 生命周期同时继承父 `AGENTS.md`、父 docs、多个产品状态和嵌套仓库变化。结果是 checkpoint、diff、Skill 触发、审查范围和持久状态都无法自然对应单一产品。理想边界应为：

```text
一个项目
= 一个 Git 仓库
= 一个 AGENTS.md
= 一个 docs 状态系统
= 一个 Codex 生命周期
```

## 2. 目标状态

```text
D:\DevEnv\Projects\
├── lyric-captioner-android\
│   ├── .git\
│   ├── AGENTS.md
│   ├── .agents\
│   ├── docs\
│   └── Android 项目代码
├── health-assistant\           <- 独立迁移任务
└── Codex-Dev-experience\       <- 独立迁移任务
```

`codex-debug-log` 继续作为独立中央仓库管理，不合并进任一产品仓库。父 `Work` 在所有项目验证完成前保留为只读归档和回滚源。

## 3. 方案比较

### 方案 A：拆分独立仓库（推荐）

#### LyricCaptioner

使用父仓库的不可变备份作为源，在临时 clone 中执行：

```powershell
git subtree split --prefix=lyric-captioner-android -b migration/lyric-captioner-history
```

再从该分支创建新仓库工作树。这样保留所有触及该前缀的提交，并把此前缀提升为新仓库根。当前只有一个相关提交，因此可验证范围清晰。

#### docs

父 `docs/` 中混有项目状态和工程级文件，不能无条件整体移动。审批后应按清单把 LyricCaptioner 权威文档复制到新仓库 `docs/`，并在新仓库创建单独的 `chore: migrate project state documents` 迁移提交。父仓库保留原文档和历史，不立即删除。

由于 docs 不在 `lyric-captioner-android/` 前缀内，`subtree split` 不会把 docs 的父仓库提交关系接到新项目历史中。若用户要求“项目代码与根 docs 保留为同一条重写历史”，需先安装并验证 `git filter-repo`，在临时 clone 中进行多路径过滤和路径重写；这属于另一条需要再次审批的高复杂度路线。

#### health-assistant

它已经有独立 `.git`，但父仓库只记录 gitlink。先解决或绕过只读 ownership 审计，核对 HEAD、remote、dirty 状态和对象完整性；再通过备份后复制/移动该完整仓库到 `Projects`。不在本次 LyricCaptioner 脚本中自动处理。

#### codex-debug-log

它已经是独立仓库且当前 HEAD 与父索引 gitlink 一致。保持在中央日志路径，或另行审批移动；绝不并入 LyricCaptioner。父 gitlink 的清理应等全部拆分完成后单独提交。

#### ffmpeg-kit

保留为嵌套独立仓库原样复制，先用 bundle/工作树备份保护其 HEAD 和 5 个未提交修改。是否在新仓库将它修复为正式 submodule、改为普通 vendored 目录或移除，属于单独审批；当前推荐“原样隔离、暂不清理”。

#### 未提交修改

迁移前同时保存：完整文件级备份、`git status --porcelain=v2`、tracked diff、staged diff、untracked 文件清单、ignored 文件清单、每个嵌套仓库的 HEAD/status/diff。提取提交历史后，把属于新项目的当前工作树内容覆盖到 staging 副本，人工核对差异，再创建明确的迁移快照提交。未提交内容不能声称为“历史已保留”，只能声称为“工作状态已备份并迁移”。

### 方案 B：继续 Monorepo

- Codex Skill 和 AGENTS 规则继续在多个产品间共享，产品专用门禁容易与工作区级规则混合。
- `git diff`、checkpoint 和审查默认覆盖所有项目；嵌套 gitlink 的 dirty 标记又隐藏了内部差异。
- 多项目并行开发需要额外 path filter、稀疏 checkout、CODEOWNERS 和 CI 路由，管理成本高于当前只有少量提交的拆分成本。
- 优点是无需历史重写和目录迁移；但不满足用户确定的“一项目一仓库”目标。

### 方案 C：只修复 Submodule

可能做法是补建 `.gitmodules` 并为 3 个 gitlink填写正确 URL，然后执行 `git submodule sync`。风险在于：

- `health-assistant` URL 尚未读取确认；猜测 URL 会制造错误映射。
- `ffmpeg-kit` 有未提交修改，任何 update/checkout 都可能影响现场。
- 修复 submodule 只能消除配置异常，不能解决 LyricCaptioner 与父 docs/AGENTS/生命周期耦合。
- 若随后仍执行方案 A，这次修复只是临时变更并增加迁移步骤。

结论：不建议把 C 当最终方案。仅当需要让父仓库在过渡期恢复标准 submodule 操作时，才在完整备份和独立审批后修复。

## 4. 历史保留技术比较

| 方法 | 历史完整性 | 风险 | 复杂度 | 本项目结论 |
|---|---|---|---|---|
| `git subtree split` | 完整保留指定前缀的相关提交；不含前缀外 docs | 低；不改源仓库 | 低 | 当前推荐 |
| 临时 clone + `git filter-repo` | 可保留并重写多个路径，最灵活 | 中；需要安装工具并精确设计 rename | 中高 | 只有要求 docs 同历史时采用 |
| 临时 clone + 内置历史过滤 | 可实现，但命令复杂且易误配 | 高 | 高 | 不推荐 |
| 手动复制 + `git init` | 不保留提交历史，只保留当前文件 | 低到中 | 低 | 不满足默认历史要求 |

所有过滤操作只能在备份后的临时 clone 中运行，禁止在 `D:\DevEnv\Work` 原仓库直接执行。

## 5. 分阶段迁移步骤

### 阶段 0：备份当前仓库

1. 记录父仓库 root/branch/HEAD/status、索引 gitlink、remote 和对象完整性。
2. 创建带时间戳的完整文件备份，不删除源文件；保留 ignored 与 untracked 文件。
3. 创建父仓库 `git bundle --all`。
4. 分别为可读取的嵌套仓库创建 bundle，并保存 dirty diff/清单。
5. 写入 `migration-state.json`，记录源、目标、备份路径、阶段和哈希。

### 阶段 1：冻结父仓库

停止对 `D:\DevEnv\Work` 的产品代码和 docs 写入。冻结是流程约束，不删除、不锁定、不修改父 `.git`。

### 阶段 2：提取 LyricCaptioner 历史

从备份 clone 执行 `git subtree split`，创建独立历史分支；验证提交数、树内容和根路径。

### 阶段 3：创建独立仓库

先在 `D:\DevEnv\Projects\.migration-staging\lyric-captioner-android` 创建 staging 仓库。目标正式目录必须不存在；脚本不得覆盖已有目录。

### 阶段 4：迁移当前文件与 docs

从文件备份恢复项目当前工作状态；按审批清单复制父 `AGENTS.md`、`.agents/` 与 LyricCaptioner docs。排除 `.git`、构建缓存和机器级下载；对 ignored 大文件另行核对是否确需迁移。人工检查后才允许提交迁移快照。

### 阶段 5：验证 Git 历史和工作状态

- `git fsck --full`
- 比较源/目标的项目文件清单与 SHA-256（排除 `.git` 和明确排除项）
- 验证原 HEAD 中项目树与新历史 HEAD 树等价
- 验证未提交项目修改在 staging 中有对应差异
- 验证 `git log --follow` 的代表性文件
- 验证新仓库 root、branch、status、remote（如批准配置）
- 单独确认 `ffmpeg-kit` HEAD 与 dirty diff 未丢失

本任务不是开发任务，不以 Gradle 构建作为迁移必需步骤；如用户要求，可在后续审批中增加只读/现有构建验证。

### 阶段 6：切换 Codex 工作流

用户复核 staging 报告后，才把 staging 提升为正式目录；确认项目级 `AGENTS.md`、`.agents/skills`、`docs/PROJECT_STATE.md` 全部在新根下。父仓库仍保留，直到所有独立仓库分别验收。

### 阶段 7：父仓库收尾（单独审批）

是否从父索引删除项目路径、修复/移除 gitlink、归档父仓库或最终删除父 `.git`，必须另开审批。默认不执行。

## 6. 回滚方案

迁移采用复制到 staging 的方式，源 `D:\DevEnv\Work` 在验收前不变，因此首选回滚是：停止脚本、保留报告、删除或隔离未启用的 staging 副本，然后继续使用原路径。

若后续已批准切换：

1. 停止在新仓库写入。
2. 用 `migration-state.json` 找到原始备份与 bundle。
3. 校验 bundle：`git bundle verify <bundle>`。
4. 将原 `D:\DevEnv\Work` 保留目录重新作为活动工作区；如原目录受损，从完整文件备份恢复到新的恢复目录，再用 bundle clone 恢复 Git 对象。
5. 逐个恢复嵌套仓库 bundle 和保存的 dirty patch；不使用 `reset --hard` 或 `clean -fd`。
6. 比较基线 HEAD `618f36e...`、状态清单和 SHA-256 后才宣布回滚完成。

禁止用恢复流程覆盖一个含新修改的现有目录。`-Restore` 必须要求精确备份路径、空目标目录和显式审批令牌。

## 7. 验收标准与未知项

### 通过标准

- 原仓库、完整备份和 bundle 均可读取。
- 新仓库根只包含 LyricCaptioner 范围及经审批的项目治理文件。
- 指定前缀的提交历史可达，代表性文件 history 可追溯。
- 当前 dirty 项目状态逐项对账，无文件被静默遗漏。
- 未执行父仓库 `.git` 删除、gitlink 清理或源目录移动。

### 尚需确认

- docs 是全部迁移还是按权威清单迁移。
- `ffmpeg-kit` 最终作为 submodule、vendored 目录还是保留隔离状态。
- `health-assistant` 的 remote、dirty 内容和 ownership 处理方式。
- `Codex-Dev-experience` 对应父仓库哪些文件。
- 是否要求根 docs 与代码共享重写后的提交历史；若是，需要审批安装 `git filter-repo`。
