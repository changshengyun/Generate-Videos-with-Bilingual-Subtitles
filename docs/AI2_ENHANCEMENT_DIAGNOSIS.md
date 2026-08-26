# AI 增强效果根因分析与 AI2 Prompt 优化方案

## 文档状态

- `DIAG_REV: 2026-08-26.001`
- 状态:`ANALYSIS_ONLY`(本文只做分析与方案,不修改任何代码、Prompt 或合同)
- 输入证据:
  - 桌面 `LyricCaptioner-Whisper-RAW-2026-08-26/`(两份 RAW 存档,外部用户证据)
  - 2026-08-26 工作树源码(`processing/enhancement/` 全链路)
- 证据等级标注:`代码事实` = 当前源码可验证;`RAW 证据` = 桌面存档原文;`人工判读` = 分析者推断,未经系统验证;`外部事实` = 需联网核验。

## 1. 基准输入(外部 RAW 证据)

两个测试用例均来自真机(设备 `fcf4b0cb` / `25098PN5AC`)本地 Whisper 直接识别结果,不含 AI 纠错。

### TEST_CASE_1(18.716s,3 cue,confidence 未记录)

| cue_id | 时间 | confidence | RAW 英文 |
|---|---|---|---|
| `whisper-0-0` | 0–7360 | 未记录 | `I stopped CPR after all it's no use The spirit was gone we would never come too` |
| `whisper-1-7360` | 7360–11440 | 未记录 | `I'm pissed off you let me give you all that youth for free` |
| `whisper-2-11440` | 11440–18640 | 未记录 | `For so long, learning` |

特征(存档文档自述):一个父 cue 融合两句、错词、漏词、错误歌词定位。人工判读:高度疑似 Glass Animals《Youth》("The spirit was gone, we would never come through";"come too" 为错词)。

### TEST_CASE_2(39.080s,3 cue,`job_id=caption-532941707`)

| cue_id | 时间 | confidence | RAW 英文 |
|---|---|---|---|
| `whisper-0-0` | 0–17840 | 0.70015913 | `Sadie of stars, are you shanning just for me? Sadie of stars, there's so much I can say` |
| `whisper-1-17840` | 17840–28480 | 0.6325985 | `Who know, I fear they fom the first embrace I shall with you` |
| `whisper-2-28480` | 28480–38880 | 0.8152909 | `I know all dreams to finally come true` |

特征(存档文档自述):歌曲名称错识别、拼写错误、语法错配、一个父 cue 内含两句真实歌词。人工判读:高度疑似《La La Land》插曲《City of Stars》("Sadie of stars" 应为 "City of stars";"shanning" 应为 "shining")。

### Prompt 优化的边界(存档文档冻结)

- 输入只使用以上 RAW Whisper cue;不把当前 AI2、搜索歌词、自动拆分或人工编辑结果混入基准输入。
- Prompt 优化时必须保持 `cue_id`、顺序和父 cue 时间范围可追踪。
- 两个用例分别覆盖:合句+错词漏词;错歌名+拼写语法错配+合句。

## 2. 分层根因分析

### 2.1 Whisper RAW 质量层(已确认,输入侧先天缺陷)

- `RAW 证据`:两组均有明显错词("come too"、"shanning"、"fom")、合句(cue 内两句歌词)与低置信段(0.63–0.70)。
- `代码事实`:Whisper small 模型对歌声素材的识别质量是既有已知问题(见 `docs/debug/ASR_SMALL_BASE_VERSION_COMPARISON.md`),本层不由 AI2 负责修复,但 AI2 的所有下游环节都必须以此为输入前提设计。
- 结论:AI2 的目标不是"完美还原",而是"在错词+合句+低置信输入下仍然收敛到正确歌曲与正确双语"。当前实现对此前提的防护不足(见 2.2–2.4)。

### 2.2 信息面:AI 请求合同丢弃了 confidence 与素材时长(代码事实,影响最大)

