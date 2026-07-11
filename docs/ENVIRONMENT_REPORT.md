# LyricCaptioner 环境审计报告

审计时间：2026-07-11（Asia/Shanghai）。所有检查均为只读或项目内构建；未修改系统环境。

## 开发环境

- Windows 11 内核 `10.0.22631`，x64；PowerShell。
- 物理内存约 31.7 GiB；审计时磁盘可用：C 约 8.8 GiB、D 约 378.9 GiB、E 约 135.9 GiB。C 盘余量偏低，是 Gradle/Android 临时文件风险。
- `JAVA_HOME=D:\DevEnv\Tools\JDK`；Temurin OpenJDK `17.0.19`。
- Gradle Wrapper `8.9`，分发包固定为 `file:///D:/DevEnv/Tools/gradle-8.9-bin.zip`。
- `local.properties` 的实际 SDK：`C:/Users/Legion/AppData/Local/Android/Sdk`。`ANDROID_HOME`/`ANDROID_SDK_ROOT` 未设置，`adb` 不在 PATH；使用 SDK 绝对路径可运行 ADB 37.0.0。

## 实际构建配置与依赖

- Android application、Kotlin `2.0.21`、AGP `8.7.3`、Compose BOM `2024.12.01`。
- `compileSdk 36`、`targetSdk 35`、`minSdk 26`、Java/Kotlin 17。
- Media3 `1.10.1`（ExoPlayer/UI/Transformer/Effect）、ML Kit Translate `17.0.3`、coroutines `1.9.0`、JUnit `4.13.2`。
- 可选原生构建：NDK `27.3.13750724`、CMake `3.22.1`、C++17、whisper.cpp 源码目标 `v1.9.1`；ABI 为 `arm64-v8a` 与 `x86_64`。

## 本轮验证结果

- `testDebugUnitTest --rerun-tasks`：成功，24 项、0 failure/0 error/0 skipped。
- `testReleaseUnitTest --rerun-tasks`：成功，24 项、0 failure/0 error/0 skipped。
- `assembleDebug` 与 `assembleDebug -PenableWhisperNative=true`：成功；native APK 大小 54,410,991 bytes，两个 ABI 均包含 `liblyriccaptioner_whisper.so`、`libtranslate_jni.so`。
- 构建警告：AGP 8.7.3 官方测试上限为 compileSdk 35，而项目使用 36；这是版本兼容风险，不是本轮构建失败。
- CMake 警告：工具仅理解 SDK XML 至 v3，但遇到 v4，表明 Android Studio/command-line tools 版本存在漂移；本轮仍构建成功。
- `stripDebugDebugSymbols` 无法 strip `libandroidx.graphics.path.so` 与 `libtranslate_jni.so`，以原样打包；Debug 可接受，发布体积/符号策略未验证。
- ADB 37.0.0 可通过 SDK 绝对路径运行；当前 `adb devices -l` 为空，无法进行本轮设备验收或重新采集导出 logcat。

## 模拟器与真机证据差异

| 项目 | 模拟器证据 | 真机证据 | 结论 |
|---|---|---|---|
| App 启动/UI | 仓库有模拟器截图 | 本轮无设备 | 仅部分证据 |
| H.264 测试视频/双语字幕产物 | 仓库有 MP4/SRT/截图产物 | 无真机播放证据 | 不能外推硬件编码兼容 |
| x86_64 Whisper JNI | APK 已打包 | 不代表运行成功 | 构建事实 |
| ARM64 Whisper JNI | APK 已打包 | 未执行真实模型 | 关键缺口 |
| MediaCodec 音频抽取 | 代码与 JVM DSP 测试 | 未测设备解码器 | 关键缺口 |
| ML Kit 下载/离线翻译 | 实现存在 | 未测首次下载与离线复用 | 关键缺口 |
| Media3 导出 | 历史指定模拟器重复报 `Video frame processing error`；仓库样例产物来源不可追溯 | 未测厂商编码器、色彩/音轨/字幕位置 | 当前首要故障与关键缺口 |

## 缺失、冲突与限制

- 无已连接设备；无法验证 ARM64、厂商 MediaCodec、热/内存/电量和 SAF 持久权限。
- Whisper 模型保持本地且未纳入仓库；本轮未下载或安装模型。
- 根仓库 Git 历史只有一次快照提交，无法从历史重建功能演进；审计基准工作树本身为 dirty。`health-assistant` 和 `codex-debug-log` 是 gitlink，但根仓库缺失 `.gitmodules` 映射，属于仓库治理问题，不影响 Android 编译。
- `health-assistant` 的嵌套仓库所有者与当前用户不同，Git 报 dubious ownership；本轮未修改全局 `safe.directory`。
- `third_party/ffmpeg-kit` 仍以 gitlink 残留且工作树显示 modified，但当前 Android app 依赖表和源码未引用 FFmpegKit；视为废弃/旁路残留，不能在本轮删除。

## 安装/配置计划与回滚

- 本轮建议安装：无。下一步优先使用已有 SDK 与用户提供的 ARM64 真机。
- 如后续需要处理 AGP/compileSdk 或 SDK XML 漂移，必须先单独提出版本对齐方案、影响与回滚，不在本轮升级。
- 项目级构建产物可用 `gradlew clean` 清理；本轮未改全局状态，无系统回滚项。
