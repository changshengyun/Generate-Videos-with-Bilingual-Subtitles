# 当前任务

- 任务编号：`CLS-001`
- 修订号：`1`
- 任务名称：个人自用稳定版收口
- 工作状态：`PERSONAL_USE_ACCEPTED`
- 产品门禁：`PERSONAL_USE_ACCEPTED`
- 验收设备：`fcf4b0cb / 25098PN5AC`

## 一、已接受前置证据

- `TRN-001` 保持 `COMPONENT_VERIFIED`。
- 用户已接受下载准备失败和翻译失败的单元测试证据并放行 E2E-001，不再补真机失败注入。
- ASR、翻译、归档与导出沿用既有实现和失败/取消证据，本轮不重复无价值故障注入。

## 二、交付目标

在个人 ARM64 设备上对约 5 分钟固定素材只执行一次最终完整成功验收：

1. 视频通过 App SAF 导入。
2. 全程保持 `ASR: LOCAL`，完成真实 Whisper JNI 识别。
3. 关闭 Wi-Fi 与移动数据，使用已缓存 ML Kit 英中模型完成离线翻译。
4. 验证双语字幕、时间戳、人工确认、预览及保存重启恢复。
5. 完成字幕烧录导出，验证非零 MP4、H.264/AAC、合理时长、关键字幕可见和 Media3 可播放。
6. 记录来源、SHA-256、各阶段/总耗时、峰值 RSS、输出大小、临时文件、设备温度与存储变化。

## 三、验收与测试

- 全量 `:app:testDebugUnitTest`。
- 普通 `:app:assembleDebug` 与 `:app:assembleDebug -PenableWhisperNative=true`。
- 若修改代码，只安装更新 APK 一次。
- 当前收口状态只能是 `PERSONAL_USE_ACCEPTED` 或 `BLOCKED`。

## 四、边界

- 不卸载、不清除 App 数据、不删除 Whisper 或 ML Kit 模型。
- 不修改 FFmpeg AAR、Whisper 版本、ML Kit 依赖、Media3、导出路线或全局工具链。
- 不模拟低存储，不执行破坏性设备操作，不扩展多厂商矩阵。
- 不提交 Git，不同步 GitHub。

## 五、当前进度

- 已读取活动文档并确认用户接受 TRN-001 证据。
- E2E-001 revision 3 已完成设备闭环与资源基线采集；CLS-001 正在冻结个人自用稳定版，当前状态为 `PERSONAL_USE_ACCEPTED`。

# E2E-001 revision 3 completion (2026-07-18)

- 已绕过 MIUI/Media3 的 idle 前提：使用 `adb screencap -d`、定向 `input`、logcat、dumpsys 和文件/媒体元数据确认；未以 `uiautomator waitForIdle` 或 dump 成功作为前提。
- 设备 `fcf4b0cb / 25098PN5AC`：1220x2656，portrait，rotation 0。残留文件选择器由 force-stop 关闭后，App 主窗口重新成为 control target。
- 固定素材：`.tool-downloads/e2e-001/e2e-5min-source.mp4`，299.247733 秒，SHA-256 `FFBF914C7F46A67CA50488285899B86F2A2E928E58BC5999A0A26FD43059933F`；未提交素材。
- 真实流程通过：视频导入 -> `ASR: LOCAL`（Model/JNI ready，48 条英文 cue）-> 缓存 ML Kit 离线英中翻译（48/48，`READY`）-> 人工修改并确认 -> 预览 -> 保存 -> force-stop/重启/Open Project 恢复 -> 新 MP4 导出 -> Media3 回放。
- 阶段耗时：ASR 52.044 秒；离线翻译 1.573 秒；FFmpeg 导出回调约 9.375 秒（App export_started 到完成约 10.877 秒）；从本轮首个记录的导入完成事件到导出完成的可记录墙钟间隔约 33 分 40.204 秒。
- 新输出：`/sdcard/Download/lyric-captioner-output (2).mp4`，设备时间戳 16:43，46,277,905 字节，SHA-256 `D309B177569F638D7427D34ED96F2F9A4CDB8D1EF5EAB0527E2D130B241DD789`；H.264 592x1280 + AAC，299.791667 秒。10 秒关键帧可见双语烧录字幕，App 预览显示 `Export complete`，Media3 `ExoPlayer Init` 无异常。
- 保存归档 12,893 字节、48 条 cue；人工修改 cue 的中文与 `confirmed=true` 在重启后恢复。FFmpeg `cache/ffmpeg-exports` 由 `run-as` 核验为空。
- 资源基线：ASR 采样峰值 RSS 418,604 kB、PSS 306,182 kB；导出采样峰值 RSS 335,684 kB；结束采样 RSS 325,960 kB、PSS 176,924 kB。结束采样温度：battery 32.7 C、CPU0 45.9 C、CPU7 44.9 C、skin 33.35 C。存储可用空间由记录的 137,660,528 kB 变为 137,611,676 kB。
- 离线阶段记录 Wi-Fi=0、mobile_data=0；结束已恢复 Wi-Fi=1、mobile_data=0。VPN 是设备原有连接，未被修改。
- 既有 77 项 `testDebugUnitTest`、lintDebug、普通 Debug 和 Native Debug 构建证据继续有效；本轮无代码变化，未重复执行。
- 最终状态：`ARM64_E2E_VERIFIED`。

