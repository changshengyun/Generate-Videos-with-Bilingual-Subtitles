# 放宽识别 Prompt 后的真机数据重跑（Live）

运行时间：2026-08-29T17:37:33.7542213，端点 `https://api.deepseek.com/chat/completions`，模型 `deepseek-v4-pro`。
输入：2026-08-27 20:31 真机抓取的 4 条 Whisper cue（job_id=device-capture-live-rerun）。

## 输入 cues

| id | 时间轴 | confidence | 英文原文 |
|---|---|---|---|
| whisper-0-0 | 0..4320 | 0.85 | I stopped CPR, after all it's no use |
| whisper-1-4320 | 4320..7360 | 0.8 | The spirit was gone, we would never come to |
| whisper-2-7360 | 7360..11440 | 0.78 | I'm pissed off you let me give you all that youth for free |
| whisper-3-11440 | 11440..18720 | 0.62 | For so long, let me |

## 流程 1 歌曲识别（DeepSeek 调用 #1）

### 使用的 Prompt

```text
输入是同一首英文歌曲的整批 Whisper 识别字幕，内容可能包含错词、漏词和错误断句。你需要完成两个任务：
任务A 歌曲识别：
1.必须综合整批字幕中的多条歌词推断对应歌曲，优先依据整批线索整体判断；线索较少时也必须给出最可能的猜测，不得弃权。
2.Whisper 可能把歌词中的关键词或歌名本身识别错（例如把歌名唱词听成形近词）。识别时必须依据整批歌词的语义、意象、句式和用词推断真实歌曲；不得直接照抄 Whisper 原文中疑似歌名的字面拼写作为候选歌名。
3.单条字幕可能包含同一歌曲的两句歌词，这不是异常输入，综合判断时按两句理解。
4.最多返回 2 个候选，按可能性从高到低排列，每个候选给出歌名及其最知名的原唱歌手（artist），候选不能声称已经确认。两个候选必须是不同的歌曲，不得返回同一首歌的别名、别名拼写或翻唱版本。
5.禁止返回空 candidates 数组。即使整批证据不足或拿不准，也必须给出你判断中最可能的那首歌；猜错会被下游歌词验证否决，不会造成错误确认，所以宁可给出猜测也不要返回空。
任务B 净化歌词行：
6.输出 cleaned_lines 数组，与输入 cues 一一对应：条数相同、顺序相同。每一行是对应 raw_english 的净化歌词文本。
7.净化只允许拼写、同音词、语法层面的修复（例如把听错的形近词改回常见正确写法），禁止凭你对歌曲的记忆改写、补写或替换歌词内容；拿不准的措辞必须保留 Whisper 原文。
8.纯噪声行（例如 [Music]、(upbeat music)、纯标点）输出空字符串 ""，不得编造歌词填充。
只返回 JSON，格式必须严格为：{"candidates":[{"title":"...","artist":"..."}],"cleaned_lines":["..."]}。
```

### 模型返回结果（格式化）

```json
{
  "candidates": [
    { "title": "So Long, London", "artist": "Taylor Swift" },
    { "title": "The Smallest Man Who Ever Lived", "artist": "Taylor Swift" }
  ]
}
```

## 流程 2 歌词检索 + 本地 DP 校验（无 LLM 调用）

songMatch：`SongMatch(status=CONFIRMED, title=So Long, London, artist=Taylor Swift, confidence=0.9618056, source=lrclib:35965276)`

## 流程 3 双语生成（DeepSeek 调用 #2，已验证模式）

### 使用的 Prompt

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
{"schema_version":"<copy input>","job_id":"<copy input>","processing_version":"deepseek-v4-pro-lyrics-search-context.v5","cues":[{"id":"<copy input>","start_ms":0,"end_ms":1,"corrected_english":"complete English line","chinese":"coherent Chinese lyric line"}]}.
每个 cue 必须包含上面展示的全部六个字段。不要返回 song_match。
```

### 输入要点

mode=`verified_complete_lyrics`；已注入歌曲 `So Long, London` / `Taylor Swift`（`lrclib:35965276`）的完整英文歌词与逐 cue canonical 对齐。

### 模型返回结果

| id | 时间轴 | corrected_english | chinese |
|---|---|---|---|
| whisper-0-0 | 0..4320 | I stopped CPR, after all, it's no use | 我停止了心肺复苏，毕竟已无济于事 |
| whisper-1-4320 | 4320..7360 | The spirit was gone, we would never come to | 灵魂已逝，我们再也无法苏醒 |
| whisper-2-7360 | 7360..11440 | I'm pissed off you let me give you all that youth for free | 我愤怒的是你让我白白付出了所有青春 |
| whisper-3-11440 | 11440..18720 | For so long, London | 再见了，伦敦 |

## 流程 4 本地校验

state=`CLOUD_APPLIED` source=`CLOUD_AI` 应用 `4` 条。

| id | 英文 | 中文 |
|---|---|---|
| whisper-0-0 | I stopped CPR, after all, it's no use | 我停止了心肺复苏，毕竟已无济于事 |
| whisper-1-4320 | The spirit was gone, we would never come to | 灵魂已逝，我们再也无法苏醒 |
| whisper-2-7360 | I'm pissed off you let me give you all that youth for free | 我愤怒的是你让我白白付出了所有青春 |
| whisper-3-11440 | For so long, London | 再见了，伦敦 |

## 结论：放宽前后对比（同一份 4 条设备数据）

| 阶段 | 旧严格 prompt（20:31 真机） | 新放宽 prompt（本次） |
|---|---|---|
| 流程 1 歌曲识别 | 弃权返回空候选 | 识别出 "So Long, London" — Taylor Swift |
| 流程 2 检索校验 | 0 命中，NOT_FOUND | `SongMatch(status=CONFIRMED, title=So Long, London, artist=Taylor Swift, confidence=0.9618056, source=lrclib:35965276)` |
| 流程 3 双语生成 | UNCONFIRMED 保守，残句直译 | 已验证模式，依据完整权威歌词纠错 |

识别阶段不再弃权后，下游检索+本地 DP 验证完整接管；若歌曲被确认，Whisper 错词会被权威歌词纠正。
