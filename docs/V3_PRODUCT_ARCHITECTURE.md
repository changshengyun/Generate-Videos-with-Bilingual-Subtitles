# LyricCaptioner V3 产品与架构方案

## 文档状态

- 状态：`ACTIVE_REFERENCE / IMPLEMENTATION_REQUIRES_TASK_AUTHORIZATION`
- 日期：`2026-08-02`
- 目的：保存 V3 的产品方向、交互合同、架构方案、阶段建议和决策门禁。
- 本文是 V3 详细参考；当前阶段和执行授权仍分别以 `CURRENT_TASK.md` 与 `PROJECT_STATE.md` 为准。
- V2 已按用户验收结论归档到 `../docs-v2/`，不再覆盖 V3 活动状态。

## V3 核心目标

在保留 Android 本地视频编辑、字幕时间轴、Media3 预览和 FFmpegKit 导出的基础上，V3 同时推进四条相互隔离的产品改进：

1. 缓存当前 Whisper 模型上下文，减少连续识别任务的重复模型加载。
2. 先重构用户交互，再据此重做正式产品 UI。
3. 统一字幕文本框、逐段样式与预览/全屏/导出坐标系统。
4. 在完成用户决策后，将中文翻译与歌词纠错从本地 OPUS-MT 改为云端结构化 AI 增强。

云端增强建议链路为：

1. 本地 ASR 产生带稳定时间区间的英文字幕草稿。
2. App 将一次任务的完整英文字幕批次发送到自有后端。
3. 后端结合整段上下文完成英文纠错、歌曲上下文判断和中文翻译。
4. 可选第二轮审校仅处理低置信度或高修改幅度片段。
5. App 严格校验结构化 JSON，并按原 `cue_id` 原子回填字幕。
6. 用户确认后沿用现有预览、保存恢复和视频导出链路。

V3 的缓存、交互和 UI 改进不能表述为 ASR 准确率提升。云端链路提升的是“最终字幕质量”；没有固定评测数据时仍不得声称 ASR 模型准确率已经提高。

## 当前 V2 基线与包体事实

测量对象：`app/build/outputs/apk/debug/app-debug.apk`，双 ABI Debug APK。

| 项目 | 当前测量或推算 |
|---|---:|
| 当前 APK | `408,425,875 bytes / 389.51 MiB` |
| Whisper `ggml-small.en-q5_1.bin` | `190,098,681 bytes` |
| 当前打包模型原始合计 | `311,205,010 bytes / 296.79 MiB` |
| 当前模型在 APK 内压缩后 | `267,309,476 bytes / 254.93 MiB` |
| 删除 OPUS-MT 资产后的双 ABI Debug 估计 | `约 309 MiB` |
| 同时删除 OPUS-MT 与 ONNX Runtime 后 | `约 271 MiB` |
| 再改为 ARM64-only 后 | `约 227 MiB` |

以上删除后体积是基于当前 APK 条目的派生估计，必须在真实 V3 构建中重新测量。Release、AAB、ABI Split 和代码压缩会改变最终分发体积。

## Whisper 模型上下文缓存

### 当前事实

- 当前模型为 `ggml-small.en-q5_1.bin`，native 路径使用 CPU、Greedy、`language=auto`、`translate=false`、最多 4 个 CPU 线程，并对整段 16 kHz 单声道 WAV 调用 `whisper_full`。
- 一次识别任务只加载一次模型，不是每条字幕重新加载；但每个新识别任务都会重新创建并加载 Whisper 上下文。
- 当前 OPUS-MT translator 实例已经复用 tokenizer、encoder session 和 decoder session；逐条字幕翻译重复的是实际 encoder/decoder 推理，不是每条重新加载模型。

### V3 目标结构

```text
WhisperModelRuntime（进程级单例）
  -> modelIdentity(path + name + size + sha256)
  -> nativeHandle -> whisper_context*
  -> Mutex / 单线程识别队列
  -> 每任务独立 audio state、cancel token、临时缓冲区和结果
```

JNI 边界建议收敛为：

```text
nativeLoad(modelPath) -> handle
nativeTranscribe(handle, audioPath, cancellationToken) -> result
nativeClose(handle)
```

