# 项目当前状态

## 一、当前门禁

`PERSONAL_USE_ACCEPTED`

- 项目根目录：`D:\DevEnv\Projects\lyric-captioner-android`
- 当前阶段：个人自用稳定版收口
- 当前任务：`CLS-001`，修订号 `1`
- 当前状态：`PERSONAL_USE_ACCEPTED`
- 验收设备：ARM64 `25098PN5AC`（ADB `fcf4b0cb`）

## 二、已确认基线

- `ASR-001` 已在目标设备达到 `ARM64_DEVICE_VERIFIED`，固定媒体生成 3 条非空、时间戳有效且有序的英文 cue。
- `LocalTranslator` 已由 `MlKitLocalTranslator` 实现，使用 ML Kit `translate:17.0.3` 的英文到中文官方 API。
- `ProjectArchive` 写入 v2 并兼容读取 v1；v2 已保存 cue 的英文、中文、时间戳、候选和确认状态，无需扩展归档格式。
- 当前翻译入口已由统一 TranslationModule 管理模型状态、取消边界和批量原子提交；已有中文不会被覆盖。
- 英文变化会清除旧中文，文本/时间变化会取消确认；单语 cue 不允许确认。

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

## 五、已关闭的组件门禁

- 下载失败、翻译中途失败、取消和重试的原子性均有单元测试。
- 真机取消、零写入和重试证据已保留；下载失败与翻译失败的原子性由单测覆盖，按照用户决定不再补真机故障注入。
- `TRN-001` 保持 `COMPONENT_VERIFIED`；`E2E-001` 已达到 `ARM64_E2E_VERIFIED`，不再存在阻断当前个人自用版本的组件门禁。

## 六、E2E-001 授权与验收范围

- 用户已接受 TRN-001 的单测失败证据并放行 E2E-001；不再补真机下载或翻译失败注入。
- 目标设备限定为个人自用 ARM64 `fcf4b0cb / 25098PN5AC`，不扩展多厂商设备矩阵。
- 本轮只执行一次最终约 5 分钟完整成功验收，建立阶段耗时、总耗时、峰值 RSS、输出大小、温度和存储变化基线，不臆造阈值。
- 完整流程必须覆盖 SAF 导入、Local ASR、缓存模型离线翻译、人工确认、预览、归档重启恢复、字幕烧录导出与 Media3 回放。
- 当前门禁：`PERSONAL_USE_ACCEPTED`。
# E2E-001 revision 2 state (2026-07-18)

- Repository baseline confirmed: main / 08266d2570a8c2008bfa696c582980841c1099cf. Existing E2E document edits and third_party/ffmpeg-kit dirty state were preserved.
- Correctness component is verified by 77 passing JVM tests plus lint and both Debug build variants. Whisper fixture remains ignored and validated at 147951465 bytes with SHA-1 465707469ff3a37a2b9b8d8f89f2f99de7299dac.
- Device runtime after the single Native APK install: ASR: LOCAL, Model: ready, JNI: ready, Translation model: READY.
- A prior non-revision-2 export remains available as evidence: non-zero MP4, H.264/AAC, 299.791667 seconds, 46279071 bytes, SHA-256 0E0BA50D179C2044A9A7957AF92308933FA043B3EA1AFFEE5A02106723104585. It is not presented as the revision-2 final export.
- Revision-2 ARM64 E2E continuation is incomplete due device UI automation idle failure after 5-minute import; no claim is made for new-version ASR, translation, save/restart, export, or Media3 playback.
- Final state: PARTIAL_PASS.

# E2E-001 revision 3 final state (2026-07-18)

- `E2E-001` 已从 `IMPLEMENTING` 完成到 `ARM64_E2E_VERIFIED`。本轮没有重新安装 APK、构建或重复 77 项测试。
- 目标设备 `fcf4b0cb / 25098PN5AC`：1220x2656，portrait，rotation 0。执行方式为 adb screencap、定向 input、logcat、dumpsys、归档解析和 ffprobe；不把 uiautomator idle/dump 成功作为前提。
- 真实闭环证据：导入固定 299.247733 秒素材；Local ASR 生成 48 条非空英文 cue；缓存 ML Kit 模型离线翻译 48/48；人工修改并确认 cue；预览；保存、force-stop、重启和 Open Project 后恢复双语及确认状态；成功导出并由 Media3 回放。
- 本轮新 MP4 为 `/sdcard/Download/lyric-captioner-output (2).mp4`，46,277,905 字节，SHA-256 `D309B177569F638D7427D34ED96F2F9A4CDB8D1EF5EAB0527E2D130B241DD789`，H.264/AAC，592x1280，299.791667 秒。关键帧双语字幕可见；旧 46,279,071 字节输出未计入。
- 阶段/资源基线：ASR 52.044 秒；翻译 1.573 秒；FFmpeg 回调约 9.375 秒；可记录墙钟间隔约 33 分 40.204 秒；采样峰值 RSS 418,604 kB，导出阶段 335,684 kB；结束温度 battery 32.7 C、CPU0 45.9 C、CPU7 44.9 C、skin 33.35 C；可用存储 137,660,528 -> 137,611,676 kB；`cache/ffmpeg-exports` 为空。
- 离线翻译时 Wi-Fi/mobile_data=0/0，结束恢复 Wi-Fi/mobile_data=1/0；VPN 保持设备原有状态。仅维护活动文档，未提交 Git 或同步 GitHub。

# CLS-001 closeout state (2026-07-18)

- 当前状态统一为 `PERSONAL_USE_ACCEPTED`。冻结内容是个人自用稳定版，不代表公开发布合规。
- 允许保留的未提交差异仅为：E2E revision 2 正确性修复、对应测试和三个活动文档；`third_party/ffmpeg-kit` 既有 dirty 状态保持不变。
- Native Debug APK：`app/build/outputs/apk/debug/app-debug.apk`；SHA-256 `BA54697CD204D8334A5DD7484E4EC517D90EB5E144AD1A60F3182E213B225DBE`；applicationId `com.example.lyriccaptioner`；versionCode `1`；versionName `0.1.0`；ABI `arm64-v8a`, `x86_64`。
- 本地 APK 与设备已安装 APK 的完整 SHA-256 相同；签名证书 SHA-256 均为 `a51e9235816b2f34195a459764ff7155958e3a5c503aa782d125a000c0817528`，因此具备直接更新安装条件。本轮未安装，不输出或复制私钥。
- `git diff --check` 通过。77 项单测、lintDebug、普通 Debug、Native Debug 和 ARM64 E2E 沿用既有通过证据，因无代码变化不重复。
- 模型、测试视频、输出 MP4、日志和 APK/构建产物仅在已忽略的 `.tool-downloads`、Gradle 或 build 目录中；没有进入 Git 未跟踪集合，未发现仓库外密钥候选。未清理既有证据。
- `REL-001` 仅在未来公开分发时启用；不执行许可证、商店、SBOM 或多设备矩阵工作。
- 检查点条件：`READY_FOR_CHECKPOINT`。不提交 Git，不同步 GitHub。
