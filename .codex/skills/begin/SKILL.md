---
name: begin
description: 从 Primary/root 协调壳初始化或恢复 LyricCaptioner 的内部 Brain–Limbs 工作流。用户显式调用 $begin、新开项目对话、要求启动或恢复 Brain、恢复已有阶段或提供明确开发目标时使用；创建或复用唯一内部 Brain，禁止 root 直接派 Limbs，并区分内部 Agent 与明确要求的独立 Codex 侧边栏任务。
---

# Begin

把本技能作为 LyricCaptioner 新对话的统一入口。Primary/root 只承担消息转发和 Agent 生命周期协调；它不是 Brain，也不是第三种正式业务角色。

## Primary/root 冷启动

1. 工作目录固定为 `D:\DevEnv\Projects\lyric-captioner-android`。
2. root 只读取足以定位仓库、`.codex/config.toml`、`.codex/agents/brain.toml`、`docs/CURRENT_TASK.md`、`docs/PROJECT_STATE.md` 和 `Active Brain canonical path` 的内容。
3. 将持久化 canonical path 与当前 Agent tree 的 runtime liveness 分开判断。path 存在且 live 时复用：idle Brain 使用 `followup_task`，running Brain 使用 `send_message`。
4. path 不存在时使用 `spawn_agent` 和默认 task name `lyriccaptioner_brain`；path 存在但不 live 时使用 path 末段作为默认 task name 创建 replacement Brain。replacement Brain 负责受控 reconciliation，root 不得代替它处理状态。
5. 将用户目标、持久化 canonical path 和 liveness 证据交给 Brain。由 Brain 完整读取 `../lyriccaptioner-brain/SKILL.md`、治理协议、三份活动文档和实时 Git，并核对状态、矩阵、工作树与下一动作。
6. 配置文件只证明 Brain/Limbs 已注册；持久化 path 也不能证明实例 live。

root 不得将自己宣布为 Brain，不得分析产品或技术根因，不得写活动文档、直接验收或直接创建 Limbs。状态冲突由 Brain 返回 `STATE_RECONCILIATION_REQUIRED`。

## 工具路由

- `spawn_agent`：root 创建内部 Brain；Brain 创建自己的 Limbs 子级。
- `followup_task`：仅用于触发 live 且 idle 的活动 Brain。
- `send_message`：仅用于向 live 且 running 的活动 Brain 投递当前轮次消息。
- `create_thread`：只有用户明确要求“独立 Codex 窗口”“独立 Codex 侧边栏任务”或“独立 Codex 任务”时，才由 root 调用；Brain 永远不得调用它。
- “开一个 Brain”“启动 Brain”“恢复 Brain”以及单说“Brain 窗口”都路由到内部 Brain spawn/reuse，不得隐式 `create_thread`。
- root 绝不直接 `spawn_agent` 创建 Limbs。

## Brain 决定是否开发

- 只有 `$begin`，且当前为 `Active stage: NONE`、`MATRIX_REQUIRED` 或 `PAUSED_BY_USER`：Brain 只完成初始化并报告状态，不创建验收矩阵，不启动 Limbs。
- 已有活动阶段和冻结矩阵：直接按现有矩阵恢复执行，无需用户二次确认。
- 用户在调用 `$begin` 时同时明确给出新阶段或开发目标：Brain 可依据活动 Roadmap、项目约束和该授权建立唯一编号验收矩阵；先向用户报告矩阵、Limbs 拆分和模型配置，再立即派工。
- 新目标与现有产品边界冲突、涉及架构替换、权限、安全、破坏性操作或范围扩张时，进入 `HUMAN_DECISION`。

## Brain–Limbs 执行

1. Brain 负责证据解释、跨层根因分析、路线与架构决策、冻结矩阵、任务拆分、活动文档和最终验收，不执行普通开发工作。
2. Brain 使用 `spawn_agent` 创建自己的 `Limbs-功能名` 子级，并为其指定 `TASK_ID`、`PARENT_BRAIN`、所有权和证据合同。
3. Limbs 负责原始证据、Brain 指定的判别实验、实现、测试、构建和设备验证；在已选方案内自主修复普通编译、测试、构建、配置和局部实现错误。
4. ANR、OOM、并发、架构、数据一致性和安全问题默认返回 Brain 裁决；Limbs 不作跨层最终结论。
5. Brain 通过等待机制只接收含 `PARENT_BRAIN` 的结构化终态，不轮询过程日志、逐轮记录或中间推理。
6. 实现与集成完成后创建只读 `Limbs-验收`，独立核对冻结矩阵、真实集成差异和证据等级。
7. Brain 作出最终裁决：
   - `ACCEPTED`：简短同步三份活动文档并创建中文阶段提交。
   - `REJECTED`：把具体修复重新交给 `Limbs-功能名`，满足门禁后重新验收。
   - `HUMAN_DECISION`：向用户完整给出问题、背景、约束、证据、选项、影响、风险、推荐、不决定后果和仍可继续工作。

## 文档最小化

- 普通开发只维护 `CURRENT_TASK.md`、`PROJECT_STATE.md` 和 `DEVELOPMENT_ROADMAP.md`。
- Limbs 不写项目管理文档，只返回结构化结果。
- Brain 只在阶段开始、状态真实变化和阶段结束时简短更新三份活动文档。
- 不创建 `REQUIREMENTS.md`、`TECH_OPTIONS.md`、`SPIKE_PLAN.md`、`ENVIRONMENT_REPORT.md` 或同类普通任务文档。
- `docs/archive/` 只在活动文档过长或阶段关闭需要一次性压缩时更新。

## 模型与启动报告

- 报告 Brain 与 Limbs 的模型和推理强度，并区分配置值与运行时确认值。
- Brain 默认避免 `gpt-5.6-sol / high`；使用项目当前较低强度配置。
- 启动报告保持简短：

```text
BEGIN_STATUS: READY | MATRIX_REQUIRED | STATE_RECONCILIATION_REQUIRED | HUMAN_DECISION
ACTIVE_BRAIN:
STATE_REV:
CURRENT_STAGE:
STAGE_STATE:
GATE:
GIT_SNAPSHOT:
PROTECTED_WORKTREE:
MODEL_CONFIG:
LIMBS_RUNTIME:
DISPATCHED_LIMBS_AND_PARENT:
NEXT_ACTION:
```

报告后按门禁继续；只有没有活动矩阵、存在外部阻断或需要人为决定时才停下。

## 安全

- 保留既有修改和未跟踪内容，不得 reset、clean、批量暂存、强制 checkout、重写历史或 push。
- 不连接未授权设备，不执行未授权的破坏性操作。
- 编译、mock、模拟器或低等级证据不得升级为真机或正式产品 PASS。
- 不把 API Key、Authorization、解密密钥、完整请求响应或私人路径写入聊天、日志、源码、测试夹具或活动文档。