- 第一次识别校验并加载模型；后续任务复用同一模型上下文。
- 同一上下文默认只允许一个正在运行的识别任务，不盲目并发共享 native 状态。
- 模型名称、路径、大小或 SHA-256 改变时，等待当前任务安全结束，关闭旧上下文并加载新模型。
- 取消顺序必须是请求 abort -> `whisper_full` 安全退出 -> 确认推理线程结束 -> 释放任务临时状态；不得在 native 线程仍使用上下文时释放模型。
- 如果取消后无法证明上下文可继续安全复用，则标记缓存失效，在当前任务完全退出后重建；不能用潜在悬空句柄换取热启动。
- App 进程被系统终止后缓存自然消失。常规任务结束和页面切换不应自动卸载模型；严重内存压力下是否释放由 `V3-DEC-001` 决定。

### 性能与质量边界

- 缓存只消除重复磁盘读取、模型解析、内存分配和初始化成本，不直接减少 Whisper 核心推理计算量。
- 分别记录冷启动加载、冷启动推理、热启动推理和总耗时，同时记录峰值 RSS、温度、崩溃、空结果、取消结果和时间戳有效性。
- CPU 线程数调优和 Android GPU 后端属于后续独立实验。当前手机存在 GPU 不代表 whisper.cpp Android GPU 路径可直接启用。
- 没有准确人工歌词时，只能比较耗时、资源与稳定性，不能宣称 WER/CER 或识别质量提升。

## V3 交互合同

### 主流程

```text
系统相册导入视频
  -> 识别/增强进行中
  -> 成功后停留当前栏目并显示“编辑字幕”主动入口
  -> 用户点击后进入“字幕编辑”
  -> 字幕列表与逐段样式编辑位于该栏目内
  -> 视频内统一字幕文本框范围预览
  -> 全屏检查
  -> 默认保存到系统相册，或由用户选择其他位置
```

- 字幕生成成功后不得自动跳转；只有用户点击“编辑字幕”入口才进入编辑栏目。打开旧项目、恢复后台状态或字幕列表变化不得抢占用户当前栏目。
- 字幕列表不再作为跨流程突出的独立大区块，而是字幕编辑栏目内的主要内容。
- 每条 cue 展示英文、中文、时间范围、确认状态和样式入口；批量默认样式与单条覆盖必须视觉上可区分。
- 先冻结任务流、导航、状态反馈和错误恢复，再做颜色、卡片、字体层级等视觉设计。

### 产品化 UI

- 删除“歌词字幕工作台”、`V2`/`V3` 等开发阶段文本和测试状态堆叠。
- 顶栏只保留正式产品品牌、当前项目必要信息和少量全局操作；品牌名称由 `V3-DEC-001` 决定。
- 不把所有能力同时铺在单屏。主操作跟随当前阶段，次要诊断信息进入详情或开发构建专用入口。
- 保留单手操作、清晰触控区域、加载/取消/失败恢复、无障碍语义和系统 Insets。

## 字幕布局与逐段样式

### 数据职责

- 项目继续保存兼容性 `CaptionLayout` 与 `DefaultCaptionStyle`，仅作为新 cue、旧项目迁移和未覆盖字段的内部回退；产品 UI 不再提供独立的项目默认样式面板。
- 每个 `CaptionCue` 保存自己的 `CaptionStyleOverride` 和可选 `CaptionLayoutOverride`。字号、字体、英中颜色、描边、粗斜体、对齐与上下位置都从对应字幕卡片进入，写操作必须显式绑定 cue ID。
- 未编辑 cue 继承兼容性基础值；“恢复基础样式/位置”只清除当前 cue 的覆盖。旧 V2-v4 项目迁移后默认没有 cue 覆盖，视觉结果必须保持。
- Compose 预览与 ASS 导出共用同一 resolver 解析 cue 样式和位置，禁止单条字幕调整污染其他 cue。

### 规范坐标系统

- 使用源视频像素作为唯一规范空间，并保存归一化布局：`xRatio / yRatio / widthRatio`。
- 字号保存为源视频高度比例：`fontSizeRatio = sourceFontPx / sourceVideoHeight`。
- Media3 普通和全屏预览必须先计算 `FIT` 后的有效视频矩形；竖屏视频在横屏预览产生的黑边不参与字幕位置、宽度或字号计算。
- 预览映射：`previewFontPx = fontSizeRatio * displayedVideoHeight`。
- 导出映射：ASS 使用 `PlayResX = sourceVideoWidth`、`PlayResY = sourceVideoHeight`，并用 `exportFontPx = fontSizeRatio * sourceVideoHeight`。
- 文本框的边距、对齐、换行宽度、描边和字号必须通过共享的布局/样式解析器提供给 Compose 与 ASS，禁止两条路径维护不同常量。

