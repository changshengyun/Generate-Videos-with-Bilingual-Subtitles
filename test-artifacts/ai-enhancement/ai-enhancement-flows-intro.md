# AI 增强字幕流程：简要功能介绍

整条链路把"视频里听到的英文歌"变成"英文纠错 + 中文翻译的双语字幕"。
共 5 个环节：流程 0 本地识别，流程 1–4 为 AI 增强四步。核心代码都在
`app/src/main/java/com/example/lyriccaptioner/processing/enhancement/`。

## 流程 0：Whisper 本地识别（ASR）

- **干什么**：把视频音轨切成片段，用设备本地的 Whisper 模型逐段识别出英文文本。
- **输入**：视频音频；**输出**：一批带时间轴的原始 cue（`id / start_ms / end_ms / raw_english / confidence`）。
- **特点**：纯本地、不联网、不花钱；识别结果含错词、漏词和错误断句（后续流程就是来修它的）。
- **关键组件**：`AsrModule`、`WhisperProcessSession`（JNI 调 whisper.cpp）。

## 流程 1：歌曲识别（DeepSeek 第 1 次调用）

- **干什么**：把整批歌词喂给 DeepSeek，让它推断"这是哪首歌、谁唱的"。
- **输入**：全部 cue 的英文原文 + 视频总时长；**输出**：1 个最可能的候选（歌名 + 原唱歌手）。
- **当前策略**：禁止弃权返回空——拿不准也必须猜，因为猜错会被流程 2 否决，不会造成错误确认。
- **关键组件**：`DeepSeekCaptionEnhancementProvider.enhance()`、`IDENTIFICATION_SYSTEM_PROMPT`、`parseSongCandidates()`。
- **启动门槛**：cue 数量 ≥ 3 才会发起识别；不足直接走保守模式。

## 流程 2：歌词检索 + 本地 DP 校验（不花 LLM 钱）

- **干什么**：拿流程 1 的歌名去 LRCLIB 查完整歌词；查不到就用歌词文本兜底检索；查到后用**本地动态规划（DP）算法**把 Whisper 识别结果和权威歌词逐句对齐。
- **输入**：候选歌名/歌手（或歌词文本）；**输出**：`songMatch` —— CONFIRMED（对齐置信度 ≥ 0.82）/ UNCONFIRMED / NOT_FOUND。
- **信任边界（最重要的一条设计）**：模型说什么都不算数，是否"确认歌曲"只由本地 DP 对齐置信度决定；模型响应里的 `song_match` 字段被解析器直接丢弃。
- **关键组件**：`LrclibSongLyricsSearchTool`（检索）、`SongLyricsCandidateVerifier`（DP 对齐）。

## 流程 3：双语生成（DeepSeek 第 2 次调用）

- **干什么**：生成最终双语字幕。分两种模式：
  - **已验证模式**（流程 2 确认了歌曲）：请求里注入完整权威歌词 + 逐 cue 对齐表，模型先依据权威歌词整批纠错英文，再结合整首歌上下文翻译中文——Whisper 的错词在这一步被纠正。
  - **未确认模式**（没查到歌词）：只允许保守纠错 + 自然翻译，明令禁止编造歌词、禁止冒充网易云译文。
- **硬约束**：输出必须和输入 1:1 对应——条数、id、时间戳一个都不能动；单条含两句歌词的 cue 用换行符分隔并一一对应。
- **关键组件**：`VERIFIED_LYRICS_SYSTEM_PROMPT` / `UNCONFIRMED_SYSTEM_PROMPT`、`parseEnhancementResponse()`。

## 流程 4：本地校验 + 原子发布

- **干什么**：最后一道闸门。本地校验器逐条核对模型返回的条数、id、时间戳是否与输入一致，通过后**原子替换**编辑器里的字幕，状态置为 `CLOUD_APPLIED`。
- **输入**：模型响应 + 原始请求；**输出**：校验通过 → 新字幕进编辑器；任何一条不一致 → 整体拒绝，编辑器内容不变。
- **关键组件**：`CaptionEnhancementResponseValidator`。

## 一张图看懂数据流

```
视频音频
  │ 流程0 Whisper 本地识别
  ▼
4+ 条英文 cue（带时间轴，含错词）
  │ 流程1 DeepSeek #1：这是什么歌？ → 1 个最可能候选
  │ 流程2 LRCLIB 检索 + 本地 DP 对齐 → CONFIRMED / UNCONFIRMED / NOT_FOUND
  │ 流程3 DeepSeek #2：纠错英文 + 翻译中文（确认了歌曲就用权威歌词纠错）
  ▼
4 条双语字幕（1:1 对应）
  │ 流程4 本地校验：条数 / id / 时间戳全对上
  ▼
原子写入编辑器（CLOUD_APPLIED）
```

## 设计要点速记

1. **本地验证说了算**：歌曲是否确认只信本地 DP，不信模型自我判断。
2. **纠错有据可依**：确认歌曲时模型只能依据权威对齐表纠错，不许凭记忆补写。
3. **先纠错后翻译**：两阶段分离，避免逐句孤立直译导致上下文不一致。
4. **确定性合同**：输入输出严格 1:1，由流程 4 强制执行，任何偏差整体拒绝。
