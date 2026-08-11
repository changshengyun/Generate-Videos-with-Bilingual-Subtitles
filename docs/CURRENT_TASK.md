# Current Task: V3-MEDIA-001

## Current status

- Stage: `V3-MEDIA-001`
- Status: `V3-MEDIA-001 / MATRIX_DEFINED / IMPLEMENTATION_AUTHORIZED`
- Previous stage: `V3-EDITOR-002 / PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`（用户接受 Developer 回交并直接关闭；不做二次验收）
- Scope: 将产品视频入口统一为 AndroidX `PickVisualMedia(VideoOnly)`，将成品视频直接、原子地写入 `MediaStore.Video` 的 `Movies/LyricCaptioner`；保持项目/模型/支持文件的既有 SAF 能力和源媒体安全
- V2 functional baseline: `8a48d88`
- Documentation baseline: `3117eb1`
- Implementation authorization: `V3_MEDIA_001_DISPATCHED / DIRECT_STAGE_COMPLETION`
- Physical-device gate: `WAIVED_BY_USER_FOR_CURRENT_DEVELOPMENT / EVIDENCE_NOT_MEASURED`；保留已有失败/缺失记录，但不再阻断当前开发，也不得伪造 PASS
- AI audit: `V3-AI-001 / NOT_IMPLEMENTED / PRODUCTION_PROMPT_ABSENT / SEPARATE_STAGE_REQUIRED`；现有 DeepSeek 仅覆盖 BYOK 与 `GET /models` 认证，Developer 不得自拟 production Prompt
- Process rule: 一个阶段只冻结一份完整矩阵；普通缺陷在 `V3-MEDIA-001` 内直接修到矩阵通过，不创建 R1/R2/R3/R4，不等待 Brain 二次验收
- Next action: 直接实施并完成下方 `V3-MEDIA-001`；不得启动 `V3-AI-001`、UI 重设计、清理阶段或真实设备验证

## V3-MEDIA-001 acceptance matrix

| ID | 必须证明 |
|---|---|
| M01 | 产品“导入视频/重新绑定视频”唯一使用 AndroidX `ActivityResultContracts.PickVisualMedia` 与 `PickVisualMediaRequest(VideoOnly)`；移除产品视频入口对 `OpenDocument` 的直接调用。AndroidX 在系统 Photo Picker 不可用时自行回退 `ACTION_OPEN_DOCUMENT` 属于同一 contract，不得另做第二个产品入口。 |
| M02 | 用户取消选择返回 `null` 时，当前项目、视频 URI、字幕、样式、导出 URI、工作状态和持久化权限均不改变，也不启动校验/识别任务。 |
| M03 | 选中后继续验证 URI 可读、非空、存在视频轨、时长合法且不超过 5 分钟；拒绝非视频、空内容、不可读、时长未知/越界媒体。不得修改源文件、请求写权限、解析 raw filesystem path 或把完整源视频复制进长期私有存储。 |
| M04 | 对 Photo Picker URI 尝试保留只读持久访问；成功、provider 不支持、仅会话可读和不可用状态必须明确区分。项目保存/重启恢复继续验证媒体访问；失效 URI 仍通过同一个 Photo Picker 入口重新绑定，并保留字幕、时间轴、样式和项目状态。 |
| M05 | `NEW_VIDEO` 与 `RELINK` 继续遵守 `VideoImportPolicy`：新视频按既有规则失效不兼容派生输出；重新绑定只替换媒体关联，不破坏已有字幕和编辑状态。 |
| M06 | 产品“导出视频”不再启动 `CreateDocument("video/mp4")`，点击后直接创建相册导出任务；项目归档、Whisper 模型及尚未进入清理阶段的支持文件 SAF 入口不在本阶段删除或改写。 |
| M07 | API 29+ 通过 `MediaStore.Video.Media` 创建唯一、规范化 `.mp4` 名称，`MIME_TYPE=video/mp4`、`RELATIVE_PATH=Movies/LyricCaptioner`、`IS_PENDING=1`；不得构造或记录 raw filesystem path。只有完整写入和输出校验成功后才将 `IS_PENDING` 更新为 0。 |
| M08 | minSdk 26–28 使用受限的 MediaStore legacy 写入策略；如确需存储权限，只允许 `WRITE_EXTERNAL_STORAGE` 且 `maxSdkVersion=28`，仅在 API 26–28 运行时请求。API 29+ 不得请求存储权限；整个应用不得新增 `READ_MEDIA_VIDEO`、`READ_EXTERNAL_STORAGE`、MANAGE_EXTERNAL_STORAGE 或全盘访问。权限拒绝必须可见，且不得创建目标行或启动 FFmpeg。 |
| M09 | 导出目标必须以显式 task-owned destination/session 建模，不能继续只根据 URI 查询推断所有权。源 URI 与目标 URI/底层文件不得相同；既有 source-safety 和输出完整性校验必须保留。 |
| M10 | 成功路径必须完成：创建 task-owned row → FFmpeg 生成并校验临时成品 → 可取消复制到目标 URI → 校验写入字节 → publish → 更新 `exportUri`。分享入口继续使用最终 content URI，并授予临时只读权限。 |
| M11 | insert、FFmpeg、临时输出校验、目标复制、字节校验或 publish 任一步失败，都只删除本任务创建的未发布 row；不得删除源文件、既有相册媒体或用户其他文档，不得留下 0-byte/partial/pending 可见成品。 |
| M12 | 取消与完成竞态使用幂等终态：取消 FFmpeg/copy，回滚未发布 row，状态为 cancelled；取消后不得 publish，publish 成功后不得被迟到的取消删除。重复 rollback/commit 不得崩溃或越权删除。 |
| M13 | 同一时间最多一个导出任务；重复点击、重新导入/绑定媒体、字幕变化和项目切换必须按既有失效/串行规则处理，旧任务不得覆盖新项目状态。 |
| M14 | UI 明确区分选择取消、权限拒绝、导入失败、导出中、取消、失败和“已保存到系统相册”；不得向界面、日志、异常、测试输出或 debug snapshot 暴露 raw path、完整私人 content URI 或媒体正文。内部 `exportUri` 仅用于播放/分享。 |
| M15 | focused tests 使用 fake picker result、fake permission policy、fake MediaStore gateway 和 fake export engine，覆盖 API 26/28/29/36、cancel/null、persist grant、insert/publish/delete、所有失败点、重复终态、权限矩阵、同源拒绝、重绑保持和状态映射；禁止真实私人媒体、真实相册写入和网络。 |
| M16 | 相关 focused tests、完整 JVM、ASR Python、lint、普通 Debug、native Debug、AndroidTest 构建全部通过；报告测试数与当前 APK 实物，执行源码/测试输出/APK secret scan。物理设备媒体流继续按用户授权豁免，不伪造相册运行时证据。 |

| Category | V3-MEDIA-001 requirement |
|---|---|
| Main path | Photo Picker 选择单个视频 → 校验并保留只读访问 → 既有识别/编辑链保持 → 点击导出直接写入系统相册 `Movies/LyricCaptioner` → 成功后可分享最终 content URI。 |
| Mandatory evidence | M01–M16；picker contract、持久访问/重绑、MediaStore task-owned session、API 26–36 权限策略、pending publish/rollback、取消竞态、完整回归、产物和 secret scan。 |
| Prohibited | 不新增自定义视频文件选择/保存位置；不修改项目/模型 SAF；不删除 SRT/歌词分支；不修改 Whisper、DeepSeek、BYOK、Prompt、字幕时间轴/样式/渲染或无关 UI；不执行真实相册/真机验证；不清理既有脏状态；不 push。 |
| Completion | M01–M16 全部通过后直接记录 `PARTIAL_PASS / MEDIA_COMPONENT_VERIFIED / PHYSICAL_DEVICE_MEDIA_FLOW_WAIVED_BY_USER`，同步三份活动文档并结束阶段；不回交 Brain 做二次验收。 |
| Incomplete | 普通代码/测试/构建缺陷继续留在本阶段修复，不新建 R 编号。只有需要新增产品权限、破坏旧项目/源媒体或扩大范围时才返回 `HUMAN_DECISION`。 |

## V3-MEDIA-001 内部 Agent 并行计划

| Role | 独占范围 | 交付物与边界 |
|---|---|---|
| 内部 Agent A：picker/import contract | 新增 picker/import policy、URI access 纯模型及专属 tests；不得修改 `EditorScreen.kt`、`MainViewModel.kt`、exporter、Manifest 或三份活动文档 | 冻结 `PickVisualMedia(VideoOnly)` 请求、null cancel、persist/session-only/unavailable、NEW_VIDEO/RELINK 和恢复重绑契约 |
| 内部 Agent B：MediaStore destination | 新增 MediaStore gateway、task-owned export session、API/permission policy 及专属 tests；不得修改 `EditorScreen.kt`、`MainViewModel.kt`、FFmpeg exporter 或三份活动文档 | 实现 insert/pending/publish/rollback、API 26–28 legacy 策略、唯一命名和幂等终态；平台调用置于可替换 gateway 后 |
| 内部 Agent C：failure/race proof | 新增 export lifecycle、permission、state mapping 与集成测试文件；第一波只写 RED tests，不修改生产 UI/ViewModel/exporter 或三份活动文档 | 覆盖每个失败点、取消/完成竞态、重复终态、单任务串行、同源保护、重绑保持和隐私断言 |
| 主 Agent（协调与集成） | `EditorScreen.kt`、`MainViewModel.kt`、`FfmpegKitSubtitleExporter.kt`、必要的 pipeline/interface、Manifest/Gradle、三份活动文档、完整回归、secret scan 和 Git | 先提交文档 checkpoint；第一波并行内部 Agent A/B/C，接口冻结后串行接入共享热点；内部 Agent 不提交、不 push、不修改三份活动文档 |

- 不便并行的 UI launcher、ViewModel export Job、exporter ownership 接线和 Manifest 权限由主 Agent 串行完成；不得为了并行让多个内部 Agent 同时修改共享热点。

## V3-EDITOR-002 / R4 implementation evidence (2026-08-11)

