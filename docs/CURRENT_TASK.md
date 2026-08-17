# Current Task

- `STATE_REV: 2026-08-14.018`
- Active stage: `V3-SAVE-EXPORT-001`
- Last closed stage: `V3-CLEAN-001 / ACCEPTED / USER_CONFIRMED / COMMIT_DEFERRED`
- Paused stage: `V3-EDITOR-003 / COMPONENT_IMPLEMENTED / INTEGRATION_PAUSED_FOR_GOVERNANCE`
- Next candidate: resume `V3-EDITOR-003` only after the current save/export incident closes
- Gate: `HUMAN_DECISION / MIUI_PICKER_SELECTION_REQUIRED`
- Development: `REPAIR_BUILT_INSTALLED / DEVICE_REVERIFY_BLOCKED`
- Runtime topology target: `Primary/root coordinator shell -> internal Brain -> Brain-owned Limbs`
- Active Brain canonical path: `/root/brain_architecture_audit_replacement`
- Brain writer: active internal Brain only
- HEAD: `9a798ccb3890128565a12c924c11e6468908a2b9`

## 当前目标：V3-SAVE-EXPORT-001

在不丢失手机当前 App 进程内已完成视频与字幕状态的前提下，先保存可恢复项目，再定位并修复真实 FFmpegKit/MediaStore 导出失败。首次采证前禁止安装、强停、清数据、重启 App 或手机；首次证据与必要持久信息安全保存后才允许 `adb install -r`，始终禁止清数据。

## 唯一冻结故障矩阵

矩阵编号：`V3-SAVE-EXPORT-001-M1`。

| ID | 验收条件 | 必须证据 | 失败状态 |
|---|---|---|---|
| `SAVE-01` | 当前运行态通过系统文档入口保存为非零 `.lcp` 工程归档（不是 MP4）；可由 `ProjectArchive` 读取，并在覆盖安装后恢复视频引用、字幕、样式与布局 | 保存前后 UI、脱敏日志、文档记录存在/大小、archive 读取或恢复结果；用户提供的既有外部 `.lcp` 只作脱敏恢复线索 | 保存取消、空文件、序列化失败、不可读、误当成视频或恢复字段丢失均失败 |
| `EXPORT-01` | 产品入口调用真实 `FfmpegKitSubtitleExporter`，FFmpegKit 会话成功完成且 return code 为 0；不得走 mock/fallback/固定成功 | `export_started`、FFmpegKit 窄日志、return code、目标复制/发布边界 | 未进入 FFmpegKit、非零/空 return code 或异常提前结束均失败 |
| `EXPORT-02` | MediaStore 发布非零 MP4，媒体探测同时存在视频流与音频流、时长大于 0，并可由系统/Media3 实际播放 | 新增 MediaStore 行、非零大小、脱敏 URI 标识、视频/音频 MIME 或 codec、时长、回放结果 | 空行、pending 残留、缺任一流、零时长或不可播放均失败 |
| `EXPORT-03` | 保存/导出失败可见且安全；App 无崩溃、ANR，失败任务自有 MediaStore 行被回滚，源视频不被修改 | UI 失败状态、exit-info、窄日志、失败前后 MediaStore、源记录/大小或 hash（可安全获得时） | 崩溃、ANR、误删/覆盖源、泄漏失败行或虚假成功均失败 |
| `OBS-01` | 找到并记录最早失败边界，日志仅含事件、错误类型、return code、计数/大小/耗时等脱敏字段 | 有开始时间的窄 logcat、UI 层级、PID/Activity、FFmpegKit/MediaStore/exit-info 原始证据摘要 | 只有最终 toast/状态而无最早边界，或泄露字幕正文、私人 URI/路径、Key/Authorization 均失败 |

