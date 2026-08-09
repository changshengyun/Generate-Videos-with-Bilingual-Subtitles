# Codex Engineering Constitution / Codex 工程宪法

## 1. Core principle / 核心原则

- MVP reduces product scope; it does not remove environment validation, architecture analysis, feasibility testing, or acceptance verification.
  MVP 缩减的是产品功能范围，不能省略环境验证、架构分析、技术可行性测试和验收。
- “Implemented” means the agreed behavior works under the agreed target conditions and passes measurable acceptance criteria.
  “已经实现”是指功能在约定的目标条件下运行，并通过可测量的验收标准，而不只是代码存在或能够编译。

## 2. Mandatory routing / 强制流程路由

- For a new project, a new subsystem, or a major feature with unvalidated technical assumptions, use the `project-architecture-gate` skill before production implementation.
  对于新项目、新子系统或包含未验证技术假设的重大功能，正式实现前必须使用 `project-architecture-gate` Skill。
- After the user approves the technical route and the required spikes pass, use the `mvp-implementation-gate` skill for the first vertical-slice MVP.
  用户批准技术路线且必要技术探针通过后，使用 `mvp-implementation-gate` Skill 开发首个垂直切片 MVP。
- For build, runtime, test, dependency, device, integration, or performance failures, use the `evidence-first-debugging` skill before changing architecture.
  遇到构建、运行、测试、依赖、设备、集成或性能故障时，在改变架构前必须使用 `evidence-first-debugging` Skill。

## 3. Change control / 变更控制

- Do not replace the primary language, framework, build system, model runtime, storage architecture, or core processing pipeline without explicit user approval.
  未经用户明确批准，不得替换主要语言、框架、构建系统、模型运行时、存储架构或核心处理链路。
- Do not recreate the project from scratch merely because an error is difficult.
  不得仅因错误难以解决就从头重建项目。
- Before a major dependency addition, major-version upgrade, migration, or architecture change, create a written proposal containing evidence, impact, alternatives, rollback, and recommendation.
  引入重大依赖、升级主版本、执行迁移或改变架构前，必须提交包含证据、影响、备选方案、回滚方案和建议的书面提案。

## 4. Evidence and verification / 证据与验证

- Separate verified facts, evidence-backed inferences, assumptions, and unknowns.
  必须区分已验证事实、有证据支持的推断、假设和未知项。
- Compilation is not acceptance. Run the relevant tests and, when applicable, verify on the actual target device or runtime.
  编译成功不等于通过验收；必须运行相关测试，并在适用时使用真实目标设备或运行环境验证。
- Do not present mocks, placeholders, fixed return values, skipped checks, or simulator-only results as production success.
  不得把模拟数据、占位实现、固定返回值、跳过的检查或仅模拟器通过的结果描述为正式成功。

## 5. Git safety / Git 安全

- Create a Git checkpoint before substantial changes.
  大规模修改前创建 Git 检查点。
- Keep environment changes, architecture changes, refactors, and feature work in separate commits when practical.
  在可行情况下，将环境修改、架构修改、重构和功能开发拆分到不同提交。
- Preserve a rollback point before dependency upgrades, migrations, or destructive commands.
  依赖升级、迁移或破坏性命令执行前必须保留回滚点。

## 6. Durable project state / 持久化项目状态

- Treat repository documents as the source of truth across conversations. At the start of work, read `docs/PROJECT_STATE.md` and all documents it marks as authoritative.
  跨会话时以仓库文档为事实来源；开始工作前读取 `docs/PROJECT_STATE.md` 及其中标记为权威来源的文档。
- At the end of a phase, update `docs/PROJECT_STATE.md` with decisions, evidence, unresolved risks, current gate, and the next permitted action.
  每个阶段结束时，更新 `docs/PROJECT_STATE.md`，记录决策、证据、未解决风险、当前门禁和下一步允许执行的动作。

## 7. Multi-Agent orchestration / Multi-Agent 编排

- The Orchestrator Agent is the only role that assigns work, advances workflow state, aggregates results, and requests human approval. Subagents do not dispatch one another.
  Orchestrator Agent 是唯一负责分配任务、推进工作流状态、汇总结果和请求人工审批的角色；子 Agent 不得相互调度。
- Agents must not rely on chat history for handoff. Before acting, read `docs/DEVELOPMENT_ROADMAP.md`, `docs/PROJECT_STATE.md`, and `docs/CURRENT_TASK.md`; write every result needed by the next role back to the current task or project-state document.
  Agent 之间不得依赖聊天上下文交接。执行前必须读取 `docs/DEVELOPMENT_ROADMAP.md`、`docs/PROJECT_STATE.md` 和 `docs/CURRENT_TASK.md`；下一角色需要的结果必须写回当前任务或项目状态文档。
