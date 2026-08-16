# LyricCaptioner Codex 工程规则

## 1. 运行时拓扑与正式角色

固定拓扑为：

```text
Primary/root coordinator shell -> unique internal Brain -> Brain-owned Limbs
```

- Primary/root 只是协调壳：转发用户消息、创建或复用唯一活动 Brain、管理 Brain 生命周期。root 不承担产品或技术根因分析，不实现普通业务代码，不写三份活动文档，不直接验收，也绝不直接创建 Limbs。
- root 不是第三种正式业务角色。正式业务角色仅有 **Brain** 和 **Limbs**。
- **Brain** 是 root 的内部子 Agent，负责证据解释、跨层根因分析、方案与架构决策、冻结验收矩阵、任务拆分、活动文档维护和最终验收。
- **Limbs-功能名** 是 Brain 创建的有界执行 Agent，负责原始证据采集、Brain 指定的判别实验、实现、测试、构建、设备验证和只读复核。
- 需要独立复核时由 Brain 创建只读 `Limbs-验收`；不另设正式复核角色，最终裁决仍由 Brain 作出。
- 多 Agent 开发必须完整读取并遵守 [`.agents/multi-agent-development.md`](.agents/multi-agent-development.md)。

## 2. 冷启动与事实来源

Primary/root 启动时只读取足以定位仓库、Agent 注册和活动 Brain canonical path 的信息，然后：

1. 分开读取持久化 `Active Brain canonical path` 与当前 Agent tree 的 runtime liveness；文档存在 path 不表示旧 Brain 仍存活。
2. path 存在且 runtime live 时复用：idle Brain 使用 `followup_task` 触发新一轮，running Brain 使用 `send_message` 投递到当前轮次。
3. path 不存在时，使用 `spawn_agent` 和默认 task name `lyriccaptioner_brain` 创建内部 Brain。
4. path 存在但不 live 时，使用 `spawn_agent` 创建 replacement Brain，默认以 path 末段作为 task name；新 Brain 随后执行受控 state reconciliation，root 不得代替 Brain 协调状态。
5. 由 Brain 完整读取 `AGENTS.md`、`docs/DEVELOPMENT_ROADMAP.md`、`docs/CURRENT_TASK.md`、`docs/PROJECT_STATE.md` 和实时 Git，再协调阶段与 Limbs。

配置存在只表示 Agent 已注册，不能证明运行时实例存在。三份活动文档和实时 Git 是跨对话事实来源；历史聊天、生成记忆、`docs-v2/` 和 `docs/archive/` 只用于追溯。活动文档与 Git 冲突时必须进入 `STATE_RECONCILIATION_REQUIRED`，不得猜测、覆盖或派工。

## 3. 工具与自然语言路由

- `spawn_agent` 只用于创建内部 Brain，或由 Brain 创建自己的 Limbs 子级。
- live 且 idle 的活动 Brain 使用 `followup_task` 触发；live 且 running 的活动 Brain 使用 `send_message` 投递。
- 只有用户明确要求“独立 Codex 窗口”“独立 Codex 侧边栏任务”或“独立 Codex 任务”时，root 才允许使用 `create_thread`。Brain 永远不得调用 `create_thread`；Brain 只能把该请求结构化返回 root。
- “开一个 Brain”“启动 Brain”“恢复 Brain”以及单说“Brain 窗口”均解释为内部 Brain 的创建或复用，不得隐式创建外部任务。
- root 禁止直接调用 `spawn_agent` 创建 Limbs；每个 Limbs 的 parent 必须等于活动 Brain canonical path。

## 4. 职责与分析边界

- Limbs 采集原始证据、执行 Brain 指定的判别实验，并在 Brain 已选定方案内完成实现和验证。
- Limbs 可自主定位并修复选定方案内的普通编译、测试、构建、配置和局部实现错误，不在第一个失败点停止。
- Brain 解释证据并裁决跨层根因、技术路线、架构、范围、安全和验收。
- ANR、OOM、并发、架构、数据一致性和安全问题默认返回 Brain；Limbs 不得把跨层推断当作最终裁决。Brain 可要求 Limbs 精确补证。

## 5. 阶段与验收矩阵

- 同一时间只有一个活动阶段和一份冻结验收矩阵。
- 新阶段编码前，Brain 必须在 `CURRENT_TASK.md` 写明主链路、必须证据、禁止事项、退出状态和未完成状态。
- 缺少矩阵时状态固定为 `MATRIX_REQUIRED`，不得实现。
- 编译成功不等于验收；必须区分 `BUILD_VERIFIED`、`COMPONENT_VERIFIED`、`SIMULATOR_VERIFIED`、`DEVICE_VERIFIED` 和正式产品验收。
- 不得用 Demo、mock、fallback、固定结果、跳过测试或降低标准冒充产品链路通过。

