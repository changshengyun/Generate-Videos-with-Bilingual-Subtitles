# LyricCaptioner V3 开发阶段总结

**版本性质**：V3 阶段开发归档 / Development Release Candidate

**日期**：2026-08-24

**分支**：`migration/lyric-captioner-history`

**ASR 修复基线提交**：`9c37b77`
**总体状态**：`PARTIAL_PASS / RELEASE_CANDIDATE_NOT_FULL_E2E`

## 1. 版本定位

本文件是 LyricCaptioner V3 阶段开发的正式总结，不把“代码已实现”直接等同于“完整产品验收通过”。报告采用版本说明常见的变更分类、证据矩阵和已知限制结构：用户可见的阶段交付集中记录在本文件，逐次调试证据保留在 [`docs/debug/DEBUG_REPORT.md`](../../debug/DEBUG_REPORT.md)。

V3 当前可以作为开发阶段候选版本归档，但还不能标记为完整生产版 PASS。原因是最终主链路“系统相册导入 → 真实 App ASR → AI 增强或本地回退 → 编辑 → 导出 → 回放”的目标 ARM64 真机证据尚未全部闭合。

## 2. V3 阶段目标

- 保留 Android 本地视频编辑、字幕时间轴、Media3 预览和 FFmpegKit 导出基础。
- 统一系统相册导入与导出路径。
- 建立字幕布局、逐 cue 样式、预览和导出的共享坐标语义。
- 建立整批歌词检索、歌曲候选验证、英文修正和中文翻译链路。
- 保留本地翻译回退与 BYOK 安全边界。
- 完成 Whisper Native 真机诊断，并修复 Android ARM64 下 `whisper_full()` stall。

## 3. 阶段交付

### 3.1 产品与架构

- V3 产品边界、技术路线和阶段门禁已冻结。
- ASR、媒体、编辑器、UI、AI 增强和项目归档分别保持模块边界。
- 当前文档明确区分 `COMPONENT_VERIFIED`、`DEVICE_VERIFIED` 和正式端到端验收。

### 3.2 媒体与编辑器

- 使用系统 Photo Picker/MediaStore 作为默认媒体入口。
- 字幕编辑支持逐 cue 文本与样式覆盖，项目归档保留版本兼容路径。
- 预览与 FFmpegKit 导出共享字幕布局和渲染解析规则。
- 取消、恢复、导出目标和源文件安全规则保留在现有产品边界内。

### 3.3 AI 歌词增强

- 采用“整批英文 cue → 歌曲候选 → 完整歌词检索 → 多 cue 验证 → 整首上下文翻译”的路线。
- 保留 cue ID、顺序和时间戳，增强结果通过结构校验后原子提交。
- DeepSeek 失败时保留英文并使用本地 OPUS-MT 回退；未确认的歌曲不得被标记为已确认。
- 当前 AI 阶段已有结构、合同和固定 SRT 样本证据，但不等同于完整视频端到端验收。

### 3.4 ASR 真机修复

根因已在同一 base 模型、同一固定 WAV、同一 ARM64 设备上完成对照：

- `no_context=true`：fresh context、无复用，`whisper_full=0`，`10,319 ms`，9 个 segment。
- `no_context=false`：其余条件不变，超过 `600,000 ms` 未返回。
- Native：whisper.cpp `1.9.1`，commit `f049fff`。
- 模型：`ggml-base.en.bin`，SHA-256 `a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002`。
- 固定 WAV：SHA-256 `cd76904fc36ac08de32da432a4a6c14c48bf34f267c082cb74d6a1ec5c692d1d`。

最小修复位于 [`whisper_jni.cpp`](../../../app/src/main/cpp/whisper_jni.cpp)：

```cpp
// Disable context reuse because no_context=false was verified to stall
// whisper_full on Android ARM64 with the fixed base/WAV diagnostic input.
params.no_context = true;
```

未修改 whisper.cpp 版本、模型、AudioExtractor、SessionRuntime、UI、翻译和字幕处理。

## 4. 验收证据矩阵

