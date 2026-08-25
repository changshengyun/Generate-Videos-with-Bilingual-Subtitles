# Current Task: V4-CAPTION-QUALITY-001

- `STATE_REV: 2026-08-25.014`
- `TASK_REV: V4-CAPTION-QUALITY-001.002`
- Stage state: `PARTIAL_PASS / COMPONENT_VERIFIED / USER_DEVICE_VALIDATION_PENDING`
- Product status: `CAPTION_QUALITY_AND_EDITOR_INTEGRATION_IMPLEMENTED`
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

## 5. 实际实现

- `caption-enhancement.v4` 采用 `source_id → 1..2 lines`；完整批次验证通过后才生成唯一最终字幕列表。RAW_ASR 只在 runner/coordinator 内部传递，不写入可见或可导出列表。
- 已验证 LRCLIB 对齐保留原歌词大小写、标点和 1～2 行边界；DeepSeek 英文回显必须与 canonical 归一化内容相等，最终使用原 canonical 文本。未验证歌词每个 source cue 只允许一行。
- 自动/人工拆分共用确定性策略：稳定子 ID、父边界、84ms 首选间隔、833ms 首选最小时长、英文字符权重、样式/布局/置信度继承；短区间不扩展，并由可读性规则提示复核。
- 字幕编辑整合进主页面单一 `LazyColumn`，按时间排序全部 cue；每条 cue 可直接修改双语文本和 ±0.1 秒时间，独立展开样式、字号、无衬线/衬线/等宽、粗体、斜体、颜色、描边、对齐、位置及人工拆分。
- 编辑主页面提升共享 ExoPlayer 生命周期，滚动移除顶部 item 不会释放播放器；文字/样式变化不再触发重复 Seek，滚回时从现有 `videoSize` 恢复字幕叠层。普通与全屏继续共用同一 player 和 `PlayerControlRow`。
- 项目归档无格式迁移即可保存恢复拆分 ID、时间、文本、字体/样式/布局；拆分和既有文本/时间/样式更新都使旧导出失效。instrumentation 已升级为接受 1→2 云端结果并覆盖内联文本、IME、字体和确认拆分路径。

## 6. 验证证据

- checkpoint：`f427fef4ec463b5c8ae9064278625f0edfb87b46`（`冻结字幕质量与编辑器整合矩阵`）。
- `python tools\asr_evaluate_test.py`：6/6 通过。
- 任务相关 JVM 测试：56 tests、0 failures、0 errors、0 skipped；覆盖 canonical 标点/分行、v4 合同、非法响应、本地回退、唯一批次、拆时、可读性、保存恢复、导出失效和 UI 源合同。
- `testDebugUnitTest`：362 tests 中 359 通过、3 失败。失败仅为隔离 worktree 父路径找不到原仓库已有外部 fixture：OPUS-MT `encoder_model_quantized.onnx` 与 Whisper `ggml-base.bin`/批准模型集合；资产在 `D:\DevEnv\Projects\lyric-captioner-android` 中存在，未复制、链接、下载或改变环境。
- `lintDebug`、普通 `assembleDebug`、普通 `assembleDebugAndroidTest`、Native `-PenableWhisperNative=true assembleDebug` 与 Native AndroidTest 构建全部成功。
- Native Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，116,019,973 bytes；AndroidTest APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`，220,234 bytes。
- 独立代码复审先发现 canonical 标点、播放器生命周期、旧 instrumentation 合同和极短拆分回退问题；最小修复与聚焦回归后最终复审 `PASS`，无剩余 P1/P2。
- 未运行 Agent 设备操作或 instrumentation；未取得真实 DeepSeek、真实视频主链路、普通/全屏截图、MediaStore 导出或 Media3 回放证据。

## 7. 最终状态与剩余风险

- 当前状态固定为 `PARTIAL_PASS / COMPONENT_VERIFIED / USER_DEVICE_VALIDATION_PENDING`，不得提升为 `PASS / DEVICE_VERIFIED`。
- 真实 UI 的长列表、IME、窄屏、旋转、滚动、全屏控制同步和手势互斥仅完成 instrumentation 编译，尚无设备运行证据。
- 真实歌词命中质量、DeepSeek v4 响应、导出文件编码/时长和回放仍必须由用户用指定视频终验。
- `V3-ASR-DIAG-001` 继续保持 `PARTIAL_PASS / PRODUCTION_BASE_VALIDATION_DEFERRED_BY_USER`；未补做 small，未改写为 PASS。

## 8. 下一允许动作

用户在真机上使用 `D:\DevEnv\Projects\sorce\5e4c3cd7073a9e9b03df1fbf8af6d928.mp4`，从系统相册入口完成真实 ASR、DeepSeek/本地回退、canonical 纠错、双句拆 cue、主页面编辑、保存恢复、普通/全屏截图、MediaStore 导出和 Media3 回放。没有完整证据时保持当前 PARTIAL_PASS；Agent 不操作设备。