- Historical R4 implementation evidence retained below; the user accepted this handoff and closed `V3-EDITOR-002` without secondary Brain acceptance.
- R4-01/R4-02/R4-03: default, selected-cue and cueId font-size writes now calculate and persist canonical `fontSizeRatio`, synchronizing the legacy `fontSizeSp` projection through one helper. Focused source-contract tests pass 3/3.
- R4-04/R4-07: v1-v5 import -> edit -> v6 save/reload, v6 cue edit/reload, 14/48 clamp and sibling isolation pass in `ProjectArchiveR4EditSaveReloadTest` (5/5); existing R1-R3 behavior remains in the full suite.
- R4-05: `CaptionGeometryResolver` matches Media3 1.10.1 Float FIT arithmetic, 1% tolerance, `toInt` truncation and trailing odd remainder; 3:5 in 1080x1920 is 1799px, with square/PAR focused geometry 15/15.
- R4-06: production Compose builds `CaptionPaintPlan` from the shared render spec and paints real `Stroke` then `Fill` with identical text/font/size/position/style. `CaptionPaintPlanTest` passes 4/4; ASS continues to consume the shared spec with equivalent Outline and `Shadow=0`.
- R4-08: full JVM 241/241 (0 failures, 0 skipped), ASR Python 6/6, lint 0 errors/33 warnings, ordinary Debug, native-enabled Debug and AndroidTest builds all pass. APKs: `app/build/outputs/apk/debug/app-debug.apk` = `417,446,841` bytes (2026-08-11 16:06:41 +08:00); `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` = `119,048` bytes (2026-08-10 21:46:37 +08:00). Source/test/APK secret scan: 0 pattern hits across 12 files.
- Boundary: no real-device UI, DeepSeek, BYOK key, online lyrics, Provider, Prompt or media/Whisper work was performed. Final recorded editor state: `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`.

## Brain R3 adjudication (2026-08-11)

- Verdict: `PARTIAL_PASS / SOURCE_RELATIVE_RENDERING_REQUIRED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`.
- Accepted evidence: `CaptionRenderSpec`, source-height-relative render calculations, Compose stroke/fill production code, ASS `Outline` with `Shadow=0`, pixel-aspect-ratio propagation, the shared FIT architecture, v6 base migration, 222/222 JVM regression and reported build artifacts are retained as component/build evidence; exact Media3 float parity is not accepted.
- P1 default write path: `MainViewModel.updateFontSize()` changes only `fontSizeSp`. After a v6/default style has a non-default `fontSizeRatio`, `DefaultCaptionStyle.validated()` preserves the old ratio, so A+/A- can leave Compose and ASS at the previous size.
- P1 cue write path: `updateSelectedCueFontSize()` and `updateCueFontSize()` also change only `fontSizeSp` while retaining an existing `fontSizeRatio`. After v6 restore or migration, per-cue A+/A- can be ignored and `ProjectArchive` can persist the stale ratio again.
- P1 FIT parity: the shared resolver uses `Double + floor`, while the pinned Media3 1.10.1 `AspectRatioFrameLayout` performs `Float` arithmetic followed by integer truncation. A 3:5 source in a 1080x1920 container yields 1800 in the current resolver but 1799 in Media3, so R3-05's no-known-1px-drift requirement is not met.
- Evidence gap: R3-07 claimed focused coverage of true Compose stroke/fill, but the test tree contains no reference to `CaptionOutlinedText`, `DrawStyle.Stroke` or the stroke-before-fill production bridge. Resolver numeric tests alone do not prove the production paint path.
- Consequence: R3-01, R3-07 and the active edit-save-restart path are not fully proved. Build/test success cannot upgrade the stage to the Developer candidate returned for R3.

## V3-EDITOR-002 / R4 acceptance matrix

| ID | 必须证明 |
|---|---|
| R4-01 | 默认字号的唯一可写规范值是 `fontSizeRatio`。A+/A- 必须从当前已解析字号计算新 ratio，同时同步或重新派生兼容用 `fontSizeSp`；v6 恢复后的非默认字号也必须立即生效。 |
| R4-02 | `updateSelectedCueFontSize()` 与按 `cueId` 的 `updateCueFontSize()` 必须写入新的 canonical ratio，不能保留旧 ratio 覆盖新 `fontSizeSp`；目标 cue 生效，其他 cue 不变。 |
| R4-03 | 消除“两个字段都可独立写”的歧义：新增单一 helper/API 负责 ratio、legacy projection、clamp 与 rounding。生产编辑路径不得直接只写 `fontSizeSp`。 |
| R4-04 | 覆盖两条真实归档链：v1-v5 导入→编辑字号→保存 v6→重启恢复，以及 v6 恢复→编辑字号→再次保存/恢复。恢复后的 `CaptionRenderSpec`、Compose 输入与 ASS Fontsize 必须使用新值；描边 ratio 仍完整 round-trip。 |
| R4-05 | FIT resolver 必须逐步复现当前 Media3 1.10.1 的 `Float` 运算、1% aspect tolerance、`toInt` 截断与居中余数分配。加入 3:5→1080x1920 的 1799px 反例、容差边界、奇数余量及 square/anamorphic PAR 回归。 |
| R4-06 | 为生产 stroke-before-fill 桥建立 focused 证据：可提取纯 paint plan 供 JVM 测试，并由 Compose 生产代码直接消费；必须证明先 `Stroke` 后 `Fill`、同一文本/字体/字号/位置，ASS 继续消费同一 spec 的 `Outline` 且 `Shadow=0`。不得用仅检查 resolver 数值的测试替代。 |
| R4-07 | focused tests 覆盖默认与 cue 的连续 A+/A-、14/48 边界、旧档迁移后编辑、v6 重启后编辑、cue 隔离、save/reload、Compose/ASS spec 一致性、普通/全屏、16:9/9:16/1:1、黑边、PAR 与完整九宫格组合；R1-R3 已通过能力不得回归。 |
| R4-08 | focused/full JVM、ASR Python、lint、普通 Debug、native Debug、AndroidTest 构建全部通过；报告测试数与当前 APK 实物，并执行本阶段源码/测试输出/APK secret scan。不得执行真机 UI。 |

| Category | V3-EDITOR-002 / R4 requirement |
|---|---|
| Main path | 恢复旧档或 v6 项目 → 在默认或某一 cue 卡片点击字号增减 → 预览与 ASS 同步变化 → 保存并重启 → 新字号和描边继续按 canonical ratio 恢复。 |
| Mandatory evidence | R4-01–R4-08；默认/cue canonical 写路径；两代归档 edit-save-reload；Media3 Float/FIT 边界；production-adjacent stroke/fill focused evidence；完整回归、产物和 secret scan。 |
| Prohibited | 不重做已通过的几何架构；不新增第二套字号真相；不启动 DeepSeek Provider/Prompt、媒体入口、Whisper 或无关 UI；不执行真机验证；不清理既有脏状态；不 push。 |
| Exit | R4 全部通过后只允许回交 `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`，由 Brain 正式关闭编辑阶段。 |
| Incomplete | 任一默认/cue 编辑仍可能保存旧 ratio、FIT 仍与 Media3 有已知像素漂移，或 stroke/fill 生产证据仍缺失：保持 `PARTIAL_PASS / SOURCE_RELATIVE_RENDERING_REQUIRED`。迁移破坏或数据不可恢复：`BLOCKED / ARCHIVE_V6_MIGRATION_REQUIRED`。 |

## V3-EDITOR-002 / R4 内部 Agent 并行计划

| Role | 独占范围 | 交付物与边界 |
|---|---|---|
| 内部 Agent A：canonical style write API | `CaptionStyleModel.kt` 及专属 model tests；不得修改 `MainViewModel.kt`、Compose、ASS、archive 或三份活动文档 | 建立唯一 ratio 更新 helper，冻结 clamp/rounding/legacy projection 规则，覆盖默认与 override 的旧值/新值组合 |
| 内部 Agent B：paint-path evidence | 新增独立 paint-plan model/test，并在接口冻结后修改 `EditorScreen.kt`；不得修改 `MainViewModel.kt`、archive、ASS exporter 或三份活动文档 | 让生产 Compose 直接消费可测试的 stroke-then-fill plan；测试层顺序与共享文字/样式参数，不改产品布局 |
| 内部 Agent C：regression proof | 归档/MainViewModel 与 geometry 专属测试文件；第一波只写 RED tests，不修改生产 `MainViewModel.kt`、model、Compose 或三份活动文档 | 覆盖 v1-v5/v6 edit-save-reload、默认/cue A+/A-、边界、sibling isolation，以及 Media3 Float 3:5/容差/奇数余量/PAR 反例，先证明当前失败再等待主 Agent 接线 |
| 主 Agent（协调与集成） | `MainViewModel.kt`、`CaptionGeometryModel.kt`、共享接口集成、必要的 archive 生产修正、三份活动文档、完整回归、secret scan 与 scoped Git commits | 先创建文档 checkpoint；第一波并行内部 Agent A/B/C，接口冻结后由主 Agent 串行接入 MainViewModel 与 Media3 FIT 修正并合并冲突；最终逐项裁决 R4-01–R4-08 |

- 不便并行的 `MainViewModel.kt` 与共享 archive 接线由主 Agent 串行完成，不让多个内部 Agent 同时修改热点文件。

## Brain R2 adjudication (2026-08-11)

- Verdict: `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / RENDER_INTEGRATION_REQUIRED`。
- R2-01/R2-02/R2-03 的主要方形像素路径通过代码复核：普通/全屏与 ASS 均消费 `CaptionGeometryResolver`，FIT 主矩形、归一化 x/y/width、anchor 语义成立，未建模 Compose padding 已移除。
- R2-04 未通过（字号）：Compose 把 `fontSizeSp` 当设备 `sp`，受 density/fontScale 影响且不会随有效视频矩形缩放；ASS 把同一整数当 1080 PlayRes 字号。共享整数不等于相同源视频相对字号。
- R2-04 未通过（描边）：Compose 只绘制 offset/blur `Shadow`；ASS 使用 `BorderStyle=1 / Outline=2 / Shadow=1` 的真实字形描边。颜色相同不代表最终描边相同。
- R2-01/R2-05 未完整通过：Media3 `VideoSize.pixelWidthHeightRatio` 未进入共享模型，非方形像素视频会产生不同黑边；共享 FIT 使用 `roundToInt`，而 Media3 FIT 对调整尺寸使用整数截断，存在 1px 偏差。
- R2-06 未通过：focused tests 只验证纯 geometry 与 ASS 字符串，没有覆盖生产 Compose 的 density/fontScale 字号、真实描边、pixel aspect ratio 或 Media3 截断行为。
- R2-07 的 212/212 JVM、构建与 APK 证据和磁盘实物一致；R1 清除覆盖、cue 隔离、中文颜色作用域及 v5 迁移未发现回归。

## V3-EDITOR-002 / R3 implementation evidence (2026-08-11)

