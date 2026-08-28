# 真机诊断：为什么第 4 条字幕还是旧文本（"let me"）

时间：2026-08-27 23:06

## 结论

**新 APK 已经装上，但装机后还没有重新点过"识别"。**
编辑器里现在显示的字幕，是**旧版本（严格 prompt）在 08-26 12:49 跑出来的旧结果**，
覆盖安装不会重新计算已有字幕，必须对视频重新执行识别，新 prompt 才会生效。

## 证据链

### ① 新包确实装上了

```
dumpsys package com.example.lyriccaptioner
    lastUpdateTime = 2026-08-27 23:03:58   ← 今晚刚更新
```

### ② 但装机后识别流程一次都没跑过

设备上的流程日志 `cache/ai-trace.jsonl`（每次识别都会覆盖重写）：

```
最后写入时间 = 2026-08-26 12:50:11   ← 昨天，新包之前
```

### ③ logcat 里也没有任何识别活动

拉取装机后（22:54）至今的全部日志（约 15.7 万行），应用进程 23471 只有
播放器解码、渲染、触摸滚动类日志，**零条**识别流程日志
（没有 `workflow_started`、没有 whisper、没有 deepseek 调用）。

### ④ trace 里现存的就是那一次旧结果（08-26 12:49，job `caption-533671023`）

```json
{"event":"song_identity_completed","fields":{"identified":false,"title":null,"artist":null}}
{"event":"lyrics_search_skipped","fields":{"reason":"song_identity_missing"}}
{"event":"final_selection","fields":{"selected_source":"AI_2","processing_level":"AI_ONLY_COMPLETE","reason":"song_identity_missing"}}
{"event":"workflow_published","fields":{"processing_version":"deepseek-v4-caption-ai-only.v1","caption_count":4}}
```

即：旧严格 prompt → 歌曲识别弃权 → 歌词检索被跳过 → 保守模式直译 → 第 4 条保持 "let me"。

## 另外一个差异点

真机上一次跑的素材和测试 3 的素材**不是同一段视频**：

| | 测试 3 / live 重跑用的数据 | 手机上一次识别的数据 |
|---|---|---|
| cue 数 | 4 条（0–4320–7360–11440） | 3 条（0–17840–28480–38880） |
| 素材时长 | 18.7 秒 | ~38.9 秒 |

所以就算重新识别，手机上这条视频的 Whisper 输出也不会和测试 3 的文本逐字相同——
但只要新 prompt 生效，歌曲识别→歌词检索→纠错链路就会走通，
第 4 条那种残句有机会被权威歌词纠正（前提是同一段音频里包含足够歌词线索）。

## 下一步

在手机上对目标视频**重新点一次识别**，跑完后我再拉一次
`ai-trace.jsonl` + 界面截图对比，就能确认新 prompt 在真机上的实际表现。
