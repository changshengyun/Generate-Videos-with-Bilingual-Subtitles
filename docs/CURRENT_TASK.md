# Current Task: V4-FLOW-001

- `STATE_REV: 2026-08-24.008`
- `TASK_REV: V4-FLOW-001.001`
- Stage state: `MATRIX_DEFINED / IN_PROGRESS`
- Product status: `V4_ONE_CLICK_CAPTION_FLOW_IMPLEMENTATION`
- Evidence ceiling: `COMPONENT_VERIFIED`；未获得设备授权，不得声明 `DEVICE_VERIFIED`
- Baseline HEAD: `daf38c884b5b8b9f6b7f1b0517232871f9113417`
- Device gate: `NO_PHYSICAL_DEVICE_ACTIONS / V4-E2E-001_REQUIRES_EXPLICIT_AUTHORIZATION`

## 1. 阶段目标

实现唯一用户入口 `generateCompleteCaptions()` 与 `cancelCaptionWorkflow()`：从一次点击开始，依次执行本地 Whisper、现有 AI 增强，并在成功或既有本地回退时自动进入字幕编辑区。

工作流状态固定为：

```text
IDLE → LOCAL_RECOGNIZING → AI_ENHANCING → READY_FOR_EDIT
                                   ↘ FAILED
任意运行阶段 → CANCELLED
```

## 2. 冻结验收矩阵

| 类别 | 冻结内容 |
|---|---|
| 主链路 | 从已导入视频的“开始识别”入口一次点击，检查视频、本地模型和 DeepSeek Key；只调用一次本地 ASR，原子提交原始 cue；只调用一次现有 AI 增强，原子提交结果，并自动切换到字幕编辑区。 |
| 必须证据 | 聚焦单元测试证明调用次数与顺序、缺少 Key、ASR 失败、AI 本地回退、AI 抛错、取消、重复点击和成功自动进入编辑区；阶段收尾完成 JVM 测试、lint、普通/Native Debug、AndroidTest 构建；在不连接真机的前提下记录可达到的最高证据等级。 |
| 禁止事项 | 不修改 AI Prompt、歌词检索、模型、响应合同、cue 时间戳、导出链路或依赖；不运行 V3 延期的生产 base 验证；不连接或操作真机；不保留用户可见的独立“AI 增强字幕”入口；不 reset、clean、批量暂存或 push。 |
| 退出状态 | 所有聚焦测试与阶段构建矩阵通过，真实业务入口的状态、取消和失败保留行为符合合同，标记 `PASS / COMPONENT_VERIFIED`；设备与真实 AI 完整主链路留给 `V4-E2E-001`。 |
| 未完成状态 | 实现完成但构建/组件证据不全为 `PARTIAL_PASS`；普通工程故障按 S1/S2 自主闭环；只有命中架构、依赖、破坏性操作、需求冲突或无法证明安全时为 `HUMAN_DECISION`。 |

矩阵于产品代码修改前冻结。实施失败后不得降低退出条件。

## 3. 允许范围

- `MainViewModel`、编辑状态模型、编辑界面及与本工作流直接相关的测试。
- 复用现有本地识别、AI 增强、回退和持久化实现；允许为可测试性提取局部协调逻辑。
- 更新三份活动文档；当前阶段结束后切换到已批准的 `V4-EDITOR-001`，并在其编码前冻结下一矩阵。

## 4. 已验证起点与历史缺口

- Git 根目录：`D:\DevEnv\Projects\lyric-captioner-android`；分支：`migration/lyric-captioner-history`；起点 HEAD：`daf38c884b5b8b9f6b7f1b0517232871f9113417`。
- V3 的本地识别与 AI 增强目前是两个用户操作；V4 只串联现有能力，不改变其合同。
- `V3-ASR-DIAG-001`：`PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`，不得补做或写成 PASS。
- V4 进入前的全部未跟踪内容保持原样，不属于本任务提交。

## 5. 下一动作

完成 `V4-PLAN-001` 精确文档 diff 与 checkpoint 后，实现 `V4-FLOW-001`，先运行最贴近的聚焦测试，阶段收尾再运行一次完整矩阵。
