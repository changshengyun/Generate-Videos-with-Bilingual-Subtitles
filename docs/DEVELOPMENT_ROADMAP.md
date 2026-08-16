# LyricCaptioner V3 开发路线

- `ROADMAP_REV: 2026-08-14.018`
- 当前任务：`V3-SAVE-EXPORT-001 / HUMAN_DECISION / MIUI_PICKER_SELECTION_REQUIRED`
- 当前历史摘要：[`archive/v3/V3_STAGE_HISTORY_2026-08-12.md`](archive/v3/V3_STAGE_HISTORY_2026-08-12.md)

## 文档职责

本文件只维护 V3 产品目标、阶段顺序、依赖和总体验收。唯一活动任务见 `CURRENT_TASK.md`，实时门禁见 `PROJECT_STATE.md`。V2 归档位于 `../docs-v2/`，关闭的 V3 证据位于 `archive/v3/`。

## V3 产品目标

1. Whisper 单模型进程级缓存，完成后保留 3–5 分钟，并在超时、模型切换、严重内存压力或不安全取消后释放。
2. 产品主链路统一为：相册导入 → 本地识别 → 云端歌词匹配与双语修正/本地回退 → 用户主动编辑 → 相册导出。
3. 所有字幕共享视频有效画面内的文本框布局，每段字幕可独立覆盖字体样式。
4. 普通预览、全屏预览和 FFmpegKit 导出使用同一源视频坐标和样式解析规则。
5. 导入和导出默认且只使用系统相册能力。
6. 删除 App 自有顶栏、测试标题和版本标签，保留系统栏与 Insets。
7. DeepSeek 只接收 cue ID、时间戳和英文文本；失败时保留英文并使用本地 OPUS-MT。
8. 最终只保留模型识别主链路和网络失败本地翻译回退；其他导出分支经清理矩阵证明后删除。
9. 字幕编辑采用视频画面内直接操作：点击选中、拖动位置、左上删除、右侧拉伸宽度、右下调整字号；底部只保留键盘与样式，样式只含基础样式、文字颜色和对齐。

## 阶段顺序

| 阶段 | 当前状态 | 目标/依赖 |
|---|---|---|
| `V3-DEC-001` | `PASS` | 冻结 V3 产品与技术边界 |
| `V3-AI-CONTRACT-001` | `PARTIAL_PASS / LIVE_LYRICS_FLOW_DEFERRED` | BYOK 与认证基线；为 AI 主链路提供安全合同 |
| `V3-ASR-SESSION-001` | `PARTIAL_PASS / COMPONENT_VERIFIED` | Whisper 缓存基础；真机性能归入最终积压 |
| `V3-EDITOR-001` | `PARTIAL_PASS / REWORKED` | 初始编辑模型 |
| `V3-EDITOR-002` | `PARTIAL_PASS / COMPONENT_VERIFIED` | 每 cue 样式、统一预览/导出解析 |
| `V3-MEDIA-001` | `PARTIAL_PASS / COMPONENT_VERIFIED` | Photo Picker 与 MediaStore 唯一媒体入口 |
| `V3-UI-001` | `PARTIAL_PASS / COMPONENT_VERIFIED` | 产品化交互外壳 |
| `V3-AI-001` | `ACCEPTED / SRT_DEVICE_VERIFIED` | 单歌曲对齐与错位 SRT 真机样本通过；不包含视频、Whisper 或完整端到端验证 |
| `V3-CLEAN-001` | `ACCEPTED / USER_CONFIRMED / COMMIT_DEFERRED` | ANR 修复、完整回归、Native Debug 覆盖安装和用户人工复验均已完成；因受保护脏工作树暂缓阶段提交 |
| `V3-EDITOR-003` | `COMPONENT_IMPLEMENTED / INCIDENT_BUILD_INSTALLED / INTEGRATION_NOT_ACCEPTED` | L1–L4 代码随保存/导出 incident APK 被动构建安装；L5/L6 未执行，仍不得冒充已集成或验收 |
| `GOV-MULTIAGENT-001` | `PLATFORM_FORWARD_TEST_PASS` | 仓库合同通过 25 项正负测试，且真实 Codex runtime 已验证 `root -> internal Brain -> Brain-owned Limbs` |
| `V3-SAVE-EXPORT-001` | `REPAIR_BUILT_INSTALLED / USER_PICKER_SELECTION_REQUIRED` | 根因修复和构建安装已完成；等待 MIUI picker 一次人工选择后恢复 `.lcp` 并复验真实 MediaStore 视频导出 |
| `V3-E2E-003` | `PLANNED` | 依赖前述阶段；ARM64 真机最终验收 |

## 当前和下一阶段

- 当前：`GOV-MULTIAGENT-001` 已通过真实平台前向验收；`V3-SAVE-EXPORT-001-M1` 已冻结，先保护并保存手机当前项目，再定位修复导出失败。
- 下一：保存/导出故障关闭后恢复 `V3-EDITOR-003` 集成；其已暂停代码保留且不冒充已构建安装。
- 最终：`V3-E2E-003` 在目标 ARM64 手机完成导入、识别、增强、编辑、恢复、导出和回放。

## 跨阶段不变量

- 不改变 V2 已验证的本地 Whisper、OPUS-MT、FFmpegKit 和 Media3 基线，除非用户重新授权。
- API Key 只在 App 内通过 Android Keystore 保护，不能进入源码、聊天、日志、测试夹具或活动文档。
- 真机、模拟器、组件和构建证据必须分级记录。
- 每个阶段只有一份验收矩阵；全部实现和集成完成后才启动只读 `Limbs-验收`。
- 阶段提交只在 Brain 裁决 `ACCEPTED` 且活动文档同步后创建，提交信息使用中文。

## V3 总体验收

正式 V3 PASS 需要目标 ARM64 真机通过系统相册导入、真实 Whisper、DeepSeek 增强或本地回退、逐 cue 编辑、预览一致性、MediaStore 导出和 Media3 回放，并证明取消、恢复、隐私、源文件安全及视觉可用性。缺少任一真机主链路证据时只能记录对应的部分状态。