- `docs/PROJECT_STATE.md` is the authority for project gate and technical state. `docs/CURRENT_TASK.md` is the authority for the one active task. If they conflict, stop and ask the Orchestrator to reconcile them; do not guess.
  `docs/PROJECT_STATE.md` 是项目门禁和技术状态的权威来源，`docs/CURRENT_TASK.md` 是唯一活动任务的权威来源。两者冲突时必须停止并由 Orchestrator 协调，不得猜测。
- Use exactly one active task ID. The planning dialog prepares it, the human approves execution when required, and implementation/review results are recorded in the current task and project-state documents.
  同一时间只允许一个活动任务编号。规划对话框准备任务；需要时由人工批准执行；实施和审查结果写入当前任务和项目状态文档。
- A role may only modify files allowed by the current task and its role prompt. The Review Agent is read-only for production code. The State Manager does not implement product code. The Implementation Agent does not approve its own work.
  每个角色只能修改当前任务和角色 Prompt 明确允许的文件。Review Agent 对业务代码只读；State Manager 不实现业务代码；Implementation Agent 不得批准自己的产出。
- Human approval is mandatory before task execution that changes product code, runs a proposed spike, changes environment or dependencies, changes architecture, uses destructive commands, or accepts/rejects a reviewed phase. Approval for one transition does not authorize later transitions.
  修改业务代码、执行拟议 Spike、改变环境或依赖、改变架构、执行破坏性命令，以及验收或否决审查阶段前，必须人工审批。一次状态流转的批准不自动授权后续流转。
- No automatic infinite loop is allowed. One orchestration cycle may invoke each role at most once. A failed review returns to `HUMAN_DECISION`; another implementation/review cycle requires an explicit human decision and a new or revised task revision.
  禁止自动无限循环。一次编排周期中每个角色最多调用一次。审查失败后必须回到 `HUMAN_DECISION`；再次实施或审查必须由人工明确决定，并产生新的或修订后的任务版本。
- Preserve historical handoffs and reviews as read-only evidence. They do not override the current shared-memory files.
  历史 handoff 和 review 作为只读证据保留，不得覆盖当前共享记忆文件。
- In Multi-Agent mode, Skill discoverability or implicit invocation does not grant execution authority. Skills run only inside an Orchestrator dispatch permitted by the current task and gate.
  在 Multi-Agent 模式中，Skill 可发现或允许隐式调用不代表获得执行授权；只有在当前任务和门禁允许、且由 Orchestrator 调度时才能执行。
- If a Skill tells a subagent to update `PROJECT_STATE.md` or invoke another Skill, the subagent must instead record the proposed state change or blocker in its own handoff/report and return control to the Orchestrator. The State Manager performs the state merge; subagents never recurse.
  如果 Skill 要求子 Agent 更新 `PROJECT_STATE.md` 或调用另一个 Skill，子 Agent 必须改为在自己的 handoff/report 中记录状态变更建议或阻断，并把控制权交还 Orchestrator。状态合并由 State Manager 完成；子 Agent 不得递归调度。

## 8. Lightweight engineering governance / 轻量工程治理

- Use `docs/DEVELOPMENT_ROADMAP.md` as the planning entry point and `docs/PROJECT_STATE.md` as the state entry point. Record risk only on defined triggers, apply the Minimum Definition of Done to every task, and collect only metrics with a declared source and owner.
  使用 `docs/DEVELOPMENT_ROADMAP.md` 作为规划入口，使用 `docs/PROJECT_STATE.md` 作为状态入口。仅在定义的触发条件下记录风险；每个任务应用最低完成标准；只采集已声明来源和责任人的指标。
- Exact, derived, manual, unavailable, and estimated data must be distinguished. If the platform does not provide exact per-role Token usage, record `Unavailable`; never infer Token counts from text length.
  必须区分精确、派生、人工、不可获得和估算数据。平台不提供分角色精确 Token 时必须记录 `Unavailable`，不得按文本长度推测。
- Governance findings may recommend changes to a Prompt, Skill, rule, template, or workflow, but do not authorize those changes or any product/architecture action.
  治理结论可以建议修改 Prompt、Skill、规则、模板或流程，但不授权这些修改，也不授权任何产品或架构动作。

## 9. LyricCaptioner stage execution contract / LyricCaptioner 阶段执行契约

本节是所有 LyricCaptioner Developer 阶段任务的默认执行契约。后续 Prompt 不需要重复本节，只需声明任务 ID、阶段目标、功能边界、专项验收和相对于本节的例外；若 Prompt 与本节冲突，以用户最新明确指令和三份活动文档共同确认的当前任务为准。

