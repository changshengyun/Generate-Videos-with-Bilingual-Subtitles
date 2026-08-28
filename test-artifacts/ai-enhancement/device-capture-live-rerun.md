# 放宽识别 Prompt 后的真机数据重跑（Live）

运行时间：2026-08-29T00:11:27.9918303，端点 `https://api.deepseek.com/chat/completions`，模型 `deepseek-v4-pro`。
输入：2026-08-27 20:31 真机抓取的 4 条 Whisper cue（job_id=device-capture-live-rerun）。

## 输入 cues

| id | 时间轴 | confidence | 英文原文 |
|---|---|---|---|
| whisper-0-0 | 0..4320 | 0.85 | I stopped CPR, after all it's no use |
| whisper-1-4320 | 4320..7360 | 0.8 | The spirit was gone, we would never come to |
| whisper-2-7360 | 7360..11440 | 0.78 | I'm pissed off you let me give you all that youth for free |
| whisper-3-11440 | 11440..18720 | 0.62 | For so long, let me |

## DeepSeek 调用 #1 失败 status=402

```json
{"error":{"message":"Insufficient Balance","type":"unknown_error","param":null,"code":"invalid_request_error"}}
```