- `代码事实`:
  - `CaptionCue.confidence` 由 `WhisperSegmentConverter` 从 native 每段置信度填充(`WhisperLocalSpeechRecognizer.kt`),编辑器也用它标记 `needsReview`(< 0.82)。
  - 但 `CaptionEnhancementRequestCue`(`CaptionEnhancementContract.kt`)只有 `id/startMs/endMs/rawEnglish` 四个字段;`CaptionEnhancementRequestMapper.map()` 不映射 confidence;`DeepSeekCaptionEnhancementJson.requestPayload()` 发送的 cue JSON 同样只有这四个字段。
  - 请求中也没有 `media_duration_ms`。
- 后果:DeepSeek 在两个阶段都拿不到"哪些 cue 可信、素材多长"的信息。TEST_CASE_2 的 cue-1(0.63)恰是错得最狠的一句,模型无从知道应对它做最大幅度纠错,而对 cue-2(0.82)应保守。桌面 AI2 输入文件中 `confidence=UNAVAILABLE` 正是该缺口的直接体现。
- 修复方向(合同变更,属未来任务):请求 cue 增加 `confidence` 字段;请求根增加 `media_duration_ms`。两者在 `CaptionCue` 与导入状态中均已存在,不引入新采集成本。

### 2.3 歌曲识别层:Prompt 未针对"错误歌名"设防(代码事实 + 人工判读)

- `代码事实`:`IDENTIFICATION_SYSTEM_PROMPT` 只要求"综合整批字幕识别歌曲、按可能性返回最多 2 个候选、证据不足返回空"。没有任何一条指示"Whisper 原文中疑似歌名的词本身可能是听错的,不得照抄,必须从整批语义推断真实歌名"。
- `人工判读`:TEST_CASE_2 的决定性错误恰在此:RAW 把 "City of stars" 听成 "Sadie of stars"。若模型跟随原文,候选就是错误歌名。
- 后果链(结构必现):错误候选 → LRCLIB 按 track_name 检索不到正确歌 → 阶段 3 只能走 UNCONFIRMED 保守路径 → 输出质量显著劣化。这是"AI 增强时好时坏"的第一结构原因。

### 2.4 检索层:只按候选歌名+歌手检索,无歌词文本兜底(代码事实,结构缺口)

- `代码事实`:`LrclibSongLyricsSearchTool` 只构造 `GET /api/search?track_name=<title>&artist_name=<artist>`。LRCLIB 同一端点还支持 `q=<任意文本>` 模糊检索(可用歌词片段反查歌曲,`外部事实`,接入前需按项目惯例做一次 Spike 核验),当前实现未使用。
- 后果:整条 verified 路径的正确性单点依赖"阶段 1 候选歌名正确"。没有任何"用 RAW 歌词文本反查"的第二通道,歌名错则全链路降级。
- 修复方向(属未来任务):候选检索为空或验证全拒时,增加一次 `q=<RAW 合并文本截断>` 的兜底检索,再走同一 verifier 验证。验证器本身与歌名无关(只做歌词对齐),天然兼容该通道。

### 2.5 验证层:小批次零容错,RAW 差时正确歌曲也会被拒(代码事实)