共同门禁：首次采证严格按“保存项目”后“导出视频”的顺序；只允许唯一设备；不得在首次采证前终止 App 进程。修复后必须通过 ASR Python、`testDebugUnitTest`、`lintDebug`、普通 Debug、Native Debug、AndroidTest、`git diff --check`，然后覆盖安装且不清数据，从已保存项目恢复并重新导出。编译成功不等于设备验收。

当前用户可见事实：`.lcp` 保存已成功生成，`SAVE-01` 仍缺覆盖安装后的恢复验证；导出明确进入 `Video export failed.`，分享入口禁用，因此不存在“UI 宣告成功但成片位置未知”的证据。故障定位集中于 `EXPORT-01..03/OBS-01`。用户截图中的敏感字段不得复述或进入证据。

首次现场证据已安全收敛：唯一设备；App PID 在采证前后连续存活；MainActivity 保持前台；两次既有用户导出事件均为 `export_started -> ffmpegkit_export_started -> 约 20ms 后 export_failed`，没有 FFmpeg session callback、return code、render/copy/publish 完成事件，也无残留任务 MediaStore 行；本轮无新 crash/ANR/exit。最早失败边界因此位于 `FfmpegKitSubtitleExporter` 的 FFmpeg session 创建之前。

Brain 根因裁决：当前代码在产品链路中先由 `MediaStoreExportGateway` 创建 task-owned pending row，再由 exporter 用通用 `ExportDestinationPolicy` 重新判断“新目标”；MediaStore 新 pending row 的 size 可为 `null`，现分类会将其当作 `EXISTING`，在 FFmpeg session 前立即拒绝。该跨层所有权分类错配是现有时间线与代码顺序共同支持的最小根因；同时 `onFailed` 丢弃 Throwable 造成 OBS-01 缺口。修复限定为：仅将“存在且 pending、size 为 null/0”的 MediaStore task row 视为可写新目标，保持普通文档/已发布/非零行 fail-closed；补充脱敏阶段/错误类型日志和纯策略回归，不改变 FFmpegKit/MediaStore 架构。

修复与构建已完成：MediaStore 专用分类、pre-session 固定阶段日志和 `onFailed` 脱敏错误类型已实现；focused、ASR 6/6、JVM、lint、普通/Native Debug、AndroidTest、diff check 均通过。Native Debug APK 为 417,446,841 bytes，SHA-256 `81967A7558961907694DA45CD974EEB3C5965B1085CC25316081D98C150E3573`；`adb install -r` 成功且未清数据。

当前唯一阻断：安装后产品“打开项目”已进入 MIUI 系统 picker，唯一非零 `.lcp` 候选大小 619 B；adb 触摸无法使该 picker 的 checkbox 选中，OK 始终 disabled。按权限边界不得注入私人 URI 或绕过 picker。需要用户在当前手机界面手动点选该 `.lcp` 并点击 OK；随后同一 Limbs 才能继续恢复校验与唯一一次导出复验。

## Brain 根因假设与判别实验

1. `H-SAVE-A`：系统 `CreateDocument` 结果被取消或 provider 未提供可写 URI；用 UI 层级、Activity 变化和 `project_save_*` 事件区分“未回调”与 repository 写失败。
2. `H-SAVE-B`：V7 直接编辑字段或当前 URI/样式状态在序列化、写入或回读时失败；保存后用非空记录和只读 archive 解析验证，失败只记录错误类别，不记录正文或 URI。
3. `H-EXPORT-A`：失败发生在 MediaStore session 建立、源内容读取或临时输入复制，FFmpegKit 尚未启动；以 `export_started`、MediaStore 新行、FFmpegKit session 事件的先后顺序判别。
4. `H-EXPORT-B`：真实 FFmpegKit 在字幕滤镜、字体、输入音轨映射或编码器处失败；以脱敏 error-level 日志、return code 与是否产生非零临时 MP4 判别。
5. `H-EXPORT-C`：FFmpegKit 成功但目标复制、SIZE 校验或 `IS_PENDING=0` 发布失败；以 render-complete、copy-complete、MediaStore size/pending 状态判别。
6. `H-EXPORT-D`：导出期间预览解码器释放、内存或进程稳定性导致崩溃/ANR；以 PID 连续性、exit-info、ANR/crash 与时间线判别，跨层根因由 Brain 裁决。

