# Next Task

Status: Proposed

## 推荐任务

使用 `evidence-first-debugging` 对当前 Media3 字幕烧录失败做一次只读证据复现：在历史相同的 Pixel_8 API 36.1 x86_64 模拟器上安装当前 native Debug APK，导入固定的 `deliverables/emulator-h264-test.mp4` 与 `deliverables/emulator-bilingual-test.srt`，执行一次 MP4 导出，记录完整 logcat、Transformer 异常链、codec/Surface 信息、输入文件 SHA-256、输出文件大小与时间戳。

## 范围边界

- 只复现和冻结证据；不修 Bug、不改代码、不升级依赖、不继续 FFmpegKit、不改变架构。
- 如果模拟器仍不可用，记录设备阻断并停止，不启动替代设备或环境改造。
- 产出的诊断日志仅在用户批准任务后生成；不得记录字幕正文或媒体内容。

## 完成条件

- 成功得到一次可重复结果，或明确记录设备不可用阻断。
- 将最早失败边界标为 application logic、Media3/codec、Surface/OpenGL、input 或 environment 中的一个/多个，并列出仍未知项。
- 不提出修复，返回用户审批后续动作。

## 批准要求

未经用户明确批准，本任务保持 `Status: Proposed`，不得执行。
