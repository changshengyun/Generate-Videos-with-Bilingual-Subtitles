# Current Task: CODEX-HYGIENE-001

- `STATE_REV: 2026-08-30.002`
- `TASK_REV: CODEX-HYGIENE-001.002`
- Stage state: `PASS / REPO_CONFIG_VERIFIED`
- Product status: `V4_RELEASED`
- Evidence ceiling: `REPO_CONFIG_VERIFIED`
- Device gate: `NO_DEVICE_ACTION`

## 1. 阶段目标

评估 `liby/dotfiles` 中针对 Codex 过度保留中间尝试、冗余注释和失真 PR 描述的方法，并把适合 LyricCaptioner 的最小规则与 repo Skill 引入当前仓库。上游个人环境、macOS、密钥、GitHub/GitLab 和无关语言规则不进入本项目。

## 2. 冻结验收矩阵

| 类别 | 冻结内容 |
|---|---|
| 主链路 | 新 Codex 任务从仓库根目录启动 → 自动读取根 `AGENTS.md` → 在收尾清理类请求中发现并加载 repo Skill → 最终注释、文档和变更说明只保留当前实现、非显然原因与可验证事实。 |
| 必须证据 | 记录上游仓库 URL 与精确 commit；OpenAI 官方文档确认 `AGENTS.md` 与 `.agents/skills` 的发现路径；Skill 通过 `quick_validate.py`；`git diff --check` 通过；精确 diff 只包含治理规则、repo Skill 和三份活动文档。 |
| 禁止事项 | 不复制上游无许可证的大段原文；不引入上游个人 dotfiles、macOS/Bash、密钥、Snowflake、Herdr、Oracle、GitHub/GitLab 专用内容；不修改 Android 业务代码、测试、依赖、模型、Prompt、产品路线；不运行构建、模拟器或真机；不 push。 |
| 退出状态 | 项目级规则与一个聚焦 Skill 均落地，结构验证和精确 diff 检查通过，且分析明确说明能力边界时，标记 `PASS / REPO_CONFIG_VERIFIED`。 |
| 未完成状态 | Skill 路径或格式无法被当前 Codex 发现时为 `BLOCKED`；需要扩大到全局 Codex 配置、外部 hooks 或大段上游复制时为 `HUMAN_DECISION`；仅完成分析但未落地时为 `PARTIAL_PASS`。 |

## 3. 允许范围

- `AGENTS.md`
- `.agents/skills/final-diff-hygiene/SKILL.md`
- `docs/DEVELOPMENT_ROADMAP.md`
- `docs/CURRENT_TASK.md`
- `docs/PROJECT_STATE.md`

## 4. 上游快照与适配原则

- Source: `https://github.com/liby/dotfiles`
- Inspected commit: `9bcb53abfc1c26bec1de918f9dd520430fe720ad`
- 直接采用的方法：注释只保留维护者需要的非显然原因；不把中间尝试写入最终代码、文档和提交说明；PR/MR 只写最终行为和 diff 无法表达的重要理由。
- 改写为本项目 Skill 的方法：仅对当前变更表面进行收尾清理；删除实现镜像、历史尝试和失效对比；保留可验证约束与失败条件；验证成本按 S0/S1/S2 和冻结矩阵决定。

## 5. 实际结果与证据

- Checkpoint: `14cad71`（冻结本矩阵并统一三份活动文档）。
- `AGENTS.md` 新增最终状态卫生规则：注释只写非显然且可验证的原因；最终文档和 PR/MR/提交说明不保留中间尝试；常规验证叙述服从模板与验收矩阵；清理不授权行为变更或扩大审查。
- 新增 `.agents/skills/final-diff-hygiene/SKILL.md`，仅处理当前变更表面的注释、文档和变更说明，不承担正确性审查或行为修改。
- OpenAI 官方文档确认：仓库根 `AGENTS.md` 会进入项目指令链；Codex 会从仓库路径 `.agents/skills` 发现 repo Skill。当前 `AGENTS.md` 为 25,432 bytes，低于文档所述默认 32 KiB 指令上限。
- `quick_validate.py .agents/skills/final-diff-hygiene`：`Skill is valid!`。
- `git diff --check`：通过。
- `git fetch --prune` 后 checkpoint 状态相对 `origin/main` 为领先 1 / 落后 0；本阶段最终提交后为领先 2 / 落后 0。
- 未运行 Android 测试或构建，未操作模拟器或真机；本阶段没有产品代码、依赖、模型、Prompt 或产品路线变化。

## 6. 能力边界

本方案可以持续提高新任务中的输出约束和收尾一致性，但属于模型指令与可调用工作流，不是强制执行器：它不能保证每次都自动触发 Skill，不能改写已经启动任务的旧指令链，也不能单独阻止越权文件写入。需要机器级阻断时仍应使用已安装的 Stop That Shit Guard 或 Codex 权限策略；本阶段不修改这些全局设施。

## 7. 下一允许动作

阶段完成，恢复为无活动任务；V5 仍未启动。下一次从仓库根目录新建 Codex 任务即可观察项目规则和 repo Skill 的实际发现/触发行为。