- R3-01/R3-02: `fontSizeRatio` and `outlineWidthRatio` are canonical source-height-relative values; v1-v5 archives migrate through the fixed 1080 projection and v6 round-trip preserves ratio fields. Compose converts the shared physical-pixel result back through density and fontScale, while normal/fullscreen use the same effective video rectangle.
- R3-03/R3-04/R3-06: `CaptionRenderSpec` is consumed by production Compose and ASS. It carries resolved geometry, font, bold/italic, bilingual colours, alignment, `fontSizePx` and `outlineWidthPx`; Compose draws stroke then fill and ASS emits equivalent Outline with `Shadow=0`.
- R3-05/R3-07: `SourceVideoSize.pixelWidthHeightRatio`, Media3 FIT tolerance, integer truncation and centering remainder are covered by geometry tests; render-spec tests cover 1080/720/4K scaling and cue isolation. Existing R1/R2 style, text, timeline, anchor and migration tests remain green.
- R3-08: focused/full JVM `222/222`, ASR Python `6/6`, `lintDebug` `0 errors / 33 warnings`, ordinary Debug, native-enabled Debug and AndroidTest builds passed. APKs: `app/build/outputs/apk/debug/app-debug.apk` = `417,446,841` bytes (2026-08-11 15:21:48 +08:00); `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` = `119,048` bytes (2026-08-10 21:46:37 +08:00).
- Boundary: no real-device UI, DeepSeek, Key, online lyrics, Provider or Prompt work. Physical UI evidence is waived by user; no formal product PASS is claimed. Developer candidate: `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`; Brain owns formal acceptance.

## V3-EDITOR-002 / R3 acceptance matrix

| ID | 必须证明 |
|---|---|
| R3-01 | 字号唯一规范值改为相对源视频高度的 `fontSizeRatio`；默认值和 cue override 都使用同一单位。归档升级为 v6，v1-v5 的 `fontSizeSp` 按固定 `LEGACY_PLAY_RES_Y=1080` 安全迁移并完整 round-trip。 |
| R3-02 | Compose 根据 `fontSizeRatio * effectiveVideoRect.height` 得到目标物理像素，再正确抵消 density/fontScale；普通与全屏的字号随有效视频画面同比缩放，系统字体缩放不得改变导出一致性。 |
| R3-03 | ASS 根据同一 `fontSizeRatio * PlayResY` 生成 Fontsize，并与 Compose 使用同一 rounding、clamp 和最小值规则；不得把同一整数分别解释为设备 sp 与 PlayRes px。 |
| R3-04 | 描边宽度使用共享的源视频相对规范值；Compose 必须绘制真实字形 stroke 再绘制 fill，ASS 使用等价 Outline 宽度。不得继续用模糊 Shadow 冒充描边；若保留阴影，必须作为独立共享参数等价建模。 |
| R3-05 | `SourceVideoSize` 纳入合法的 `pixelWidthHeightRatio`，有效显示宽高比与 Media3 PlayerView 一致；FIT 的尺寸截断和居中余数分配必须复现当前 Media3 行为，不允许已知 1px 漂移。 |
| R3-06 | 生产 Compose 与 ASS 都消费共享的最终 render spec，至少包含 fontSizePx、outlineWidthPx、字体、bold、italic、中英颜色和对齐；不得只共享原始字段或在 renderer 内再次解释单位。 |
| R3-07 | focused tests 覆盖 density/fontScale 组合、普通/全屏同比字号、1080/720/4K PlayRes、真实 stroke/fill、方形与非方形像素、Media3 FIT 截断/居中、16:9/9:16/1:1、九宫格 anchor、中英样式及两 cue 隔离。 |
| R3-08 | v1-v5→v6 迁移、v6 round-trip、R1/R2 保持项、focused/full JVM、ASR Python、lint、普通 Debug、native Debug、AndroidTest 构建全部通过并报告当前 APK 实物；不执行真机 UI。 |

| Category | V3-EDITOR-002 / R3 requirement |
|---|---|
| Main path | 旧项目或新项目得到源视频相对字号/描边 -> 普通与全屏预览按有效视频矩形缩放 -> ASS 导出按同一比例和参数渲染。 |
| Mandatory evidence | R3-01–R3-08；v6 迁移；共享最终 render spec 的生产消费路径；Media3 FIT/pixel ratio、字号与描边 focused tests；完整回归和产物证据。 |
| Prohibited | 不降低 S01-S14 或 R1/R2；不以 raw `sp`、固定 ASS px 或 Shadow 冒充共享最终样式；不启动 `V3-AI-001`；不触碰 BYOK、Whisper、在线歌词、媒体入口或无关 UI；不执行真机验证；不清理既有脏状态；不 push。 |
| Exit | R3 全部通过后只允许再次回交 `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`，由 Brain 重新裁决。 |
| Incomplete | 任一字号、描边、pixel ratio 或 FIT 行为仍不一致：`PARTIAL_PASS / SOURCE_RELATIVE_RENDERING_REQUIRED`；迁移不能证明安全：`BLOCKED / ARCHIVE_V6_MIGRATION_REQUIRED`。 |

## V3-EDITOR-002 / R3 内部 Agent 并行计划

| Role | 独占范围 | 交付物与边界 |
|---|---|---|
| 内部 Agent A：canonical style and archive | `CaptionStyleModel.kt`、`ProjectArchive.kt` 及专属 migration/round-trip tests；不得修改 Compose、ASS exporter 或三份活动文档 | 引入 `fontSizeRatio` 与共享描边宽度，完成 v1-v5→v6 迁移、严格校验和 v6 round-trip；冻结兼容常量与 rounding/clamp 规则 |
| 内部 Agent B：Media3 geometry parity | `CaptionGeometryModel.kt`、`CaptionGeometryModelTest.kt`；不得修改 Compose、ASS exporter、archive 或三份活动文档 | 纳入 `pixelWidthHeightRatio`，复现 Media3 FIT 截断和居中行为，输出供 renderer 使用的有效视频矩形与源视频相对像素换算 |
| 内部 Agent C：renderer parity | `EditorScreen.kt`、`FfmpegKitSubtitleExporter.kt` 及专属 Compose/ASS render-spec tests；不得修改 archive 或三份活动文档 | 在内部 Agent A/B 接口冻结后，让 Compose/ASS 消费同一最终 render spec；实现真实 stroke+fill、源视频相对字号/描边和 density/fontScale 独立性 |
| 主 Agent（协调与集成） | 三份活动文档、接口协调、共享热点集成、完整回归、产物核验与 scoped Git commits | 先提交三份文档 checkpoint；内部 Agent A/B 第一波并行，接口冻结后启动/接线内部 Agent C；独占冲突合并与最终 R3-01–R3-08 裁决，不让内部 Agent 相互调度 |

- 并行顺序：内部 Agent A 与内部 Agent B 可完全并行；内部 Agent C 可先准备独占测试，但生产接线必须等待 A/B 接口冻结。共享文件冲突由主 Agent 串行合并，不强行并行。

## Brain R1 re-adjudication (2026-08-11)

- Verdict: `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / RENDER_INTEGRATION_REQUIRED`。
- R1-01/R1-02/R1-03 通过代码复核：纯位置覆盖可清除，清除按 cueId 同时移除目标 cue 的 style/layout override；ASS 中文显式颜色不污染英文或下一 Dialogue。
- R1-04 未通过：Compose overlay 仍覆盖整个 PlayerView 容器，没有根据源视频宽高计算 Media3 `FIT` 后的有效视频矩形；有 letterbox/pillarbox 时，Compose 坐标基于黑边容器，而 ASS 坐标基于源视频帧。
- R1-04 未通过：Compose 在 `widthRatio` 得到的宽度内再次添加 8dp 左右 padding，真实文本宽度被额外缩窄并产生水平偏移。
- R1-04 未通过：Compose 中文字号额外乘以 `0.82`，中文不继承 cue 的 bold/italic，英文在 `bold=false` 时仍使用 `SemiBold`；ASS 对中英文使用同一解析字号、bold/italic，最终样式并不一致。
- R1-05 未通过：新增测试只验证 anchor helper 数值与 ASS 字符串，没有覆盖 Compose 有效视频矩形、黑边、不同纵横比、普通/全屏映射和最终样式一致性，因此无法证明 R1-04。
- R1-06/R1-07 的产物和回归主张与磁盘实物一致，但测试/构建通过不能覆盖上述生产渲染差异。

## V3-EDITOR-002 / R2 implementation evidence (2026-08-11)

- R2-01/R2-02/R2-05: shared `CaptionGeometryResolver` computes the centered Media3 FIT effective video rectangle from runtime `VideoSize` and the actual Compose container, then maps normalized x/y/width plus left/center/right and top/middle/bottom semantics. Normal and fullscreen overlays consume this same model; ASS consumes the model against its 1920x1080 PlayRes.
- R2-03/R2-04: `widthRatio` maps the complete text-box width; hidden Compose padding and background decoration were removed. Compose and ASS consume the same resolved font, size, bold, italic, English/Chinese colours, outline and alignment without Chinese scaling or non-bold weight substitution.
- R2-06: focused geometry 8/8 and ASS 8/8 passed, covering 6:9, 9:16, 1:1, wide/tall/equal containers, letterbox/pillarbox, normal/fullscreen parity, anchors, alignments, explicit zero-inset contract, bilingual final style and cue isolation.
- R2-07: full `testDebugUnitTest` 212/212, `python tools\\asr_evaluate_test.py` 6/6, `lintDebug` 0 errors/33 warnings, `assembleDebug`, native-enabled `assembleDebug`, and `assembleDebugAndroidTest` passed. APKs: `app/build/outputs/apk/debug/app-debug.apk` = `417,446,841` bytes; `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` = `119,048` bytes.
- Boundary: no real-device UI, DeepSeek, Key, online lyrics, Provider or Prompt work; physical UI remains waived and these are component/build proofs only. Developer candidate: `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`; Brain owns formal acceptance.

## V3-EDITOR-002 / R2 acceptance matrix

| ID | 必须证明 |
|---|---|
| R2-01 | 根据源视频宽高与 Compose 预览容器宽高计算 Media3 `FIT` 的唯一有效视频矩形；横向黑边、纵向黑边及无黑边三种情况都必须排除容器空白区域。 |
| R2-02 | 普通预览与全屏预览都只在有效视频矩形内映射归一化 x/y/width；top/middle/bottom anchor 和 left/center/right 对齐与 ASS 的源视频坐标语义相同。 |
| R2-03 | `widthRatio` 表示可排版字幕文本框的规范宽度；Compose 不得用未建模的固定 padding 再次缩窄或平移文字区域。装饰背景或内边距若保留，必须在共享几何模型中显式建模并由 ASS 等价消费。 |
| R2-04 | Compose 与 ASS 使用同一解析后的字体、字号、bold、italic、英文颜色、中文颜色、描边和对齐规则；不得在任一端额外乘字号、替换字重或漏用样式字段。 |
| R2-05 | 把有效视频矩形、归一化坐标映射和最终文本样式提取为可独立验证的共享纯模型；生产 Compose 与 ASS 都消费该模型，不接受只复制相同常量或只比较 resolver DTO。 |
| R2-06 | focused 回归至少覆盖：16:9、9:16、1:1 源视频；宽屏、窄屏与等比例容器；letterbox/pillarbox；普通/全屏；左中右、上中下；中英最终字号/粗斜体/颜色；至少两条 cue 隔离。 |
| R2-07 | 保持 R1-01/R1-02/R1-03 已修复行为及 v5 迁移不回归；focused/full JVM、ASR Python、lint、普通 Debug、native Debug、AndroidTest 构建通过并报告当前 APK 实物。 |

