# 交接词：AI 增强流程与 Prompt 优化（复制到新对话使用）

## 背景

我在优化 Android 项目 **lyric-captioner-android**（项目根目录 `d:\DevEnv\Projects\lyric-captioner-android`）里的「AI 增强字幕」功能。业务场景：约 30 秒的歌曲片段视频，经 Whisper ASR（Automatic Speech Recognition，自动语音识别）产出 4~9 条带错词的英文字幕，云端 LLM（Large Language Model，大语言模型，使用 DeepSeek）增强后输出高质量双语歌词字幕（纠错英文 + 自然中文）。

## 你的任务

帮我调整这条增强流程、优化对应的 prompt（发给模型的指令文本）。目标：猜歌稳定、检索能命中、绝不注入错误歌词、双语输出自然。

## 整体流程（先读懂再动手）

```
输入整批 ASR cues
   → 启动门槛（cue 数 <3 直接保守模式）
   → 流程1：LLM 猜歌名（≤2 候选，禁止弃权）
   → 流程2：逐候选检索歌词库 + 本地 DP 校验
      （DP = Dynamic Programming，动态规划序列比对，唯一闸门，
       阈值 0.82，≤5 条小批 0.76；全不通过且服务可用 → 歌词原文兜底检索再校验）
   → 流程3：已验证模式（按权威歌词纠错）/ 保守模式（忠实直译禁止编造），两段不同 prompt
   → 流程4：合同校验（输出与输入条数 1:1、id 与时间戳不可变、丢弃模型自报的 song_match）
外层降级：云端失败且错误类型在可恢复白名单（离线/连接/超时/可重试服务器/无效响应）
   → 转本地机器翻译兜底；认证失败等不可恢复错误直接失败
```

## 关键文件清单

- 流程实现 + 三段 prompt 常量：`app\src\main\java\com\example\lyriccaptioner\processing\enhancement\DeepSeekCaptionEnhancementProvider.kt`（`IDENTIFICATION_SYSTEM_PROMPT` / `VERIFIED_LYRICS_SYSTEM_PROMPT` / `UNCONFIRMED_SYSTEM_PROMPT`）
- 本地 DP 验证器与全部阈值：同目录 `SongLyricsCandidateVerifier.kt`
- 歌词检索（LRCLIB 在线歌词库）：同目录 `SongLyricsSearchTool.kt`
- 数据合同与错误类型：同目录 `CaptionEnhancementContract.kt`
- 流程4 校验器：同目录 `CaptionEnhancementResponseValidator.kt`
- 本地翻译降级协调层：同目录 `CaptionEnhancementCoordinator.kt`

## 本地验证沙箱（最重要，一切改动先在这里验证）

- 沙箱测试：`app\src\test\java\com\example\lyriccaptioner\processing\enhancement\ThreeVideoEnhancementSandboxTest.kt`
- 内置三组真机 ASR 数据（视频1 九条、视频2 四条、视频3 五条），真实调用 DeepSeek 与歌词库，运行后生成报告 `test-artifacts\ai-enhancement\three-video-local-sandbox-rerun.md`（含整体策略流程图、本地规则表、逐阶段 prompt 全文/输入/输出/触发策略）
- **沙箱与 app 共用同一份源码：改 prompt 或阈值后重跑沙箱，就等于改了 app，无需二次同步**
- 运行命令：`.\gradlew.bat testDebugUnitTest --tests "com.example.lyriccaptioner.processing.enhancement.ThreeVideoEnhancementSandboxTest" --console=plain`
- `.env` 里有 `DEEPSEEK_API_KEY`；缺 key 时测试自动跳过
- 沙箱未覆盖（与本次优化无关）：云端失败→本地翻译降级、单条字幕 AI 建议（suggest 入口）、错误分类重试

## 已知问题与优化方向

1. **文本兜底检索命中率太低**：兜底用 ASR 原文拼接去搜，三视频实测全部 0 命中。候选方案：让流程1 一次调用同时输出"净化歌词行"（修复后的歌词句子），用它做并行文本检索。
2. **猜歌非确定性**：同一输入两次运行答案不同（视频3 真机猜中 Eyes On Me，本地沙箱却猜成 Never Ending）。任何 prompt 版本必须同一输入重跑 5 遍看稳定性，不许凭单次结果下结论。
3. **歌词库版本差异**：视频3 实际是王菲《Eyes On Me》，猜对过一次但检索到的歌词版本与片段实际演唱内容有出入，DP 校验未过。
4. 视频1 已被系统确认是《Creepin' Up On You》（Darren Hayes），可作第一条黄金标签；视频2、3 的正确歌曲待用户确认。

## 测评红线（不可突破）

**错误确认率必须为 0**：DP 校验不达标的歌曲，任何情况下都不许标成 CONFIRMED（已确认）。本地校验是唯一有权确认歌曲的环节，永远不许信任模型自报的确认结果。

## 协作规则（必须遵守）

1. 英文缩写第一次出现必须附中文简要解释（如 ASR、LLM、DP）
2. 展示流程/步骤一律用流程图或图示，禁止 1/2/3/4 纯文字列表
3. 验证报告统一格式：整体触发/拦截策略流程图 → 输入表 → 逐阶段（流程1-4）的 prompt 全文/输入/输出/触发策略 → 输出结果表 → 汇总表
4. 不要执行 git commit（除非我明确要求）；构建必须带 `-PenableWhisperNative=true` 参数
5. 本机 PowerShell 内联 `$` 变量和中文会被吞，需要写脚本时落到 `.ps1` 文件再执行
6. 解释问题时不要默认我记得项目细节，请重新交代相关背景

## 第一步

先通读上面关键文件清单和沙箱基线报告，然后给我一份 prompt 与流程的优化方案（含测评方法），**经我确认后再动代码**。
