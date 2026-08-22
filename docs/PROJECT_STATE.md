# LyricCaptioner V3 Project State

- `STATE_REV: 2026-08-23.003`
- Repository: `D:\DevEnv\Projects\lyric-captioner-android`
- Branch: `migration/lyric-captioner-history`
- Stage baseline HEAD: `9a798ccb3890128565a12c924c11e6468908a2b9`
- Upstream: `origin/migration/lyric-captioner-history`, snapshot ahead 37
- Current task: `V3-ASR-DIAG-001`
- Stage state: `PASS / BASE_DEVICE_DIAGNOSTIC_VERIFIED`
- Product status: `D / DATA_INSUFFICIENT_FOR_CONTEXT_VS_PARAMETER`
- Current gate: `DIAGNOSTIC_COMPLETE`
- Evidence ceiling: `BASE_DEVICE_DIAGNOSTIC_ONLY`
- Last state sync: 2026-08-22

## 当前决定

- 用户明确固定“视频肯定有声音”，本阶段不再验证或怀疑输入视频音轨。
- 用户取消 small 验证；当前只允许 base 对已经冻结的同一个 WAV 运行一次。
- 每次识别必须 fresh context、`no_context=true`、结束立即 free，并输出 segment/no-speech/token 原始观测。
- 只做 Debug/instrumentation 与 Native provenance 观测，不改产品业务逻辑；结果只用于在 Context 复用、Native/编译环境、Whisper 参数之间定位。
- base 在 fresh context、`no_context=true` 下以 `whisper_full=0` 完成，8 段歌词加末尾 `(upbeat music)`；Native/编译环境不是只能输出音乐标记的全局故障。
- fresh context 与 `no_context=true` 同时变化，不能唯一地区分 Context 复用和参数因素；正式结论为 `D`。

- 用户停止旧 AI15 真机验收；旧逐 cue 修正/翻译准确率不被接受。
- 用户批准立即实现路线 B：AI 识别候选歌曲，SearchTool 检索完整英文歌词，多 cue 验证候选，再由 AI 基于整首歌词生成中文歌词并对齐回字幕。
- 路线 A（逐 cue 直译）淘汰；路线 C（无检索直接生成整首歌词）只允许作为未确认降级，不能声称歌曲匹配成功。
- 当前不考虑速度、费用、UI 或其他产品优化；现有安全硬边界继续有效。

## 架构门禁

- 权威需求：`docs/REQUIREMENTS.md`
- 路线比较：`docs/TECH_OPTIONS.md`
- 环境：`docs/ENVIRONMENT_REPORT.md`
- Spike：`docs/SPIKE_PLAN.md`
- 用户的“现在开始开发”视为对路线 B 和所列最小 Spike 的明确批准。
- 只有 SP-A 通过后才接入该歌词源；SP-B/SP-C 作为实现测试门禁。

## 受保护工作树

保留既有 `.gitignore`、`AGENTS.md`、三份活动文档、产品架构文档、`third_party/ffmpeg-kit` 脏状态、`.emulator-test-assets/`、`tools/opus-mt-en-zh/`、DeepSeek 工具和所有未知内容。不得 reset、clean、覆盖、批量暂存或 push。

## 下一允许动作

本诊断已关闭。若用户要求继续定位，下一最小动作仅允许同一 base/WAV/fresh context 的 `no_context=false` 单变量对照；不得自动修改产品 ASR 策略。

## 上下文与轮换

准确上下文不可用时记录 `Unavailable`，不估算；在阶段 `ACCEPTED` 边界轮换。