| Category | V3-EDITOR-002 / R2 requirement |
|---|---|
| Main path | 源视频按 FIT 显示 -> 字幕只在有效视频画面内按同一规范坐标和最终样式预览 -> 普通/全屏/ASS 导出使用同一几何与样式结果。 |
| Mandatory evidence | R2-01–R2-07；共享纯模型与生产消费路径；覆盖黑边、纵横比、两种预览及中英最终样式的 focused tests；完整回归和产物证据。 |
| Prohibited | 不降低 S01-S14 或 R1；不以播放器容器代替有效视频矩形；不写仅验证 ASS 字符串的伪一致性测试；不启动 `V3-AI-001`；不触碰 BYOK、Whisper、在线歌词、媒体入口或无关 UI；不执行真机验证；不清理既有脏状态；不 push。 |
| Exit | R2 全部通过后只允许再次回交 `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`，由 Brain 重新裁决。 |
| Incomplete | 任一有效画面、几何或最终样式差异仍存在：`PARTIAL_PASS / RENDER_INTEGRATION_REQUIRED`；无法获得源视频尺寸或共享模型不能安全落地：`HUMAN_DECISION / SOURCE_VIDEO_GEOMETRY_REQUIRED`。 |

## V3-EDITOR-002 / R2 内部 Agent 并行计划

| Role | 独占范围 | 交付物与边界 |
|---|---|---|
| 内部 Agent A：geometry core | 新增共享的有效视频矩形/归一化坐标纯模型及其 focused JVM tests；不得修改 `EditorScreen.kt`、ASS exporter 或三份活动文档 | 计算 Media3 FIT 下的 letterbox/pillarbox/no-bars 矩形，输出普通/全屏均可消费的 x/y/width/anchor 映射；覆盖 16:9、9:16、1:1 和不同容器比例 |
| 内部 Agent B：Compose integration | `EditorScreen.kt` 及专属 Compose/render-style tests；不得修改 ASS exporter 或三份活动文档 | 普通与全屏 overlay 消费内部 Agent A 的共享几何；消除未建模 padding；让中英文最终字号、字重、bold、italic、颜色、描边和对齐严格消费 resolver 结果 |
| 内部 Agent C：ASS and parity verification | `FfmpegKitSubtitleExporter.kt`、`AssSubtitleWriterTest.kt` 及不与内部 Agent A/B 重叠的一致性 fixtures；不得修改 Compose 或三份活动文档 | 核对 ASS 对共享坐标/最终样式的消费，补齐跨纵横比、黑边、九宫格 anchor、双语样式和 R1 保持项的对照证据；仅在证明需要时修改 exporter |
| 主 Agent（协调与集成） | 三份活动文档、任务分派、共享接口协调、冲突集成、完整回归、产物核验与 scoped Git commits | 先提交三份文档 checkpoint；冻结内部 Agent A 接口后协调 B/C 消费；独占跨内部 Agent 共享热点的最终合并，不让内部 Agent 相互调度；最后逐项裁决 R2-01–R2-07 并回交 Developer 候选 |

- 并行顺序：内部 Agent A/B/C 可先并行完成各自独占范围；涉及内部 Agent A 新接口的最终接线由主 Agent 在接口冻结后集成。若发现必须同时修改同一热点文件，停止该文件的并行写入并交由主 Agent 串行合并，不强行并行。

## Brain adjudication (2026-08-11)

- Verdict: `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / RENDER_INTEGRATION_REQUIRED`。
- S08 未通过：仅修改位置时只产生 `layoutOverride`，但清除按钮的 `hasOverride` 只检查 `styleOverride`，因此纯位置覆盖无法从 UI 清除。
- S10 未通过（颜色）：Compose 为中文使用 `secondaryColorHex`；ASS 把中英文写入同一 Dialogue，却未在中文行插入显式颜色 override，`SecondaryColour` 不会自动成为普通第二行文本颜色。
- S10 未通过（几何）：Compose 固定 `BottomCenter` 并以 bottom padding 解释 y；ASS 按 y 切换 top/middle/bottom anchor，且两端对 x/width 的映射不同，不能证明同一规范坐标产生相同预览与导出位置。
- S14 构建通过主张保留为 Developer 证据，但 AndroidTest APK 实物为 `119,048 bytes`，不是回交中的 `119,027 bytes`；App APK 实物为 `417,446,841 bytes`。
- v5 迁移、cueId 写入链路与逐 cue 隔离未发现新的阻断；物理设备 UI 继续按用户授权豁免，不把缺失证据伪造为 PASS。

## V3-EDITOR-002 / R1 implementation evidence (2026-08-11)

- R1-01/R1-02/R1-05：纯位置覆盖现在可清除；clear action 同时清除目标 cue 的 style/layout override，并保留其他 cue；清除后回落到默认布局。`cueId` 是唯一写入目标。
- R1-03：ASS 中文行在同一 Dialogue 内显式使用解析后的 `secondaryColorHex`，英文行和后续 Dialogue 不继承该 override。
- R1-04：Compose 与 ASS 共用 `CaptionVerticalAnchor`、归一化 x/y/width 和相同 top/middle/bottom anchor 语义；新增左/中/右与上/中/下映射回归。
- R1-06 artifacts: `D:\DevEnv\Projects\lyric-captioner-android\app\build\outputs\apk\debug\app-debug.apk` = `417446841` bytes；`D:\DevEnv\Projects\lyric-captioner-android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk` = `119048` bytes。
- R1-07: focused JVM 39/39；完整 `testDebugUnitTest` 203/203；`python tools\\asr_evaluate_test.py` 6/6；`lintDebug` 0 errors/33 warnings；普通 Debug、`-PenableWhisperNative=true assembleDebug`、`assembleDebugAndroidTest` 全部通过。
- Boundary: 未执行真机 UI；未触碰 DeepSeek、真实 Key、在线歌词、Provider 或 Prompt。Developer candidate: `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`，等待 Brain 重新裁决。

## V3-EDITOR-002 / R1 acceptance matrix

| ID | 必须证明 |
|---|---|
| R1-01 | cue 的“存在覆盖”状态同时覆盖 `styleOverride` 与 `layoutOverride`；仅有位置覆盖时，卡片内清除按钮仍可用。 |
| R1-02 | 清除操作按显式 `cueId` 同时清除该 cue 的样式与位置覆盖，只回落到兼容性基础值，不改变其他 cue。 |
| R1-03 | ASS 为中文行应用与 Compose `secondaryColorHex` 相同的显式文本颜色 override；颜色不得泄漏到英文行或下一条 Dialogue。 |
| R1-04 | Compose 与 ASS 复用同一套源视频归一化 x/y/width 和 anchor 语义；不得分别解释 y、重复缩窄宽度或产生额外水平偏移。 |
| R1-05 | focused 测试至少覆盖：纯位置覆盖可清除、清除 cue 隔离、中英文不同颜色导出、左/中/右与上/中/下位置映射，以及至少两条 cue 互不影响。 |
| R1-06 | 更正构建产物证据，报告磁盘实物的绝对路径、字节数和生成批次；不得沿用旧 AndroidTest APK 数字。 |
| R1-07 | focused/full JVM、ASR Python、lint、普通 Debug、native Debug 与 AndroidTest 构建全部通过；不执行真机 UI，不触碰 DeepSeek、Key、在线歌词、Provider 或 Prompt。 |

| Category | V3-EDITOR-002 / R1 requirement |
|---|---|
| Main path | 在字幕卡片内修改纯位置或样式 -> 可清除该 cue 的全部覆盖 -> Compose 与 ASS 对中英文颜色、位置、宽度和 anchor 产生同一结果。 |
| Mandatory evidence | R1-01–R1-07；针对三个 Brain 阻断的回归测试；更正后的 APK 实物证据；完整构建矩阵。 |
| Prohibited | 不降低原 S01-S14；不启动 `V3-AI-001`；不修改 BYOK、Whisper、在线歌词、媒体入口或无关 UI；不执行真机验证；不清理既有脏状态；不 push。 |
| Exit | R1 全部通过后只允许再次回交 `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`，由 Brain 重新裁决。 |
| Incomplete | 任一清除、颜色或坐标映射问题仍存在：`PARTIAL_PASS / RENDER_INTEGRATION_REQUIRED`；跨 cue 污染：`BLOCKED / CUE_STYLE_ISOLATION_REQUIRED`。 |

## Developer-returned V3-EDITOR-002 evidence (not formally accepted)

- S01-S13：组件级通过。独立全局样式面板已从实际编辑页移除；每条字幕卡片提供 cue-id 作用域的可展开样式/位置控件，写操作显式携带 cueId，样式与布局覆盖通过 v5 归档保存并可恢复。
- S10-S12：`CaptionRenderResolver` 同时供 Compose 预览边界与 ASS 导出；v1-v4 读取保持 layout override 为空并继承历史布局；v5 round-trip、非法/非有限/越界布局拒绝及两条 cue 隔离测试通过。
- Verification: focused editor/data/resolver JVM 34/34；完整 `testDebugUnitTest` 198/198；`python tools\\asr_evaluate_test.py` 6/6；`lintDebug` 0 errors/33 warnings；`assembleDebug`、`-PenableWhisperNative=true assembleDebug`、`assembleDebugAndroidTest` 均通过。
- Physical boundary: 本阶段按用户矩阵豁免真机 UI；没有真机、DeepSeek、真实 Key、在线歌词或 Provider/Prompt 证据，不声明正式产品 PASS。
- Returned candidate: `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`；Brain 已按上方裁决降为 `RENDER_INTEGRATION_REQUIRED`。

## V3-EDITOR-002 acceptance matrix

