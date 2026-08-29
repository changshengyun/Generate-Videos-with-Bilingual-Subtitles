# Current Task: V5-RELEASE-001

- `STATE_REV: 2026-08-30.005`
- `TASK_REV: V5-RELEASE-001.001`
- Stage state: `MATRIX_DEFINED / IN_PROGRESS`
- Product status: `V5_RELEASE_PREPARATION`
- Evidence ceiling: `BUILD_VERIFIED`
- Device gate: `NO_DEVICE_ACTION`
- Push gate: `PUSH_AUTHORIZED_ORIGIN_DEV_ONLY`

## 1. 阶段目标

把当前精简完成的 `716f2f4` 基线作为 V5 首个版本发布到远端 `dev` 分支。版本身份统一为 `versionName 5.0.0 / versionCode 5000`;同步 README、路线和项目状态。此阶段只改变版本身份与发布说明,不新增功能或改变运行时行为。

## 2. 主版本升级提案

| 项目 | 结论 |
|---|---|
| 证据 | 当前 V4.4.0 精简版本已达到 `PASS / BUILD_VERIFIED`;本地 `dev` 从 `716f2f4` 创建;远端不存在 `origin/dev`。 |
| 影响 | Android 包名、数据格式、模型、权限和运行链路不变;仅版本号、当前版本说明、活动状态与 Git 分支发布状态变化。 |
| 备选方案 | 保持 V4.4.0、仅创建标签、或继续推送 main;用户已明确选择 V5.0 并推送 `dev`。 |
| 回滚 | 不改写历史、不 force push;如需撤回,在 `dev` 上 revert V5 发布提交,原 V4.4 标签与 main 历史保持不变。 |
| 建议 | 使用 `5.0.0 / 5000`,只推送 `origin/dev`,不创建 V5 标签,待后续正式发布决策再处理 main/tag。 |

## 3. 冻结验收矩阵

| 类别 | 冻结内容 |
|---|---|
| 主链路 | 当前精简基线 → 切换 V5 版本身份 → 同步发布文档 → 普通/Native 构建验证 → 精确提交 → 推送 `origin/dev`。 |
| 必须证据 | `app/build.gradle.kts` 为 `5.0.0 / 5000`;README 与三份活动文档无冲突的当前版本声明;ASR Python 6/6;`testDebugUnitTest` 实际结果;`lintDebug`;普通/Native Debug;普通/Native AndroidTest;Native APK 含双 ABI Whisper;`git diff --check`;远端 `origin/dev` 指向最终提交。 |
| 禁止事项 | 不修改业务源码、Whisper、FFmpegKit、DeepSeek Prompt、歌词检索、响应合同、时间戳、BYOK/Keystore、权限、`.lcp`、导出、ABI、依赖或 `third_party`;不操作设备;不 push main;不创建标签;不 force push。 |
| 退出状态 | 版本与文档同步、所有构建门禁通过、最终提交成功推送且 `origin/dev` 与本地 HEAD 一致时,标记 `PASS / BUILD_VERIFIED / PUSHED`。 |
| 未完成状态 | 构建失败为 `PARTIAL_PASS`;远端 dev 在推送前出现独立历史或 push 被拒绝时为 `HUMAN_DECISION`;网络暂时失败但本地提交完成时为 `BLOCKED / PUSH_PENDING`。 |

## 4. 允许修改范围

- `app/build.gradle.kts`
- `README.md`
- `docs/DEVELOPMENT_ROADMAP.md`
- `docs/CURRENT_TASK.md`
- `docs/PROJECT_STATE.md`

## 5. 下一动作

提交阶段 checkpoint,再修改版本号和发布说明。