唯一执行 Agent：`V3-SAVE-EXPORT-001-L1 / Limbs-保存导出采证与修复`。它负责首次无损采证、按 Brain 精确合同修复、回归、构建、覆盖安装和恢复导出复验；不得修改三份活动文档，不得创建 Agent。

## 已完成前置门禁：GOV-MULTIAGENT-001

`PLATFORM_FORWARD_TEST=PASS`：replacement Brain `/root/brain_architecture_audit` 已恢复；只读子级运行 repo validator 得到 `REPO_CONTRACT_ENFORCED`，unittest `25/25` 通过；真实 runtime tree 为 `/root -> /root/brain_architecture_audit -> /root/brain_architecture_audit/limbs_architecture_forward`。该结论只确认平台层级和治理合同，不冒充 Android 产品验收。

## GOV-MULTIAGENT-001 已关闭目标（历史）

root 只承担运行时消息转发、活动 Brain 创建/复用和生命周期协调，不是第三个正式业务角色。唯一内部 Brain 负责证据解释、根因分析、方案决策、Limbs 拆分、活动文档和验收；Limbs 必须由 Brain 创建，负责实际开发、测试、构建、设备/证据验证。本阶段不创建外部 Codex 窗口、不操作手机、不提交或 push。

## GOV-MULTIAGENT-001 已关闭矩阵快照

矩阵编号：`GOV-MULTIAGENT-001-M1`。目标拓扑已由用户批准，不再请求路线批准。

| ID | 根因 | 具体文件与行为改变 | 判别测试 | 回滚方式 |
|---|---|---|---|---|
| `ARCH-01` | root 与 Brain 错误合并 | `AGENTS.md`、多 Agent 协议、Brain skill：root 只创建/复用内部 Brain；Brain 承担分析、决策、文档和验收 | 静态校验拒绝“主对话就是 Brain”；冷启动模拟必须得到 `root -> Brain` | 只反向应用本项治理 hunk，不整文件 checkout |
| `ARCH-02` | 没有可注册/复用的 Brain agent | `.codex/config.toml` 注册 `[agents.brain]`；新增 `.codex/agents/brain.toml`，固定职责、模型和结构化终态 | TOML 解析、双 Agent 注册、config 路径与字段契约通过 | 删除新增 Brain config，反向应用 config 单一 hunk |
| `ARCH-03` | 只有治理文本，缺少可执行路由/enforcement | 新增确定性 validator 与测试，校验 config、skill 路由、状态拓扑和角色不变量；明确 repo 无法硬拦截 Codex 平台工具 | 正例通过；Brain 缺失、root 直派 Limbs、错误 parent 等负例必须失败 | 删除新增 validator/测试；其他文件独立回退 |
| `ARCH-04` | Brain/Limbs 分析职责侵入 | 治理文件与 Agent config：Limbs 采证、执行判别实验和方案内局部修复；Brain 解释证据、跨层根因分析与路线裁决 | 职责契约；ANR/OOM/并发/架构问题必须回 Brain，编译错误可由 Limbs 自修 | 反向应用职责边界 hunk |
| `ARCH-05` | 自然语言未区分 `spawn_agent`/`create_thread` | begin/Brain skill 增加显式路由：启动 Brain 仅 internal spawn/reuse；仅用户明确要求独立侧边栏任务时允许 external thread | 路由正负例：`开 Brain` -> spawn/reuse；`新建独立 Codex 窗口` -> create_thread | 反向应用路由表 hunk |
| `ARCH-06` | Reviewer 历史残留和角色漂移 | `limbs.toml`、skill UI metadata 和治理文本只保留 Brain/Limbs；只读复核命名 `Limbs-验收` | 限定范围搜索无正式 `Reviewer` 残留 | 仅恢复对应描述 hunk |
| `ARCH-07` | 活动 Brain/parent-child 未持久记录 | 三份活动文档记录 active Brain canonical path；协议/validator 要求每个 Limbs 记录 parent 且等于 active Brain | 当前状态正例；缺 path、非 canonical path、parent 指向 root 的 fixture 必须失败 | 删除状态字段并反向应用对应校验 hunk |
| `ARCH-08` | `$begin` 仍把 primary 当 Brain | begin 改为 root 冷启动恢复或创建唯一内部 Brain，再由 Brain读状态、派 Limbs；root 禁止直派 Limbs | 冷启动 fixture 模拟：创建或复用 Brain、无 root->Limbs、无隐式 create_thread | 反向应用 begin 流程 hunk |

