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
| `ASR-001` | 真实本地语音识别 | 组件化真实 Whisper JNI 路由、英文时间戳字幕与 ARM64 设备验收 | `COMPONENT_VERIFIED / HUMAN_DECISION` |
| `TRN-001` | 翻译与人工校正 | 验证翻译准备、离线复用、确认与双语一致性 | 待排期 |
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
- 当前没有授权 ARM64 真机和兼容模型，未完成真实 Local 识别，因此保持 `COMPONENT_VERIFIED / HUMAN_DECISION`，不宣称 ARM64_DEVICE_VERIFIED。