| ID | 必须证明 |
|---|---|
| S01 | 字幕编辑页不再显示独立“项目默认样式”面板。 |
| S02 | 字幕编辑页不再显示独立“当前字幕覆盖”面板。 |
| S03 | 每条字幕卡片都有自己的可展开“字幕样式”入口；未展开卡片保持紧凑，不同时铺开全部色板。 |
| S04 | 每条卡片可独立调整字号、字体、英文颜色、中文颜色、描边颜色、粗体、斜体和对齐。 |
| S05 | 每条卡片可独立上移/下移；调整一条字幕的位置不改变其他字幕。 |
| S06 | 所有写操作显式携带 `cueId`，不能依赖可能在重组期间变化的 `selectedCaptionId` 决定目标。 |
| S07 | 修改任一 cue 的样式或位置不影响其他 cue 的文本、时间、样式、位置和确认状态。 |
| S08 | 卡片内“恢复基础样式/位置”只清除该 cue 的覆盖，并回落到兼容性基础值。 |
| S09 | `DefaultCaptionStyle` 与项目级 `CaptionLayout` 只作为新 cue、旧项目迁移和未覆盖字段的内部回退，不再暴露全局编辑面板。 |
| S10 | cue 级位置通过独立 layout override 建模；Compose 与 ASS 必须通过同一 resolver 得到相同最终样式和位置。 |
| S11 | 归档升级为 v5；v1-v4 读取时 cue layout override 为空并继承历史项目布局，不能丢字幕、时间或样式。 |
| S12 | v5 完整 round-trip cue 样式与位置；非法、非有限或越界 layout 数据被安全拒绝。 |
| S13 | 文本、时间、候选、确认、删除和列表选择功能保持可用，样式控件具备 cue-id 级无障碍 semantics。 |
| S14 | focused/full JVM、ASR Python、lint、普通 Debug、native Debug 和 AndroidTest 构建通过；物理设备 UI 不作为本阶段阻断项。 |

| Category | V3-EDITOR-002 requirement |
|---|---|
| Main path | 用户进入字幕编辑 -> 在某条字幕卡片内展开“字幕样式” -> 只调整该条字幕的字体、字号、颜色、描边、粗斜体、对齐和位置 -> 预览与导出使用同一结果 -> 保存重启后恢复。 |
| Mandatory evidence | S01-S14；至少两条 cue 的互不影响测试；v4→v5 迁移与 v5 round-trip；Compose/ASS resolver 一致性；完整构建矩阵。 |
| Prohibited | 不实现 DeepSeek 字幕 Provider 或 Prompt；不修改 BYOK；不接在线歌词；不更换 Whisper 模型/参数；不重做全局视觉 UI、媒体入口或清理 SRT；不伪造真机证据。 |
| Exit | 全部组件证据通过后只允许回交 `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`，由 Brain 裁决。 |
| Incomplete | cue 写错目标或互相污染：`BLOCKED / CUE_STYLE_ISOLATION_REQUIRED`；旧项目迁移丢失：`BLOCKED / ARCHIVE_MIGRATION_SAFETY_REQUIRED`；预览/导出不一致：`PARTIAL_PASS / RENDER_INTEGRATION_REQUIRED`。 |

## Previous V3-EDITOR-001 physical-attempt evidence (2026-08-10)

- Candidate: `PARTIAL_PASS / EDITOR_COMPONENT_VERIFIED / PHYSICAL_DEVICE_UI_REQUIRED`; ASR remains `PARTIAL_PASS / WHISPER_SESSION_COMPONENT_VERIFIED / PHYSICAL_DEVICE_RUNTIME_REQUIRED`
- E01–E18: component-level PASS. Model/archive, Compose policy, shared render resolver and ASS per-cue mapping are covered by focused JVM tests; no formal product PASS is claimed.
- Verification: focused editor JVM 25/25; full `testDebugUnitTest` 192/192; `python tools\\asr_evaluate_test.py` 6/6; `lintDebug` 0 errors/33 warnings; `assembleDebug`, `-PenableWhisperNative=true assembleDebug`, and `assembleDebugAndroidTest` passed.
- Artifacts: native-enabled app APK 417,446,841 bytes; AndroidTest APK 119,027 bytes.
- Physical boundary: only `fcf4b0cb / 25098PN5AC / arm64-v8a / API 36` was used with repository-owned synthetic WAV/SRT/video fixtures. A13/A15 stopped after native `inference_started`; no real handle reuse, completed timing, temperature, empty-result or crash evidence was obtained. No Key, DeepSeek or lyrics path was touched.
- A13/A15 evidence: native log recorded `whisper_context_created handle=1` and `whisper_jni_inference_started`; the 250 ms and 500 ms fixtures still produced no `whisper_full_exited` within 300 s. The cancellation run recorded `abort_requested handle=1 active=1` but no native exit within 120 s. Observed process RSS was 528,136-532,564 KiB while blocked; load/inference/total, temperature, empty-result and crash fields are unavailable.
- Editor physical UI evidence: test-owned media/SRT was installed, but import and UI smoke instrumentation timed out (120-240 s) without a result bundle or verifiable product-entry sequence. No editor PASS is claimed.
- Current verification commands: `testDebugUnitTest` passed; native-enabled `assembleDebugAndroidTest` passed; the device runs above are the only physical evidence for this batch and are failures/incomplete, not acceptance.
- Preserved scope: BYOK, ASR/session, media, lyrics, Whisper parameters and existing dirty/untracked state were not cleaned or broadened.

## V3-EDITOR-001 acceptance matrix

| ID | 必须证明 |
|---|---|
| E01 | ASR 成功后停留当前栏目并显示成功状态。 |
| E02 | 只有点击“编辑字幕”才进入编辑栏目。 |
| E03 | 取消、失败、空结果不显示成功入口。 |
| E04 | 打开旧项目和状态恢复不自动抢占栏目。 |
| E05 | 字幕列表只归属于编辑栏目。 |
| E06 | `CaptionLayout` 使用合法归一化源视频坐标。 |
| E07 | 所有 cue 共享同一项目文本框范围。 |
| E08 | 默认样式正确作用于无覆盖 cue。 |
| E09 | 单 cue 覆盖不影响其他 cue。 |
| E10 | 清除覆盖后正确继承默认样式。 |
| E11 | 修改默认样式不覆盖 cue 的显式字段。 |
| E12 | 文本、时间轴和确认状态编辑不破坏样式。 |
| E13 | V2 全局样式正确迁移，cue 默认无覆盖。 |
| E14 | V3 archive 完整 round-trip。 |
| E15 | Compose 与 ASS 使用相同 resolver 结果。 |
| E16 | ASS 按 cue 输出最终样式且时间戳不变。 |
| E17 | 非法布局和样式字段被拒绝或安全规范化。 |
| E18 | 既有 ASR、BYOK、项目恢复和导出回归通过。 |

## Stage gate

| Category | Frozen requirement |
|---|---|
| Main path | ASR 成功后仍停留“识别/翻译”栏目并显示明确成功状态；只有用户点击“编辑字幕”才进入编辑栏目。字幕列表只在编辑栏目显示；项目使用一个源视频归一化文本框、一个默认样式和可选 cue 覆盖，Compose 预览与 ASS 导出消费同一解析结果。 |
| Mandatory evidence | E01–E18；focused 数据/迁移/resolver/JVM 测试；Compose/UI focused 测试；完整 JVM、ASR Python、lint、普通 Debug、native-enabled Debug、AndroidTest 构建；可用模拟器 editor instrumentation。所有真机 UI 验证登记到最终积压，不在本阶段执行。 |
| Prohibited | ASR 成功自动切栏；屏幕像素/预览尺寸/黑边作为持久化坐标；两套预览/导出默认值或覆盖顺序；真机 ADB/安装/instrumentation；DeepSeek 网络/真实 Key/在线歌词/逐 cue AI；整体视觉重设计；MediaStore/Photo Picker、Whisper/session cache、SRT/旧分支清理。 |
| Exit | E01–E18 与全部组件验证通过后，只能回交 `PARTIAL_PASS / EDITOR_COMPONENT_VERIFIED / PHYSICAL_DEVICE_UI_DEFERRED_BY_USER`；不得声明正式产品 PASS。 |
| Incomplete | 数据模型和归档通过但 Compose/ASS 共用解析未完成：`PARTIAL_PASS / EDITOR_MODEL_VERIFIED / RENDER_INTEGRATION_REQUIRED`；V2 迁移丢失样式、字幕或时间轴：`BLOCKED / PROJECT_MIGRATION_SAFETY_REQUIRED`。 |

## Frozen editor constraints

- 项目只保存一个 `CaptionLayout(xRatio, yRatio, widthRatio)`，使用源视频归一化坐标并限制在有效画面内；cue 编辑不得改变项目文本框。
- 项目保存一个 `DefaultCaptionStyle`；每个 cue 只保存可选 `CaptionStyleOverride`。未覆盖字段继承默认值，清除覆盖立即回落，修改默认值不改写显式覆盖。
- 文本、时间轴、确认状态与样式覆盖相互独立；旧 V2 `SubtitleStyle` 迁移为 V3 默认样式，旧 cue 无覆盖并获得安全默认布局。
- V3 archive 完整保存 layout/default style/cue overrides；损坏或越界值必须拒绝或安全规范化。
- Compose 与 ASS 只能调用同一 resolver；ASS 按 cue 使用最终样式且不得改变文本或时间戳。
- 打开项目、恢复状态或字幕列表变化不得抢占栏目；取消、失败和空结果不得显示成功入口。

## FINAL_PHYSICAL_DEVICE_VERIFICATION_BACKLOG

- ASR A13：真实 native context 连续两次识别、冷/热路径及真实 handle 复用。
- ASR A15：真实冷/热 context load、inference、total、峰值 RSS、温度、空结果和崩溃数据。
- 只有用户以后明确说“开始真机验证”时才集中执行；当前不得连接、安装、等待、轮询或推测真机数据。

## Historical accepted stage: V3-ASR-SESSION-001

## V3-ASR-SESSION-001 acceptance matrix

| ID | 必须证明 |
|---|---|
| A01 | 冷任务创建一次 native context，任务完成后进入空闲缓存。 |
| A02 | 3 分钟内第二个任务复用同一 context，create count 仍为 1。 |
| A03 | 5 分钟到期释放旧 context，下一任务创建新 context。 |
| A04 | 两个并发请求严格串行，native 最大并发推理数为 1。 |
| A05 | 前一任务的音频、取消状态和结果不污染后一任务。 |
| A06 | 模型路径、大小或 SHA-256 变化均使旧 context 失效。 |
| A07 | 模型切换不会在活跃 native 推理期间释放旧 handle。 |
| A08 | 取消触发 abort，并等待 native 完全退出后释放；下一任务安全重建。 |
| A09 | 空闲和活跃状态下的严重内存压力均执行正确释放策略。 |
| A10 | create/load/transcribe 失败后无缓存泄漏，后续任务可恢复。 |
| A11 | 重复 close/release 不 double-free、不崩溃。 |
| A12 | 新进程/runtime 实例从空缓存开始，不伪造跨进程复用。 |
| A13 | 真实 native context 连续两次识别证明冷/热路径和 handle 复用。 |
| A14 | 输出 cue 时间戳合法；缓存前后结果无串任务污染。 |
| A15 | 冷/热分别记录 context 加载、推理、总耗时、峰值 RSS、温度、空结果和崩溃。 |
| A16 | BYOK、项目恢复、导出和既有 ASR baseline 无回归。 |