共同门禁：两个 TOML Agent 配置与注册可验证；begin/Brain skill frontmatter 及 `agents/openai.yaml` 合法，skill `quick_validate` 通过；确定性路由与拓扑正负测试通过；Reviewer 残留搜索、`git diff --check` 通过；从冷启动 fixture 模拟 `$begin` 成功。证据上限只能标记 `REPO_CONTRACT_ENFORCED`，不得把仓库脚本冒充 Codex 平台硬拦截；平台行为留给第二任务的新窗口前向验收。

## Brain 验收（2026-08-14）

| ID | 裁决 | 已实现证据 |
|---|---|---|
| `ARCH-01` | `PASS` | root 协调壳、内部 Brain、Brain-owned Limbs 三层拓扑已统一到治理文件和两个 skill |
| `ARCH-02` | `PASS` | `.codex/config.toml` 双注册；新增 `brain.toml`，Brain=`gpt-5.6-sol/medium`，Limbs=`gpt-5.6-luna/high` |
| `ARCH-03` | `PASS_AT_REPO_LEVEL` | Python 3.9 标准库 validator 输出 `REPO_CONTRACT_ENFORCED`；明确不能硬拦截平台工具 |
| `ARCH-04` | `PASS` | 跨层分析/裁决归 Brain；采证、判别实验、开发和验证归 Limbs；普通局部错误边界已收窄 |
| `ARCH-05` | `PASS` | internal `spawn_agent`、idle `followup_task`、running `send_message`、root-only explicit `create_thread` 均有正负测试 |
| `ARCH-06` | `PASS` | 受管治理范围无正式 Reviewer 残留；只读复核统一为 `Limbs-验收` |
| `ARCH-07` | `PASS_AT_REPO_LEVEL` | 两份状态文档记录同一 active Brain path；治理 Limbs Parent 与终态 `PARENT_BRAIN` 相等，错误 parent 负例通过 |
| `ARCH-08` | `PASS_AT_REPO_LEVEL` | 冷启动区分 persisted path 与 runtime live；persisted+not-live 创建同 leaf replacement，absent 创建默认 Brain |

Brain 复跑结果：repo validator PASS；unittest `25/25` PASS；两个 skill 使用 `python -X utf8 .../quick_validate.py` 均为 `Skill is valid!`；受管范围角色/歧义搜索 PASS；`git diff --check` 无 whitespace error。没有运行 Android/Gradle/ADB，不触碰手机当前项目。

## GOV-MULTIAGENT-001 已关闭 Limbs 账本

| TASK_ID | Limbs | 文件所有权 | Parent | 状态 |
|---|---|---|---|---|
| `GOV-MULTIAGENT-001-L1` | `Limbs-多Agent架构实现` | `AGENTS.md`、`.agents/multi-agent-development.md`、`.codex/config.toml`、`.codex/agents/*.toml`、两个项目 skill 及其 UI metadata、必要 validator/测试 | `/root/brain_architecture_audit` | `COMPLETE / BRAIN_ACCEPTED / 25 TESTS PASS` |

