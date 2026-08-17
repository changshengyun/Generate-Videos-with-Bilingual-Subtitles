# Brain–Limbs 多 Agent 开发协议

## 固定拓扑

```text
Primary/root coordinator shell
└─ unique internal Brain
   ├─ Limbs-开发
   ├─ Limbs-测试
   └─ Limbs-验收
```

- Primary/root 是非正式的运行时协调壳，只转发用户消息、创建或复用唯一活动 Brain、管理其生命周期。root 不分析产品或技术根因，不实现普通代码，不写活动文档，不作验收裁决，且绝不直接创建 Limbs。
- 正式业务角色只有 Brain 和 Limbs。Brain 是内部控制、分析、决策、状态和验收层；`Limbs-功能名` 是 Brain-owned 有界执行层。
- 不设置第三种正式角色。独立只读复核统一命名为 `Limbs-验收`，最终裁决由 Brain 作出。

## 冷启动和工具路由

1. root 只读足以确定仓库、Agent 注册和持久化 `Active Brain canonical path` 的信息，并从当前 Agent tree 单独确认 runtime liveness。
2. path 存在且 live 时复用：idle Brain 使用 `followup_task` 触发新一轮；running Brain 使用 `send_message` 投递到当前轮次。
3. path 不存在时，root 使用 `spawn_agent` 和默认 task name `lyriccaptioner_brain` 创建内部 Brain。
4. path 存在但不 live 时，root 使用 `spawn_agent` 创建 replacement Brain，默认 task name 为持久 path 的末段；replacement Brain 负责受控 reconciliation，root 不得把自己当成 Brain。
5. Brain 完整读取三份活动文档和实时 Git，协调状态后使用 `spawn_agent` 创建自己的 Limbs 子级。
6. 配置文件只证明 Agent 已注册，持久化 path 也不能证明实例 live；运行时必须单独确认。

自然语言路由固定如下：

| 用户表述 | 路由 |
|---|---|
| “开一个 Brain”“启动 Brain”“恢复 Brain” | 内部 Brain spawn/reuse |
| “Brain 窗口” | 内部 Brain spawn/reuse |
| 明确“独立 Codex 窗口”“独立 Codex 侧边栏任务”“独立 Codex 任务” | Brain 结构化返回 root，仅 root 可 `create_thread` |

不得把模糊的“窗口”隐式解释成外部任务。Brain 永远不得调用 `create_thread`。root 不得直接调用 `spawn_agent` 创建 Limbs。

## 状态拓扑契约

- `CURRENT_TASK.md` 和 `PROJECT_STATE.md` 都必须记录 `Active Brain canonical path: /root/...`；`/root` 本身不是有效活动 Brain path。
- 每个活动 Limbs 账本必须包含 `Parent` 列，所有活动行的 Parent 必须等于活动 Brain canonical path。
- Limbs 只接受其 parent Brain 分配的一个 `TASK_ID`，终态必须回报同一 `PARENT_BRAIN`。
- root 到 Limbs 的直接边在任何阶段都无效；只有 `Brain -> Limbs` 且 parent 等于 active Brain 才合法。

## 职责与分析边界

Brain 负责：

- 解释 Limbs 返回的原始证据；
- 跨层根因分析；
- 技术路线、架构、范围、安全和验收裁决；
- 冻结矩阵、任务拆分、依赖协调、活动文档和用户沟通；
- 要求 Limbs 进行精确补证。

Limbs 负责：

- 原始证据采集；
- Brain 指定的判别实验；
- 已选方案内的实现、测试、构建、设备验证和只读复核；
- 已选方案内普通编译、测试、构建、配置和局部实现错误的自主诊断与修复。

ANR、OOM、并发、架构、数据一致性和安全问题默认返回 Brain。Limbs 可以陈述观测事实和有界推断，但不得把跨层结论、路线选择或验收结论作为最终裁决。

## 阶段状态机

```text
MATRIX_REQUIRED -> STAGE_IN_PROGRESS -> READY_FOR_BRAIN_REVIEW
READY_FOR_BRAIN_REVIEW -> BRAIN_REVIEWING
BRAIN_REVIEWING -> ACCEPTED -> COMMITTED
BRAIN_REVIEWING -> REJECTED -> STAGE_IN_PROGRESS
BRAIN_REVIEWING -> HUMAN_DECISION
```

每个 Limbs 返回终态后，Brain 重新计算整个阶段。仍有未完成工作、依赖、失败或证据缺口时保持 `STAGE_IN_PROGRESS`。全部实现与集成证据完成后，Brain 创建只读 `Limbs-验收`，再依据冻结矩阵作出：

- `ACCEPTED`：同步活动文档，关闭阶段并按 Git 门禁处理阶段提交。
- `REJECTED`：将具体修复交给 Limbs，满足门禁后重新验收。
- `HUMAN_DECISION`：仅限规则无法裁决的产品、架构、权限、安全、破坏性操作或范围扩张。

## 派工、并行与所有权

- Brain 为每个 Limbs 指定 `TASK_ID`、`PARENT_BRAIN`、目标、文件所有权、禁止范围、依赖、验收条件和证据要求。
- 只有依赖解除、写入范围不重叠、验证不会相互干扰的任务才并行。
- 公共配置、Schema、锁文件、迁移、注册表、公共接口和三份活动文档必须单写；活动文档唯一写入者是活动 Brain。
- Brain 不替代 Limbs 做普通实现、测试、构建、设备检查或原始证据采集。
- Brain 通过等待机制接收结构化终态，不持续轮询过程日志、逐轮记录或中间推理；只在阻断、证据冲突或精确纠偏时有界追问。

Limbs 终态合同：

```text
STATUS: COMPLETE | BLOCKED | HUMAN_DECISION
TASK_ID:
PARENT_BRAIN:
SUMMARY:
CHANGED_FILES:
VERIFICATION:
RISKS_OR_BLOCKERS:
DECISIONS_NEEDED:
CONTEXT_USAGE: <exact percent> | Unavailable
CONTEXT_STATE: NORMAL | WATCH | ROTATE_PENDING | Unavailable
```

## 模型、活动文档与轮换

- Brain 项目配置为 `gpt-5.6-sol / medium`；Limbs 使用项目 Agent 配置。阶段开始前区分报告配置值与运行时确认值。
- `DEVELOPMENT_ROADMAP.md` 记录路线，`CURRENT_TASK.md` 记录唯一活动阶段和账本，`PROJECT_STATE.md` 记录实时门禁和基线。
- Limbs 不写项目管理文档；Brain 只在阶段开始、真实状态变化和结束时简短同步三份活动文档。
- 上下文仅使用运行时准确数据；不可用时写 `Unavailable`。`<60%` 为 `NORMAL`，`60%–69%` 为 `WATCH`，`>=70%` 为 `ROTATE_PENDING`。
- 轮换前必须收敛 Limbs、同步活动文档并核对 Git；替换 Brain 后必须更新 canonical path 及所有活动 Limbs parent。

仓库 validator 只能强制仓库内的注册、文本、路由模拟和状态拓扑合同，证据上限为 `REPO_CONTRACT_ENFORCED`；它无法硬拦截 Codex 平台工具调用，真实运行时层级仍需前向验收。