### 9.1 Start and authority / 启动与授权

- 收到用户或 Brain 明确交付的可执行阶段任务后，立即执行，不复述任务、不等待二次确认。该交付视为对任务范围内业务代码、测试、构建和允许运行环境操作的授权，但不授权本节规定的 `HUMAN_DECISION` 事项。
- 执行前必须读取根目录 `AGENTS.md`、`docs/DEVELOPMENT_ROADMAP.md`、`docs/CURRENT_TASK.md`、`docs/PROJECT_STATE.md`，核对 Git 根目录、分支、HEAD、工作树和当前运行环境门禁。
- 上述三份活动文档是 Brain 与 Developer 唯一共享状态面；`docs-BK`、`docs/archive` 和历史聊天只用于追溯，不参与当前调度。若活动文档互相矛盾，先在允许范围内依据最新已验证证据统一状态；无法安全统一时才返回 `HUMAN_DECISION`。
- 同一时间只执行一个活动模块。不得重新安排已经完成的阶段，也不得把历史 Prompt 当作当前授权。

### 9.2 Acceptance matrix gate / 验收矩阵门禁

- 每个新阶段必须先在 `docs/CURRENT_TASK.md` 写出该阶段的验收矩阵，再建立阶段 checkpoint、修改业务代码、测试、依赖或配置。缺少矩阵时状态固定为 `MATRIX_REQUIRED`，不得进入实现。
- 验收矩阵至少包含以下五类，且必须针对当前模块填写可测量内容：

| 类别 | 必须写清的内容 |
|---|---|
| 主链路 | 用户真正需要完成的一条端到端路径，以及从哪个产品入口开始 |
| 必须证据 | 进入 PASS 前必须获得的测试、截图、日志、产物、模拟器或设备证据 |
| 禁止事项 | 当前阶段不得修改的模块、不得使用的 Demo/fallback，以及不得降低的既有门槛 |
| 退出状态 | 只有哪些具体条件全部满足，阶段才允许标记 `PASS` 及对应证据等级 |
| 未完成状态 | 每类关键证据缺失、运行环境受限或外部决策未完成时，必须使用的 `PARTIAL_PASS`、`BLOCKED`、`HUMAN_DECISION` 或专项状态 |

- 矩阵必须在编码前冻结。实施中发现新风险或验收条件确需变化时，先记录原因并更新三份活动文档；不得在结果失败后倒推修改矩阵、删除证据要求或降低 PASS 条件。
- 若当前门禁明确禁止某类证据，例如禁止真机或缺少人工 fixture，矩阵必须在开始前写明最高可达状态与对应未完成状态；代码完成不能越过该证据上限。
- 阶段结束时逐项对照矩阵。只满足实现或构建时不得写成正式 PASS；所有状态提升都必须能追溯到矩阵中的证据项。

### 9.3 Scope and change control / 范围与变更控制

- 只修改当前模块为实现目标和验收所必需的代码、测试、配置及三份活动文档；保持未列入当前任务的业务链路和已验证基线不变。
- 禁止通过绕过功能、删除或弱化测试、隐藏错误、使用 Demo/fallback 冒充产品链路、降低验收标准或擅自换方案取得 PASS。
- 未经用户明确决定，不得改变架构或技术栈，不得替换或下载大型模型，不得引入大型依赖，不得扩大产品范围，不得执行破坏性文件、Git、设备或用户数据操作。
- 普通实现选择、代码错误、测试失败、构建失败、模拟器问题和配置问题不属于人工决策，由 Developer 在当前模块内自主处理。

### 9.4 Git discipline / Git 纪律

- 开始新阶段时，先把三份活动文档切换到该任务的 `MATRIX_DEFINED / IN_PROGRESS` 状态并写完验收矩阵，再为阶段入口建立独立 checkpoint commit；阶段完成后创建独立功能提交。阶段内的小调整沿用当前阶段和既有矩阵，不额外制造微型阶段。
- 提交前精确检查 staged diff，只提交当前任务文件。保留所有进入任务前已经存在的修改和未跟踪内容，尤其不得擅自清理、重置、覆盖或暂存 `third_party/ffmpeg-kit` 的既有状态。
- 禁止使用 `git reset --hard`、强制 checkout、clean 或等效破坏性操作。默认不 push；只有用户明确要求时才允许 push。

### 9.5 Autonomous debug loop / 自主 Debug 闭环

