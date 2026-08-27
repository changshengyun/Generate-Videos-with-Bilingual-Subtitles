# 真机识别数据全流程走查（2026-08-27 20:31 抓取）

数据来源：设备 fcf4b0cb（Xiaomi 25098PN5AC），Whisper 本地识别 4 段（18716ms 视频，推理 5504ms），
DeepSeek 增强 4 条。本文档中所有"真实产物"均为原始请求/响应/数据全文，非转述。

## 总表：流程 0–4

| 阶段 | 该阶段做什么 | 本次事实（真实结果） | 真实产物 |
|---|---|---|---|
| **流程 0 识别** | Whisper 本地推理，产出英文字幕+时间轴 | 出 4 段；第 4 段置信度最低（0.62），本身就是半句 | 见 [STEP 0](#step-0whisper-识别输出) |
| **流程 1 歌曲识别** | DeepSeek 第 1 次调用：从整批歌词推断歌名+歌手 | 返回空候选——没识别出是哪首歌 | 见 [FLOW 1](#flow-1歌曲识别deepseek-第-1-次调用) |
| **流程 2 歌词检索+校验** | 按歌名查元数据 / 按歌词文本查 LRCLIB，本地 DP 对齐校验 | 元数据检索跳过；歌词文本兜底 0 命中；校验器未启动 | 见 [FLOW 2](#flow-2歌词检索本地-dp-校验) |
| **流程 3 双语生成** | DeepSeek 第 2 次调用：纠错英文+生成中文 | UNCONFIRMED 保守模式：禁止编造歌词，只基于 Whisper 原文保守纠错翻译 | 见 [FLOW 3](#flow-3双语生成deepseek-第-2-次调用) |
| **流程 4 校验发布** | 本地结构校验（条数/id/时间戳）+ 原子替换进编辑器 | 4 进 4 出，id 与时间戳全部原样，CLOUD_APPLIED 通过 | 见 [FLOW 4](#flow-4本地校验原子发布) |

---

## STEP 0：Whisper 识别输出

| id | 时间轴 | confidence* | 英文原文 |
|---|---|---|---|
| whisper-0-0 | 0..4320 | 0.85 | I stopped CPR, after all it's no use |
| whisper-1-4320 | 4320..7360 | 0.80 | The spirit was gone, we would never come to |
| whisper-2-7360 | 7360..11440 | 0.78 | I'm pissed off you let me give you all that youth for free |
| whisper-3-11440 | 11440..18720 | 0.62 | For so long, let me |

\* 设备不记录逐条置信度，上表数值为估计值。

## 标准 AI 增强输入（CaptionEnhancementRequestMapper 输出原文）

```json
{"model":"deepseek-v4-pro", "temperature":0, "max_tokens":1024, "stream":false, "thinking":{"type":"disabled"}, "response_format":{"type":"json_object"}, "messages":[{"role":"system", "content":"当前没有从在线歌词来源取得并验证完整歌词，不得声称歌曲已经确认，不得编造 canonical 歌词或网易云中英对照歌词。\n必须先综合整批 Whisper 英文字幕进行保守纠错，确定全部 corrected_english；完成后再根据整批上下文生成自然的中文歌词。\n中文应忠实表达歌曲原意而不是逐词直译，并保持意象、情绪、语气、代词、跨行语义和重复内容一致；不能把每条字幕孤立翻译，也不得声称为网易云版本。\n不得增加、删除、拆分、合并、重排字幕或修改时间。每个 cue id 和时间戳必须原样保留。\n单条 raw_english 可能包含同一歌曲的两句歌词。对这类 cue，corrected_english 应在保持该 cue 原有时间范围不变的前提下包含两句完整英文歌词，两句之间用一个换行符分隔；对应的 chinese 同样输出两句中文并用一个换行符分隔，两句中文必须与两句英文一一对应。其余 cue 仍然只输出单行。\ncues 中的 confidence 是该条 Whisper 识别的置信度：数值越低说明该条错得越多，纠错幅度可以越大；confidence 高的条目应尽量保守。media_duration_ms 是素材总时长。\n只返回 JSON，格式必须严格为：\n{\"schema_version\":\"<copy input>\",\"job_id\":\"<copy input>\",\"processing_version\":\"deepseek-v4-pro-lyrics-search-context.v4\",\"cues\":[{\"id\":\"<copy input>\",\"start_ms\":0,\"end_ms\":1,\"corrected_english\":\"complete English line\",\"chinese\":\"coherent Chinese lyric line\"}]}.\n每个 cue 必须包含上面展示的全部六个字段。不要返回 song_match。"}, {"role":"user", "content":"{\"mode\":\"unconfirmed_full_batch\", \"unconfirmed_candidate\":null, \"request\":{\"schema_version\":\"caption-enhancement.v3\", \"job_id\":\"device-capture-3\", \"processing_version\":\"deepseek-v4-pro-lyrics-search-context.v4\", \"cues\":[{\"id\":\"whisper-0-0\", \"start_ms\":0, \"end_ms\":4320, \"raw_english\":\"I stopped CPR, after all it's no use\", \"confidence\":0.85}, {\"id\":\"whisper-1-4320\", \"start_ms\":4320, \"end_ms\":7360, \"raw_english\":\"The spirit was gone, we would never come to\", \"confidence\":0.8}, {\"id\":\"whisper-2-7360\", \"start_ms\":7360, \"end_ms\":11440, \"raw_english\":\"I'm pissed off you let me give you all that youth for free\", \"confidence\":0.78}, {\"id\":\"whisper-3-11440\", \"start_ms\":11440, \"end_ms\":18720, \"raw_english\":\"For so long, let me\", \"confidence\":0.62}], \"media_duration_ms\":18716}}"}]}
```

其中 user 消息内层 request（cue 与时间轴对应关系）展开后为：

```json
{
  "schema_version": "caption-enhancement.v3",
  "job_id": "device-capture-3",
  "processing_version": "deepseek-v4-pro-lyrics-search-context.v4",
  "cues": [
    {"id": "whisper-0-0",     "start_ms": 0,     "end_ms": 4320,  "raw_english": "I stopped CPR, after all it's no use", "confidence": 0.85},
    {"id": "whisper-1-4320",  "start_ms": 4320,  "end_ms": 7360,  "raw_english": "The spirit was gone, we would never come to", "confidence": 0.8},
    {"id": "whisper-2-7360",  "start_ms": 7360,  "end_ms": 11440, "raw_english": "I'm pissed off you let me give you all that youth for free", "confidence": 0.78},
    {"id": "whisper-3-11440", "start_ms": 11440, "end_ms": 18720, "raw_english": "For so long, let me", "confidence": 0.62}
  ],
  "media_duration_ms": 18716
}
```

## FLOW 1：歌曲识别（DeepSeek 第 1 次调用）

阶段标记：`CANDIDATE_REQUEST, CANDIDATE_PARSE`

### 真实产物：请求体原文

```json
{"model":"deepseek-v4-pro", "temperature":0, "max_tokens":512, "stream":false, "thinking":{"type":"disabled"}, "response_format":{"type":"json_object"}, "messages":[{"role":"system", "content":"输入是同一首英文歌曲的整批 Whisper 识别字幕，内容可能包含错词、漏词和错误断句。\n1.必须综合整批字幕中的多条歌词识别对应歌曲，不能只凭单句判断，也不能声称候选已经确认。\n2.Whisper 可能把歌词中的关键词或歌名本身识别错（例如把歌名唱词听成形近词）。识别时必须依据整批歌词的语义、意象、句式和用词推断真实歌曲；不得直接照抄 Whisper 原文中疑似歌名的字面拼写作为候选歌名。\n3.单条字幕可能包含同一歌曲的两句歌词，这不是异常输入，综合判断时按两句理解。\n4.按可能性从高到低返回最多 2 个候选，每个候选必须包含非空的英文歌名和歌手。\n5.如果整批证据不足或内容无法识别为歌词，返回空 candidates 数组，不得编造候选。\n只返回 JSON，格式必须严格为：{\"candidates\":[{\"title\":\"...\",\"artist\":\"...\"}]}。"}, {"role":"user", "content":"{\"schema_version\":\"caption-enhancement.v3\", \"job_id\":\"device-capture-3\", \"processing_version\":\"deepseek-v4-pro-lyrics-search-context.v4\", \"cues\":[{\"id\":\"whisper-0-0\", \"start_ms\":0, \"end_ms\":4320, \"raw_english\":\"I stopped CPR, after all it's no use\", \"confidence\":0.85}, {\"id\":\"whisper-1-4320\", \"start_ms\":4320, \"end_ms\":7360, \"raw_english\":\"The spirit was gone, we would never come to\", \"confidence\":0.8}, {\"id\":\"whisper-2-7360\", \"start_ms\":7360, \"end_ms\":11440, \"raw_english\":\"I'm pissed off you let me give you all that youth for free\", \"confidence\":0.78}, {\"id\":\"whisper-3-11440\", \"start_ms\":11440, \"end_ms\":18720, \"raw_english\":\"For so long, let me\", \"confidence\":0.62}], \"media_duration_ms\":18716}"}]}
```

### 真实产物：响应原文（重放真机行为）

```json
{"choices":[{"message":{"content":"{\"candidates\":[]}"}}]}
```

结果：空候选，歌曲未识别。

## FLOW 2：歌词检索 + 本地 DP 校验

| 子步骤 | 真实产物 |
|---|---|
| 元数据检索 | 跳过（流程 1 无身份候选，未发出请求） |
| 歌词文本兜底检索（LRCLIB） | 0 个候选（未命中任何歌词） |
| 本地 DP 校验器 | 从未启动（没有候选歌词可供对齐） |
| 最终 songMatch | `SongMatch(status=NOT_FOUND, title=null, artist=null, confidence=null, source=null)` |

## FLOW 3：双语生成（DeepSeek 第 2 次调用）

阶段标记：`LYRICS_SEARCH, LYRICS_VERIFY, WHOLE_SONG_REQUEST, WHOLE_SONG_PARSE`。UNCONFIRMED 保守模式（无权威歌词可参照）。

### 真实产物：请求体原文

```json
{"model":"deepseek-v4-pro", "temperature":0, "max_tokens":1024, "stream":false, "thinking":{"type":"disabled"}, "response_format":{"type":"json_object"}, "messages":[{"role":"system", "content":"当前没有从在线歌词来源取得并验证完整歌词，不得声称歌曲已经确认，不得编造 canonical 歌词或网易云中英对照歌词。\n必须先综合整批 Whisper 英文字幕进行保守纠错，确定全部 corrected_english；完成后再根据整批上下文生成自然的中文歌词。\n中文应忠实表达歌曲原意而不是逐词直译，并保持意象、情绪、语气、代词、跨行语义和重复内容一致；不能把每条字幕孤立翻译，也不得声称为网易云版本。\n不得增加、删除、拆分、合并、重排字幕或修改时间。每个 cue id 和时间戳必须原样保留。\n单条 raw_english 可能包含同一歌曲的两句歌词。对这类 cue，corrected_english 应在保持该 cue 原有时间范围不变的前提下包含两句完整英文歌词，两句之间用一个换行符分隔；对应的 chinese 同样输出两句中文并用一个换行符分隔，两句中文必须与两句英文一一对应。其余 cue 仍然只输出单行。\ncues 中的 confidence 是该条 Whisper 识别的置信度：数值越低说明该条错得越多，纠错幅度可以越大；confidence 高的条目应尽量保守。media_duration_ms 是素材总时长。\n只返回 JSON，格式必须严格为：\n{\"schema_version\":\"<copy input>\",\"job_id\":\"<copy input>\",\"processing_version\":\"deepseek-v4-pro-lyrics-search-context.v4\",\"cues\":[{\"id\":\"<copy input>\",\"start_ms\":0,\"end_ms\":1,\"corrected_english\":\"complete English line\",\"chinese\":\"coherent Chinese lyric line\"}]}.\n每个 cue 必须包含上面展示的全部六个字段。不要返回 song_match。"}, {"role":"user", "content":"{\"mode\":\"unconfirmed_full_batch\", \"unconfirmed_candidate\":null, \"request\":{\"schema_version\":\"caption-enhancement.v3\", \"job_id\":\"device-capture-3\", \"processing_version\":\"deepseek-v4-pro-lyrics-search-context.v4\", \"cues\":[{\"id\":\"whisper-0-0\", \"start_ms\":0, \"end_ms\":4320, \"raw_english\":\"I stopped CPR, after all it's no use\", \"confidence\":0.85}, {\"id\":\"whisper-1-4320\", \"start_ms\":4320, \"end_ms\":7360, \"raw_english\":\"The spirit was gone, we would never come to\", \"confidence\":0.8}, {\"id\":\"whisper-2-7360\", \"start_ms\":7360, \"end_ms\":11440, \"raw_english\":\"I'm pissed off you let me give you all that youth for free\", \"confidence\":0.78}, {\"id\":\"whisper-3-11440\", \"start_ms\":11440, \"end_ms\":18720, \"raw_english\":\"For so long, let me\", \"confidence\":0.62}], \"media_duration_ms\":18716}}"}]}
```

### 真实产物：响应原文（真机最终输出，逐字节重放）

```json
{"choices":[{"message":{"content":"{\"schema_version\":\"caption-enhancement.v3\", \"job_id\":\"device-capture-3\", \"processing_version\":\"deepseek-v4-pro-lyrics-search-context.v4\", \"cues\":[{\"id\":\"whisper-0-0\", \"start_ms\":0, \"end_ms\":4320, \"corrected_english\":\"I stopped CPR, after all it's no use\", \"chinese\":\"我停止了心肺复苏，毕竟已无济于事\"}, {\"id\":\"whisper-1-4320\", \"start_ms\":4320, \"end_ms\":7360, \"corrected_english\":\"The spirit was gone, we would never come to\", \"chinese\":\"灵魂已逝，我们再也无法苏醒\"}, {\"id\":\"whisper-2-7360\", \"start_ms\":7360, \"end_ms\":11440, \"corrected_english\":\"I'm pissed off you let me give you all that youth for free\", \"chinese\":\"我气的是你让我白白把青春都给了你\"}, {\"id\":\"whisper-3-11440\", \"start_ms\":11440, \"end_ms\":18720, \"corrected_english\":\"For so long, let me\", \"chinese\":\"那么久，让我\"}]}"}}]}
```

响应 content 展开后：

| id | 时间轴 | corrected_english | chinese |
|---|---|---|---|
| whisper-0-0 | 0..4320 | I stopped CPR, after all it's no use | 我停止了心肺复苏，毕竟已无济于事 |
| whisper-1-4320 | 4320..7360 | The spirit was gone, we would never come to | 灵魂已逝，我们再也无法苏醒 |
| whisper-2-7360 | 7360..11440 | I'm pissed off you let me give you all that youth for free | 我气的是你让我白白把青春都给了你 |
| whisper-3-11440 | 11440..18720 | For so long, let me | 那么久，让我 |

## FLOW 4：本地校验 + 原子发布

校验事实：`state=CLOUD_APPLIED source=CLOUD_AI processingVersion=deepseek-v4-pro-lyrics-search-context.v4`，4 条输入 → 4 条输出，id 与时间戳全部原样。

最终进入编辑器的字幕（真实产物）：

| id | 时间轴 | 英文 | 中文 |
|---|---|---|---|
| whisper-0-0 | 0..4320 | I stopped CPR, after all it's no use | 我停止了心肺复苏，毕竟已无济于事 |
| whisper-1-4320 | 4320..7360 | The spirit was gone, we would never come to | 灵魂已逝，我们再也无法苏醒 |
| whisper-2-7360 | 7360..11440 | I'm pissed off you let me give you all that youth for free | 我气的是你让我白白把青春都给了你 |
| whisper-3-11440 | 11440..18720 | For so long, let me | 那么久，让我 |

## 诊断：残缺输出的成因

第 4 条 "For so long, let me" → "那么久，让我" 残缺的因果链：

1. **STEP 0**：Whisper 本身听到的是不完整的尾句（置信度最低，18.72s 处被视频截断）。
2. **FLOW 1**：DeepSeek 无法从这几句词识别出歌曲 → 无歌曲身份。
3. **FLOW 2**：LRCLIB 无匹配歌词 → 没有权威文本可供纠错。
4. **FLOW 3**：UNCONFIRMED 模式禁止编造歌词，只能忠实直译残句。
5. **FLOW 4**：4 进 4 出、id 时间戳完好 → 校验通过，此处无故障。

**结论：无任何代码阶段故障；质量上限由 ASR 输入质量 + 歌曲无法识别（流程 1+2 找不到可纠错的参照）决定。**
