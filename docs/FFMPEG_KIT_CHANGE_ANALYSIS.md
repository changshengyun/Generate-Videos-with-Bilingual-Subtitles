# ffmpeg-kit change analysis

检查日期：2026-07-12（Asia/Shanghai）

## 仓库状态

- 路径：`D:\DevEnv\Projects\lyric-captioner-android\third_party\ffmpeg-kit`
- HEAD：`d6be56d7aec286eb3c292d6b23ff07a6b70d8693`（tag `v6.0` / `v6.0.LTS`）
- 分支：detached HEAD（`HEAD (no branch)`）
- 主仓库索引：模式 `160000`，同一 gitlink SHA
- `.gitmodules`：主仓库不存在映射
- 工作树：5 个已跟踪文件修改；无未跟踪文件
- diff 规模：11 insertions、9 deletions
- app 集成状态：Gradle 依赖和当前调用链均未使用 FFmpegKit

## 分类标准

- **A 必须保留修改**：已有明确产品依赖、验证结果或恢复要求，删除会破坏当前生效能力。
- **B 实验修改**：意图可由上下文解释，但未通过构建/集成验收，必须隔离保存，不能视为正式方案。
- **C 未知修改**：意图、必要性或正确性缺少足够证据；不得自动处理。

## 5 个修改的分类

| 文件 | 修改摘要 | 分类 | 判断 |
|---|---|---|---|
| `android/ffmpeg-kit-android-lib/build.gradle` | `versionCode` 240600 → 260600；增加空行 | **C 未知修改** | 没有版本发布、冲突修复或构建需求证据解释 260600；空行无功能意义。不得自动保留到正式 fork，也不得回退。 |
| `scripts/android/cpu-features.sh` | `make`/`make install` 改为 `ninja` | **B 实验修改** | 与 Windows Android NDK 的 Ninja 构建尝试一致，但未见成功构建或 AAR 验证。 |
| `scripts/function-android.sh` | 识别 MSYS/MINGW 为 Windows；放宽 `cmake.exe` 查找；强制 Ninja generator 与 `ninja.exe` | **B 实验修改** | 意图明确为 Windows/MSYS 工具链适配；正确性、跨平台影响和路径引用均未验证。 |
| `scripts/function.sh` | 为 libass 移除 expat、libuuid、fontconfig 依赖启用 | **B 实验修改** | 与缩减可选依赖的历史实验说明一致，但可能改变字体发现和构建能力，未通过功能/许可/兼容验证。 |
| `scripts/main-android.sh` | libass 就绪条件同步移除 expat、libuuid、fontconfig、libpng | **B 实验修改** | 是上一处依赖裁剪的配套实验；没有证明产物可构建或字幕渲染正确。 |

**A 类数量：0。** 当前 app 未集成 FFmpegKit，也没有一项修改具备“必须保留到生效路线”的验证证据。

## 处理建议

1. 本轮保持嵌套仓库原样：不 checkout、不 reset、不 clean、不继续修改、不接入 app。
2. baseline commit 只提交本分析中的可恢复 diff 证据，不提交或改写 detached `ffmpeg-kit` 工作树。
3. C 类 `versionCode` 修改等待用户或原作者说明；在说明前不得自动归入实验 fork 或自动回退。
4. B 类修改仅作为隔离实验保存。若未来明确批准 FFmpegKit 路线，应在独立分支/仓库中重放，并以 Windows 构建、AAR 产物、字幕渲染、ABI、体积和许可验收重新证明。
5. 当前 SPIKE 仍只允许既定 Media3 证据探针；本文件不构成 FFmpegKit 技术路线批准。

## Diff evidence

以下内容基于 `d6be56d7aec286eb3c292d6b23ff07a6b70d8693`，用于逐项审查和人工重建修改意图。为保持文档可读性，部分未改上下文以 `@@` 省略；它不是可直接执行的 patch。本轮不执行重放。

```diff
diff --git a/android/ffmpeg-kit-android-lib/build.gradle b/android/ffmpeg-kit-android-lib/build.gradle
index f1dd6ed..d98c13e 100644
--- a/android/ffmpeg-kit-android-lib/build.gradle
+++ b/android/ffmpeg-kit-android-lib/build.gradle
@@ -10,7 +10,7 @@ android {
         minSdk 24
         targetSdk 33
-        versionCode 240600
+        versionCode 260600
         versionName "6.0"
@@ -41,6 +41,7 @@ android {
     }

+
     publishing {
diff --git a/scripts/android/cpu-features.sh b/scripts/android/cpu-features.sh
index 9013b5e..d50fd06 100755
--- a/scripts/android/cpu-features.sh
+++ b/scripts/android/cpu-features.sh
@@ -2,9 +2,9 @@
 $(android_ndk_cmake) || return 1

-make -C "$(get_cmake_build_directory)" || return 1
+ninja -C "$(get_cmake_build_directory)" || return 1

-make -C "$(get_cmake_build_directory)" install || return 1
+ninja -C "$(get_cmake_build_directory)" install || return 1
diff --git a/scripts/function-android.sh b/scripts/function-android.sh
index 0e4b78a..65e604e 100755
--- a/scripts/function-android.sh
+++ b/scripts/function-android.sh
@@ -129,7 +129,8 @@ get_toolchain() {
-  CYGWIN* | *_NT-*) HOST_OS=cygwin ;;
+  CYGWIN*) HOST_OS=cygwin ;;
+  MSYS* | MINGW* | *_NT-*) HOST_OS=windows ;;
@@ -992,13 +993,14 @@ android_ndk_cmake() {
-  local cmake=$(find "${ANDROID_SDK_ROOT}"/cmake -path \*/bin/cmake -type f -print -quit)
+  local cmake=$(find "${ANDROID_SDK_ROOT}"/cmake -path \*/bin/cmake\* -type f -print -quit)
@@
+  local ninja="$(dirname "${cmake}")/ninja.exe"
@@
   echo ${cmake} \
+    -G Ninja \
+    -DCMAKE_MAKE_PROGRAM="${ninja}" \
diff --git a/scripts/function.sh b/scripts/function.sh
index 06e3565..4dc1b07 100755
--- a/scripts/function.sh
+++ b/scripts/function.sh
@@ -1133,11 +1133,8 @@ set_library() {
   libass)
     ENABLED_LIBRARIES[LIBRARY_LIBASS]=$2
-    ENABLED_LIBRARIES[LIBRARY_EXPAT]=$2
-    set_virtual_library "libuuid" $2
     set_library "freetype" $2
     set_library "fribidi" $2
-    set_library "fontconfig" $2
     set_library "harfbuzz" $2
     set_virtual_library "libiconv" $2
diff --git a/scripts/main-android.sh b/scripts/main-android.sh
index 7266dbd..43099e8 100755
--- a/scripts/main-android.sh
+++ b/scripts/main-android.sh
@@ -91,7 +91,7 @@ while [ ${#enabled_library_list[@]} -gt $completed ]; do
     libass)
-      if [[ $OK_libuuid -eq 1 ]] && [[ $OK_expat -eq 1 ]] && [[ $OK_libiconv -eq 1 ]] && [[ $OK_freetype -eq 1 ]] && [[ $OK_fribidi -eq 1 ]] && [[ $OK_fontconfig -eq 1 ]] && [[ $OK_libpng -eq 1 ]] && [[ $OK_harfbuzz -eq 1 ]]; then
+      if [[ $OK_libiconv -eq 1 ]] && [[ $OK_freetype -eq 1 ]] && [[ $OK_fribidi -eq 1 ]] && [[ $OK_harfbuzz -eq 1 ]]; then
         run=1
```