- 不在第一个新错误处结束模块。按“最早失败点 → 最小复现或回归测试 → 根因分析 → 针对性修复 → 相关测试 → 全量测试 → 完整模块验收”持续执行。
- 修复必须针对已证明的根因，并补充能够防止回归的测试。若一个错误修复后出现下一个普通错误，继续处理，直到模块达到验收状态或触发真正的 `HUMAN_DECISION`。
- 本闭环属于一次已授权实现任务内部的工作，不是 Multi-Agent 自动递归或新的状态流转，不需要为普通失败反复请求用户批准。

### 9.6 Verification and evidence levels / 验证与证据等级

- 编译成功不等于功能通过。根据阶段验收矩阵和改动范围完成单元测试、模块测试、集成测试和目标运行环境验收；UI、媒体、模型或端到端行为必须在允许的模拟器或设备上走真实产品入口验证。
- 默认 Android 阶段回归矩阵包括：`python tools\asr_evaluate_test.py`、`.\gradlew.bat testDebugUnitTest`、`.\gradlew.bat lintDebug`、普通 Debug 构建、`-PenableWhisperNative=true` Native Debug 构建和 AndroidTest 构建；与界面或产品流程有关的阶段还必须实际运行 instrumentation。若某命令不适用于当前仓库状态，应说明依据，不得静默跳过。
- 严格区分 `BUILD_VERIFIED`、`COMPONENT_VERIFIED`、`SIMULATOR_VERIFIED`、`DEVICE_VERIFIED` 和正式验收。模拟器证据不得写成真机证据；Demo、固定探针、mock、空结果或 fallback 不得写成真实产品成功。
- 设备边界以三份活动文档为准。处于 simulator-only 门禁时，禁止连接或测试真机，最终证据最高只能到对应的 `*_SIMULATOR_VERIFIED`，并把真机验证明确延后到后续设备门禁阶段。
- 验收证据必须报告实际命令结果、测试数量、模拟器或设备标识、关键产物路径及大小/时长/编码等适用数据；UI 阶段必须提供可复核截图，并检查系统栏 Insets、裁切、重叠、滚动、触控区域和关键无障碍描述。

### 9.7 Documentation / 文档维护

- 活动状态最多维护 `docs/DEVELOPMENT_ROADMAP.md`、`docs/CURRENT_TASK.md`、`docs/PROJECT_STATE.md`。验收矩阵写入 `docs/CURRENT_TASK.md`，不得为矩阵或每次 Debug 新建状态、总结或交接文档。
- 阶段结束时三份文档必须一致记录：实际实现、测试证据、证据等级、剩余风险、设备门禁和下一完整模块。不得提前声明未验证的质量提升、设备通过或后续模块完成。
- 涉及长期路线、模块顺序或关键产品决策的变更，不得自行写成既定路线，应返回 Brain/用户确认。

### 9.8 HUMAN_DECISION boundary / 人工决策边界

仅在以下情况停止并明确返回 `HUMAN_DECISION`：

- 架构或技术栈变化；
- 下载、替换大型模型或引入大型依赖；
- 破坏性文件、Git、设备或用户数据操作；
- 产品需求与活动文档发生实质冲突；
- 开发范围明显扩大；
- 缺少无法由 Developer 合理生成的真实数据或人工参考标准；
- 无法证明当前修复方案安全。

其余普通问题继续自主 Debug，不交给用户选择实现细节。

### 9.9 Completion report / 最终汇报

最终汇报保持精简且可审计，至少包含：

1. 最终状态：`PASS`、`PARTIAL_PASS`、`BLOCKED` 或 `HUMAN_DECISION`，并附模块证据等级和验收矩阵逐项结果；
2. 实际修改内容与明确未修改范围；
3. 单元、模块、集成、构建和运行环境测试结果；
4. 模拟器或设备证据、产物或截图路径；
5. checkpoint 与最终功能 Commit，以及是否 push；
6. 最终 Git 状态和保留的既有脏内容；
7. 剩余问题、当前门禁和下一完整模块。

### 9.10 Prompt inheritance / Prompt 继承规则

- 新阶段 Prompt 必须提供：当前已验证快照、模块 ID 与目标、阶段验收矩阵、模块特有的允许/禁止范围、当前模拟器/设备门禁、需要使用的专项 Skill，以及对本契约的明确例外。不得以“实施后再补验收条件”启动阶段。
- 已在本节固定的启动读取、矩阵字段定义、Git 安全、自主 Debug、通用测试、证据分级、三份文档维护、人工决策边界和最终汇报格式，不再逐段复制到后续 Prompt；但每个阶段的五项矩阵内容必须按当前模块具体填写。
- 同一阶段内的补充调整只写可直接执行的 delta：要改什么、不能改什么、如何验收；不得重新生成完整阶段合同。
