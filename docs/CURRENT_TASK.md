# Current Task: V4-CAPTION-QUALITY-001

- `STATE_REV: 2026-08-25.013`
- `TASK_REV: V4-CAPTION-QUALITY-001.001`
- Stage state: `MATRIX_DEFINED / IN_PROGRESS`
- Product status: `CAPTION_QUALITY_AND_EDITOR_INTEGRATION_IN_PROGRESS`
- Evidence ceiling: `COMPONENT_VERIFIED`
- Device gate: `USER_LED_DEVICE_VALIDATION / NO_AGENT_DEVICE_ACTION`

## 1. 阶段目标

在不更换 Whisper、Media3、FFmpegKit、存储架构、技术栈或依赖的前提下，完成唯一最终字幕批次、已验证歌词英文纠错、双句 cue 拆分、长字幕复核和主页面内联编辑；保留一次点击 ASR → 增强 → 编辑主链路，并保证普通/全屏播放控制同步。

## 2. 冻结验收矩阵

| 类别 | 冻结内容 |
|---|---|
| 主链路 | 系统相册导入视频 → 一次点击“开始识别” → RAW_ASR 仅内部暂存 → DeepSeek 完整批次或允许的本地回退原子提交 → 自动进入按时间排序的主页面字幕编辑 → 已验证双句融合 cue 自动拆分、普通长 cue 可人工拆分 → 修改中英文、时间和逐 cue 字体/样式 → 保存恢复 → 最终单一字幕批次预览并导出。 |
| 必须证据 | 新增强合同与 validator/coordinator 聚焦测试；canonical 英文纠错、1→2 cue、比例拆时、非法响应拒绝、RAW_ASR 不可见、回退来源、可读性提示、人工拆分、保存恢复和旧导出失效测试；UI 合同/instrumentation 覆盖内联列表、样式展开、字体、长列表/IME、普通与全屏控制；冻结 Android 回归构建。真实设备截图、真实 AI、导出与 Media3 回放由用户终验，缺失时不得越过组件证据上限。 |
| 禁止事项 | 不更换或下载模型，不新增依赖，不改变 Kotlin/Compose/Media3/FFmpegKit/存储架构，不清 App/设备数据，不运行 Agent 真机操作，不 reset、clean、批量暂存或 push；本地回退必须显式标注，不得伪装为云端纠错。 |
| 退出状态 | 代码、聚焦测试、完整回归和允许环境内 UI 验证全部通过，且真实 AI、真机截图、MediaStore 导出与 Media3 回放证据齐全时，才能标记 `PASS / DEVICE_VERIFIED`。 |
| 未完成状态 | 组件与构建通过但缺少用户真机证据为 `PARTIAL_PASS / COMPONENT_VERIFIED / USER_DEVICE_VALIDATION_PENDING`；外部 Key/歌词 fixture 缺失为对应 `PARTIAL_PASS`；只有新增依赖、架构变化或无法安全验证的范围扩张才返回 `HUMAN_DECISION`。 |

矩阵已依据用户批准的“字幕质量与编辑器整合计划”冻结。本阶段明确允许在 enhancement 包内升级 Prompt、响应合同和 cue 拆分时间策略；该授权不扩展到模型、依赖、核心媒体或存储架构。

## 3. 已验证基线

- `V4-E2E-001`：`PARTIAL_PASS / DEVICE_VALIDATION_DEFERRED_BY_USER`；未取得真实 AI、导出、回放或截图，不改写为 PASS。
- `V4-FLOW-001`：`990207b`，一次点击 ASR → AI → 编辑，组件验证通过。
- `V4-EDITOR-001`：`a342db9`，插入与编辑基础能力组件验证通过。
- `V4-UI-001`：`d4ef61d`，全屏共享播放器和独立控制行已有组件实现，真机 UI 仍待验。
- `V3-ASR-DIAG-001` 固定为 `PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`。

## 4. 实施边界

- RAW_ASR 不得写入最终可见/可导出字幕列表；云端或本地回退只允许原子提交一个完整批次。
- 已验证歌词允许一个 source cue 映射为 1～2 个有序双语 cue；未验证歌词不自动宣称标准分行。
- 本地回退可作为成品，但 UI 必须显示其未完成标准歌词英文校正。
- 自动与人工拆分必须保留父边界、稳定生成子 ID、继承样式/布局并使旧导出失效。
- 编辑页使用主页面内联时间序列表；每条 cue 的样式独立展开/收起并包含字号、字体族、粗体、斜体等既有能力。

## 5. 下一允许动作

建立阶段 checkpoint，然后按“合同与拆分策略 → 唯一最终批次 → 主页面编辑器 → 聚焦验证 → 完整回归”的顺序实施。普通故障按 S1/S2 自主闭环；不操作真机。
