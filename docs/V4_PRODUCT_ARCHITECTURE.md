# LyricCaptioner V4 产品与架构现状

## 文档状态

- `ARCH_REV: 2026-08-26.001`
- 状态:`ACTIVE_REFERENCE`
- 日期:2026-08-26
- 目的:记录 V4 当前真实产品架构与模块地图,取代 `V3_PRODUCT_ARCHITECTURE.md` 作为当前架构参考。V3 文档原地保留为历史证据,不修改、不删除。
- 本文只描述"代码现在是什么",不新增计划;阶段调度仍以 `CURRENT_TASK.md` 与 `PROJECT_STATE.md` 为准。
- 所有事实均以 2026-08-26 工作树源码为准;不确定或外部来源的内容单独标注证据等级。

## V4 唯一产品主链路

```text
相册导入视频(PickVisualMedia VideoOnly,≤5 分钟,持久化读权限)
→ 一次点击“开始识别”(generateCompleteCaptions)
→ 本地 Whisper 识别(small 模型闭包,native whisper_full)
→ 自动执行 AI 增强(DeepSeek BYOK 两阶段,失败回退本地 OPUS-MT)
→ 自动进入字幕编辑(READY_FOR_EDIT 自动切换编辑分区)
→ 添加或编辑字幕(直接编辑/列表编辑/画面内直接调整)
→ 预览(Media3 PlayerView + Compose 字幕覆盖层)并导出(FFmpegKit ASS 烧录到 MediaStore)
```

编排入口:`MainViewModel.generateCompleteCaptions()` → `CompleteCaptionWorkflowRunner.run()`(`processing/CompleteCaptionWorkflow.kt`)。前置检查在 `CompleteCaptionWorkflowPreflight.blockingMessage()`:视频已导入、本地 Whisper 就绪、DeepSeek Key 已配置且验证。工作流严格串行:识别完成并提交 RAW 批次后才进入增强;增强结果整批原子替换,不提交部分 AI 输出。

## 模块地图(2026-08-26 工作树实测)

### `ui/`(V4-SIMP-001 拆分后)

| 文件 | 职责 |
|---|---|
| `EditorScreen.kt` | 主入口;分区导航(导入/生成/编辑/导出)、launcher 编排 |
| `VideoPreviewPlayer.kt` | 视频预览卡片、播放控制行 `PlayerControlRow`、全屏 Dialog 预览 |
| `SubtitlePreviewOverlay.kt` | 画面内字幕覆盖层;直接编辑模式(选点/拖动/缩放) |
| `DirectCaptionEditPanel.kt` | 编辑分区下的选中 cue 直编面板 |
| `CaptionListPanel.kt` | 字幕列表(文本编辑、时间微调、确认、删除) |
| `CaptionStyleControls.kt` | 逐 cue 样式控件(颜色/字体/对齐/位置等) |
| `DeepSeekKeySettingsPanel.kt` | AI 服务配置(BYOK Key 输入/验证/删除) |
| `WorkbenchPanels.kt` | 工作台面板与通用 Action 组件 |
| `EditorSupport.kt`、`EditorUiPolicy.kt`、`ProductUiContract.kt`、`Theme.kt` | 编辑器辅助、UI 策略、产品 UI 契约、主题 |

### `processing/`(识别、增强、导出)

| 文件 | 职责 |
|---|---|
| `AsrModule.kt`、`WhisperAsrModule` | ASR 门面;输出校验 `AsrCaptionValidator`(id/时间轴/confidence∈[0,1]) |
| `WhisperLocalSpeechRecognizer.kt` | native 段 → `CaptionCue` 转换;`WhisperSegment.confidence` 写入 cue |
| `WhisperProcessSession.kt`、`WhisperSessionRuntime.kt`、`WhisperNativeSessionBridge.kt`、`WhisperSessionContract.kt` | whisper.cpp session 缓存与 JNI 桥 |
| `WhisperModelStore.kt`、`WhisperModelCatalog.kt`、`WhisperModelImporter.kt`、`WhisperModelValidator.kt` | 模型资产路由(当前默认 small)、导入与校验 |
| `AndroidAudioExtractor.kt` | MediaExtractor 音轨抽取 |
| `AppPipelineFactory.kt` | ASR/翻译/导出管线组装 |
| `CompleteCaptionWorkflow.kt` | 识别→增强串行编排(见上) |
| `CaptionRenderResolver.kt`、`CaptionPaintPlan.kt` | 预览与导出共用的字幕渲染解析 |
| `ExportEngine.kt`、`FfmpegKitSubtitleExporter.kt` | FFmpegKit 导出(内部含 `AssSubtitleWriter`) |
| `MediaStoreExportGateway.kt`、`ExportDestinationPolicy.kt`、`ExportLifecycle.kt` | MediaStore 目的地、安全边界与生命周期 |
| `TranslationModule.kt`、`OnnxLocalTranslator.kt`、`LocalTranslationModelCatalog.kt`、`LocalTranslationModelStore.kt`、`SentencePieceTokenizer.kt` | 本地 OPUS-MT 翻译(AI 失败回退路径) |