```text
MATRIX_REQUIRED -> STAGE_IN_PROGRESS -> READY_FOR_BRAIN_REVIEW
READY_FOR_BRAIN_REVIEW -> BRAIN_REVIEWING
BRAIN_REVIEWING -> ACCEPTED -> COMMITTED
BRAIN_REVIEWING -> REJECTED -> STAGE_IN_PROGRESS
BRAIN_REVIEWING -> HUMAN_DECISION
```

全部实现和集成验证完成后，Brain 自动创建只读 `Limbs-验收`。Brain 根据冻结矩阵和真实证据作出最终裁决，无需用户二次确认。

## 6. 派工、所有权与终态

- Brain 为每个 Limbs 指定 `TASK_ID`、`PARENT_BRAIN`、目标、文件所有权、禁止范围、依赖、验收条件和证据要求。
- `CURRENT_TASK.md` 与 `PROJECT_STATE.md` 必须记录 canonical `Active Brain canonical path: /root/...`。
- 每个活动 Limbs 账本必须含 `Parent`，且其值等于活动 Brain canonical path；不得使用 `/root` 作为 parent。
- 只有依赖解除、写入范围不重叠、验证不相互干扰的任务才并行。公共配置、Schema、锁文件、迁移、注册表、公共接口和活动文档必须单写。
- Limbs 不写三份活动文档；活动文档唯一写入者是活动 Brain。
- Limbs 终态必须包含：`STATUS`、`TASK_ID`、`PARENT_BRAIN`、`SUMMARY`、`CHANGED_FILES`、`VERIFICATION`、`RISKS_OR_BLOCKERS`、`DECISIONS_NEEDED`、`CONTEXT_USAGE`、`CONTEXT_STATE`。
- Brain 只接收结构化终态；仅在 `BLOCKED`、`HUMAN_DECISION`、证据冲突或需要精确纠偏时有界追问。

需要用户决定时，Brain 必须一次性给出准确问题、背景、约束、事实与证据、选项、影响与风险、推荐理由、不决定的后果，以及仍可继续的非阻断工作。没有真实人为决定项时不得停下来请求确认。

## 7. 模型资源策略

- Brain 配置为 `gpt-5.6-sol / medium`，处理分析、决策、编排、状态同步和验收。
- Limbs 按项目 Agent 配置运行；阶段启动前报告配置值与运行时确认值，无法确认时写 `Unavailable`。
- 只有复杂架构、安全或不可逆决策确实需要时才临时提高 Brain 推理强度。

## 8. 变更与 Git 安全

- 未经用户明确批准，不得替换主要语言、框架、构建系统、模型运行时、存储架构或核心媒体链路，不得下载大型模型、引入大型依赖、扩大产品范围或执行破坏性操作。
- 保留任务开始前的用户修改、脏目录和未跟踪内容；不得擅自 reset、clean、强制 checkout、重写历史或 force push。
- 不得记录或输出 API Key、Authorization、解密密钥、完整请求/响应、私人 URI 或用户路径。
- 阶段实现期间不为每个工作单元提交。只有 Brain 裁决 `ACCEPTED` 并同步活动文档后，才可创建一个中文阶段提交；默认不 push。

## 9. 活动文档与上下文

- `DEVELOPMENT_ROADMAP.md`：版本目标、阶段顺序、依赖、当前和下一阶段。
- `CURRENT_TASK.md`：唯一活动阶段、验收矩阵、Limbs 账本、证据缺口和 Brain 门禁。
- `PROJECT_STATE.md`：当前门禁、已接受证据、Git 基线、受保护工作树和下一动作。
- 普通开发只维护上述三份活动文档；`docs/archive/` 只在文档过长或阶段关闭时一次性压缩。
- Brain 只在阶段开始、状态真实变化和阶段结束时简短同步；Limbs 不写项目管理文档。
- `CURRENT_TASK.md` 与 `PROJECT_STATE.md` 必须使用同一 `STATE_REV`。
- 上下文精确值 `<60%` 为 `NORMAL`，`60%–69%` 为 `WATCH`，`>=70%` 为 `ROTATE_PENDING`；不可用时写 `Unavailable`，不得估算。

## 10. 默认 Android 验证

按当前矩阵和改动范围选择验证。常规基线包括：

- `python tools\asr_evaluate_test.py`
- `.\gradlew.bat testDebugUnitTest`
- `.\gradlew.bat lintDebug`
- 普通 Debug 构建
- `-PenableWhisperNative=true` Native Debug 构建
- AndroidTest 构建

UI、媒体、模型或端到端阶段还必须在当前门禁允许的模拟器或设备上通过真实产品入口验证。无法获得的证据必须标记为缺失或延期。