Limbs 不写三份活动文档、不分析或改变目标拓扑、不创建 Agent/窗口、不操作设备。配置模型为 `gpt-5.6-luna / high`；运行时确认不可获得时必须报告 `Unavailable`。

## 暂停保留：V3-EDITOR-003

把当前字幕卡片式编辑升级为视频预览区内的直接编辑：点击当前字幕后显示文本框边界；可拖动位置；左上角删除；右侧中点调整文本框宽度；右下角连续调整字号。编辑面板只保留“键盘”和“样式”，样式只包含基础样式、文字颜色和左/中/右对齐。

上一阶段 `V3-CLEAN-001` 的实现、回归、Native Debug 覆盖安装和 ANR 修复证据已完成；用户于 2026-08-14 明确确认现有 V3 任务全部完成，Brain 据此接受最终人工复验。由于当前工作树混有受保护的既有修改，阶段提交暂缓，不得批量暂存或冒充已提交。

## V3-EDITOR-003 暂停矩阵快照（非活动矩阵）

矩阵编号：`V3-EDITOR-003-M1`。

| ID | 验收条件 | 必须证据 |
|---|---|---|
| `E3-01` | 点击当前可见字幕后暂停预览、选中稳定 cue ID，并显示边框；点击画面空白处取消选中 | Compose UI/语义测试；真机截图与操作记录 |
| `E3-02` | 拖动字幕框只改变当前 cue 的 `xRatio/yRatio`，字幕始终位于实际视频有效画面内，不进入 FIT 黑边 | 纯几何单测；横/竖视频 UI 测试；真机拖动 |
| `E3-03` | 左上角删除按钮只删除当前 cue，不改变其他 cue 文本、样式和时间戳 | ViewModel/策略单测；UI 测试 |
| `E3-04` | 右侧中点只调整当前 cue `widthRatio`，左边界固定并遵守最小/最大边界 | 纯几何单测；手势 UI 测试 |
| `E3-05` | 右下角只连续调整当前 cue `fontSizeRatio`，不改变宽度、位置和时间戳 | 纯几何/状态单测；手势 UI 测试 |
| `E3-06` | 编辑面板只有“键盘/样式”；没有“读文字/模板/字体”；键盘可分别编辑英文和中文且不被 IME/系统栏遮挡 | UI 契约测试；Pixel 8 截图；真机输入 |
| `E3-07` | 样式仅含基础样式、统一文字颜色、左/中/右对齐；基础样式预览与 FFmpegKit 导出一致 | 模型/渲染/ASS 单测；导出帧对比 |
| `E3-08` | 项目保存恢复后逐 cue 位置、宽度、字号和样式不丢失；普通预览、全屏预览、导出共用坐标与样式解析 | archive round-trip；预览/导出契约测试 |
| `E3-09` | 常规回归通过，最终 Native Debug APK 可安装；真机完成选择、拖动、删除、宽度、字号、键盘和样式链路 | ASR Python、JVM、lint、普通/Native Debug、AndroidTest、安装和真机证据 |

## 明确非目标

- 不实现模板、字体库、读文字、旋转、动画、贴纸、时间轴重构或抖音其他功能。
- 不替换 Kotlin、Compose、Material 3、ViewModel、Media3、FFmpegKit 或当前持久化架构。
- 不通过只改预览而忽略导出、固定结果、mock、隐藏 fallback 或降低标准冒充通过。
- 不下载新字体/大型依赖，不清理 App 数据，不 reset/clean/push，不覆盖未知工作树内容。

## 冻结技术契约