- `代码事实`(`SongLyricsCandidateVerifier` 常量):`MIN_ELIGIBLE_CUES=3`、`MIN_MATCHED_CUES=3`、`MIN_COVERAGE=0.75`、`MIN_CUE_SIMILARITY=0.62`、`MIN_AVERAGE_SIMILARITY=0.78`、`MIN_MEDIAN_SIMILARITY=0.78`、`MIN_CONFIDENCE=0.82`。
- 两个基准用例都是恰好 3 cue。此时 `minimumMatches = max(3, ceil(3×0.75)) = 3`:**全部** cue 都必须匹配上歌词 span,任何一句(尤其 TEST_CASE_2 错误密集的 cue-1)匹配失败,整首验证即拒绝。同时 average/median ≥ 0.78 意味着错误密集 cue 的低相似度没有缓冲空间。
- 短 cue(如 "For so long, learning",4 token)的 edit-similarity 天然脆弱:一个词错即大幅失分,虽然 `similarity()` 已用 token-Dice 加权缓解,但仍低于长句。
- 后果:即使阶段 1 候选正确、检索成功,验证也极易在小批次+低质 RAW 下拒绝正确歌曲,把系统推入 UNCONFIRMED。与 2.3/2.4 叠加后,verified 路径在基准用例上的通过概率被三重压缩。
- 修复方向(属未来任务,需重新基线化阈值):按 eligible 数量分档放宽 minimumMatches(如 3 cue 时允许 1 句不匹配);或对 average/median 设小批次修正项。任何阈值调整都必须重跑既有正例/反例合同测试防误确认。

### 2.6 Prompt/生成层:三个 system prompt 的具体缺陷(代码事实)

对照基准输入的特征,现有 prompt 缺少四类指示:

| # | 缺失指示 | 基准用例中的体现 | 现状(代码事实) |
|---|---|---|---|
| G1 | "疑似歌名可能是听错的,需从整批语义推断真实歌名,不得照抄" | TEST_CASE_2 "Sadie of stars" | IDENTIFICATION prompt 无此内容 |
| G2 | "一个 cue 的 raw_english 可能包含两句歌词" | 两组 cue-0 均合句 | 三个 prompt 均未提及;模型可能只按单句纠错,或把第二句当噪音 |
| G3 | "corrected_english 允许在 cue 范围内包含两句(可换行),中文对应处理" | 合句 cue 的双语应各自两行 | prompt 未定义多句呈现格式;`requireText`/`isWellFormedText` 实际允许 `\n`,合同层面无障碍 |
| G4 | 逐 cue 置信差异(哪些 cue 错得多) | TEST_CASE_2 cue-1(0.63) vs cue-2(0.82) | 请求不含 confidence(见 2.2),prompt 无从引用 |

另有两处次要观察(低置信):
- `max_tokens = cues.size × 192` 对 3 cue 批为 768;合句 cue 的双语 JSON 输出偏紧,存在截断即 `INVALID_RESPONSE` 整批回退的风险。
- `WHOLE_SONG_PARSE` 之后 `parseEnhancementResponse` 要求严格字段;模型把毫秒输出为字符串/科学计数会触发 `requiredLong` 异常。现有 prompt 已用 "<copy input>" 措辞防护,风险低但非零。

### 2.7 响应校验层:误拒风险评估(代码事实)

- validator 的 id/时间戳/数量/顺序逐条比对是合同核心,必须保留;未发现会误拒合法响应的规则(空串拒绝、良构文本检查均合理)。
- 真实误拒风险主要来自模型侧格式漂移(start_ms 变字符串等),属 2.6 次要观察,不是根因。

## 3. 根因排序(针对"AI 增强效果不稳定")

| 排序 | 根因 | 置信度 | 证据 |
|---|---|---|---|
| 1 | AI 请求丢弃 confidence 与素材时长,模型信息不足 | 高 | 代码事实 + RAW 证据(confidence=UNAVAILABLE) |
| 2 | 检索只按候选歌名+歌手,无歌词文本兜底;候选错则 verified 路径全断 | 高 | 代码事实 |
| 3 | 小批次(3 cue)验证零容错(全匹配 + avg/median 0.78),低质 RAW 下正确歌曲被拒 | 高 | 代码事实 |
| 4 | IDENTIFICATION prompt 未设防"错误歌名照抄" | 中高 | 代码事实 + 人工判读 |
| 5 | prompt 未定义"一个 cue 两句歌词"的处理与输出格式 | 中高 | 代码事实 + RAW 证据(两组 cue-0) |
| 6 | max_tokens 对短批次合句双语输出偏紧 | 低 | 推断 |

