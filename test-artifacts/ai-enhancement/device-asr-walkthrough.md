# 真机 ASR 数据离线 Walkthrough（9 段真实 Whisper 输出）

运行时间：2026-08-29T17:37:33.7356037
数据来源：`test-artifacts/device-capture/device-asr-base.json`
设备：Xiaomi 25098PN5AC (fcf4b0cb)，Whisper v1.9.1，31.6s 音频，9 段识别
注意：本测试使用假 HTTP 响应（离线），不消耗 API 额度

## 输入：真机 Whisper 识别结果

| # | 时间 | 识别文本 | 置信度 | token 数 |
|---|---|---|---|---|
| 0 | 0..2800ms | I have to live without you | 0.81 | 8 |
| 1 | 2800..7000ms | Nobody could, I need to be around you | 0.8 | 10 |
| 2 | 7000..10600ms | Watching you, no one else can love you | 0.85 | 10 |
| 3 | 10600..12600ms | Like I do | 0.71 | 4 |
| 4 | 12600..17000ms | Healing and I'm creeping up on you | 0.6 | 9 |
| 5 | 17000..20000ms | I know that it won't be right | 0.86 | 9 |
| 6 | 20000..25000ms | If I stay all night to be among you | 0.77 | 10 |
| 7 | 25000..29000ms | Creeping my own you | 0.66 | 6 |
| 8 | 30000..31400ms | (upbeat music) | 0.67 | 7 |

## 流程 Stage Trace

```
enhance(request)
  ├─ searchScheduler == null → Legacy 路径
  │
  ├─ CANDIDATE_REQUEST
  ├─ CANDIDATE_PARSE
  ├─ LYRICS_SEARCH
  ├─ LYRICS_VERIFY
  ├─ VERIFIED_LYRICS_SELECTED
  ├─ WHOLE_SONG_REQUEST
  └─ WHOLE_SONG_PARSE
```

Stage 序列：`CANDIDATE_REQUEST → CANDIDATE_PARSE → LYRICS_SEARCH → LYRICS_VERIFY → VERIFIED_LYRICS_SELECTED → WHOLE_SONG_REQUEST → WHOLE_SONG_PARSE`

## Flow 1: 歌曲识别

- HTTP 请求：Chat Completions（`/chat/completions`）
- 输入：9 条 Whisper cue（含 1 条噪声 `(upbeat music)`）
- 假响应返回：`Creeping Up on You` by `The Beatles`
- Stage: CANDIDATE_REQUEST → CANDIDATE_PARSE

## Flow 2: LRCLIB 搜索 + DP 验证

- searchTool.search() 返回模拟歌词（8 行）
- SongLyricsCandidateVerifier.verify() 进行 DP 对齐
- DP 验证结果：CONFIRMED（过线）
- Stage: LYRICS_SEARCH → LYRICS_VERIFY → VERIFIED_LYRICS_SELECTED

## Flow 3: 双语生成

- 模式：`verified_complete_lyrics`
- HTTP 请求：Chat Completions（`/chat/completions`）
- Stage: WHOLE_SONG_REQUEST → WHOLE_SONG_PARSE

### 输出字幕

| id | 时间 | corrected_english | chinese |
|---|---|---|---|
| device-asr-0 | 0..2800ms | I have to live without you | 中文歌词 device-asr-0 |
| device-asr-1 | 2800..7000ms | Nobody could, I need to be around you | 中文歌词 device-asr-1 |
| device-asr-2 | 7000..10600ms | Watching you, no one else can love you | 中文歌词 device-asr-2 |
| device-asr-3 | 10600..12600ms | Like I do | 中文歌词 device-asr-3 |
| device-asr-4 | 12600..17000ms | Healing and I'm creeping up on you | 中文歌词 device-asr-4 |
| device-asr-5 | 17000..20000ms | I know that it won't be right | 中文歌词 device-asr-5 |
| device-asr-6 | 20000..25000ms | If I stay all night to be among you | 中文歌词 device-asr-6 |
| device-asr-7 | 25000..29000ms | Creeping my own you | 中文歌词 device-asr-7 |
| device-asr-8 | 30000..31400ms | (upbeat music) | 中文歌词 device-asr-8 |

## Flow 4: 本地批次校验

- state: `CLOUD_APPLIED`
- source: `CLOUD_AI`
- songMatch: `SongMatch(status=CONFIRMED, title=Creeping Up on You, artist=The Beatles, confidence=0.90714437, source=lrclib:device-asr)`
- 应用 9 条字幕

## 汇总

| 维度 | 值 |
|---|---|
| 输入 cue 数 | 9 |
| 输出 cue 数 | 9 |
| Stage 数 | 7 |
| 歌曲状态 | CONFIRMED |
| Flow 4 状态 | CLOUD_APPLIED |
| 是否走真实 API | 否（离线假响应） |
