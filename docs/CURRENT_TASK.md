# Current Task: V4-SIMP-002

- `STATE_REV: 2026-08-30.003`
- `TASK_REV: V4-SIMP-002.001`
- Stage state: `MATRIX_DEFINED / IN_PROGRESS`
- Product status: `V4_RELEASED`
- Evidence ceiling: `BUILD_VERIFIED`
- Device gate: `NO_DEVICE_ACTION`

## 1. 阶段目标

按照 `simplify-codebase` 的 Change/Broad 方法，对 v4.4.0 当前工作树执行行为保持的熵回收：删除已证明无生产消费者的仓库负担、测试遗留、死代码和直接依赖；把当前版本与架构文档统一到最终实现。不得改变产品功能、技术路线、运行时合同或持久化格式。

## 2. 冻结验收矩阵

| 类别 | 冻结内容 |
|---|---|
| 主链路 | 从 v4.4.0 发布后源码出发：建立消费者证明 → 按所有权边界删除无生产消费者的代码、依赖、二进制和过期文档 → 普通/Native 构建继续成功 → 版本、入口、主链路和发布说明保持一致。 |
| 必须证据 | 删除前后 tracked 文件数量、工作树字节数和 Kotlin/文档行数；每个删除候选的生产/测试/文档消费者搜索；`python tools\asr_evaluate_test.py`；`testDebugUnitTest` 的实际结果（测试源码已移出仓库时必须明确记录 `NO-SOURCE`，不得冒充回归通过）；`lintDebug`；普通与 `-PenableWhisperNative=true` Debug 构建；普通与 Native AndroidTest 构建；APK 文件大小和关键 native 条目；`git diff --check` 与最终精确 diff。 |
| 禁止事项 | 不修改用户可见行为、Whisper、DeepSeek Prompt、歌词检索、响应合同、cue 时间戳、BYOK/Keystore、权限、`.lcp` 持久化、导出技术路线或 ABI；不引入依赖；不改写 Git 历史；不修改 `third_party/ffmpeg-kit`；不操作模拟器或真机；不覆盖受保护本地内容；不 reset、clean、批量暂存或 push。 |
| 退出状态 | 仅删除达到消费者映射与合同证明的候选；残留搜索、所有适用构建和 lint 通过，版本统一为 `v4.4.0 / 4400`，最终 diff 不含行为变化时，标记 `PASS / BUILD_VERIFIED`。 |
| 未完成状态 | 动态、外部、持久化或诊断消费者无法排除的候选必须保留并记录；任一构建或 lint 失败为 `PARTIAL_PASS`；需要改变产品能力、兼容合同或架构才能继续时返回 `HUMAN_DECISION`。 |

## 3. 已批准范围

- 删除未被构建引用的 FFmpegKit full AAR，保留正式使用的 minimal-gpl AAR。
- 删除随 `app/src/test/`、`app/src/androidTest/` 移出后失去消费者的 Debug 测试入口、JUnit 配置和测试专用生产代码。
- 删除经全仓精确搜索和历史消费者映射证明不可达的独立文件与文件内成员。
- 逐项删除当前源码、脚本、配置和文档均未使用的直接依赖；每项必须由构建验证。
- 将三份活动文档、README 与 V4 架构说明统一到 v4.4.0 最终状态；历史设计理由只有在转移到当前 owner 后才允许退役旧文档。

## 4. 执行批次

1. 仓库负担：冗余 AAR、测试依赖与 Debug 测试入口。
2. 死代码：完整无消费者文件，再处理共享文件内的无消费者成员。
3. 依赖：按单项消费者证明移除未使用直接依赖。
4. 文档：修复当前 owner，退役完全失效记录，保留仍拥有兼容或历史决策的资料。

## 5. 下一动作

建立本阶段 checkpoint，采集当前构建与体积基线，然后从最高置信度、最易撤销的仓库负担开始。