### `processing/enhancement/`(AI 增强,唯一云端链路)

| 文件 | 职责 |
|---|---|
| `CaptionEnhancementContract.kt` | 中立线合同:请求/响应/状态/错误/SongMatch 数据类型 |
| `CaptionEnhancementCoordinator.kt` | 一次完整增强编排:mapper → provider → validator;失败白名单回退本地翻译 |
| `CaptionEnhancementRequestMapper.kt` | 本地 cue 批 → 请求(丢弃 confidence;见 AI2 诊断文档) |
| `CaptionEnhancementResponseValidator.kt` | 全批校验:id/顺序/时间戳逐条比对,任何不一致整批拒绝 |
| `DeepSeekCaptionEnhancementProvider.kt` | 两阶段 DeepSeek 适配器;3 个 system prompt 与请求 JSON 构造 |
| `SongLyricsSearchTool.kt` | LRCLIB `/api/search` 客户端(仅 track_name + artist_name 检索) |
| `SongLyricsCandidateVerifier.kt` | 单调 DP 对齐验证:cue 批 ↔ 完整歌词 |
| `StrictJson.kt` | 严格 JSON 解析(防注入/截断) |
| `byok/` | DeepSeek Key 的 Android Keystore 存取、BYOK 管理与连通性探测 |

### 其余模块

- `audio/`:`LinearPcm16Resampler`、`Pcm16ChannelMixer`、`Pcm16ToMono16kProcessor`、`Pcm16WavWriter` — 16 kHz 单声道 WAV 预处理链。
- `captions/`:`CaptionTimeline`(时间轴查询)、`CaptionTimingEditor`、`LyricLineAligner`(歌词文本对齐建议)、`SrtParser`(导入)、`SrtWriter`(仅测试/instrumentation 使用,产品 UI 无 SRT 导出入口;详见 SRT 缺口文档)。
- `model/`:`CaptionCue`(含 `confidence`、`correctionCandidates`、样式/布局 override)、`EditorState`、各类编辑与导入策略、`ProjectSnapshot`。
- `project/`:`AndroidProjectRepository`、`ProjectArchive`(`.lcp` 项目存档读写)。

## AI 增强两阶段架构(当前实现)

唯一入口 `DeepSeekCaptionEnhancementProvider.enhance()`(`deepseek-v4-pro`,`PROCESSING_VERSION = deepseek-v4-pro-lyrics-search-context.v3`,temperature=0,thinking disabled,json_object):

```text
阶段 1:歌曲识别(CANDIDATE_REQUEST / CANDIDATE_PARSE)
  条件:cues.size >= MIN_ELIGIBLE_CUES(= 3)
  IDENTIFICATION_SYSTEM_PROMPT + 整批 raw_english
  → 最多 MAX_SONG_CANDIDATES(= 2)个 {title, artist} 候选

阶段 2a:检索(LYRICS_SEARCH)
  每个候选调用 LRCLIB GET /api/search?track_name=&artist_name=
  过滤 instrumental/空歌词/非空行 < 3 的结果;候选间延迟 250ms

阶段 2b:本地验证(LYRICS_VERIFY)
  SongLyricsCandidateVerifier.verify(cues, candidate):
  - DP 单调对齐:每个 cue 消费连续 token span(最多跨 2 行歌词)
  - 单 cue 相似度 >= 0.62 才可匹配;未匹配 cue 记 0 分负证据
  - 门槛:matched >= max(3, ceil(eligible*0.75)),
    coverage >= 0.75,average/median 相似度 >= 0.78,综合置信 >= 0.82
  → 最佳候选成为 VerifiedSongLyrics(含 cue→canonical 英文映射)

阶段 3:整首上下文生成(WHOLE_SONG_REQUEST / WHOLE_SONG_PARSE)
  verified != null → VERIFIED_LYRICS_SYSTEM_PROMPT
    + complete_english_lyrics + cue_canonical_alignments(整首歌词纠错+翻译)
  verified == null → UNCONFIRMED_SYSTEM_PROMPT
    + unconfirmed_candidate(若有)(保守纠错+翻译,禁止声称歌曲已确认)
  max_tokens = cues.size * 192,范围 [768, 16384]

阶段 4:本地整批校验与应用(CLOUD_VALIDATING → CLOUD_APPLIED)
  CaptionEnhancementResponseValidator:
  - schema/job_id/processing_version/cue 数量与顺序逐条比对
  - 每个 cue 的 id/start_ms/end_ms 必须与请求完全一致
  - corrected_english 与 chinese 必须非空且良构
  通过 → 原子替换原批 cue 的 english/chinese;任何失败 → 整批拒绝
```

