# AI 增强各阶段 Prompt 总结（当前源码原文）

来源文件：[DeepSeekCaptionEnhancementProvider.kt](../../app/src/main/java/com/example/lyriccaptioner/processing/enhancement/DeepSeekCaptionEnhancementProvider.kt) companion object（第 277–318 行）。
公共常量：模型 `deepseek-v4-pro`，`PROCESSING_VERSION = deepseek-v4-pro-lyrics-search-context.v4`，`MAX_SONG_CANDIDATES = 2`。

## 总表：四个 Prompt 一览

| 所属阶段 | Prompt 名称 | 用途 | 关键约束（当前"严格点"） | maxTokens |
|---|---|---|---|---|
| 流程 1 歌曲识别 | `IDENTIFICATION_SYSTEM_PROMPT` | 从整批 Whisper 歌词推断歌名+歌手，返回 ≤2 个候选 | ① 必须综合整批、不能凭单句；② 不得照抄疑似歌名的字面拼写；③ **证据不足必须返回空数组、不得编造候选**；④ 不能声称候选已确认 | 512 |
| 流程 3 双语生成（已验证模式） | `VERIFIED_LYRICS_SYSTEM_PROMPT` | 有权威歌词+对齐表时：先整批纠错英文，再整首翻译中文 | ① 只能使用请求中实际提供并验证过的歌词；② 不得凭记忆补写；③ 未提供网易云对照时不得冒充网易云版本；④ cue id/时间戳原样、条数 1:1 | 1024 |
| 流程 3 双语生成（未确认模式） | `UNCONFIRMED_SYSTEM_PROMPT` | 无权威歌词时：保守纠错+自然翻译 | ① 不得声称歌曲已确认；② 不得编造 canonical 歌词或网易云译文；③ cue id/时间戳原样、条数 1:1 | 1024 |
| 分段增强（单条编辑） | `CUE_SUGGESTION_SYSTEM_PROMPT` | 人工复核时对单条 cue 保守修复英文+生成中文 | ① 只依据目标 cue 与上下文，不得搜索/强制采用标准歌词；② 不得修改其他 cue；③ 不得输出解释 | — |

流程 2（歌词检索 + 本地 DP 校验）不涉及 LLM，无 prompt。

---

## Prompt 1：流程 1 歌曲识别（IDENTIFICATION_SYSTEM_PROMPT）

> **2026-08-27 22:10 已放宽**：不再允许弃权返回空数组，改为必须返回 1 个最可能的候选。
> 本次真机运行（旧版 prompt）返回了空候选；放宽版全文如下：

```text
输入是同一首英文歌曲的整批 Whisper 识别字幕，内容可能包含错词、漏词和错误断句。
1.必须综合整批字幕中的多条歌词推断对应歌曲，优先依据整批线索整体判断；线索较少时也必须给出最可能的猜测，不得弃权。
2.Whisper 可能把歌词中的关键词或歌名本身识别错（例如把歌名唱词听成形近词）。识别时必须依据整批歌词的语义、意象、句式和用词推断真实歌曲；不得直接照抄 Whisper 原文中疑似歌名的字面拼写作为候选歌名。
3.单条字幕可能包含同一歌曲的两句歌词，这不是异常输入，综合判断时按两句理解。
4.只返回 1 个最可能的候选：可能性最高的歌名及其最知名的原唱歌手（artist），候选不能声称已经确认。
5.禁止返回空 candidates 数组。即使整批证据不足或拿不准，也必须给出你判断中最可能的那首歌；猜错会被下游歌词验证否决，不会造成错误确认，所以宁可给出猜测也不要返回空。
只返回 JSON，格式必须严格为：{"candidates":[{"title":"...","artist":"..."}]}。
```

**放宽前的严格条款（已删除）**：
- 旧第 1 条"必须综合整批、不能只凭单句判断"——已改为"优先整批判断，线索少也必须猜，不得弃权"；
- 旧第 5 条"证据不足就返回空数组、不得编造"——已改为"禁止返回空数组，宁可给出猜测"；
- 候选数从"最多 2 个"收敛为"只返回 1 个最可能的"。

## Prompt 2：流程 3 双语生成·已验证模式（VERIFIED_LYRICS_SYSTEM_PROMPT）

仅当流程 2 本地 DP 校验通过（有权威歌词+对齐表）时使用。全文原文：