注:1–3 属结构/合同层,4–6 属 prompt 文本层。仅做 prompt 优化(4–6)可改善但无法根治 1–3;仅改结构不优化 prompt 则模型行为不变。分层修复才是完整方案。

## 4. AI2 Prompt 优化建议(文本草案,未执行)

以下草案按存档边界设计:保持 `cue_id`、顺序、父 cue 时间范围可追踪;不混入当前 AI2 结果;不改变响应 JSON 结构。**执行修改属未来新任务,受 `PROJECT_STATE.md` 当前"不修改 AI Prompt"约束,本文不落地。**

### 4.1 IDENTIFICATION_SYSTEM_PROMPT 增补(针对 G1/G2)

在现有第 1 条之后插入:

```text
2.Whisper 可能把歌词中的关键词或歌名本身识别错(例如把歌名唱词听成形近词)。
识别时必须依据整批歌词的语义、意象、句式和用词推断真实歌曲;
不得直接照抄 Whisper 原文中疑似歌名的字面拼写作为候选歌名。
3.单条字幕可能包含同一歌曲的两句歌词,这不是异常输入,综合判断时按两句理解。
```

(原 2、3 条顺延;`candidates` 数量与空数组规则不变。)

### 4.2 VERIFIED_LYRICS_SYSTEM_PROMPT / UNCONFIRMED_SYSTEM_PROMPT 共同增补(针对 G2/G3)

在"不得增加、删除、拆分、合并、重排字幕或修改时间"之后插入:

```text
单条 raw_english 可能包含同一歌曲的两句歌词。对这类 cue,corrected_english 应在保持
该 cue 原有时间范围不变的前提下包含两句完整英文歌词,两句之间用一个换行符分隔;
对应的 chinese 同样输出两句中文并用一个换行符分隔,两句中文必须与两句英文一一对应。
其余 cue 仍然只输出单行。
```

(该约定与现有合同兼容:`isWellFormedText` 允许 `\n`;`SrtWriter`/ASS 导出按行拼接,双语两行呈现无需合同变更。)

### 4.3 请求 payload 结构建议(针对 G4,需合同变更,优先级最高)

```json
{
  "schema_version": "...", "job_id": "...", "processing_version": "...",
  "media_duration_ms": 39080,
  "cues": [
    {"id": "...", "start_ms": 0, "end_ms": 17840,
     "raw_english": "...", "confidence": 0.70015913}
  ]
}
```

配套 prompt 增补一句:

```text
confidence 是该条 Whisper 识别的置信度,数值越低说明该条错得越多,纠错幅度可以越大;
confidence 高的条目应尽量保守。
```

### 4.4 次要参数建议(低置信,随 4.3 一并评估)

- 合句批次 `max_tokens` 下限从 768 提至 1024,或公式改为 `cues × 256`。
- 保持 `temperature=0` 与 `json_object` 不变。

## 5. 回归基准使用说明

- 两个测试用例固定为 Prompt/结构优化的唯一基准输入,来源于桌面 RAW 存档,不得混入任何 AI2 历史输出。
- 每次优化后的离线评估顺序:IDENTIFICATION 是否返回正确歌名(TEST_CASE_2 判定标准为候选含《City of Stars》,人工判读目标)→ verified 路径是否接通(检索+验证是否 CONFIRMED)→ 逐 cue 比对 corrected_english/chinese 与父 cue 时间范围。
- 阈值(2.5)与检索兜底(2.4)的修改必须先在 JVM 合同测试上复跑既有正例/反例,防止误确认率上升。

## 6. 相关文档

- 架构现状:`V4_PRODUCT_ARCHITECTURE.md`
- SRT 输出缺口:`SRT_OUTPUT_GAPS.md`
- ASR 侧既有诊断:`debug/ASR_SMALL_BASE_VERSION_COMPARISON.md`
- 需求原始边界:`REQUIREMENTS.md`(V3-AI-001)