时间边界测试必须使用可注入 monotonic clock/scheduler，不让 JVM 测试真实等待 3–5 分钟。

## Stage gate

| Category | Frozen requirement |
|---|---|
| Main path | 当前模型首次识别创建并加载一个进程级 native context；任务完全结束后开始空闲计时，3 分钟内后续任务复用，最多缓存 5 分钟；同一 context 严格串行；到期、模型身份变化、严重内存压力或取消后状态不安全时安全释放并在下次任务重建。 |
| Mandatory evidence | A01–A16；focused/full JVM、`python tools\\asr_evaluate_test.py`、lint、普通 Debug、native-enabled Debug、AndroidTest 构建；唯一授权真机 production native/session instrumentation；冷/热 create/free/handle、加载/推理/总耗时、RSS、温度、空结果、崩溃和时间戳证据。 |
| Prohibited | 不缓存任务音频、取消令牌、临时推理状态或字幕结果；不启用 GPU、不换模型、不改 Whisper 线程数或识别参数；不修改 DeepSeek、BYOK、歌词、翻译、编辑器、Media3、FFmpegKit、媒体入口、SRT 或 UI；不读取用户私人媒体，不连接其他设备，不清理既有脏状态，不 push。 |
| Exit | A01–A16、完整构建和真机 native 证据全部通过后，只能回交 Developer 候选 `PASS / WHISPER_SESSION_CACHE_VERIFIED / PHYSICAL_DEVICE_RUNTIME_VERIFIED`；缓存只证明降低重复模型加载成本，不宣称 ASR 准确率、WER/CER 或核心推理速度提升。 |
| Incomplete | 逻辑与构建通过但缺真机 native 证据：`PARTIAL_PASS / WHISPER_SESSION_COMPONENT_VERIFIED / PHYSICAL_DEVICE_RUNTIME_REQUIRED`；无法证明取消/释放/并发期间无 use-after-free：`BLOCKED / NATIVE_LIFETIME_SAFETY_REQUIRED`；第二次任务仍加载模型：`BLOCKED / CACHE_REUSE_NOT_PROVEN`。 |

## Frozen runtime and ownership constraints

- Runtime 是进程级单模型实例；模型身份至少绑定规范路径、文件大小和 SHA-256，任一变化都失效。
- 使用 monotonic clock；空闲从任务完全结束后开始。单个 context 用 `Mutex` 或单线程队列串行访问。
- 空闲模型切换立即释放；活跃模型切换标记 pending invalidation，任务退出后释放。
- 取消顺序固定为请求 abort -> `whisper_full` 返回 -> 推理线程结束 -> 清理任务临时状态 -> context 失效并安全释放；本阶段取消后的 context 不复用。
- 严重内存压力下空闲 context 立即释放，活跃 context 标记 pending release 并在任务退出后释放。
- create、transcribe 或 free 失败不得留下可复用的半有效 handle；release/close 必须幂等。
- 主 Agent（协调与集成）独占三份活动文档、Git、共享接口及 `WhisperLocalSpeechRecognizer.kt`、`AppPipelineFactory.kt`、`MainViewModel.kt` 等集成热点。

## Developer evidence and A01–A16 result (2026-08-10)

| ID | Result | Evidence |
|---|---|---|
| A01 | `PASS` | focused fake-native runtime 证明冷任务只 create 一次，完成后 snapshot 进入 idle cache。 |
| A02 | `PASS` | 注入 clock 前进 3 分钟后第二任务复用同一 handle，create count 仍为 1。 |
| A03 | `PASS` | 注入 scheduler 在 5 分钟边界释放旧 handle，下一任务 create 新 handle；JVM 未真实等待。 |
| A04 | `PASS` | 两个并发请求经 `Mutex` 严格串行，fake native max concurrency 为 1；JNI 另有 per-session inference mutex。 |
| A05 | `PASS` | focused 测试以不同音频标识、取消状态和结果证明任务临时数据不进入 cache。 |
| A06 | `PASS` | canonical path、size、SHA-256 三类变化分别触发旧 context 失效。 |
| A07 | `PASS` | 活跃模型切换只标记 pending invalidation，native 退出后才 free。 |
| A08 | `PASS`（组件级） | fake-native 顺序证明 abort -> native exit -> free，取消后下一任务新建；真实设备 native 顺序尚未执行。 |
| A09 | `PASS` | 空闲严重内存压力立即释放；活跃压力标记 pending release 并在任务退出后释放。 |
| A10 | `PASS` | create/transcribe/free 失败均移除半有效缓存，后续任务恢复。 |
| A11 | `PASS` | 重复 close/free 幂等，JNI registry 对未知/已释放 handle 忽略重复 free。 |
| A12 | `PASS` | 新 runtime 实例从空 snapshot 开始，不复用另一 runtime 的 handle。 |
| A13 | `UNVERIFIED / NATIVE_INFERENCE_TIMEOUT` | 真机仅记录 `handle=1` 创建和首次 `inference_started`；250 ms 与 500 ms 测试音频均未在 300 s 内返回，未取得连续识别或 handle 复用。 |
| A14 | `PASS`（组件级） | JVM 覆盖合法 cue 时间戳、顺序和跨任务隔离；真机缓存前后 cue 证据随 A13 待补。 |
| A15 | `UNVERIFIED / METRICS_UNAVAILABLE` | 阻塞期间仅观察到 RSS 528,136-532,564 KiB；load/inference/total、温度、空结果和崩溃字段没有 instrumentation 回交，不能填写或推导。 |
| A16 | `PASS`（回归级） | focused 14/14；完整 JVM 169/169；ASR Python 6/6；lint 0 errors/33 warnings；普通 Debug、native-enabled Debug、AndroidTest 构建全部通过。 |

- Native lifecycle：JNI 使用 registry-backed opaque positive handle、显式 create/transcribe/requestAbort/free、native atomic abort、per-session inference mutex；free 等待 active `whisper_full` 临界区退出，取消/失败 handle 立即禁止复用，重复 free 不 double-free。
- Production integration：`AppPipelineFactory` 通过 `WhisperProcessSession` 复用进程级单模型 runtime；严重 `ComponentCallbacks2` 内存压力转发到 runtime。任务音频、取消令牌、临时状态和结果不保存在 cache。
- 完整 JVM：169 tests、0 failures、0 errors、0 skipped；`python tools\\asr_evaluate_test.py`：6 passed；lint：0 errors/33 warnings。
- `assembleDebug`、`-PenableWhisperNative=true assembleDebug`（arm64-v8a + x86_64）、`assembleDebugAndroidTest` 均通过。沙箱内 NDK `clang++.exe` 权限失败后，在允许访问本机 NDK 的执行环境用同一 native 命令通过。
- 当前 app APK：383,030,793 bytes；AndroidTest APK：118,877 bytes。
- Secret scan：app APK 0 Key token/0 credential-bearing Bearer；AndroidTest APK 4 个既有允许 synthetic Key token/0 credential-bearing Bearer；本阶段源文件 0/0。
- 真机在阶段开始时核对为 `fcf4b0cb / 25098PN5AC / arm64-v8a / API 36`，但安装前 ADB/USB 断连；三次后续检查均无设备。APK 未安装，session instrumentation 未启动，未读取、复制或提交任何用户媒体。
- Checkpoint：`3aec389`（`文档(v3-asr)：冻结 Whisper 会话缓存验收矩阵`）。功能提交使用标题 `功能(v3-asr)：实现 Whisper 进程级会话缓存`；不 push。
- Brain 已正式接受 `PARTIAL_PASS / WHISPER_SESSION_COMPONENT_VERIFIED / PHYSICAL_DEVICE_RUNTIME_DEFERRED_BY_USER`；A13/A15 进入最终真机验证积压。不得声明物理设备 runtime 已验证、正式 PASS、准确率/WER/CER 或核心推理速度提升。

## Historical closed stage: V3-AI-CONTRACT-001 / R1

## V3-AI-CONTRACT-001-R1 live-key acceptance matrix

| Category | LIVE-KEY requirement |
|---|---|
| Main path | 构建安装 Debug APK -> 用户只在真机 App 内手动输入真实 DeepSeek Key -> 最小认证成功并加密保存 -> 强停重启恢复 masked `CONFIGURED` -> 使用已保存 Key“测试连接” -> 同一有效 Key 替换并确认新 IV/原子切换 -> synthetic invalid Key 替换失败且旧真实 Key 仍可认证 -> 删除 record 与 alias -> 重启后 `UNCONFIGURED`。 |
| Mandatory evidence | R1 focused JVM、完整 `testDebugUnitTest`、`python tools\asr_evaluate_test.py`、`lintDebug`、普通 Debug、native-enabled Debug、AndroidTest 构建；production 真机 UI/Keystore/网络验证；认证端点与脱敏 HTTP 结果分类；record 无明文、重启、same-key rotation、失败保留旧 Key、删除；APK、日志、允许截图和测试输出 secret scan。 |
| Prohibited | 不发送视频、字幕、歌词、媒体路径或用户内容；不记录 Authorization header、Key、请求对象、响应正文或完整 URL 查询；不通过聊天、终端、环境变量、ADB、剪贴板脚本或自动化文件输入真实 Key；不截图密码输入过程；不测试歌词/逐 cue/Whisper/媒体/SRT；不新增大型依赖、后端或代理，不连接其他设备，不清理既有脏状态，不 push。 |
| Exit | 全矩阵已通过，Brain 正式接受 `PARTIAL_PASS / SECURE_BYOK_VERIFIED / DEEPSEEK_AUTH_VERIFIED / LIVE_LYRICS_FLOW_DEFERRED`；该裁决验证真机 BYOK 安全链路和 DeepSeek 最小认证，但不是正式产品 PASS。 |
| Incomplete | Key 无效或额度/账号状态阻止认证：`PARTIAL_PASS / DEEPSEEK_ACCOUNT_ACTION_REQUIRED / LIVE_LYRICS_FLOW_DEFERRED`，只提示用户在 App 内重输或处理账号；设备/网络/构建或安全证据缺失：对应 `BLOCKED` 专项状态；需要新架构、大型依赖、第二个真实凭据或扩大到歌词链路：`HUMAN_DECISION`。 |

## R1 live-key formal evidence (2026-08-10)

