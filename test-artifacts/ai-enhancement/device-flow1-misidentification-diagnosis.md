# 真机第 4 条字幕未纠正根因诊断：流程 1 歌曲识别非确定性误判

日期：2026-08-27 23:44–23:50
设备：真机 fcf4b0cb（5G 网络），DeepSeek Key 尾号 4bc2
测试输入：与测试 3 / PC live 测试完全相同的视频（18716ms，11.44–18.72s 段）

## 结论

**根因：流程 1（LLM 歌曲识别）输出具有非确定性，本次真机运行把歌曲误识别为
"The Smallest Man Who Ever Lived"（Taylor Swift），导致流程 2 验证失败、
整条链路退回 UNCONFIRMED 保守模式，第 4 条 "For so long, let me" 无法被纠正。**

不是网络问题、不是 Key 问题、不是代码执行故障：
- 状态显示 "DeepSeek enhanced 4 captions." → 三次 DeepSeek 请求全部 2xx，Key 有效
- 手机到 LRCLIB / DeepSeek 网络此前已实测可达
- 系统按设计正确否决了错误候选（本地 DP 把关生效），行为符合架构原则

## 证据链（设备端临时诊断日志，tag=LCEnhanceDiag）

```
08-27 23:46:39.153  MainViewModel: event=asr_completed mode=LOCAL captionCount=4
08-27 23:46:40.562  LCEnhanceDiag: flow1_candidates count=1 The Smallest Man Who Ever Lived | Taylor Swift
08-27 23:46:41.711  LCEnhanceDiag: flow2_identity_search index=0 identity='The Smallest Man Who Ever Lived | Taylor Swift' hits=20
08-27 23:46:57.767  LCEnhanceDiag: flow2_result UNCONFIRMED identity='The Smallest Man Who Ever Lived | Taylor Swift' foundLyrics=true searchUnavailable=false
```

运行后 UI 快照（caption_state）第 4 条：`For so long, let me` / 那么久，让我 —— 与
UNCONFIRMED 保守模式（禁止纠错、忠实直译）的输出特征完全吻合。

对比：2026-08-27 22:30 PC live 测试（同一份 4 条 cue）流程 1 识别为
"So Long, London"，流程 2 本地 DP CONFIRMED（0.9618），第 4 条纠正为
"For so long, London" / "再见了，伦敦"。

## 为何两次运行结果不同

流程 1 的请求体完全相同（Whisper 确定性 + 同一视频），但 LLM 采样输出不同：
两次猜的都是 Taylor Swift《The Tortured Poets Department》专辑内曲目
（"The Smallest Man Who Ever Lived" 与 "So Long, London" 开头情绪/措辞相近），
属于低信息量残句下的合理猜测区间，但一次猜对、一次猜错。
下游验证机制正确拦截了错误候选，代价是这次运行失去了权威歌词参照。

## 顺带发现的设计缺口（可修复）

`DeepSeekCaptionEnhancementProvider.findVerifiedLyrics` 中，歌词文本兜底检索的
触发条件为 `best == null && !foundLyrics && !searchUnavailable`。
本次运行错歌名在 LRCLIB 搜到了 20 条歌词（foundLyrics=true），兜底被门控挡住。
若把条件放宽为「候选歌词找到了但无一通过 DP 验证时也尝试文本兜底」，
本次运行本可通过歌词原文检索回 "So Long, London" 并纠正第 4 条。
这是一个低风险、高收益的改进点（DP 验证器仍是最终把关者）。

## 观测手段说明（为何之前查不到）

- `AiTraceRecorder`（ai-trace.jsonl）在当前主源码中零引用，设备端无流程日志
- 本次通过在 `DeepSeekCaptionEnhancementProvider` 增加临时 `onDiagnosticDetail`
  回调（纯 Kotlin，不污染 JVM 单测），并在 `MainViewModel` 接线到
  `Log.i("LCEnhanceDiag", ...)`，重编装机后一次性拿到证据
- 源码中的临时打点已移除；当前手机上安装的 APK 仍带日志输出，
  如需再次观察，重新运行识别后 `adb logcat -s LCEnhanceDiag` 即可

## 后续建议

1. **修复兜底门控**（推荐）：`foundLyrics=true` 但 DP 全部未过时也走歌词文本兜底检索
2. 可选：流程 1 失败/未确认时对状态文案增加提示（当前统一显示 "DeepSeek enhanced"，
   用户无法区分 verified 与 unconfirmed 两种结果质量）
3. 误识别概率的量化测量（PC 端重放流程 1 prompt 多次）未做，如需要可补
