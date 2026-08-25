# LyricCaptioner V4 Project State

- `STATE_REV: 2026-08-26.018`
- Repository: `D:\DevData\Codex\.codex\worktrees\c3dc\lyric-captioner-android`
- Branch: `codex/v4-caption-quality-001`
- V4 baseline HEAD: `d410a8da3b57ff7de32d754213b5cc896db34c55`
- Current implementation HEAD: `2a60171`
- Current task: `V4-EDITOR-CONTROL-001`
- Stage state: `PARTIAL_PASS / COMPONENT_VERIFIED`
- Product status: `EDITOR_CONTROLS_IMPLEMENTED / NOT_INSTALLED`
- Current gate: `NOT_INSTALLED / USER_DEVICE_VALIDATION_PENDING / NO_AGENT_DEVICE_ACTION`
- Evidence ceiling: `COMPONENT_VERIFIED`
- Last state sync: 2026-08-26

## 当前决定

- V4 是新的当前产品版本；V3 只保留历史证据。
- V3 未完成的生产 base 验证固定记录为 `V3-ASR-DIAG-001 / PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`，不补做、不改写为 PASS，也不阻塞 V4。
- 唯一产品主链路为：相册导入 → 一次点击开始识别 → 本地 Whisper → 自动 AI 增强 → 自动进入字幕编辑 → 添加或编辑字幕 → 预览并导出。
- `V4-E2E-001` 因用户接管真机测试固定为 `PARTIAL_PASS / DEVICE_VALIDATION_DEFERRED_BY_USER`；没有真实 AI、导出、回放或截图证据。
- 用户批准 `V4-CAPTION-QUALITY-001` 修改 enhancement Prompt、响应合同和 cue 拆分策略；仍不授权更换模型、新增依赖、改变媒体/存储架构或破坏性操作。
- RAW_ASR 只作为内部输入；云端或显式标注的本地回退原子提交唯一最终批次，预览、保存和导出不得混合来源。
- 真机终验改由用户执行；本阶段 Agent 不连接、安装或操作设备。
- `V4-CAPTION-QUALITY-001` 已实现 enhancement v4、canonical 原文/双句拆分、唯一最终批次、可读性提示、人工拆分、主页面逐 cue 编辑与共享播放器生命周期；独立复审最终 `PASS`。
- 用户真机反馈证明上一阶段仍有英文编辑清空中文、人工拆分边界不可靠、全屏控制不可见、全屏字幕不可编辑和 AI 修复质量不足；上一阶段保持 `PARTIAL_PASS`，新任务 `V4-CAPTION-REPAIR-001` 的矩阵已冻结。
- 新任务批准在现有 Provider 内执行第一次整批增强、自动拆分和第二次整批局部修复；第二阶段失败保留完整首轮结果待审核，人工单 cue AI 结果必须预览后应用。
- `V4-CAPTION-REPAIR-001` 已实现 enhancement v5、canonical 区间与原文约束、一次整批子 cue 修复、首轮安全回退、处理等级持久化、安全人工拆分、单 cue 建议预览、样式 Bottom Sheet、全屏控制与字幕直接编辑；独立复审 `PASS`。
- `3fa18cc` 已按用户决定把最终歌词匹配置信度门槛调整为 30%；新任务不再修改歌曲匹配、AI Prompt、LRCLIB、canonical 或处理等级。
- `V4-EDITOR-CONTROL-001` 验收矩阵已冻结，只实现两个独立编辑锁、固定式非模态样式面板和相邻字幕合并；最终不安装手机。
- `V4-EDITOR-CONTROL-001` 已完成普通/全屏共享布局锁、独立样式锁、按属性全局/单条写入、固定非模态样式面板和相邻字幕合并；未修改歌曲匹配、AI Prompt、LRCLIB、canonical、处理等级或依赖。
- 简单变更按 S0 只检查精确 diff；普通功能按 S1 聚焦验证；复杂故障按 S2 证据优先。三次修复失败后冻结修改并只运行一个最小判别实验。

## 当前验收门禁

- `V4-FLOW-001` 与 `V4-EDITOR-001` 已达到 `PASS / COMPONENT_VERIFIED`。
- `V4-UI-001` 达到 `PARTIAL_PASS / COMPONENT_VERIFIED / SIMULATOR_BLOCKED`；Pixel 8 因已有 snapshot pending 无法启动，未取得新截图或 instrumentation 证据。
- 本阶段最新收尾：ASR Python 6/6；聚焦 Provider 11/11；全量 JVM 372 中 369 通过，3 项仅因隔离 worktree 无法发现原仓库已有 OPUS-MT/Whisper 外部 fixture 而失败；lint、普通/Native Debug、普通/Native AndroidTest 构建全部成功。
- `V4-CAPTION-QUALITY-001` 达到 `PARTIAL_PASS / COMPONENT_VERIFIED / USER_DEVICE_VALIDATION_PENDING`；实现与冻结矩阵逐项证据见 `docs/CURRENT_TASK.md`。
- `V4-CAPTION-REPAIR-001` 达到 `PARTIAL_PASS / COMPONENT_VERIFIED / USER_DEVICE_VALIDATION_PENDING`；Native APK 116,019,973 bytes，SHA-256 `F4207AC2A6988C0B4C8D189D04B2DFF498EBAC423672FBB286538A3CE3D3ABC1`。真实 DeepSeek、设备截图、导出和回放仍由用户终验。
- `V4-EDITOR-CONTROL-001` 达到 `PARTIAL_PASS / COMPONENT_VERIFIED / NOT_INSTALLED`；聚焦 JVM 24/24、ASR 6/6、lint、普通/Native Debug 与 AndroidTest 构建成功，独立复审 `PASS`。全量 JVM 382 项中 379 通过，3 项仍为隔离 worktree 外部模型 fixture 缺失。
- 当前 Native APK 116,019,973 bytes，SHA-256 `10AFAE4F4B2E05CAC04629A39A87BE2A4D10105CEB2A4F921F65C74AA90CF76D`；未安装、未运行 instrumentation，真机交互证据待用户验证。
- 在取得完整主链路证据前仍保持 `COMPONENT_VERIFIED` 证据上限；真实 AI、真实设备、真实导出与回放结果必须按实际执行提升或保留为未完成。
- 阶段实现与构建成功不等于完整 V4 产品 PASS。

## 受保护工作树

保留所有进入 V4 前的未跟踪或脏内容，包括 `.emulator-test-assets/`、`.env`、`dist/`、`docs/debug/ASR_SMALL_BASE_VERSION_COMPARISON.md`、`tools/opus-mt-en-zh/` 和未知内容。不得 reset、clean、覆盖、批量暂存或 push。

## 下一允许动作

交付 `V4-EDITOR-CONTROL-001` Native APK，由用户自行安装并执行真机交互验证。Agent 不安装或操作设备；后续仅在用户明确授权的新动作范围内继续。

## 权威资料

- 路线：`docs/DEVELOPMENT_ROADMAP.md`
- 唯一活动任务与冻结矩阵：`docs/CURRENT_TASK.md`
- 既有 AI 需求与路线背景：`docs/REQUIREMENTS.md`、`docs/TECH_OPTIONS.md`、`docs/ENVIRONMENT_REPORT.md`、`docs/SPIKE_PLAN.md`
- V3 历史证据：`docs/archive/v3/`
