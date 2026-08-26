# SRT 字幕输出待修改问题清单

## 文档状态

- `GAP_REV: 2026-08-26.001`
- 状态:`ANALYSIS_ONLY`(本文只列问题与修改方向,不修改任何代码)
- 输入证据:2026-08-26 工作树源码 + 桌面 RAW 存档(`LyricCaptioner-Whisper-RAW-2026-08-26/`)
- 证据等级:`代码事实` = 当前源码可验证;`RAW 证据` = 桌面存档;`外部事实` = 需联网核验。

## 问题总览

| ID | 问题 | 严重度 | 层 |
|---|---|---|---|
| GAP-1 | SRT 导出能力已实现但产品 UI 未接线 | 高(功能缺失) | UI |
| GAP-2 | 时间轴按父 cue 粒度输出,合句 cue 时间窗过长,字幕与歌词行错位 | 高(体验) | 数据/导出 |
| GAP-3 | 合句 cue 双语 SRT 块 4 行堆叠,且 SRT 再导入往返失真 | 中 | 导出/导入 |
| GAP-4 | cue 不可拆分合同与行级显示需求的冲突 | 中(设计决策) | 合同 |
| GAP-5 | LRCLIB syncedLyrics(带时间戳歌词)未使用,行级对时数据源被丢弃 | 中(机会) | 检索 |
| GAP-6 | SRT 纯文本与 ASS/MP4 样式导出的一致性边界未在产品中说明 | 低 | 文档/UX |

## GAP-1:SRT 导出未接线(代码事实)

- 现状:`captions/SrtWriter.kt` 完整实现(排序、HH:MM:SS,mmm 时间戳、双语块输出),但全 main 源码无调用点;仅 `SrtWriterTest`(JVM)与 `LocalAiInstrumentation.kt:371`(测试产物)使用。
- 导出分区(`ui/EditorScreen.kt` 导出 WorkflowPanel)只有:导出视频、分享视频、保存项目、取消导出。没有任何 SRT 导出入口。
- 影响:用户无法从产品拿到可给其他播放器/剪辑软件使用的 `.srt` 文件;"导出 SRT"是双语字幕工具的基础预期功能。
- 修改方向:导出分区增加"导出 SRT"按钮(`ActivityResultContracts.CreateDocument("text/plain")`,模式同"保存项目"的 `projectCreator`);`MainViewModel` 增加 `exportSrt(uri)`(复用现有 `SrtWriter`,写 UTF-8);同时更新 `DerivedOutputPolicy` 失效逻辑。涉及文件:`ui/EditorScreen.kt`(或导出面板所在文件)、`MainViewModel.kt`。

## GAP-2:时间轴按父 cue 粒度输出(代码事实 + RAW 证据)

- 现状:`SrtWriter.writeBlock` 与 ASS `AssSubtitleWriter` 都直接使用 `cue.startMs/cue.endMs`;AI 响应合同强制每个 cue 的 `start_ms/end_ms` 与请求完全一致(validator 逐条比对),AI 无法给出句级时间。
- `RAW 证据`:
  - TEST_CASE_1 `whisper-0-0`:0–7360ms(7.36s)内包含两句歌词;
  - TEST_CASE_2 `whisper-0-0`:0–17840ms(**17.84s**)内包含两句歌词。
- 影响:两句歌词共享整个父 cue 时间窗——第一句唱完很久后字幕仍停留,或第二句提前出现;音乐 MV 场景下双语字幕与歌声严重错位。cue 越长错位越明显。
- 修改方向(三选一或组合,均属未来任务):
  1. **合同扩展(推荐)**:AI 响应 cue 增加可选 `sub_lines` 字段(`[{english, chinese, offset_ratio}]`,行级文本+父窗内相对位置),父 cue 合同不变、validator 增加可选校验;导出时按比例折算行级时间轴。
  2. **本地等分**:导出前对多行 cue 按行数等分父窗(零 AI 依赖,精度低,仅缓解)。
  3. **syncedLyrics 对时**:见 GAP-5,verified 歌词带 LRC 时间戳时直接行级对时,精度最高。
- 涉及文件:`CaptionEnhancementContract.kt`、`DeepSeekCaptionEnhancementProvider.kt`(prompt)、`CaptionEnhancementResponseValidator.kt`、`SrtWriter.kt`、`FfmpegKitSubtitleExporter.kt`(ASS 同步受益)。

## GAP-3:合句双语块堆叠与往返失真(代码事实)

