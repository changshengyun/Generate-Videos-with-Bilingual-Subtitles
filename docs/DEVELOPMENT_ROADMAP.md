# LyricCaptioner 长期开发路线

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
| `E2E-001` | 端到端设备与性能 | 建立设备矩阵和 5 分钟资源基线 | 待排期 |
| `REL-001` | 兼容性与发布合规 | API、ABI、依赖许可、SBOM 和发布检查 | 待排期 |

## 四、ARC-001 已交付内容

- 新增 `ProjectRepository` 与 Android SAF 实现，业务层不再直接承担归档读写。
- 归档格式升级为 v2，保留完整字幕和导出字段，并兼容 v1。
- 视频 URI 通过 `OpenDocument` 获取并尝试持久化读取权限。
- 媒体访问结果显式区分持久权限、会话权限、提供器不支持和不可用。
- 重启恢复、失效媒体提示、字幕/样式保留和视频重绑已在既有 Pixel_8 模拟器上验证。
- 归档格式、仓储契约和全量 JVM 回归测试已通过；Debug APK 已组装并安装验证。

## 五、当前禁止事项

- 不把 `SIMULATOR_VERIFIED` 写成真实 ARM64 设备验证或产品发布接受。
- 未取得新任务授权前，不扩展到真实设备、Whisper/ML Kit、导出路线或性能基线。
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
