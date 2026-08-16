# LyricCaptioner V3 Project State

- `STATE_REV: 2026-08-14.018`
- Repository: `D:\DevEnv\Projects\lyric-captioner-android`
- Branch: `migration/lyric-captioner-history`
- HEAD: `9a798ccb3890128565a12c924c11e6468908a2b9`
- Current gate: `V3-SAVE-EXPORT-001 / HUMAN_DECISION / MIUI_PICKER_SELECTION_REQUIRED`
- Development: `ACTIVE`
- Last state sync: 2026-08-14

## 已关闭阶段

- `V3-CLEAN-001` 的 ANR 根因已由设备 exit-info 定位到主线程歌词 verifier；重计算已移至 `Dispatchers.Default`。
- focused `9/9`、ASR `6/6`、JVM `307/307`、lint、普通/Native Debug、AndroidTest 和 diff check 已通过；最终 Native APK 已覆盖安装并保留数据。
- 用户于 2026-08-14 明确确认当前 V3 既有任务全部完成，Brain 接受该人工产品验收并关闭 `V3-CLEAN-001`。
- 由于工作树混有大量受保护的既有修改，关闭状态为 `ACCEPTED / USER_CONFIRMED / COMMIT_DEFERRED`，不得伪称已提交或批量暂存。

## 平台前向验收

- `PLATFORM_FORWARD_TEST=PASS`。
- replacement Brain canonical path 为 `/root/brain_architecture_audit`；真实树为 `/root -> internal Brain -> Brain-owned Limbs`。
- 只读 Limbs 执行 repo validator 成功、unittest `25/25` 成功，且未执行 ADB/设备操作。
- 模型配置值：Brain `gpt-5.6-sol / medium`，Limbs `gpt-5.6-luna / high`；运行时确认值均为 `Unavailable`。

## 当前阶段

- 唯一活动阶段：`V3-SAVE-EXPORT-001`。
- 唯一冻结矩阵：`V3-SAVE-EXPORT-001-M1`，覆盖 `SAVE-01`、`EXPORT-01..03`、`OBS-01`，详见 `docs/CURRENT_TASK.md`。
- `SAVE-01` 的产物是 `.lcp` 工程归档而非 MP4；用户提供的既有外部 `.lcp` 路径属于私有信息，只允许脱敏核对恢复链路。视频成片只由 `EXPORT-01..03` 验证。
- 用户已确认 `.lcp` 保存成功；恢复验证仍缺。导出 UI 明确为 `Video export failed.` 且分享入口禁用，故障范围已收窄到真实导出与观测边界，不存在已宣告成功的 MP4。
- 首次证据已保存到活动状态：唯一设备、PID 连续、无新 crash/ANR；失败发生在 `ffmpegkit_export_started` 后约 20ms 且早于 FFmpeg session/return code，任务 MediaStore 行已回滚无残留。
- Brain 选定最小修复：修正 task-owned pending MediaStore 空行的所有权分类，并补脱敏 pre-session 错误边界；普通文档、已发布或非零目标继续 fail-closed，不改变核心媒体架构。
- 最小修复、focused/ASR/JVM/lint/普通与 Native Debug/AndroidTest/diff check 均通过；Native APK 已 `install -r` 成功且未清数据。
- 安装后产品打开项目已进入 MIUI picker，唯一候选 `.lcp` 为非零 619 B；自动化无法勾选，OK 按钮保持禁用。为避免绕过系统 picker/私人 URI，恢复与导出复验暂停，等待用户一次手动选择。
- 目标拓扑：`Primary/root 协调壳 -> 唯一内部 Brain -> Brain-owned Limbs`；root 不是第三个正式业务角色。
- Active Brain canonical path: `/root/brain_architecture_audit_replacement`。
- 本阶段只允许一个 `Limbs-保存导出采证与修复` 贯穿设备采证、精确修复、构建安装和复验；三份活动文档只由 active Brain 写。
- repo validator 已输出 `REPO_CONTRACT_ENFORCED`；25/25 正负 unittest、两个 skill 官方 quick validate、角色残留搜索和 diff check 通过。
- 冷启动合同已区分 persisted canonical path 与 runtime liveness：live 才复用；not-live 创建同 leaf replacement；idle/running 分别使用 `followup_task`/`send_message`；Brain 永远不能调用 `create_thread`。
- repo 可以提供确定性入口/契约校验，但不能硬拦截 Codex 平台工具；真实运行时层级仍必须由第二任务的新窗口冷启动验收。

## 暂停保留的 V3-EDITOR-003

- 状态：`COMPONENT_IMPLEMENTED / INCIDENT_BUILD_INSTALLED / INTEGRATION_NOT_ACCEPTED`。
- L1–L4 代码已完成，JVM 334/334；保存/导出故障修复按用户要求构建并安装了当前完整工作树，因此这些暂停代码已随 incident APK 进入设备，但 L5/L6 仍未执行，不能标记为已集成或验收。
- 原 `V3-EDITOR-003-M1` 全量矩阵与技术契约保留在 `CURRENT_TASK.md` 的非活动快照中，治理验收后恢复。
- 手机内用户已处理完成、待保存/导出的项目是受保护运行状态；本阶段禁止 install、force-stop、启动 App、点击设备或清数据。

## 下一允许动作

用户在当前 MIUI picker 中手动选择唯一 `.lcp` 并点击 OK；随后复用同一 Limbs 完成恢复和唯一一次导出复验。完成前不得宣称 SAVE 可恢复或导出修复已真机 PASS。

## 受保护工作树

保留任务开始前的 `.gitignore`、`AGENTS.md`、`docs/V3_PRODUCT_ARCHITECTURE.md`、`third_party/ffmpeg-kit` 和所有未知未跟踪内容；不得 reset、clean、批量暂存或 push。新阶段仅修改冻结矩阵授权的文件。
