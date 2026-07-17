# 项目当前状态

## 一、当前门禁

`TRN-001 / COMPONENT_VERIFIED`

- 项目根目录：`D:\DevEnv\Projects\lyric-captioner-android`
- 当前阶段：本地英中翻译与人工校正
- 当前任务：`TRN-001`，修订号 `1`
- 当前状态：`COMPONENT_VERIFIED`
- 验收设备：ARM64 `25098PN5AC`（ADB `fcf4b0cb`）

## 二、已确认基线

- `ASR-001` 已在目标设备达到 `ARM64_DEVICE_VERIFIED`，固定媒体生成 3 条非空、时间戳有效且有序的英文 cue。
- `LocalTranslator` 已由 `MlKitLocalTranslator` 实现，使用 ML Kit `translate:17.0.3` 的英文到中文官方 API。
- `ProjectArchive` 写入 v2 并兼容读取 v1；v2 已保存 cue 的英文、中文、时间戳、候选和确认状态，无需扩展归档格式。
- 当前翻译入口会跳过已有中文，但尚未提供完整模型状态机、取消边界和可测试的原子批处理。
- 当前英文编辑只取消确认，尚未清除旧中文；确认入口尚未阻止单语 cue。

## 三、TRN-001 已验证事实

- 统一 TranslationModule 已完成，没有创建平行翻译路线；模型状态显式区分 `NEEDS_DOWNLOAD`、`PREPARING`、`READY`、`FAILED`。
- 翻译目标仅限英文非空且中文为空；成功整批提交，已有人工中文保持，失败或取消零写入。
- 自动翻译不确认；双语均非空才可确认；英文变更清除旧中文；文本或时间变化取消确认。
- v2 归档继续保存中文与确认状态并兼容 v1；force-stop、重启和 Open Project 已恢复人工中文与 `Confirmed`。
- 全量 JVM 测试 68 项通过；普通 Debug 与 Native Debug 通过。
- 目标设备首次准备耗时 11.857 秒，3 条 Local ASR cue 翻译成功且时间戳不变。
- 关闭 Wi-Fi 和双卡数据后，重启 App 仍显示模型 `READY`；固定英文 cue 离线翻译耗时 253 ms；网络随后恢复。
- 30 条批次在翻译阶段取消后零写入，重试 30/30 成功。

## 四、边界与风险

- 不卸载 App、不清除数据、不删除现有 Whisper 或 ML Kit 模型。
- 不修改 Whisper JNI、FFmpeg、AAR、Media3 或导出路线。
- ML Kit 模型可能已由历史运行缓存；若设备已有模型，不删除模型来伪造“首次下载”，只记录本轮首次准备与实际缓存状态。
- 普通实现问题按最小复现、根因、最小修复、回归和全量验收处理。

## 五、剩余门禁

- 下载失败、翻译中途失败、取消和重试的原子性均有单元测试。
- 真机已验证取消和重试，但首次模型下载完成后不能在“不删除 ML Kit 模型”的边界内再次制造下载失败；有效超长文本也未触发真实翻译失败。
- 当前不具备下载失败与翻译失败的真实设备证据，因此门禁保持 `COMPONENT_VERIFIED`，不标记为 `ARM64_DEVICE_VERIFIED`。