### 失败与回退边界

- 回退白名单(在 Coordinator 内冻结,不信任 provider 的 `recoverable`):`OFFLINE / CONNECTION / TIMEOUT / RETRYABLE_SERVER / INVALID_RESPONSE` → 本地 OPUS-MT `translateMissingChinese`(只用原始 Whisper 英文,绝不用部分 AI 结果)。
- `AUTHENTICATION / UNKNOWN / LOCAL_TRANSLATION` 不回退,直接上抛固定安全文案。
- BYOK:Key 仅存 Android Keystore(`AndroidKeystoreDeepSeekKeyStore`),请求时解密;异常与日志脱敏(Authorization/Bearer/sk- 密钥正则替换)。

### SongMatch 状态机

- `CONFIRMED`:必须带 title/artist/source 且 confidence ≥ 0.80(validator 强制)。
- `UNCONFIRMED`:有候选但未通过歌词验证;title/artist 必须成对。
- `NOT_FOUND`:无候选且检索无结果;不得携带歌曲元数据。

## 数据与不变量合同

- cue `id`、顺序、`start_ms`、`end_ms` 在 AI 链路中原样往返;禁止增加、删除、拆分、合并、重排 cue 或修改时间(由 validator 与合同测试双重守护)。
- AI 响应只能填充 `corrected_english` 与 `chinese` 两个文本字段;整批成功才提交(CaptionBatchCommitPolicy)。
- `CaptionCue.confidence` 由 Whisper 每段置信度填充,用于编辑器 `needsReview`(< 0.82 标记待复核);当前 AI 请求合同不包含该字段(见 `AI2_ENHANCEMENT_DIAGNOSIS.md`)。
- 导出与预览共用 `CaptionRenderResolver`,保证 Compose 预览与 ASS 烧录的坐标/样式语义一致(ASS PlayRes 固定 1920×1080)。

## 与 V3 方案的关键差异(文档 vs 现实)

| 主题 | `V3_PRODUCT_ARCHITECTURE.md` 方案(2026-08-02) | V4 实际实现 |
|---|---|---|
| 云端增强形态 | App → 自有后端 → 后端整批处理 | DeepSeek API BYOK 直连,无自有后端 |
| 翻译主线 | 中文翻译以本地 OPUS-MT 为主线,云端为决策后目标 | OPUS-MT 降级为 AI 失败回退;DeepSeek 双语生成为主线 |
| 歌词来源 | 未定义检索与验证 | LRCLIB 检索 + 本地 DP 对齐验证 + SongMatch 状态机 |
| 两阶段边界 | 可选第二轮审校低置信片段 | 两阶段=歌曲识别→整批生成;无按片段二轮审校 |
| UI/编辑 | V3 交互重构目标 | V4 已实现一键链路、画面内直接编辑、分区工作台与全屏预览 |

## 已知边界与证据缺口

- 真机 E2E(`V4-E2E-001`)尚未获得设备授权,真实 AI、真实导出、回放证据未取得;本文架构描述达到 `COMPONENT_VERIFIED` 证据等级。
- ASR 侧 small/base 与 runtime 差异的既有诊断见 `docs/debug/ASR_SMALL_BASE_VERSION_COMPARISON.md`（V4.4 已移出仓库，可从 git 历史找回）;本文不重复结论。
- AI 增强效果问题与 Prompt 优化分析见 `AI2_ENHANCEMENT_DIAGNOSIS.md`;SRT 输出缺口见 `SRT_OUTPUT_GAPS.md`。
