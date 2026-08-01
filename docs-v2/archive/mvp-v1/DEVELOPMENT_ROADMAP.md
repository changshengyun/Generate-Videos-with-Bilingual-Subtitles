# LyricCaptioner MVP V1 开发路线（归档）

> 本文件是 MVP V1 在 `PERSONAL_USE_ACCEPTED` 时的只读快照。当前开发入口为 `docs/DEVELOPMENT_ROADMAP.md`。

## 一、产品方向

在 Android 上处理不超过 5 分钟的本地视频，支持 SRT、歌词或人工字幕，完成双语编辑、预览和 MP4 导出。编译、组件验证、模拟器验证、目标设备验证和产品接受必须分开记录。

## 二、路线原则

- Kotlin、Compose、SAF、Media3 预览和现有 `CaptionPipeline` 保持分层。
- 每个开发批次只交付一个完整模块，并明确允许范围和停止条件。
- 依赖、AAR、ABI、NDK、原生代码、设备操作和全局环境变更需要单独授权。
- `docs-BK` 只作历史证据，不是当前执行入口。

## 三、模块顺序

| 批次 | 模块 | 目标 | 状态 |
|---|---|---|---|
| `EXP-001` | 可靠字幕烧录导出 | 统一导出契约、失败清理、输出校验与模拟器闭环 | `SIMULATOR_VERIFIED` |
| `ARC-001` | 项目归档与 SAF 恢复 | 保存、重启恢复、权限分类、失效媒体保留与重绑 | `SIMULATOR_VERIFIED` |
| `ASR-001` | 真实本地语音识别 | 组件化真实 Whisper JNI 路由、英文时间戳字幕与 ARM64 设备验收 | `ARM64_DEVICE_VERIFIED` |
| `TRN-001` | 翻译与人工校正 | 验证翻译准备、离线复用、确认与双语一致性 | `COMPONENT_VERIFIED` |
| `E2E-001` | 端到端设备与性能 | 建立个人 ARM64 设备的 5 分钟完整流程与资源基线 | `ARM64_E2E_VERIFIED` |
| `REL-001` | 兼容性与发布合规 | API、ABI、依赖许可、SBOM 和发布检查 | 未来公开分发时启用 |

## 四、ARC-001 已交付内容

- 新增 `ProjectRepository` 与 Android SAF 实现，业务层不再直接承担归档读写。
- 归档格式升级为 v2，保留完整字幕和导出字段，并兼容 v1。
- 视频 URI 通过 `OpenDocument` 获取并尝试持久化读取权限。
- 媒体访问结果显式区分持久权限、会话权限、提供器不支持和不可用。
- 重启恢复、失效媒体提示、字幕/样式保留和视频重绑已在既有 Pixel_8 模拟器上验证。
- 归档格式、仓储契约和全量 JVM 回归测试已通过；Debug APK 已组装并安装验证。

## 五、个人自用稳定版边界

- 不把 `SIMULATOR_VERIFIED` 写成真实 ARM64 设备验证或产品发布接受。
- 当前版本仅冻结为个人自用，不视为公开发布接受。
- 不卸载应用、清除数据、重置/删除 AVD，不修改 Git 历史或全局工具链。

## 六、ASR-001 验收边界

- 普通 Debug 允许继续使用明确标注的 Demo 路由，但不得把 Demo 结果标记为 Local。
- Native Debug 必须包含 `arm64-v8a` 的 `liblyriccaptioner_whisper.so`，并通过 JNI 日志证明实际调用。
- ASR 只生成英文字幕及有效、有序时间戳；翻译属于后续 `TRN-001`。
- 完成状态：无真机/模型时为 `COMPONENT_VERIFIED / HUMAN_DECISION`；真实 ARM64 识别闭环通过时为 `ARM64_DEVICE_VERIFIED`；关键根因无法处理时为 `BLOCKED`。

## 七、ASR-001 本轮结果

