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