这意味着：竖屏视频即使在横屏播放器里有大面积黑边，用户调整的仍是竖屏视频本身的字幕比例。全屏只是把同一有效视频矩形放大，导出则还原到源视频分辨率，因此视觉字号和相对位置能够一致。

## 系统相册默认行为

- 导入建议默认使用 Android Photo Picker/系统媒体选择体验；需要持久项目访问或非相册文件时提供“选择其他位置”的 SAF 入口。
- 导出建议默认通过 MediaStore 保存到 `Movies/LyricCaptioner` 并出现在系统相册；需要自定义文件名或目录时提供“保存到其他位置”。
- Android 没有与 Photo Picker 完全对称的“在相册中选择导出目录”接口，因此产品文案必须区分“保存到相册”和“选择其他位置”。
- 两条路径都必须保留源文件安全、目标失败原子性、取消不留空文件和旧导出失效规则。

## 推荐产品链路

```text
系统选择器导入本地视频
  -> 本地提取音频
  -> Local ASR 生成 Raw CaptionCue
  -> 自有后端 API
       -> 可选歌曲识别/合法歌词检索
       -> 第一轮英文纠错 + 中文翻译
       -> 低置信度片段第二轮审校
  -> 严格 JSON Schema 返回
  -> Android 校验 cue_id、时间区间、数量和文本
  -> 整批原子更新 CaptionCue
  -> 人工确认/编辑
  -> Media3 预览
  -> FFmpegKit 导出
```

## 时间轴与数据不变量

- App 继续使用 `cue_id + start_ms + end_ms`，不使用视频帧号作为字幕主键或时间基准。
- 云端不得改变本地时间区间，不得静默新增、删除、合并、拆分或重排 cue。
- 如果未来允许合并或拆分，必须设计独立的显式映射协议并由本地重新校验时间轴；不包含在首个 V3 MVP 中。
- 永久保留 `raw_english`，AI 结果写入 `corrected_english` 和 `chinese`，支持比较和恢复。
- 请求失败、取消、超时、限流、拒绝、响应截断或 Schema 无效时，不得部分覆盖当前字幕。
- 任何字幕文本变化继续使旧导出失效。

## 建议的 API Schema

### Request

```json
{
  "schema_version": 1,
  "job_id": "uuid",
  "source_language": "en",
  "video_context": {
    "duration_ms": 0,
    "optional_title": null,
    "optional_artist": null
  },
  "cues": [
    {
      "cue_id": "cue-001",
      "start_ms": 1200,
      "end_ms": 3800,
      "raw_english": "example lyric",
      "asr_confidence": 0.72
    }
  ]
}
```

### Response

```json
{
  "schema_version": 1,
  "job_id": "uuid",
  "processor_version": "provider:model:prompt-version",
  "song": {
    "title": null,
    "artist": null,
    "match_confidence": 0.0,
    "match_source": "unverified"
  },
  "cues": [
    {
      "cue_id": "cue-001",
      "start_ms": 1200,
      "end_ms": 3800,
      "raw_english": "example lyric",
      "corrected_english": "Example lyric",
      "chinese": "示例歌词",
      "confidence": 0.91,
      "review_status": "accepted"
    }
  ]
}
```

### 本地强制校验

- `schema_version` 与 `job_id` 匹配请求。
- 响应的 `cue_id` 集合与请求完全相同且无重复。
- 每个 cue 的 `start_ms`、`end_ms` 与本地原值完全一致。
- 英文、中文、数量、字符串长度、Unicode 和状态枚举合法。
- 响应只在整批通过后提交；失败时保留原字幕并提供重试。
- 记录供应商模型版本、Prompt 版本和处理时间，但日志不得包含完整用户歌词或私有媒体路径。

## 翻译输入方案决策

### 推荐：只发送英文