- Device: `fcf4b0cb / 25098PN5AC / arm64-v8a / API 36 / qcom`；唯一授权真机，用户只在 App 内手动输入真实 Key，未通过聊天、终端、环境变量、ADB、剪贴板脚本、源码或测试文件传递。
- Production probe 固定为 `GET https://api.deepseek.com/models`，禁止重定向，不带 query、不发送请求正文或用户内容，也不读取/记录响应正文；首次保存、重启后的“测试连接”和 same-key rotation 均得到脱敏 `HTTP 2xx` 分类。
- 首次成功后 production `noBackupFilesDir` record 为 147 bytes，仅恢复 masked `CONFIGURED`；二次使用同一有效 Key 替换后 record SHA-256 与 12-byte GCM IV 均改变，明确只证明 same-key rotation，不冒充第二个凭据。
- synthetic invalid Key 替换得到脱敏 `HTTP 401`，原 record SHA-256 与 IV 保持不变；随后“测试连接”仍以旧真实 Key 得到 `HTTP 2xx`。
- 删除后 production record 不存在；强停重启为 `UNCONFIGURED`、无 masked Key。结合 production health 对“record 缺失但 alias 存在”固定返回 `NEEDS_REENTRY`，该结果证明 production record 与 alias 均已删除；App 最终再次强停。
- R1 focused JVM：31 tests、0 failures、0 skipped；完整 `:app:testDebugUnitTest`：155 tests、0 failures、0 errors、0 skipped；`python tools\asr_evaluate_test.py`：6 tests passed。
- `lintDebug`：0 errors / 33 warnings；普通 Debug、native-enabled Debug、AndroidTest 构建全部通过。最终 stripped app APK 为 382,081,973 bytes，AndroidTest APK 为 91,700 bytes；真机 synthetic production Keystore/UI instrumentation 通过。
- Secret scan：app APK 0 Key token；AndroidTest APK 4 个允许的 synthetic token、0 disallowed；源码 25 个允许的 test/synthetic token、0 disallowed；测试输出 0；logcat 1 个允许的 synthetic token、0 disallowed、0 credential-bearing Bearer、0 DeepSeek query URL；本阶段未创建截图文件，production record 最终不存在。
- Checkpoint: `1567402`（`文档(v3-r1)：冻结真实 Key 验证矩阵`）；实现与证据提交使用标题 `功能(v3-r1)：验证 DeepSeek 真机 BYOK 认证`，不 push。
- Brain 已正式接受 `PARTIAL_PASS / SECURE_BYOK_VERIFIED / DEEPSEEK_AUTH_VERIFIED / LIVE_LYRICS_FLOW_DEFERRED`；已验证真机 BYOK 安全链路和 DeepSeek 最小认证，未验证在线歌词、歌曲匹配、逐 cue 增强或完整产品链路，不得声明正式产品 PASS。

## V3-AI-CONTRACT-001-R1 acceptance matrix

| Category | R1 requirement |
|---|---|
| Main path | App AI service settings -> masked DeepSeek BYOK input -> minimal validation -> Android Keystore AES-256-GCM encryption -> `noBackupFilesDir` ciphertext/IV record -> short-lived decrypt only for request construction. |
| Mandatory evidence | R1-R01 至 R1-R10；真实取消与 write count；status/cancel decrypt count 为 0；统一串行化与可见删除失败；production `AndroidKeystoreDeepSeekKeyStore` round-trip/corruption/alias-loss/delete instrumentation；masked settings UI；focused/full JVM、lint、普通 Debug、native-enabled Debug、AndroidTest 构建与 secret scan。 |
| Prohibited | Real key, backend/provider expansion, online lyrics, UI redesign, Whisper/cache/media/editor/SRT cleanup, plaintext key in preferences/DataStore/archive/SavedState/logs/APK/tests. |
| Exit | R1-R01 至 R1-R10、production Android Keystore instrumentation、完整构建矩阵、secret scan 和三份文档全部通过；Brain 已正式裁决为 `PARTIAL_PASS / SECURE_BYOK_COMPONENT_VERIFIED / LIVE_KEY_TEST_REQUIRED`，真实 Key 产品流仍需后续授权。 |
| Incomplete | JVM/构建通过但 Android Keystore instrumentation 未运行：`PARTIAL_PASS / ANDROID_KEYSTORE_RUNTIME_TEST_REQUIRED / LIVE_KEY_TEST_REQUIRED`；安全、取消、原子删除或明文生命周期无法证明：`BLOCKED / SECURITY_PROOF_REQUIRED`。 |

## R1 security/BYOK rework evidence (2026-08-10)

- Brain 此前否决旧实现：production health 曾完整解密 Key、取消证据仅覆盖 probe、alias 删除部分失败会回落为 `UNCONFIGURED`，且 Provider 路线文档未统一；本增量修复并补齐证据后，Brain 已正式接受组件级安全裁决。
- 当前增量把 Key 密文/IV/mask 绑定为独立 AES-GCM health 标签的 AAD，health 只认证空明文标签；写入改为可回滚 prepare/commit 事务并在 commit 前后检查 Job；孤立 alias 与删除部分失败固定为 `NEEDS_REENTRY`。
- Checkpoint: `bbb9761`；旧实现红线基线 6 项中 R1-R03、R1-R04、R1-R05 共 3 项失败，分别证明删除后写回、删除吞错和 status/cancel 明文解密缺口。
- R1 focused JVM：27 tests、0 failures、0 skipped；完整 `:app:testDebugUnitTest`：148 tests、0 failures、0 skipped；`python tools\asr_evaluate_test.py`：6 tests passed。
- `:app:lintDebug`、`:app:assembleDebug`、`-PenableWhisperNative=true :app:assembleDebug`、`:app:assembleDebugAndroidTest` 全部通过；最终 native build 在允许的非沙箱构建中完成 NDK strip，移除了 native 调试符号中的本机绝对路径。
- production `AndroidKeystoreDeepSeekKeyStore` instrumentation 仅在既有 `Pixel_8 / emulator-5554 / sdk_gphone64_x86_64 / API 36` 模拟器使用 synthetic Key 通过：AndroidKeyStore AES-256-GCM、12-byte Key IV、独立 12-byte health IV、142-byte test-owned `noBackupFilesDir` record、空明文 AAD health 认证、重启恢复、IV 轮换、ciphertext corruption、alias loss、alias 删除部分失败、re-entry、可见且脱敏的 record 删除失败与最终 delete 均通过；未连接物理设备。
- 取消证据：ViewModel validation Job 已 cancel-and-join，probe 释放后 write count 仍为 0；另一个 Android runtime commit-boundary Job 在持久化前取消，write count 0、record 不存在、最终 `UNCONFIGURED`；JVM 还覆盖加密准备阶段取消和 replacement commit 取消保留旧记录。
- `status()` 与 `cancelInput()` 只调用不返回 Key 明文的 AAD health 接口；JVM decrypt count 为 0；只有 `withDecryptedKey` 调用返回完整 Key 的 `decrypt()`，对应 decrypt count 为 1。
- Compose instrumentation：密码 semantics、仅末四位掩码、非空掩码截图、验证中真实取消入口，以及 save/replace/collapse/cancel/delete 后输入清空均通过；UI semantics 未出现完整 synthetic sentinel。
- source/resources/test-output/APK secret scan 未发现真实 Key、credential-bearing Authorization header、歌词正文或打包私有运行路径；最终 native-enabled app APK 为 382,081,953 bytes，AndroidTest APK 为 91,551 bytes，AndroidTest 只包含 synthetic sentinel 与测试专用 alias/record。lint 报告自身仍含构建工具生成的绝对工作目录元数据，不进入 APK 或 app runtime 输出。
- 安全/BYOK 修复提交为 `935ff92`；Brain 正式裁决为 `PARTIAL_PASS / SECURE_BYOK_COMPONENT_VERIFIED / LIVE_KEY_TEST_REQUIRED`。该裁决不是正式产品 PASS，且没有真实 DeepSeek Key、真实网络调用、真实认证、歌词匹配、物理设备或完整设备产品流证据。
- Git 脏状态继续保留：既有 `AGENTS.md` 修改、dirty `third_party/ffmpeg-kit`，以及 41 个未跟踪文件（31 个 `.emulator-test-assets` 文件、9 个 `tools/opus-mt-en-zh` 文件、1 个 `._cache_adb.exe`）；均不属于本次文档提交。

## Historical R1 implementation evidence before security rework (2026-08-10)