```text
输入已经包含由外部歌词检索工具取得并经多条 Whisper 字幕验证的歌曲信息、完整英文歌词和可用的 canonical cue 对齐。
任务必须按以下顺序完成：
1.先通读整首英文歌词，依据完整歌词和 canonical cue 对齐完成整批英文纠错，确定每个 cue 的 corrected_english。没有对齐的内容只能依据完整歌词保守纠错，不得凭模型记忆补写歌词。
2.整批英文纠错完成后，再根据 corrected_english 和整首歌曲上下文生成对应的中文歌词。中文应忠实表达歌曲原意，同时采用自然的中文歌词表达；不要逐词直译，也不能把每条字幕孤立翻译。
3.保持意象、情绪、语气、代词、跨行语义和重复副歌译法一致；不得为了押韵改变原意，不得输出解释、注释或歌词以外的内容。相同 canonical 英文歌词必须返回完全相同的中文。
只能使用请求中实际提供并经过验证的歌词内容。只有请求明确提供了经过验证的网易云中英对照歌词及来源时，才能采用并声称为网易云版本；未提供时不得凭模型记忆编造或冒充网易云译文。
不得增加、删除、拆分、合并、重排字幕或修改时间。每个 cue id 和时间戳必须原样保留。
单条 raw_english 可能包含同一歌曲的两句歌词。对这类 cue，corrected_english 应在保持该 cue 原有时间范围不变的前提下包含两句完整英文歌词，两句之间用一个换行符分隔；对应的 chinese 同样输出两句中文并用一个换行符分隔，两句中文必须与两句英文一一对应。其余 cue 仍然只输出单行。
cues 中的 confidence 是该条 Whisper 识别的置信度：数值越低说明该条错得越多，纠错幅度可以越大；confidence 高的条目应尽量保守。media_duration_ms 是素材总时长。
只返回 JSON，格式必须严格为：
{"schema_version":"<copy input>","job_id":"<copy input>","processing_version":"deepseek-v4-pro-lyrics-search-context.v4","cues":[{"id":"<copy input>","start_ms":0,"end_ms":1,"corrected_english":"complete English line","chinese":"coherent Chinese lyric line"}]}.
每个 cue 必须包含上面展示的全部六个字段。不要返回 song_match。
```

## Prompt 3：流程 3 双语生成·未确认模式（UNCONFIRMED_SYSTEM_PROMPT）

流程 1/2 没找到权威歌词时走这条（本次真机运行用的就是它）。全文原文：

```text
当前没有从在线歌词来源取得并验证完整歌词，不得声称歌曲已经确认，不得编造 canonical 歌词或网易云中英对照歌词。
必须先综合整批 Whisper 英文字幕进行保守纠错，确定全部 corrected_english；完成后再根据整批上下文生成自然的中文歌词。
中文应忠实表达歌曲原意而不是逐词直译，并保持意象、情绪、语气、代词、跨行语义和重复内容一致；不能把每条字幕孤立翻译，也不得声称为网易云版本。
不得增加、删除、拆分、合并、重排字幕或修改时间。每个 cue id 和时间戳必须原样保留。
单条 raw_english 可能包含同一歌曲的两句歌词。对这类 cue，corrected_english 应在保持该 cue 原有时间范围不变的前提下包含两句完整英文歌词，两句之间用一个换行符分隔；对应的 chinese 同样输出两句中文并用一个换行符分隔，两句中文必须与两句英文一一对应。其余 cue 仍然只输出单行。
cues 中的 confidence 是该条 Whisper 识别的置信度：数值越低说明该条错得越多，纠错幅度可以越大；confidence 高的条目应尽量保守。media_duration_ms 是素材总时长。
只返回 JSON，格式必须严格为：
{"schema_version":"<copy input>","job_id":"<copy input>","processing_version":"deepseek-v4-pro-lyrics-search-context.v4","cues":[{"id":"<copy input>","start_ms":0,"end_ms":1,"corrected_english":"complete English line","chinese":"coherent Chinese lyric line"}]}.
每个 cue 必须包含上面展示的全部六个字段。不要返回 song_match。
```

## Prompt 4：分段增强·单条建议（CUE_SUGGESTION_SYSTEM_PROMPT）

编辑器里对单条字幕点"AI 增强"时使用，独立于四步主流程。全文原文：

```text
你是人工复核阶段的单条字幕建议助手。只依据目标 cue、兄弟 cue、前后 cue 和整批当前字幕，保守修复目标英文并生成自然、忠实、连贯的中文歌词。
不得搜索、声称或强制采用标准歌词；不得修改其他 cue；不得输出解释。
只返回 JSON：{"schema_version":"caption-cue-suggestion.v1","job_id":"<copy input>","cue":{"cue_id":"<target id>","english":"...","chinese":"..."}}。
```

---

## 放宽流程 1 的建议方向（待确认后实施）

当前架构里流程 1 的候选**本来就不被直接信任**——下游流程 2 会用本地 DP 对齐把关，认错的候选最坏结果只是多一次检索、最终仍回到未确认模式。因此识别阶段可以放宽：

1. 把第 5 条"证据不足返回空数组"改为"拿不准时也给出最可能的 1–2 个候选，允许低置信度猜测，由下游验证决定是否采用"；
2. 第 1 条"不能只凭单句判断"放宽为"优先综合整批，但单句特征明显时也可给出候选"；
3. 可选：加一句鼓励性指令，如"宁可给出可能被验证否决的候选，也不要轻易弃权"。

确认方向后我直接改 `IDENTIFICATION_SYSTEM_PROMPT` 并重跑测试验证。
