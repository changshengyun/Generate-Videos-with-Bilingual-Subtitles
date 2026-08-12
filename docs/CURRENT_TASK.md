# Current Task: V3-AI-001

- `STATE_REV: 2026-08-12.008`
- `TASK_REV: V3-AI-001.008`
- Stage state: `COMMITTED`
- Product status: `ACCEPTED / LYRICS_ACCURACY_SRT_DEVICE_VERIFIED`
- Evidence ceiling: `SRT_DEVICE_VERIFIED / SINGLE_SONG_ALIGNED_AND_MISALIGNED_SAMPLES`
- Brain writer: current primary thread
- Baseline HEAD: `421dc9cd3a158c0c9894e398df070c96a691dd12`

## 1. 当前目标与主链路

用户已停止旧 AI15 验收，并批准按准确性优先路线重做 AI 增强：

```text
Whisper 全批英文
-> AI 识别候选歌曲
-> SearchTool 检索完整英文歌词
-> 多 cue 反向验证歌曲
-> AI 基于整首歌词生成中英对照
-> 映射回原 cue 时间轴
-> 原子提交
```

中文必须是整首歌曲上下文中的歌词译文，不是逐 cue 独立直译。

## 2. 授权、范围与路线

- 用户于 2026-08-12 明确授权立即开发检索优先、整首上下文翻译路线。
- 允许修改 `processing/enhancement` 内合同、SearchTool、Provider、validator/coordinator 及对应测试和必要工厂接线。
- 允许只读在线歌词源 Spike；禁止记录歌词正文、Key、Authorization、完整请求/响应或私人路径。
- 禁止修改 Whisper、媒体、编辑、样式、归档和导出架构；禁止启动 V3-CLEAN-001；禁止 push。
- 旧 `V3-AI-001-AI15-DEVICE` 已被本次需求替代，不再恢复。

## 3. 冻结验收矩阵

| ID | 必须证明 | 当前状态 |
|---|---|---|
| ACC01 | AI 从 Whisper 全批生成有界歌名/歌手候选 | `COMPONENT_VERIFIED` |
| ACC02 | `SongLyricsSearchTool` 返回完整英文歌词及来源 ID | `COMPONENT_VERIFIED / ONLINE_SOURCE_VERIFIED` |
| ACC03 | 多 cue 对齐验证接受正确歌词并拒绝错误歌曲 | `COMPONENT_VERIFIED / SPAN_ALIGNMENT` |
| ACC04 | 第二步 Prompt 使用完整歌词生成上下文一致中文，禁止逐 cue 独立直译 | `COMPONENT_VERIFIED` |
| ACC05 | 重复副歌译文一致；跨行语义使用整首上下文 | `DEVICE_VERIFIED / SINGLE_SONG_QUALITY_SAMPLE` |
| ACC06 | 结果保留 cue ID、顺序、时间戳并整批原子提交 | `COMPONENT_VERIFIED` |
| ACC07 | 检索失败不声明歌曲确认；仅允许标记未确认的整批上下文生成 | `COMPONENT_VERIFIED` |
| ACC08 | BYOK、安全错误、取消和本地失败回退不回归 | `COMPONENT_VERIFIED` |
| ACC09 | focused/full JVM、ASR、lint、普通/native Debug、AndroidTest 构建通过 | `COMPONENT_VERIFIED` |
| ACC10 | 生成固定测试 SRT，在 ARM64 真机直接通过生产增强链路验证歌曲检索、整首中英结果、cue 时间和输出 SRT | `DEVICE_VERIFIED / ALIGNED_AND_MISALIGNED_SAMPLES` |

## 4. 工作单元

| TASK_ID | 目标 | 所有权 | 依赖 |
|---|---|---|---|
| `V3-AI-001-SEARCH-SPIKE` | 验证歌词源与对齐阈值 | 只读网络；测试建议 | 无 |
| `V3-AI-001-SEARCH-CONTRACT` | SearchTool、候选、完整歌词合同与测试 | enhancement 新合同/测试 | Spike |
| `V3-AI-001-SEARCH-PROVIDER` | 两步 DeepSeek 编排、检索和整首上下文请求 | Provider/JSON/测试 | Contract |
| `V3-AI-001-SEARCH-INTEGRATION` | 工厂接线、回归和完整构建 | 必要接线/验证 | 前两项 |

## 5. Agent 账本

| agent_id | role | task_unit | status | owned files/components | baseline | output/evidence | last update |
|---|---|---|---|---|---|---|---|
| `/root/lyrics_search_spike` | `limbs` | `V3-AI-001-LYRICS-ACCURACY-IMPL` | `COMPLETE` | enhancement SearchTool、Provider、候选验证、测试与构建 | `421dc9c` | focused 60/60；full JVM 285/285；构建通过 | 2026-08-12 |
| `/root/lyrics_search_spike` | `limbs` | `V3-AI-001-SRT-DEVICE-TEST` | `COMPLETE` | 固定 SRT、生产增强、输出 SRT 与真机证据 | `421dc9c` | ARM64/API36：CLOUD_AI、CONFIRMED、8/8、时间保持、SRT 可回读 | 2026-08-12 |
| `/root/reviewer_lyrics_accuracy` | `reviewer` | `V3-AI-001.005` | `REJECTED` | 当前阶段集成差异与 ACC01–ACC10 | `421dc9c` | ACC03 存在边界错位拒真和部分匹配误确认 | 2026-08-12 |
| `/root/lyrics_search_spike` | `limbs` | `V3-AI-001-ALIGNMENT-REWORK` | `COMPLETE` | span 对齐、负证据、回归与错位 SRT 真机复测 | `421dc9c` | 66/66；full 291/291；错位 SRT 真机 VERIFIED_LYRICS | 2026-08-12 |
| `/root/reviewer_lyrics_accuracy` | `reviewer` | `V3-AI-001.007` | `ACCEPTED` | 复审 ACC03 修复和错位 SRT 证据 | `421dc9c` | ACC01–ACC10 通过；两项 P1 关闭；单歌曲样本为证据上限 | 2026-08-12 |

## 6. Reviewer 门禁与下一动作

Reviewer 已复审 span 对齐、未匹配负证据、66/66 专项测试、291/291 全量 JVM 和两份真机 SRT 证据，裁决 `ACCEPTED`。本阶段按冻结矩阵关闭，证据上限仍是单歌曲的对齐与错位字幕样本，不代表视频、Whisper 或完整端到端流程已验证。

下一动作：保持 `V3-AI-001` 关闭；如继续开发，先为 `V3-CLEAN-001` 建立新的冻结验收矩阵。
