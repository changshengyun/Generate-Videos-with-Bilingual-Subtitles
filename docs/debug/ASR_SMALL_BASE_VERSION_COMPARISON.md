# ASR small/base 两版本对比分析

## 范围与证据等级

本报告比较 `9a798cc`（当前主树 HEAD）、`bffcf5d`（最新已提交 ASR 调整）以及必要历史节点。报告不修改产品代码、测试、Git 状态或设备。

已知设备事实与 Git 事实分开记录：

| 事实 | 等级 | 说明 |
|---|---|---|
| `9a798cc` 同环境 small 正常 | 精确用户真机 A/B | 同一设备 `fcf4b0cb`、whisper.cpp `v1.9.1/f049fff`；small APK SHA-256 `B58F6214...B44F5E`，model SHA-256 `BFDFF489...8AD30` |
| `9a798cc` 同环境 base 失败 | 精确用户真机 A/B | 同一设备/runtime；base APK SHA-256 `7A6B38A8...F9C2D5`，model SHA-256 `A03779C8...6D002` |
| 8 月 20 日前后最新工作树 small/base 均失败 | 当前回归事实 | 支持“仅模型大小不足以解释”，不证明所有 small 资产/版本都失败 |
| `9a798cc` | Git 可验证 | 2026-08-12；此时默认源码仍明确选择 small；`67d39e4` 在其后才切到 base |
| `bffcf5d` | Git 可验证 | 2026-08-17；small、`language=en`、线程上限 2 |
| 当前 UI 移植工作树/手机仍为 small | Git/APK 可验证 | ASR 文件未改，仍是 `9a798cc` 的 auto/4 session 路线；移植后未重新运行识别 |

## 版本矩阵

| 版本/节点 | 默认模型 | JNI 参数 | Native 架构 | 结果/限制 |
|---|---|---|---|---|
| `9a798cc` small | small | `language=auto`、线程≤4 | session cache | 同环境精确 A/B 正常 |
| `9a798cc` base | base | `language=auto`、线程≤4 | session cache | 同环境精确 A/B 失败；确认模型变量影响 |
| `8a48d88` | small.en-q5_1 | `language=auto`、线程≤4 | one-shot JNI | 归档曾记录 48 captions；媒体/模型/runtime 不等价 |
| `9a798cc` | small | `language=auto`、线程≤4 | session cache | 当前 HEAD；small 默认选择明确存在 |
| `bffcf5d` | small.en-q5_1 | `language=en`、线程≤2 | session cache | 最新提交；后续事实显示 small/base 均可能 raw=1 |
| 当前 UI 移植工作树/手机 | small | `language=auto`、线程≤4 | session cache | ASR 闭包保持 `9a798cc`；移植后未重新运行识别 |

## 从 `9a798cc` 到 `bffcf5d` 的代码差异

### ASR 相关

1. `app/build.gradle.kts`
   - `9a798cc` 默认仍为 small；`67d39e4`（其后）才把默认打包模型 `small.en-q5_1` → `ggml-base.en.bin`。
   - `bffcf5d`：再切回 `ggml-small.en-q5_1.bin`。
   - 该路径只说明 APK 资产选择，不证明设备实际运行的模型身份。

2. `WhisperModelStore.kt`
   - `9a798cc`：`ensureBundledModel()` 使用 `smallEnQ5_1`；`67d39e4` 后才改为 `baseEn`。
   - `bffcf5d`：恢复 `smallEnQ5_1`。
   - 当前工作树/手机已回到 small；generated asset 清理用于避免增量残留旧模型。

3. `whisper_jni.cpp`
   - `67d39e4`：引入 abort 后 deferred `whisper_free`，并保留 `language=auto`、线程上限 4。
   - `bffcf5d`：`language=auto` → `en`，线程上限 4 → 2；保留 deferred free。
   - 当前 UI 移植工作树没有 `whisper_jni.cpp` 差异，仍使用 `9a798cc` 的 `auto`、线程≤4；此前最新工作树中的 en/2、deferred-free 等实验不属于当前安装版本。
   - 已知 A/B 表明 `en/2` 与 `auto/4` 同样 raw=1；session 与 one-shot 也同样 raw=1。因此这些差异不能继续作为当前 raw=1 的首要解释。

4. `WhisperSessionRuntime.kt`、`WhisperProcessSession.kt`、`WhisperNativeSessionBridge.kt`
   - 这些文件在 `9a05a3f` 引入，`9a798cc` 与 `bffcf5d` 之间没有新的 session 架构变化。
   - session cache 是 V3 架构变化，但已被 current session vs old one-shot A/B 降级为非直接根因。

### 未发生 ASR 语义变化的路径

