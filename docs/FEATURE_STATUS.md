# LyricCaptioner 功能证据状态

每项只使用一个最高且有证据支持的状态。`Accepted` 必须满足目标设备和正式验收阈值；本轮没有任何功能达到 Accepted。

| 功能 | 唯一状态 | 证据与限制 |
|---|---|---|
| Android 工程与普通 Debug APK | Build verified | 本轮 `test assembleDebug` 成功 |
| Native Whisper APK 双 ABI 打包 | Build verified | native 构建成功；APK 含 ARM64/x86_64 `.so`，未运行推理 |
| 视频导入与 5 分钟限制 | Code exists | UI/VM 代码存在；未在目标设备验证 URI、时长与错误路径 |
| 视频播放预览 | Code exists | Media3 ExoPlayer 调用存在；只有历史截图，当前提交设备集成未证明 |
| SRT 解析与写出 | Component verified | JUnit 组件测试通过；设备 SAF 读写未集成验证 |
| 字幕时间轴查询与边界编辑 | Component verified | 对应 JUnit 测试通过 |
| PCM 混音、重采样、WAV 写入 | Component verified | 对应 JUnit 测试通过；不等于设备媒体解码已验证 |
| 歌词候选匹配 | Component verified | `LyricLineAligner` 测试通过；不是自动原歌词检索 |
| 粘贴歌词平均建轴 | Code exists | 真实代码按总时长均分；未做设备集成测试 |
| 项目归档序列化/反序列化 | Component verified | JUnit 测试通过；跨重启 URI 权限未验证 |
| 字幕文字编辑、确认、删除、样式 | Code exists | Compose/VM 路径存在；未做 UI/设备测试 |
| Demo 英文识别 | Simulated | 固定三条字幕、固定时间与延迟，不读取音频 |
| Demo 中文翻译 | Simulated | 固定映射，其他内容返回“待翻译” |
| 上下文自动纠错 | Simulated | `DemoCaptionCorrector` 原样返回；Local 管线也使用它 |
| Android 音频抽取 | Code exists | MediaExtractor/MediaCodec 实现存在；无目标设备解码证据 |
| Whisper 真实英文识别 | Blocked | JNI 构建通过，但缺连接的 ARM64 真机、实际模型和真实样本运行证据 |
| ML Kit 英译中 | Code exists | 正式调用存在；首次下载、翻译结果和离线复用未验证 |
| 双语字幕实时预览 | Code exists | Compose overlay 与时间轴调用存在；Surface 层级/布局未设备验证 |
| Media3 烧录字幕 MP4 | Failed | 正式导出和输出完整性检查存在；历史指定模拟器重复报 `Video frame processing error`，目标文件为 0 bytes；本轮无设备，未重新复现 |
| SRT 文件设备导入/导出 | Code exists | SAF 代码存在；设备文件提供方兼容未验证 |
| 项目文件设备保存/恢复 | Code exists | SAF 代码存在；重启后恢复未验证 |
| 失败清理与重试 | Code exists | WAV/临时 MP4 有清理逻辑；取消、低存储和异常矩阵未验证 |
| 5 分钟端到端性能与资源 | Not started | 无正式耗时、峰值内存、温升或电量基线 |
| API 26 最低设备兼容 | Not started | 只有 Gradle 配置，无 API 26 目标设备结果 |
| 发布许可与分发验收 | Not started | 未见完整 SBOM/许可审计和 release 验收 |
| 指定模拟器端到端闭环 | Failed | 导入和双语预览有历史部分证据，烧录导出重复失败 |
| 真机端到端产品闭环 | Blocked | 本轮 ADB 设备列表为空 |

## 已真实验收的功能

无。当前最高证据等级为 `Component verified` 或 `Build verified`，均未达到目标设备验收和 `Accepted`。