- 输入更干净，Token 和延迟更低。
- 删除 OPUS-MT 与 ONNX Runtime 后包体明显下降。
- 避免“ASR 错误 -> 本地翻译错误 -> 大模型被错误中文锚定”的错误传播。
- 云端模型利用完整歌曲上下文同时完成纠错和中文翻译。

### 不推荐作为默认：先本地翻译再发送中英文

- 保留本地模型和 ONNX Runtime，违背减小包体的目标。
- 本地翻译错误可能限制云端模型的判断。
- 增加本地耗时、请求 Token 和结果冲突处理。
- 仅在明确需要离线中文 fallback 或对照实验时作为可选路线重新评估。

## 两轮 AI 审校策略

首个 V3 版本不应对所有字幕无条件调用两次大模型。

1. 第一轮处理完整 cue 批次，生成纠错英文、中文和置信度。
2. 本地或后端确定性检查修改幅度、缺句、重复、ID 集合、时间区间和歌曲匹配置信度。
3. 仅将低置信度、高修改幅度或存在冲突的 cue 送入第二轮。
4. 第二轮输出仅允许 `accept`、`revise` 或 `needs_human_review`。
5. 两轮均没有原始音频时，只能验证语言与歌词一致性，不能证明声学内容真实；高风险冲突必须交给人工或音频能力复核。

## ASR 模型路线

### V3 MVP 建议

- 首先保留当前 `ggml-small.en-q5_1.bin`，将云端增强的收益与 ASR 模型切换收益分开测量。
- `ggml-large-v3-turbo-q5_0.bin` 可作为同一 whisper.cpp 栈内候选，官方模型文件约 `547 MiB`。
- 候选模型必须在同一歌曲 fixture、同一预处理和同一 ARM64 设备上比较 WER、关键词召回、时间戳、耗时、峰值内存、温度、崩溃和包体。
- 未证明歌曲质量改善前，不切换默认模型，不把候选大型模型直接并入正式 APK。

### 非默认备选

- sherpa-onnx/Parakeet 等路线属于 ASR 运行时和技术栈切换，必须单独进行架构 Spike 和模型评测。
- 如果产品已经接受全程联网，可另行比较云端 ASR；这会进一步减小包体，但改变隐私、成本和离线能力，不属于当前推荐路线。

## Android 改造边界

### 复用

- `CaptionCue`、字幕时间轴、字幕编辑和确认状态。
- 项目保存恢复与旧导出失效规则。
- Media3 正常/全屏预览和字幕叠加。
- FFmpegKit 双语字幕烧录。
- `LyricLineAligner` 可作为行匹配的 fallback 或结果校验工具。
- V2 已验证的系统媒体导入、项目持久化、失效 URI 重绑和取消语义。

### 替换或新增

- 用批量 `CaptionEnhancementService` 替代逐句本地翻译主流程。
- 新增后端 API Client、DTO、严格 Schema 解析、超时、取消、重试和错误映射。
- 新增 AI 处理状态、隐私提示、失败恢复和人工复核状态。
- 重新加入 Android `INTERNET` 权限。
- 删除 OPUS-MT 打包、Store、Validator、Tokenizer、ONNX Translator 及仅服务于该翻译实现的 ONNX Runtime 依赖。
- API Key 只能保存在自有后端，禁止嵌入 APK。
- 新增进程级 `WhisperModelRuntime`、native handle 生命周期、串行识别和缓存失效策略。
- 新增项目级 `CaptionLayout`、默认字幕样式、cue 级样式覆盖和 V2 项目迁移。
- 新增共享的源视频坐标/字号解析器，供 Compose 普通预览、全屏预览和 ASS 导出共同使用。
- 将字幕列表并入字幕编辑栏目；新字幕生成成功后停留当前栏目，由用户通过“编辑字幕”入口主动导航。
- 默认导入进入系统媒体选择体验，默认导出写入系统相册；保留高级 SAF 路径。
- 重做产品品牌与主导航，删除测试工作台和版本标签展示。

## 后端责任

- 保存供应商密钥并完成用户/设备认证、配额、限流和滥用防护。
- 实施严格 JSON Schema、超时、幂等 `job_id`、重试和响应大小限制。
- 默认不持久化完整歌词和用户私有路径；日志只记录必要的匿名指标。
- 提供模型和 Prompt 版本追踪，支持回滚。
- 歌曲识别不得只依赖大模型记忆。需要歌曲精确匹配时，使用用户提供的元数据、音频指纹或获得授权的歌词来源。
- 明确歌词来源、授权和供应商内容政策；无可信来源时将歌曲匹配标记为 `unverified`。