# CLS-001 personal-use closeout (2026-07-18)

- 当前冻结状态：`PERSONAL_USE_ACCEPTED`。本轮只审核差异、APK、签名和活动文档，不修改业务代码、构建工具链或设备数据。
- 允许保留的差异：E2E revision 2 正确性修复、对应测试和三个活动文档；`third_party/ffmpeg-kit` 为既有 dirty 状态，未修改、未暂存、未清理。
- Native Debug APK：`app/build/outputs/apk/debug/app-debug.apk`；SHA-256 `BA54697CD204D8334A5DD7484E4EC517D90EB5E144AD1A60F3182E213B225DBE`；applicationId `com.example.lyriccaptioner`；versionCode `1`；versionName `0.1.0`；ABI `arm64-v8a`, `x86_64`。
- 签名证书 SHA-256：`a51e9235816b2f34195a459764ff7155958e3a5c503aa782d125a000c0817528`。设备已安装 APK 与本地 APK 的证书和完整 APK SHA-256 均一致，可直接更新安装；未输出或复制私钥，且本轮未安装。
- `git diff --check` 通过。普通测试、构建和真机 E2E 沿用既有通过证据，本轮因无代码变化未重复。
- 模型、测试视频、输出 MP4、日志和构建产物仅存在于已忽略的 `.tool-downloads` / Gradle 生成目录，不进入 Git 未跟踪集合；未发现仓库外的密钥文件候选。未清理既有证据或生成物。
- `REL-001` 仅在未来需要公开分发时启用；本轮不执行许可证、商店、SBOM 或多设备矩阵工作。
- 检查点条件：`READY_FOR_CHECKPOINT`；本轮不提交 Git、不同步 GitHub。
# E2E-001 revision 2 evidence (2026-07-18)

- Phase history: CORRECTNESS_FIXING -> DEVICE_REGRESSION -> PARTIAL_PASS.
- Correctness fixes completed: explicit NEW_VIDEO/RELINK policy, SRT confirmation rule, centralized derived-output invalidation, and exact Whisper model validation with temporary-file validation and atomic replacement.
- Validation gate passed: 77 testDebugUnitTest tests, lintDebug, assembleDebug, and assembleDebug -PenableWhisperNative=true.
- Native APK was installed once with adb install -r; no uninstall, data clear, or model deletion.
- Device startup verified ASR: LOCAL, Model: ready, JNI: ready, and Translation model: READY.
- Final revision-2 5-minute E2E continuation was not claimed: after 5-minute media import, MIUI/Media3 kept uiautomator in "could not get idle state", so Local ASR, translation, save/restart, final export, and Media3 playback could not be reliably driven in this run.
- Final status: PARTIAL_PASS.