| 领域 | 证据 | 结果 | 等级 |
|---|---|---|---|
| JVM 单元测试 | `./gradlew.bat testDebugUnitTest` | 335 tests，0 failures，0 errors，0 skipped | `COMPONENT_VERIFIED` |
| Native Debug 构建 | `./gradlew.bat assembleDebug -PenableWhisperNative=true` | `BUILD SUCCESSFUL` | `BUILD_VERIFIED` |
| Native AndroidTest 构建 | `./gradlew.bat assembleDebugAndroidTest -PenableWhisperNative=true` | `BUILD SUCCESSFUL` | `BUILD_VERIFIED` |
| ASR 固定输入诊断 | ARM64 `fcf4b0cb`，base/WAV 固定 hash | `whisper_full=0`，9 segments，首段与末段均有文本 | `DEVICE_VERIFIED` |
| `no_context=false` 对照 | 同设备、同模型、同 WAV，>10 分钟观察窗 | 未返回，确认 Native stall | `DEVICE_VERIFIED` |
| 真实 App base 入口 | 新 APK 尚未重新安装 | 未取得生产入口结果 | `PENDING_REINSTALL` |
| 完整 V3 E2E | 导入、ASR、AI、编辑、导出、回放全链路 | 尚未形成一条完整可复核证据链 | `NOT_CLAIMED` |

### ASR 运行结果

```text
whisper_full return: 0
inference_time: 10319 ms
segment_count: 9
Segment0: I have to live without you
LastSegment: (upbeat music)
```

末段音乐标记来自 Native decoder 对高 no-speech 窗口的原始输出，不是 Kotlin 字幕过滤新增的文本。

## 5. 构建产物

- APK：`app/build/outputs/apk/debug/app-debug.apk`
- APK 大小：386,360,609 bytes
- APK SHA-256：`587e86f8c9ffef14089625885587ab0df1273b397813cabf4c35092a7787d917`
- 构建参数：`-PenableWhisperNative=true`
- 构建日期：2026-08-24；当前工作树构建通过。

## 6. 已知限制与未完成事项

1. 当前真实 App base 入口仍需安装本次构建产物后，在 `fcf4b0cb` 上重新执行一次；报告不把诊断入口结果冒充生产入口结果。
2. 不运行 small，不扩展 Context reuse 对照，不调整 Whisper 参数，不修改 ASR 架构。
3. V3 完整真机 E2E 尚未闭合，尤其是 AI 增强、逐 cue 编辑、导出和 Media3 回放的同一次任务证据。
4. V3-AI-001 的歌词质量阈值仍需用户认可的固定参考样本建立；当前结构验收不能替代语言质量验收。
5. `compileSdk = 36` 与当前 Android Gradle Plugin 的兼容性警告仍存在，但本次构建成功；该问题不在本阶段修复范围内。

## 7. 复现与回滚

ASR 诊断复现入口、设备文件和字段定义见 [`docs/debug/DEBUG_REPORT.md`](../../debug/DEBUG_REPORT.md)。

本阶段 ASR 修复可以通过回退提交 `9c37b77` 恢复到修复前状态；回退只应在明确需要重现旧 stall 行为时执行，不应清理或重置工作树中的其他 V3 修改。

## 8. 提交记录

- ASR 最小修复：`9c37b77 fix(asr): enable no_context for whisper android inference`
- 本阶段总结：本文件及活动文档将随本次 V3 阶段提交绑定。
- 未提交的编辑器、媒体、AI、测试资产和本地环境文件不应被本总结提交隐式覆盖或清理。

## 9. 下一门禁

安装当前构建产物，在真实 App 中选择同一个 base 模型并执行一次字幕识别；若获得 `whisper_full=0`、多段英文字幕和可复核设备证据，再将生产入口状态从 `PENDING_REINSTALL` 提升。之后才进入 `V3-E2E-003` 的完整真机验收。

## 10. 文档参考

- [GitHub Docs: About releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)：release 应绑定具体 Git 历史点，并可包含说明和可下载产物。
- [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/)：采用面向读者的整理后变更分类，不把完整 Git log 当作变更说明。
