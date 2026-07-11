# Git 仓库边界迁移审批报告

状态：`EXECUTED — HISTORICAL APPROVAL RECORD`

> 迁移已于 2026-07-12 完成，当前独立仓库为 `D:\DevEnv\Projects\lyric-captioner-android`。以下内容保留为迁移审批历史，不是当前待执行清单。

## 当前结论

推荐：**方案 A，拆分独立仓库**。

原因：

1. 一个产品对应一个 Git/Codex/AGENTS/docs 生命周期，操作边界明确。
2. checkpoint、diff、review 和回滚只覆盖当前项目。
3. 当前父仓库仅 1 个提交，使用 `git subtree split` 的历史提取范围容易验证。
4. 可先复制到 staging 并保持父仓库不变，迁移失败时回滚成本低。

不推荐把方案 C（补 `.gitmodules`）作为最终方案。它只能修复 gitlink 映射异常，不能消除多项目边界耦合。

## 已确认风险

- 根 `.gitmodules` 缺失，但索引含 `codex-debug-log`、`health-assistant` 和 `ffmpeg-kit` 三个 gitlink。
- 当前工作树 dirty；只过滤 HEAD 会遗漏未提交修改和未追踪文档。
- `ffmpeg-kit` 内有 5 个修改文件，不能执行自动 submodule update/checkout。
- `health-assistant` 触发 Git dubious ownership，尚未完成内部仓库审计。
- 父 `docs/` 不在项目目录前缀内；`subtree split` 不会自动把它们纳入项目历史。
- `D:\DevEnv\Projects` 尚不存在；任何创建和切换都需要批准。

## 推荐执行边界

第一轮获批后仍只执行：备份、临时 clone、staging 提取、复制、校验。保持：

- `D:\DevEnv\Work` 原目录不移动；
- 父 `.git` 不删除；
- 父索引和 gitlink 不修改；
- 不配置远程、不 push；
- 不修改 Android/Kotlin/C++/Gradle 业务或构建文件。

staging 验证通过后，再提交第二次“正式切换/父仓库收尾”审批。

## 需要用户批准事项

请逐项批准或拒绝：

| 编号 | 审批项 | 推荐默认值 |
|---|---|---|
| A1 | 创建 `D:\DevEnv\Projects` 和 migration staging 目录 | 批准 |
| A2 | 创建完整文件备份、父仓库 bundle、嵌套仓库证据包 | 批准 |
| A3 | 使用 `git subtree split` 提取 `lyric-captioner-android` 提交历史 | 批准 |
| A4 | 将当前项目工作状态复制到 staging，并生成差异报告 | 批准 |
| A5 | 把父 `AGENTS.md` 与 `.agents/` 复制为项目级治理文件 | 批准，但需复核内容范围 |
| A6 | 把 LyricCaptioner 权威 docs 复制到新仓库 | 批准，按清单迁移 |
| A7 | 保持 `ffmpeg-kit` 原样隔离并备份 dirty 状态 | 批准 |
| A8 | 本轮处理/修复父 `.gitmodules` | 拒绝，延后 |
| A9 | 本轮迁移 `health-assistant` | 拒绝，另立任务 |
| A10 | 本轮移动 `codex-debug-log` | 拒绝，保持中央仓库 |
| A11 | 安装 `git filter-repo` 以重写 docs + code 联合历史 | 拒绝，除非明确要求 |
| A12 | 配置新 remote、push 或创建 GitHub 仓库 | 拒绝，另行审批 |
| A13 | 删除父仓库 `.git` 或从父索引删除项目 | **禁止，本轮不批准** |
| A14 | staging 验收后切换正式工作目录 | 第二次审批 |

## 建议审批语句

如同意推荐的第一轮安全范围，可回复：

```text
批准 A1-A7；拒绝 A8-A13；A14 等 staging 验收后再审批。
```

在收到明确批准前，`scripts/git-migrate-to-independent-repo.ps1` 只允许默认 `Check` 模式，禁止传入执行令牌。