- 交付了统一 ASR 模块，覆盖模型状态、音频提取、Whisper JNI、英文 cue 转换、取消和临时音频清理。
- 51 项全量 JVM 单测通过；普通 Debug 和 `enableWhisperNative` Native Debug 均通过，Native APK 包含 arm64-v8a Whisper JNI。
- Local、Demo、Unavailable 路由均有明确状态和原因；本轮没有把 Demo 结果作为 Local 成功报告。
- 官方 `ggml-base.bin` 已下载到忽略目录并通过 147,951,465 字节与 SHA-1 `465707469ff3a37a2b9b8d8f89f2f99de7299dac` 校验；模型经 App 的 SAF 入口导入，未直接写入私有目录。
- ARM64 设备 `fcf4b0cb`（`25098PN5AC`，Android 16）显示 `ASR: LOCAL`、`Model: ready`、`JNI: ready`，真实 JNI transcribe 对固定媒体生成 3 条非空英文字幕。
- 设备导出的 SRT 时间戳为 `0-7360 ms`、`7360-11440 ms`、`11440-18640 ms`，全部有效且有序；成功、失败和取消路径的临时 WAV 均已清理。
- 修复了 JNI 将 `detect_language=true` 误用为转写参数的问题；该标志实际会在语言检测后退出并返回 0 段。修复后 ASR-001 达到 `ARM64_DEVICE_VERIFIED`。

## 八、TRN-001 验收结果

- 复用现有 `LocalTranslator` 与 ML Kit 英中翻译，不创建平行翻译架构。
- 模型状态显式区分 `NEEDS_DOWNLOAD`、`PREPARING`、`READY`、`FAILED`；首次准备允许 ML Kit 官方 API 联网下载，后续必须支持离线复用。
- 批量翻译只填充英文非空且中文为空的 cue，整批成功后一次提交，不覆盖人工中文；失败或取消不得写入部分结果。
- 英文变更必须清除旧中文，英文、中文或时间变更必须取消确认；只有双语均非空时允许确认。
- v2 归档继续保存中文与确认状态，并保持 v1/v2 读取兼容。
- 新增统一 `TranslationModule`、模型状态机、整批原子提交、取消与重试；英文修改清除旧中文，文本或时间修改取消确认，双语门禁控制确认。
- 68 项全量 JVM 单测通过，其中 TRN-001 新增 17 项；普通 Debug 和 `enableWhisperNative` Native Debug 均通过。
- ARM64 设备首次真实准备从 `NEEDS_DOWNLOAD` 经 `PREPARING` 到 `READY`，耗时 11.857 秒；3 条 Local ASR 英文 cue 均生成非空中文，ID、顺序和 `0-7360 / 7360-11440 / 11440-18640 ms` 时间戳保持。
- 人工修改第一条中文并确认后，项目保存、force-stop、重启和 Open Project 恢复 3 条双语 cue、修改文本与确认状态。
- 关闭 Wi-Fi 和双卡移动数据后，App 重启仍显示模型 `READY`；固定英文 cue 在 253 ms 内离线翻译成功，随后恢复原网络状态。
- 30 条批次在真机翻译阶段取消后零写入，直接重试在 720 ms 内完成 30/30；下载准备失败、翻译中途失败和取消后的原子性由单测覆盖。
- 因首次模型已真实下载，且边界禁止删除 ML Kit 模型或注入伪失败，真机无法再真实重现下载失败；有效超长输入也未触发真实翻译失败。未将这些场景伪报为设备通过，最终状态为 `COMPONENT_VERIFIED`。

## 九、E2E-001 当前边界

- 用户已接受 TRN-001 的单元测试失败证据并放行下一阶段；TRN-001 保持 `COMPONENT_VERIFIED`，不再补真机下载或翻译失败注入。
- 在个人 ARM64 设备 `fcf4b0cb / 25098PN5AC` 上只执行一次最终约 5 分钟完整成功验收，流程为视频导入、Local ASR、离线翻译、人工确认、预览、保存重启恢复、字幕烧录导出和 Media3 回放。
- 只建立素材、阶段耗时、总耗时、峰值 RSS、输出大小、温度和存储变化基线，不预设性能阈值。
- 不扩展多厂商矩阵，不重复既有失败/取消注入，不修改 FFmpeg AAR、Whisper、ML Kit 依赖、Media3 或全局工具链。
- 当前状态：`E2E-001 / ARM64_E2E_VERIFIED`。
# E2E-001 revision 2 evidence (2026-07-18)