- `AndroidAudioExtractor.kt`、`Pcm16ToMono16kProcessor.kt`、`LinearPcm16Resampler.kt`、`Pcm16ChannelMixer.kt`、`Pcm16WavWriter.kt`：历史上未见相关变化；当前 audio 已确认正常。
- `AsrModule.kt`、`AsrCaptionValidator`：当前新增观测只记录 audio/raw/post 计数、覆盖率和耗时，不改变识别或 validator 语义。
- `AppPipelineFactory.kt`、`MainViewModel.generateCaptions()`、UI 生成入口：未发现 `9a798cc`→`bffcf5d` 间会把多段 native 输出变成单段的变化。
- CMake、NDK、ABI、GGML flags：未见相关变化；third_party whisper.cpp 需单独按 runtime hash 追溯。

## 两层原因判断

### 层一：模型差异

已确认或较强支持：

- `9a798cc` 的同环境、同 runtime、同设备 small/base A/B 只改变模型变量：small 正常，base 失败。
- 两个 APK/model 均有精确脱敏 hash 身份，确认该失败由 base 与该歌声样本/解码兼容性触发，而非公共代码差异。
- `67d39e4` 与 `bffcf5d` 确实切换了 base/small 资产路由；这解释模型层差异，但不能代表后续最新工作树。

限制与反证：

- 最新 8 月 20 日工作树 small/base 均失败，但当时 small 运行的完整 APK/model/runtime 身份没有像 `9a798cc` A/B 一样固定。
- 因此最新工作树的 small 失败支持公共/runtime 回归的中等推断，不能推翻 `9a798cc` 同环境模型变量结论。

结论：在 `9a798cc` 同环境中，模型变量已确认是 base 失败的原因；base 不是当前生产目标。最新工作树 small 仍失败，另有中等强度公共/runtime 回归迹象。

### 层二：新版公共 ASR 回归

候选变化：

- `9a05a3f` session cache/context lifecycle；已被 session/one-shot 同结果降级。
- `67d39e4` 之后的 JNI/model/runtime 变化及 ignored 本地状态：可解释最新 small 失败，但目前仅中等推断。
- `bffcf5d` `language`/线程调整；已被 `en/2` vs `auto/4` 同结果排除。
- third_party whisper.cpp 实际历史 hash 尚不完整，仍是 provenance 缺口，不是已确认根因。

结论：`9a798cc` 的模型原因已确认；最新工作树 small 失败与旧 small 正常之间存在未完全识别的公共/runtime 差异，但不能把某个 hunk 判为唯一根因。最早确认边界是 `whisper_full result=0` 后 native raw=1；音频和后处理已排除到更窄范围。

## 原因排序

1. `9a798cc` 下 base 与该歌声样本/解码兼容性失败；置信度高，已有精确同环境模型 A/B。
2. 最新工作树相对旧 small 正常版本的公共/runtime 回归；置信度中，因最新 small 运行身份不完整。
3. whisper.cpp 版本或 ignored 本地源码差异；置信度低到中，需 hash 级 A/B。
4. session 生命周期、`language`、线程、audio extractor、Kotlin postprocess；已有 A/B 或当前证据排除为本次主因。

## 安全边界：保留当前 `9a798cc` small ASR 闭包时移植 UI

允许：

- 保留已验证的 `9a798cc` small ASR 闭包（model selection、session/audio/post route），仅移植当前 UI/editor/import 代码。
- 保留当前 `MainViewModel`、Compose UI、`EditorState`、翻译、导出、编辑和 MediaStore 代码。
- 只在隔离 ASR adapter 中固定经过证明的 model/bridge 参数，并继续通过当前 `AsrModule` 返回 `CaptionCue`。
- 先固定 model/WAV/runtime hash，再做单变量复现。

禁止：

- 直接 checkout/回退整个 `8a48d88` 或 `bffcf5d`。
- 将产品重新切换到 base；当前证据已确认 `9a798cc` base 对该样本失败。
- 回退 `WhisperSessionRuntime`、CMake、audio extractor 或整份 JNI 文件以“恢复稳定”。
- 触碰当前导出/编辑/翻译改动来修 ASR。
- 将旧 48-caption 文档记录当作当前同样本验收。

## 结论与下一步

当前最好解释是：`9a798cc` 同环境 A/B 已确认 base 模型变量导致失败；8 月 20 日前后的新工作树中 small/base 都失败，说明另有中等可信度的公共/runtime 回归，但当时 small 运行身份尚未达到同等证据等级。当前实现保留 `9a798cc` small ASR 闭包，只移植 UI/editor/import；UI Agent 未触碰 ASR 文件，移植后的识别质量尚未重新运行验收。