- 现状:`SrtWriter.writeBlock` 用 `english + "\n" + chinese` 拼 SRT 块;`SrtParser.parseBlock` 的往返规则是"首行归英文、其余行全部归中文"(`english = textLines.firstOrNull()`,`chinese = textLines.drop(1).joinToString("\n")`)。
- 对合句 cue(AI2 优化后 `corrected_english`/`chinese` 各含两行,见 `AI2_ENHANCEMENT_DIAGNOSIS.md` §4.2),导出的 SRT 块是 4 行:
  ```text
  1
  00:00:00,000 --> 00:00:17,840
  Sadie of stars…(第一句英文)
  City of stars…(第二句英文)
  星光之城…(第一句中文)
  星光之城…(第二句中文)
  ```
  该块再经 `importSrt` 导入后:英文只剩第一句,第二句英文混入 `chinese` 字段首行——**往返失真**。
- 影响:4 行堆叠超出常规字幕行数(通常 ≤2 行)易被播放器截断;往返失真直接破坏数据。
- 修改方向:`SrtParser.parseBlock` 增加启发式双语切分(如按行首是否含 CJK 字符分组,英文行归 english、CJK 行归 chinese);`SrtWriter` 保持现格式不动即可兼容。涉及文件:`captions/SrtParser.kt`。
- 注:当前 prompt 尚未输出两行英文(优化前 `corrected_english` 为单行拼接),该失真在 prompt 优化落地后会实际暴露,应与 `AI2_ENHANCEMENT_DIAGNOSIS.md` §4.2 同批处理。

## GAP-4:cue 不可拆分合同 vs 行级显示(代码事实,设计决策点)

- 现状:整批原子性是既有正确设计(`REQUIREMENTS.md` V3-AI-001 硬约束:"保留原 cue ID、顺序和时间戳;整批成功后才提交"),validator 严格执行。
- 冲突:用户期望"每行歌词一条字幕",合同要求"cue 数量/时间戳原样往返"。两者在合句输入下不可兼得,除非引入"子行"层级。
- 修改方向:不破坏现有合同(拆分父 cue 会波及编辑器、项目存档、导出全链路,风险大);采用 GAP-2 的方案 1(可选 `sub_lines` 字段)在不触碰父 cue 合同的前提下表达行级信息。该决策需单独立项评审,本文不预定结论。

## GAP-5:LRCLIB syncedLyrics 未使用(代码事实 + 外部事实)

- `代码事实`:`LrclibSongLyricsSearchTool.parseCandidates` 只读取 `plainLyrics`(`item.optionalString("plainLyrics")`),`syncedLyrics` 字段被忽略;`SongLyricsCandidate.completeEnglishLyrics` 也只承载纯文本。
- `外部事实`:LRCLIB `/api/search` 条目通常同时含 `plainLyrics` 与 `syncedLyrics`(LRC 格式 `[mm:ss.xx] 歌词行`),后者即行级时间戳。接入前需按项目惯例做一次 Spike 核验覆盖率与格式稳定性。
- 影响:行级对时(解决 GAP-2)的现成高精度数据源在检索阶段即被丢弃;verified 路径上即使歌曲确认,也只能拿无时间歌词。
- 修改方向:`SongLyricsCandidate` 增加 `syncedLyrics` 字段(可空),检索解析透传;verified 场景若存在 syncedLyrics,导出时用它做行级对时(与 GAP-2 方案 3 合并实现)。涉及文件:`SongLyricsSearchTool.kt`、`SongLyricsCandidateVerifier.kt`(透传)、`SrtWriter.kt`/ASS 导出。

## GAP-6:SRT 与 ASS/MP4 导出一致性边界(说明性)

- SRT 是纯文本时间轴格式,无颜色/字体/位置能力;ASS(烧录进 MP4)承载完整样式(双语分行、中文换色 `\c`、逐 cue 样式)。两者内容同源(`CaptionRenderResolver` 同一解析边界),但呈现能力本质不同,这不是缺陷。
- 建议(低优先级):产品内对"导出视频 vs 导出 SRT"的样式差异给出一句说明,避免用户误以为 SRT 丢失样式是 bug。随 GAP-1 接线时一并处理。

## 执行边界

- 以上全部修改均属未来新任务;当前 `V4-SIMP-001` 阶段冻结矩阵禁止触碰 AI Prompt、响应合同与受保护工作树,本文不执行任何代码变更。
- GAP-2/3/4/5 与 `AI2_ENHANCEMENT_DIAGNOSIS.md` 的 Prompt 优化方案存在耦合(合句输出格式、sub_lines 合同扩展),立项时应作为一个特性包统一评审,避免合同两次破坏性变更。

## 相关文档

- 架构现状:`V4_PRODUCT_ARCHITECTURE.md`
- AI 增强根因与 Prompt 优化:`AI2_ENHANCEMENT_DIAGNOSIS.md`
- 原始需求边界:`REQUIREMENTS.md`