- Correctness gate completed before device continuation: explicit video import mode, bilingual SRT confirmation, derived-output invalidation, and approved Whisper model integrity/atomic import.
- Regression gate: 77 unit tests passed; lintDebug, ordinary Debug, and Native Debug builds passed.
- ARM64 device fcf4b0cb / 25098PN5AC was updated once and reported ASR: LOCAL with Model/JNI ready and cached translation READY.
- The final revision-2 five-minute E2E was not completed because the MIUI picker/Media3 UI became non-idle (`could not get idle state`) after media import. Existing prior accepted output evidence is retained but is not counted as a new revision-2 export.
- Gate result: PARTIAL_PASS; no Git commit or GitHub sync.

# E2E-001 revision 3 result (2026-07-18)

- 修订 3 不改变产品代码，采用 adb 截图/定向输入、阶段 logcat、dumpsys 和文件/媒体元数据推进；不依赖 uiautomator idle 或 dump 成功。MIUI 残留文件选择器仅关闭其进程，未清除 App 数据。
- 设备验收 `fcf4b0cb / 25098PN5AC`，1220x2656 portrait rotation 0。固定五分钟素材时长 299.247733 秒，SHA-256 `FFBF914C7F46A67CA50488285899B86F2A2E928E58BC5999A0A26FD43059933F`。
- 本轮真实闭环完成：SAF 导入、Local Whisper JNI ASR、缓存模型离线翻译、人工编辑确认、预览、项目保存、force-stop/重启/Open Project 恢复、字幕烧录和 Media3 回放。日志确认 `asr_completed mode=LOCAL captionCount=48`、翻译 48/48 committed、`project_load_completed captionCount=48` 和 FFmpeg `returnCode=0`。
- 人工确认与一致性：保存归档 48 条 cue；人工修改后的中文和 `confirmed=true` 重启后恢复，其他自动翻译 cue 仍未确认；时间戳、ID、顺序未改变。
- 新结果必须与旧输出区分：`lyric-captioner-output (2).mp4`，设备时间戳 16:43，46,277,905 字节，SHA-256 `D309B177569F638D7427D34ED96F2F9A4CDB8D1EF5EAB0527E2D130B241DD789`，H.264/AAC，592x1280，299.791667 秒。10 秒抽帧可见双语字幕；App 预览和 Media3 初始化/回放通过。旧 46,279,071 字节文件未计入。
- 基线：ASR 52.044 秒，离线翻译 1.573 秒，FFmpeg 回调约 9.375 秒；记录墙钟间隔约 33 分 40.204 秒。采样峰值 RSS 418,604 kB（ASR），导出阶段 335,684 kB；结束温度 battery 32.7 C、CPU0 45.9 C、CPU7 44.9 C、skin 33.35 C；可用存储记录为 137,660,528 -> 137,611,676 kB。FFmpeg 工作目录为空。
- 离线阶段 Wi-Fi/mobile_data 为 0/0，结束恢复为 1/0；未删除 Whisper 或 ML Kit 模型。既有 77 单测、lintDebug、普通 Debug 与 Native Debug 证据沿用，未因无代码变化重复。
- Gate result: `ARM64_E2E_VERIFIED`; no Git commit or GitHub sync.

## 十、CLS-001 个人自用稳定版收口

- 当前状态：`PERSONAL_USE_ACCEPTED`。E2E-001 的 `ARM64_E2E_VERIFIED` 证据冻结为个人自用稳定基线，不扩展为公开发布合规结论。
- 差异审计只保留 E2E revision 2 正确性修复、对应测试和三个活动文档；`third_party/ffmpeg-kit` 既有 dirty 状态保持不变，未修改、未暂存、未清理。
- Native Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，SHA-256 `BA54697CD204D8334A5DD7484E4EC517D90EB5E144AD1A60F3182E213B225DBE`，applicationId `com.example.lyriccaptioner`，versionCode `1`，versionName `0.1.0`，ABI 为 `arm64-v8a` 与 `x86_64`。
- 本地与设备已安装 APK 的完整 SHA-256 及签名证书 SHA-256 `a51e9235816b2f34195a459764ff7155958e3a5c503aa782d125a000c0817528` 一致；未重新安装，未输出或复制私钥。
- `git diff --check` 通过；既有 77 项单测、lintDebug、普通 Debug 和 Native Debug 证据沿用，不重复执行。
- 模型、测试视频、输出 MP4、日志和构建产物均在已忽略的证据/生成目录中，未进入 Git 未跟踪集合；未发现仓库外密钥候选。REL-001 仅在未来公开分发时启用。
- 收口判断：`READY_FOR_CHECKPOINT`；不提交 Git，不同步 GitHub。