## 建议阶段

| 阶段 | 目标 | 主要验收 |
|---|---|---|
| `V3-DEC-001` | 冻结交互、样式、媒体入口、品牌、缓存生命周期、API、隐私、歌词来源和离线策略 | 用户逐行回答决策表并批准阶段边界 |
| `V3-ASR-CACHE-001` | 当前 Whisper 模型进程级缓存 | 冷/热耗时、串行任务、取消后复用/重建、模型切换和内存压力 |
| `V3-UX-001` | 字幕编辑导航、列表归位、统一文本框与逐段样式 | 旧项目迁移、逐段覆盖、预览/全屏/导出一致性 |
| `V3-MEDIA-001` | 系统相册优先的导入与导出 | 默认路径、另选位置、持久权限、失败/取消和源文件安全 |
| `V3-UI-001` | 正式产品 UI | 品牌、层级、导航、状态、无障碍与目标设备截图 Review |
| `V3-API-001` | 后端与 Android 严格 Schema Spike | 固定 cue 批次往返、错误/取消/幂等、无密钥泄露 |
| `V3-AI-001` | 一轮英文纠错与中文翻译 | 固定数据集质量、ID/时间不变量、原子提交 |
| `V3-PKG-001` | 移除 OPUS-MT/ONNX 翻译路径 | 包体实测、旧项目兼容、无本地翻译残留 |
| `V3-AI-002` | 低置信度二轮审校 | 成本、延迟、质量提升和人工复核门槛 |
| `V3-ASR-003` | 可选大型 ASR 候选评测 | 同数据/同设备显著提升后才允许切换 |
| `V3-E2E-003` | V3 最终产品验收 | 导入、ASR、云端增强、编辑、恢复、导出和播放 |

一次只激活一个阶段。`V3-DEC-001` 未通过前，不实施业务代码、生产 API、依赖、权限、模型或打包变化。模型缓存、交互、媒体、UI 和云端增强必须按阶段分别提交和验收。

## V3 验收指标

- 最终英文和中文质量必须使用固定、人工确认的数据集评测。
- 分别记录原始 ASR、第一轮 AI、第二轮 AI 的质量，不能只报告最终最好结果。
- 记录 Schema 失败率、歌曲误匹配率、人工复核比例、延迟、Token/调用成本和失败恢复成功率。
- 记录 APK/AAB 下载体积、安装体积、模型私有复制后的磁盘占用、峰值内存和温度。
- 网络失败、取消、限流和后端不可用时，原始 ASR 字幕仍可编辑和导出，不得丢失项目。
- 不允许 Demo、固定歌词、历史输出或未授权歌词来源冒充产品成功。

## 需要用户确认的关键决策

当前待确认项以 `CURRENT_TASK.md` 的 `D01-D10` 为唯一回答入口，覆盖：

1. 云端增强路线是否保留，以及大模型/API、自有后端、隐私和歌词来源。
2. Whisper 缓存的内存压力释放策略。
3. 统一字幕文本框可调维度和逐段样式属性。
4. 新旧项目样式继承方式。
5. 系统相册导入/导出的具体产品语义。
6. 正式产品名称、顶栏内容和主动导航规则。
7. 云端失败或断网时是否保留英文 ASR 编辑/导出能力。

## 专业补充建议

1. **检索约束式纠错**：先通过可靠来源确认歌曲，再让模型对齐和翻译，避免凭模型记忆生成看似正确的完整歌词。
2. **置信度驱动级联**：只对低置信度 cue 运行第二轮或人工复核，控制费用、延迟和过度修改。
3. **双轨可追溯字幕**：保存原始 ASR、AI 修订、处理版本和用户最终稿，支持审计、撤销和质量评测。

## 参考

- OpenAI Whisper：<https://github.com/openai/whisper>
- whisper.cpp 模型清单：<https://github.com/ggml-org/whisper.cpp/blob/master/models/README.md>
- sherpa-onnx Android/ASR：<https://github.com/k2-fsa/sherpa-onnx>
- Structured Outputs：<https://developers.openai.com/api/docs/guides/structured-outputs>
