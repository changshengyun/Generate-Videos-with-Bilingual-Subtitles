# LyricCaptioner 项目简报

## 产品目标

在 Android 设备上处理不超过 5 分钟的个人短视频：导入视频，通过真实本地 ASR 或 SRT/歌词/人工方式得到英文字幕，生成或编辑中文字幕，校正时间与样式，预览后导出烧录双语字幕的可播放 MP4。

## 硬性边界

- Android-only，当前配置 `minSdk 26`、`targetSdk 35`；核心媒体处理本地优先。
- 不保证 100% 还原歌曲原歌词；低置信度结果必须可人工修正。
- 核心验收物是 App 在约定 Android 运行环境中实际导出的非空、可播放、有音视频轨且字幕可见的 MP4。
- Demo、固定返回值、桌面工具生成的样例文件、代码存在、编译或 APK 打包均不能替代产品验收。
- 本轮仅恢复真实状态；不开发、不修 Bug、不换栈、不安装大型依赖、不改系统环境。

## 当前阶段

`ARCHITECTURE_REVIEW`。架构分类为 **C：存在明确实现故障，但架构尚未被否定**。

当前集成路线是 Kotlin/Compose + Media3 + Android MediaCodec + 可选 whisper.cpp JNI + ML Kit。Media3 字幕烧录在历史指定 Pixel_8 API 36.1 x86_64 模拟器上重复失败，错误为 `Video frame processing error`；本轮无连接设备，无法获取新 logcat。FFmpegKit 仅是未完成、未集成的历史实验，不是当前技术栈。

## 当前最小真实能力

SRT、字幕时间轴、歌词匹配、PCM/WAV 和项目归档已有组件测试；普通与 native Debug APK 可构建。默认运行时在模型或 JNI 未就绪时进入固定 Demo 生成路径。任何设备级编辑、真实 ASR、ML Kit、SAF 恢复和 MP4 导出均未达到正式验收。
