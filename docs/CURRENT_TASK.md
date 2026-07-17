# 当前任务

- 任务编号：`TRN-001`
- 修订号：`1`
- 任务名称：翻译与人工校正
- 工作状态：`COMPONENT_VERIFIED`
- 产品门禁：`TRN-001 / COMPONENT_VERIFIED`
- 负责范围：ML Kit 模型准备、离线复用、原子批量翻译、人工编辑确认、双语一致性与归档恢复

## 一、交付目标

1. 基于现有 `LocalTranslator` 和 ML Kit 建立统一 `TranslationModule`。
2. 显式提供 `NEEDS_DOWNLOAD`、`PREPARING`、`READY`、`FAILED` 模型状态。
3. 只翻译英文非空且中文为空的 cue，不覆盖人工中文，并保持 ID、顺序和时间戳不变。
4. 批量失败或取消时零写入；成功后整批一次提交，自动翻译保持未确认。
5. 只有英文、中文均非空时允许确认；英文变化清除旧中文，任一文本或时间变化取消确认。
6. 中文和确认状态经保存、重启、重新打开后保持；归档继续兼容 v1/v2。
7. 日志只记录模型状态、数量、阶段、耗时和错误类型，不记录字幕正文。

## 二、允许修改范围

- `app/src/main/java/com/example/lyriccaptioner/processing/`
- `app/src/main/java/com/example/lyriccaptioner/MainViewModel.kt`
- `app/src/main/java/com/example/lyriccaptioner/model/`
- `app/src/main/java/com/example/lyriccaptioner/ui/EditorScreen.kt`
- 对应 `app/src/test/` 测试
- 本任务活动文档：`CURRENT_TASK.md`、`DEVELOPMENT_ROADMAP.md`、`PROJECT_STATE.md`

## 三、明确不包含

- 不修改 Whisper JNI、FFmpegKit/AAR、Media3、字幕导出路线或全局工具链。
- 不下载 ML Kit 官方英中模型之外的依赖，不删除 Whisper 或 ML Kit 模型。
- 不卸载 App、不清除数据，不修改 `docs-BK`、Git 历史或 GitHub。

## 四、验收门槛

- 模型状态、首次准备、离线复用、批量原子性、跳过人工中文、失败、取消、重试、确认规则和双语一致性单测通过。
- 全量 `:app:testDebugUnitTest` 通过。
- 普通 `:app:assembleDebug` 与 `:app:assembleDebug -PenableWhisperNative=true` 通过。
- ARM64 设备 `fcf4b0cb / 25098PN5AC` 完成联网准备、三条字幕翻译、人工修改确认、保存重启恢复、断网复用和失败/取消/重试检查。

## 五、最终门禁

`COMPONENT_VERIFIED`

## 六、本轮验收结果

- 统一 TranslationModule 与 `NEEDS_DOWNLOAD / PREPARING / READY / FAILED` 状态机已完成；仅处理英文非空、中文为空的 cue，批量成功后一次提交。
- 全量 `:app:testDebugUnitTest` 共 68 项，Failures=0、Errors=0、Skipped=0；TRN-001 新增 17 项测试。
- 普通 `:app:assembleDebug` 与 Native `:app:assembleDebug -PenableWhisperNative=true` 均通过。
- 首次设备准备：模型从 `NEEDS_DOWNLOAD` 经 `PREPARING` 到 `READY`，耗时 11.857 秒；ML Kit 记录英中模型 hash。
- 3 条 Local ASR 英文字幕均获得非空中文；ID、顺序和时间戳保持，自动翻译后仍未确认。
- 第一条中文人工追加 `!` 后确认；保存项目、force-stop、重启并 Open Project 后，3 条双语 cue、人工修改和 `Confirmed` 状态恢复。
- Wi-Fi 与双卡移动数据关闭后，重启 App 仍为 `READY`；固定英文 `Offline model reuse works` 在 253 ms 内得到非空中文，结束后网络恢复到原状态。
- 30 条批次取消于翻译阶段，状态显示未应用任何翻译，首条中文为空；直接重试完成 30/30。
- 下载准备失败、翻译中途失败、空翻译、取消和重试的零部分写入由单测验证。真机首次下载完成后，边界禁止删除模型；有效超长输入也未触发真实翻译失败，因此下载失败和翻译失败未取得真机证据。
- 最终状态：`COMPONENT_VERIFIED`，未伪造 `ARM64_DEVICE_VERIFIED`。
