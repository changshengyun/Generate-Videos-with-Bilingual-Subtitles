# LyricCaptioner V3 开发路线

- `ROADMAP_REV: 2026-08-12.003`
- 当前任务：`V3-AI-001 / COMMITTED`
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
| `V3-CLEAN-001` | `PLANNED` | 依赖 V3-AI-001 验收；删除非主链路分支 |
| `V3-E2E-003` | `PLANNED` | 依赖前述阶段；ARM64 真机最终验收 |

## 当前和下一阶段

- 当前：`V3-AI-001.008` 已通过 Reviewer 并提交，歌词检索、整首上下文翻译、span 对齐和 SRT 真机专项证据已冻结。
- 下一：如继续开发，先为 `V3-CLEAN-001` 建立验收矩阵；不得把本阶段单歌曲 SRT 证据升级为完整产品链路验收。
- 最终：`V3-E2E-003` 在目标 ARM64 手机完成导入、识别、增强、编辑、恢复、导出和回放。

## 跨阶段不变量

- 不改变 V2 已验证的本地 Whisper、OPUS-MT、FFmpegKit 和 Media3 基线，除非用户重新授权。
- API Key 只在 App 内通过 Android Keystore 保护，不能进入源码、聊天、日志、测试夹具或活动文档。
- 真机、模拟器、组件和构建证据必须分级记录。
- 每个阶段只有一份验收矩阵；Reviewer 只在整个阶段进入 `READY_FOR_REVIEW` 后自动启动。
- 阶段提交只在 Reviewer `ACCEPTED` 且活动文档同步后创建，提交信息使用中文。

## V3 总体验收

正式 V3 PASS 需要目标 ARM64 真机通过系统相册导入、真实 Whisper、DeepSeek 增强或本地回退、逐 cue 编辑、预览一致性、MediaStore 导出和 Media3 回放，并证明取消、恢复、隐私、源文件安全及视觉可用性。缺少任一真机主链路证据时只能记录对应的部分状态。