- R1 checkpoint: `a18574`; implementation commits: `6e77550`, `d9addce`; no push.
- 内部 Agent A: strict fallback allowlist and sanitized provider/validation/programming/cancellation failures in `CaptionEnhancementCoordinator.kt`.
- 内部 Agent B: `DeepSeekByokManagerImpl` plus `AndroidKeystoreDeepSeekKeyStore`; AES-256-GCM, Android Keystore alias, random 12-byte IV, atomic private `noBackupFilesDir` record, replacement/delete/corruption/concurrency handling.
- 内部 Agent C: injectable manager in `MainViewModel.kt` and a collapsible AI service configuration panel in `EditorScreen.kt`; password input is transient and cleared after actions/collapse; only masked suffix is exposed.
- R1 focused security/BYOK/UI tests: 16 passed. Full `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, native-equivalent `-PenableWhisperNative=true :app:assembleDebug`, and `:app:assembleDebugAndroidTest` passed.
- `:app:assembleNativeDebug` remains absent in this checkout; native-enabled Debug exercised the configured CMake route. Kotlin daemon/NDK strip permission warnings remain environmental.
- No real DeepSeek key, live probe, network call, device product-flow verification, provider lyrics retrieval, backup restore test, or APK/runtime secret scan with a real key was performed. Brain must keep `LIVE_KEY_TEST_REQUIRED`.

## 主 Agent implementation evidence (2026-08-10)

- Checkpoint commit: `bfc7751` (`test(v3): freeze caption enhancement contract`).
- Feature commit: `69b991e` (`feat(v3): implement caption enhancement contract`). No push performed.
- 内部 Agent A owns request mapping and response validation; 内部 Agent B owns coordinator/error mapping/local fallback; 内部 Agent C owns processing snapshot, atomic commit policy, editor/project state and V3 archive compatibility.
- Focused four-test command: PASS. Full `:app:testDebugUnitTest`: PASS. `:app:lintDebug`: PASS. `:app:assembleDebug`: PASS. Native-equivalent `-PenableWhisperNative=true :app:assembleDebug`: PASS. `:app:assembleDebugAndroidTest`: PASS.
- The requested `:app:assembleNativeDebug` task does not exist in this checkout; the native-enabled Debug command above exercised the configured CMake native path. Kotlin daemon and NDK strip permission warnings were environmental; fallback compilation/build completed successfully.
- No live Provider, API key, network lyrics retrieval, device run, UI/media change, model/cache change, or V2 cleanup was performed. Brain must adjudicate the stage; this is not a formal product acceptance claim.

## Confirmed product decisions

1. 本地 Whisper 先生成带 `cue_id/start_ms/end_ms/raw_english` 的分段字幕；后端不重新识别音频。
2. 完整原始字幕批次通过 API 交给 AI。AI 根据现有识别文本匹配对应歌曲和在线歌词，再按原 cue 修正英文并返回中文翻译。
3. 云端响应不得改变 cue ID 或时间区间，不得静默增删、合并、拆分或重排字幕。
4. 云端不可用、超时、服务错误或响应校验失败时，保留 Whisper 原始英文，并使用现有本地 OPUS-MT 生成中文；结果必须标记为 `LOCAL_FALLBACK`。
5. Whisper 使用单模型进程级缓存：识别结束后保留 context 3-5 分钟；空闲超时、模型切换、严重内存压力或取消后状态不安全时释放；同一 context 串行使用。
6. 字幕文本框统一调整宽度、水平位置和垂直位置；高度根据中英文、字号、行距和内边距自动计算，并动态限制最小宽度/安全边界，禁止文字裁切或覆盖。
7. 每段字幕可独立覆盖字体、字号、中英文字色、描边色、粗体、斜体和对齐；V2 样式迁移为项目默认样式，新增设置只保存差异覆盖。
8. 导入只使用系统相册/Photo Picker；导出只保存到系统相册/MediaStore，不再提供自定义位置。
9. 删除 App 顶栏，但保留系统状态栏、导航栏和 Window Insets。
10. 识别成功后不自动跳转，只显示“识别成功”；用户自行点击现有“编辑字幕”入口。
11. 当前密钥路线为 `DEVICE_DIRECT_BYOK / ANDROID_KEYSTORE_REQUIRED`：供应商 API Key 只允许由用户在设备内输入，以 Android Keystore 包装的 AES-256-GCM 密文保存；不得写入 APK、Git、文档、日志、普通 Preferences/DataStore 或项目归档。真实 Provider 认证与网络调用延后。
12. V3 最终只保留两条产品处理链路：主链路“视频导入 -> 模型识别 -> 云端匹配/修正/翻译 -> 字幕编辑确认 -> 导出”和降级链路“云端不可用 -> 本地 OPUS-MT -> 编辑确认 -> 导出”。SRT 插入及其他替代导出分支在独立 `V3-CLEAN-001` 清单和回归门禁后删除。

## Stage state machine

```text
V3-DEC-001 / PASS
  -> V3-AI-CONTRACT-001 / MATRIX_DEFINED / IN_PROGRESS
  -> BRAIN_TEST_SPEC_FROZEN
  -> TEST_FILES_ADDED
  -> RED_BASELINE_CAPTURED
  -> CHECKPOINT_CREATED
  -> PARALLEL_IMPLEMENTING
  -> SERIAL_INTEGRATION
  -> FOCUSED_TESTS_PASSED
  -> FULL_STAGE_MATRIX_PASSED
  -> READY_FOR_BRAIN
       -> PASS / COMPONENT_VERIFIED / LIVE_API_DEFERRED
       -> PARTIAL_PASS
       -> BLOCKED
       -> HUMAN_DECISION
```

## Brain test-first baseline

- Added 20 deterministic JVM contract tests across four new test files; no production Kotlin was added or modified.
- Focused command: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementContractTest" --tests "com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementCoordinatorTest" --tests "com.example.lyriccaptioner.model.CaptionBatchCommitPolicyTest" --tests "com.example.lyriccaptioner.project.ProjectArchiveV3ContractTest"`.
- Expected-red result: `:app:compileDebugUnitTestKotlin FAILED` because the new production contract, validator, coordinator, processing snapshot and commit policy do not yet exist. The compiler reported `unresolved reference` for those frozen symbols; this is the intended test-first boundary.
- Environment note: the Kotlin daemon also hit `AccessDeniedException` under the user-local daemon directory, then Gradle used its fallback compiler and emitted the expected missing-production-symbol errors. This does not convert the red baseline into an implementation defect or PASS.
- Test privacy wording excludes the necessary network wire payload and user-saved project archive; it applies to diagnostics, logs, exceptions, debug strings, telemetry and automatic snapshots.

没有独立 Review Agent/Review 窗口。Developer 不自称正式验收，只提交测试与证据；Brain 按验收矩阵决定最终状态和下一阶段。

## 历史合同实现证据（非当前 Next action）

以下原始 T01-T14 合同矩阵与实现结果作为历史证据保留，不再充当当前调度入口；当前执行面是上方 R1 安全增量矩阵。

## Historical acceptance matrix

| 类别 | `V3-AI-CONTRACT-001` 固定内容 |
|---|---|
| 主链路 | 固定的 Whisper cue 批次进入 `CaptionEnhancementService` -> 生成严格请求 -> Provider 返回歌曲匹配与逐 cue 英文修正/中文翻译 -> 本地完整校验 -> 整批原子提交；Provider 不可用或响应无效时，用原始英文调用 `LocalTranslator` -> 整批本地中文结果提交并标记来源 |
| 必须证据 | 测试先于生产实现；下方 T01-T14 全部通过；新增/修改行为有单元或契约测试；相关 JVM 测试、`testDebugUnitTest`、`lintDebug`、普通 Debug、Native Debug 与 AndroidTest 构建通过；报告测试数量、命令结果和关键状态机日志；证明无真实 API Key、完整歌词或私有媒体路径泄露 |
| 禁止事项 | 不接入真实 AI Provider、不创建或选择后端技术栈、不要求用户提供 API Key、不抓取真实在线歌词、不修改 UI、Whisper native/cache、Media3、FFmpegKit、字幕坐标、系统相册流程或 V2 清理范围；不删除 SRT/旧分支；不使用 Demo/fixed lyrics 冒充云端成功 |
| 退出状态 | 所有合同、校验、原子提交、云端失败映射、本地回退、取消和日志隐私测试通过，完整构建矩阵通过，且 Git 只包含本阶段合同/测试/三份活动文档和已授权治理变更时，允许 `PASS / COMPONENT_VERIFIED / LIVE_API_DEFERRED` |
| 未完成状态 | 合同实现和测试完成但完整构建未通过：`PARTIAL_PASS / BUILD_REQUIRED`；核心状态机或原子性测试失败：`BLOCKED`；歌词来源/授权仍可进入 `HUMAN_DECISION`；本历史矩阵原有的 Provider/后端/密钥方案未决描述已被后续 DeepSeek + `DEVICE_DIRECT_BYOK / ANDROID_KEYSTORE_REQUIRED` 决策取代；没有真实 Key 只保持 `LIVE_API_DEFERRED`，不阻止本合同阶段 PASS |

## Test cases to write before production code

| ID | 测试内容 | 必须断言 |
|---|---|---|
| `T01` | 请求映射 | `job_id/schema_version` 合法，cue ID、顺序、时间区间和原始英文完整保留 |
| `T02` | 云端成功 | 每个请求 cue 恰好获得一条修正英文和中文，来源为 `CLOUD_AI`，整批一次提交 |
| `T03` | 歌曲匹配信息 | 歌名、歌手、匹配置信度和来源可选且受长度/枚举约束；无可靠匹配时不得伪造已确认歌曲 |
| `T04` | cue 集合异常 | 缺失、额外或重复 cue ID 时拒绝整批响应，不部分覆盖 |
| `T05` | 时间轴被修改 | 任一 `start_ms/end_ms` 与请求不同即拒绝整批响应 |
| `T06` | 字段与大小限制 | 空英文、非法 Unicode/枚举、超长文本、错误 schema/job ID 被拒绝 |
| `T07` | 云端不可用 | 离线、连接失败、超时、可重试 5xx 和无效 Schema 进入本地翻译回退 |
| `T08` | 本地回退输入 | OPUS-MT 使用原始 Whisper 英文；输出来源为 `LOCAL_FALLBACK`，不声称英文已被 AI 修正 |
| `T09` | 云端成功不回退 | 有效云端结果提交后不得再调用本地 translator |
| `T10` | 用户取消 | 取消进入 `CANCELLED`，不自动启动本地回退，不覆盖当前字幕 |
| `T11` | 本地回退失败 | 保留完整原始英文和既有项目状态，不提交半批中文，返回可恢复错误 |
| `T12` | 原子性 | 云端或本地任一 cue 失败时，整批不提交；旧导出失效只在完整新字幕提交后触发 |
| `T13` | 来源与状态持久化 | `CLOUD_AI/LOCAL_FALLBACK/RAW_ASR`、处理版本和错误状态能保存恢复，不混淆来源 |
| `T14` | 隐私与密钥 | 诊断日志、异常、debug string、遥测事件和自动测试快照不包含 API Key、完整歌词批次或用户私有媒体路径；实际网络 wire payload 必须携带原始英文、用户主动保存的项目归档允许持久化字幕，二者不属于该日志隐私断言；仅允许非敏感 API 模式配置持久化 |

## Authorized implementation scope

- 新增或调整 Provider-neutral 的请求/响应 DTO、`CaptionEnhancementService` 接口、响应校验器、结果来源枚举、状态机和错误映射。
- 使用 fake Provider 与 fake/local translator 编写确定性测试。
- 在不接真实网络的前提下实现云端成功与本地回退的 orchestration 边界。
- 必要时对 `CaptionCue`/项目状态增加最小兼容字段，但不得在本阶段重做 UI 或迁移全部历史项目结构。
- 先增加测试和失败基线，再实现生产代码；普通失败按 `AGENTS.md` 自主 Debug。

## API key boundary

- 当前已确认路线为 `DEVICE_DIRECT_BYOK / ANDROID_KEYSTORE_REQUIRED`。
- R1 只实现用户输入、Android Keystore 包装的 AES-256-GCM 本地密文、最小配置 UI 与短生命周期解密边界；不接入真实 Provider 网络调用。
- 真实认证、歌词匹配、完整云端链路与真实用户 Key 测试留给后续明确授权阶段。

## Final report format

1. 候选状态及证据等级，不自称正式验收；
2. 验收矩阵五项逐条结果；
3. T01-T14 及完整构建矩阵的实际命令、数量和结果；
4. 实现文件、测试文件和明确未修改范围；
5. 云端成功、本地回退、取消、原子失败的状态转换证据；
6. API Key/歌词/路径未泄露检查；
7. checkpoint、功能 Commit、是否 push 和最终 Git 状态；
8. `LIVE_API_DEFERRED` 的剩余条件，以及下一阶段建议。
