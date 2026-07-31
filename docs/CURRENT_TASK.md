# 当前任务：V2-ASR-002

## 状态

- Revision: `1`
- Status: `FIXTURE_REQUIRED`
- Owner: `Development Agent`
- Product Gate: `V2_ASR_QUALITY_DATA_REQUIRED`
- Verification Mode: `SIMULATOR_ONLY_TEMPORARY`

## 本轮结论（2026-07-31）

- 已按活动文档进入下一阶段 `V2-ASR-002`：英文歌曲和英文单词识别提质。
- 仓库当前没有合格的英文歌曲质量评测 fixture：缺少至少 3 段 30-60 秒英文歌曲音频、人工准确歌词、时间范围/标注和统一采样条件。
- 因缺少人工准确参考歌词，本轮不得运行主观模型选择，也不得把 beep/合成视频或普通短句测试当作歌曲识别质量证据。
- 当前默认模型保持 `ggml-small.en-q5_1.bin`；未切换识别模型。

## 已完成的可交付项

- 保留现有 V2-ASR 模型校验、目录、原子导入和 `tools/asr-evaluate.py`。
- 修正 ASR 评测工具的英文引号归一化。
- 评测工具现在只接受已批准本地 Whisper 模型名：
  - `ggml-base.bin`
  - `ggml-base.en.bin`
  - `ggml-small.en-q5_1.bin`
- 评测工具会拒绝：
  - 未批准模型名；
  - 少于 3 段 fixture；
  - 非 30-60 秒 fixture；
  - 缺少准确 reference 的 fixture；
  - 多模型之间 fixture 名称、reference 或时长不一致；
  - 空 cue、无效时间戳、崩溃、空结果和 Demo fallback 会进入指标输出，不会被静默吞掉。
- 新增 `tools/asr_evaluate_test.py`，使用合成非歌曲文本验证评测工具契约；该测试不能作为模型质量样本。
- `.gitignore` 增加本地 ASR 质量 fixture 与评测输出目录：
  - `tools/asr-fixtures/`
  - `tools/asr-results/`

## 验证

- `python tools\asr_evaluate_test.py`：6 tests passed。
- `.\gradlew.bat testDebugUnitTest --tests "com.example.lyriccaptioner.processing.WhisperModelValidatorTest" --tests "com.example.lyriccaptioner.processing.WhisperModelImporterTest"`：BUILD SUCCESSFUL。

## 人工复核视频生成记录（2026-07-31）

- 在 `SIMULATOR_ONLY_TEMPORARY` 下使用 `emulator-5554` 和当前 `ggml-small.en-q5_1.bin` 生成了 3 个仅供人工观看的 review MP4。
- 三个源视频位于 `D:\DevEnv\Projects\sorce\`，本轮未修改或覆盖源视频。
- 模拟器 Wi-Fi 和 data 均已关闭；产品包无 Demo fallback 路径，运行时使用本地 Whisper JNI 和本地 OPUS-MT。
- 生成链路：本地视频输入 -> Local Whisper -> OPUS-MT -> FFmpegKit 导出 -> Media3 播放。
- 输出文件：
  - `D:\DevEnv\Projects\sorce\5e4c3cd7073a9e9b03df1fbf8af6d928-small-en-review.mp4`
  - `D:\DevEnv\Projects\sorce\6101d9b51a973fcc6bc8432d87851280-small-en-review.mp4`
  - `D:\DevEnv\Projects\sorce\f1764157e6fccc410443c5cbefaecfac-small-en-review.mp4`
- 这些视频没有人工准确参考歌词，不能生成 WER/CER，不能作为模型质量提升证明。
- 当前仍等待用户对三个输出视频进行人工复核；任务状态保持 `FIXTURE_REQUIRED`。

## 下一步所需人工输入

进入真正的模型质量比较前，必须提供或确认以下本地私有 fixture，放入已忽略目录，不提交 Git：

1. 至少 3 段 30-60 秒英文歌曲音频。
2. 每段对应人工准确英文歌词。
3. 每段音频的起止时间、裁剪方式和统一预处理条件。
4. 对每个模型在同一设备/同一预处理条件下产生的 cue JSON、耗时、峰值 RSS、温度、崩溃/空结果/Demo fallback 标记。

## 边界

- 不提交模型、歌曲、歌词、日志、APK 或生成物。
- 不读取 `docs/archive/`、`docs-BK/` 或旧交接文档。
- 不修改 FFmpegKit、Media3、导出路线或 `third_party/ffmpeg-kit`。
- 不切换识别模型；只有候选模型在固定数据集上明确优于当前模型时，才允许进入安全切换讨论。
- 不提交 Git，不同步 GitHub。

最终状态：`FIXTURE_REQUIRED`。