- 每个 cue 仍是一个双语文本框；键盘页分别编辑英文和中文，位置、宽度、字号、颜色、对齐共同作用于该 cue。
- 手势以 `CaptionGeometryResolver.effectiveVideoRect()` 的真实视频矩形换算为归一化源视频坐标。
- 拖动过程中可使用 Compose 本地临时值实时预览；手势结束后按稳定 cue ID 一次提交 ViewModel，并调用既有派生输出失效策略。
- 视觉手柄不要求 48dp，但触控目标不得小于 48dp，并提供明确中文无障碍语义。
- 统一文字颜色同时写入当前 cue 的英文和中文颜色；描边/背景由基础样式预设控制，不增加独立字体面板。
- 如基础样式需要背景字段，字段必须向后兼容旧项目，并由 Compose 预览和 FFmpegKit/ASS 同时消费。

## Brain–Limbs 账本

| TASK_ID | Limbs | 文件所有权 | 依赖 | 状态 |
|---|---|---|---|---|
| `V3-EDITOR-003-L1` | `Limbs-直接编辑基础` | 字幕样式/布局模型、新增纯几何与预设策略及其单测 | 无 | `COMPLETED / JVM 314 PASS` |
| `V3-EDITOR-003-L2` | `Limbs-预览交互UI` | `EditorScreen.kt`、专属 UI 契约测试 | L1 | `COMPONENT_IMPLEMENTED / JVM 334 PASS` |
| `V3-EDITOR-003-L3` | `Limbs-编辑状态写入` | `MainViewModel.kt`、ProjectArchive v7 单写迁移、专属状态/持久化测试 | L1 | `COMPONENT_IMPLEMENTED / JVM 334 PASS` |
| `V3-EDITOR-003-L4` | `Limbs-预览导出一致性` | 渲染/FFmpegKit 文件及专属测试 | L1 | `COMPONENT_IMPLEMENTED / JVM 334 PASS` |
| `V3-EDITOR-003-L5` | `Limbs-集成验证` | 集成修复、AndroidTest、构建、安装、真机验证 | L2/L3/L4 | `PLANNED` |
| `V3-EDITOR-003-L6` | `Limbs-验收` | 只读 | L5 | `PLANNED` |

并行规则：L1 先固定共享数据与函数契约；L1 完成后 L2/L3/L4 并行，文件范围不得重叠；全部返回后再执行 L5；最终由 L6 只读复核，Brain 作正式裁决。

## 模型资源

- Brain 配置值：`gpt-5.6-sol / medium`。
- Limbs 项目配置值：`gpt-5.6-luna / high`。
- 运行时确认值：各 Limbs 在结构化终态中回报；不可获得时记为 `Unavailable`。

## V3-SAVE-EXPORT-001 Limbs 账本

| TASK_ID | Limbs | 文件所有权 | Parent | 状态 |
|---|---|---|---|---|
| `V3-SAVE-EXPORT-001-L1` | `Limbs-保存导出采证与修复` | `ExportDestinationPolicy`、`FfmpegKitSubtitleExporter`、`MainViewModel` 的最小观测/所有权修复及专属测试；回归、构建、覆盖安装、`.lcp` 恢复和导出复验 | `/root/brain_architecture_audit_replacement` | `BLOCKED / USER_PICKER_SELECTION_REQUIRED` |

## 退出与未完成状态

- 全部实现和集成证据完成：`READY_FOR_BRAIN_REVIEW`，随后自动启动只读 `Limbs-验收`。
- 任一矩阵项失败、构建失败、设备证据缺失或预览/导出不一致：保持 `STAGE_IN_PROGRESS`。
- 只有需要改变核心媒体链路、持久化架构、权限、安全或破坏性操作且现有规则无法裁决时进入 `HUMAN_DECISION`。

## 下一动作

用户在当前手机 MIUI picker 中选择唯一 `.lcp` 并点击 OK。完成后复用同一 `Limbs-保存导出采证与修复`：核对 ProjectArchive 恢复的媒体引用、4 条字幕和样式/布局（不输出正文/URI），再只导出一次并验证真实 FFmpeg return code、非零已发布 MediaStore MP4、音视频双流、时长和可播放性。
