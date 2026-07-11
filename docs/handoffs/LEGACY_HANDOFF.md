# LyricCaptioner Legacy Handoff

> Purpose: preserve investigation leads for an independent audit. This is not an architecture approval and it does not redefine product acceptance.
>
> Written: 2026-07-11. Evidence labels express the confidence of the historical session, not a new audit conclusion.

## 1. Product Goal And Original Boundary

- [VERIFIED] The requested target is an Android-only mobile video editor for short videos, normally no longer than five minutes.
- [VERIFIED] The requested MVP flow is: import video -> create or import bilingual English/Chinese captions -> edit caption text and timing -> edit font size, English color, Chinese color, and bottom position -> preview -> export a non-empty subtitle-burned MP4 -> play it in the current Pixel_8 API 36.1 x86_64 emulator.
- [VERIFIED] English song/voice recognition must remain editable. Low-confidence lyrics must not silently become asserted facts.
- [VERIFIED] The user explicitly prioritized a usable and fast first version over broad compatibility and defensive hardening.
- [VERIFIED] This phase does not require all Android devices, all video codecs, song fingerprinting, a lyric database, or guaranteed recovery of original lyrics.
- [VERIFIED] Local MSI installers and Whisper model files must not be synchronized to GitHub.

## 2. Timeline

- [VERIFIED] 2026-07-09: Gradle/JDK/SDK toolchain was repaired enough to produce a Debug APK. Historical output recorded `BUILD SUCCESSFUL`.
- [VERIFIED] 2026-07-10: Whisper JNI, model import, SRT flow, Media3 exporter, project persistence, and editor UI were worked on. Native Whisper build was reported present in Debug APK, but real ASR was not end-to-end verified.
- [VERIFIED] 2026-07-11: A Pixel_8 API 36.1 x86_64 emulator successfully imported `emulator-h264-test.mp4` and a bilingual SRT. Preview visibly displayed English and Chinese captions. Export repeatedly failed with `Video frame processing error`; selected destination files remained zero bytes.
- [VERIFIED] 2026-07-11: Media3 was raised from 1.7.1 to 1.10.1 and compile SDK was raised from 35 to 36. The project built successfully after the API adjustment, but the same runtime export failure was reproduced.
- [PARTIAL] 2026-07-11: A local FFmpegKit source build was started for x86_64 only. Several MSYS2 dependencies and script compatibility patches were applied. This route was not integrated into the Android app and no AAR or working APK was produced from it.

## 3. Current Product And Code Surface

- [VERIFIED] Kotlin/Jetpack Compose Android app under `app/`.
- [VERIFIED] `EditorScreen` exposes video import, SRT import/export, caption editing, local model import, export, project save/open, size/bottom controls, and English/Chinese/outline color swatches.
- [VERIFIED] `MainViewModel` owns editor state and pauses/releases preview before export.
- [VERIFIED] `Media3SubtitleExporter` is the exporter instantiated by `AppPipelineFactory`.
- [VERIFIED] `Media3SubtitleExporter` creates an `OverlayEffect` with timed bilingual text, requests H.264/AAC output, validates temporary output size, video track, audio track, and duration, then copies it to the selected `Uri`.
- [VERIFIED] `MlKitLocalTranslator` and local Whisper-related classes are present. Their actual device behavior remains separate from code presence.

## 4. Technical Routes Attempted

### A. Media3 Transformer with OverlayEffect

- [VERIFIED] This is the actual app-integrated route at handoff.
- [VERIFIED] It uses Media3 ExoPlayer/Transformer/Effect and Android `MediaCodec`.
- [VERIFIED] The exporter is configured for H.264/AAC and requests software decoder preference plus decoder fallback.
- [FAILED] On the current emulator, an imported H.264 test file with imported bilingual SRT eventually reports `Video export failed: Video frame processing error` and leaves no non-zero output file.
- [VERIFIED] The export failure returns the UI to an actionable state and shows an error message.

### B. Media3 1.10.1 Upgrade

- [VERIFIED] The dependency was upgraded from 1.7.1 to 1.10.1, requiring `compileSdk = 36` and one `DefaultAssetLoaderFactory` constructor adjustment.
- [VERIFIED] `testDebugUnitTest assembleDebug -PenableWhisperNative=true` completed successfully after this change.
- [FAILED] The same H.264/bilingual-SRT emulator export still failed with `Video frame processing error`.
- [UNKNOWN] The upgrade may still affect other devices; no device matrix was tested.

### C. Android-native FFmpeg/libass fallback exploration

- [PARTIAL] `third_party/ffmpeg-kit` was used as a source-build experiment to avoid the emulator MediaCodec/OpenGL pipeline.
- [PARTIAL] MSYS2 was installed under `D:\DevEnv\Tools\msys2-clean`; build dependencies such as `git`, autotools, GCC, patch, gperf, groff, and nasm were installed.
- [PARTIAL] Source-build patches were made under `third_party/ffmpeg-kit` for Windows NDK toolchain naming, CMake/Ninja behavior, optional library dependency handling, and old source compatibility.
- [PARTIAL] The build reached successful `cpu-features` and `freetype` stages before this handoff. Background build processes were stopped at handoff.
- [UNKNOWN] No usable FFmpegKit AAR exists in the app module at handoff.
- [UNKNOWN] No FFmpeg Java/Kotlin wrapper, ASS generator, local font staging, or app-level native exporter integration was completed.
- [STALE] Earlier claims that this route would be the most reliable emulator exporter were design hypotheses, not accepted or verified results.

## 5. Completed Or Partially Completed Functions

| Area | Confidence | Evidence / Limit |
|---|---|---|
| Debug APK build | [VERIFIED] | Build succeeded after Media3 1.10.1 and compile SDK 36 adjustment. |
| Video import | [VERIFIED] | Current emulator imported `emulator-h264-test.mp4`. |
| Bilingual SRT import | [VERIFIED] | Current emulator imported two bilingual cues. |
| Caption preview | [VERIFIED] | Emulator preview visibly showed English and Chinese lines. |
| Color controls | [VERIFIED] | English, Chinese, and outline swatches are visible in current UI. |
| Caption text/time editor | [PARTIAL] | Code and UI controls exist; current handoff did not re-run a full edit/save/reopen/export cycle. |
| Project save/open | [PARTIAL] | Code/UI exists; no current-session round-trip evidence. |
| SRT export | [PARTIAL] | Code/UI exists; no current-session output validation. |
| Whisper local recognition | [PARTIAL] | JNI/model wiring and ready indicators were visible, but no current-session real audio-to-caption proof. |
| ML Kit translation | [FAILED] | Emulator network/model download was previously unavailable; timeout/error recovery was added, but translation was not validated. |
| Burned MP4 export | [FAILED] | Runtime failure reproduced on current emulator. |

## 6. Current Blocker

- [VERIFIED] The primary product blocker is no successful subtitle-burned MP4 export in the required emulator environment.
- [VERIFIED] The active app route is still `Media3SubtitleExporter`; it fails during video frame processing on the emulator after a real save-location flow.
- [PARTIAL] Logs previously showed both Goldfish and Android software codec attempts in different runs. The exact failing codec/surface sequence must be re-collected by the audit session from fresh logcat.
- [PARTIAL] FFmpegKit source compilation is unfinished and not app-integrated, so it is not a fallback today.

## 7. Major Errors And Fixes Attempted

| Error / symptom | Label | Attempted action |
|---|---|---|
| `c2.goldfish`/Media3 export failures and `Video frame processing error` | [FAILED] | H.264 conversion of test input, preview release delay, decoder fallback, software decoder selector, Media3 1.10.1 upgrade. No successful export. |
| ML Kit stayed at translation-model download | [PARTIAL] | Wi-Fi restriction removed and a timeout/error message added. Emulator network remained unusable. |
| Android document picker issue | [PARTIAL] | Video picker changed from `GetContent` to `OpenDocument`; import subsequently worked in emulator. |
| FFmpegKit HarfBuzz clone reset | [PARTIAL] | Git HTTP/1.1 and MSYS2 tool installation were used; source later appeared downloaded. |
| FFmpegKit old script selected `cygwin-x86_64` NDK path | [PARTIAL] | Script changed to use `windows-x86_64`. |
| FFmpegKit CMake selected Visual Studio / could not find Ninja | [PARTIAL] | Script was patched for `cmake.exe`, Ninja generator, and SDK CMake bin path. |
| Restricted environment denied NDK clang | [VERIFIED] | Same build executed outside the restricted sandbox; CMake compiled its compiler test successfully. |
| FFmpegKit CPU features script called `make` in Ninja directory | [PARTIAL] | Script patched to call `ninja`; `cpu-features` then completed. |

## 8. Rejected Or Deferred Ideas

- [FAILED] Treating Media3 parameter changes alone as sufficient for this emulator was disproved by repeated runtime failures.
- [STALE] Prebuilt FFmpegKit dependency retrieval was investigated, but retired/removed upstream artifacts and unreliable mirrors made it unsuitable as an unverified delivery dependency.
- [DEFERRED] Broad multi-engine export policy was explicitly outside the requested first-phase scope. The unfinished FFmpegKit work is an experiment, not an adopted multi-engine architecture.
- [DEFERRED] Song fingerprinting, lyric databases, and guaranteed song lyric restoration were outside the requested MVP.

## 9. Facts, Inferences, Assumptions, And Stale Claims

### Confirmed facts

- [VERIFIED] Current Git repository root is `D:\DevEnv\Work`; the project is a subdirectory. At handoff branch is `chore/adopt-codex-workflow`, latest commit is `618f36e chore: capture current project state`.
- [VERIFIED] The root Git working tree already contains unrelated modified/untracked audit documents and a modified `third_party/ffmpeg-kit` submodule/worktree state.
- [VERIFIED] The project directory initially had no `docs/PROJECT_STATE.md`; this handoff creates one.
- [VERIFIED] The current app Gradle configuration has Media3 1.10.1 and `compileSdk = 36`; AGP 8.7.3 warns that it was tested up to SDK 35.
- [VERIFIED] The emulator can launch, install the Debug APK, import the supplied H.264 test video and bilingual SRT, and render bilingual preview text.

### Evidence-backed but incomplete judgments

- [PARTIAL] The failing operation is likely the emulator video frame-processing pipeline rather than file selection, caption parsing, or destination copying, because export reaches processing and then reports frame processing error.
- [PARTIAL] Real device behavior may differ from this emulator; no physical Android device evidence exists in this handoff.
- [PARTIAL] A software FFmpeg/libass pipeline could avoid the specific Media3 frame-processing failure, but its implementation and viability are not yet demonstrated in the app.

### Assumptions and unknowns

- [ASSUMPTION] The current selected model and JNI pairing can perform usable local ASR for both English and Chinese. Only readiness indicators were observed.
- [UNKNOWN] Whether the current `Media3SubtitleExporter` succeeds on any target hardware.
- [UNKNOWN] Whether `third_party/ffmpeg-kit` source patches yield a compatible, legally acceptable, and size-acceptable AAR.
- [UNKNOWN] Whether a non-Fontconfig libass build can locate a suitable Android font after integration.
- [UNKNOWN] Whether all saved project and SRT export paths still work after the latest dependency changes.

### Potentially stale conclusions to re-check

- [STALE] Existing Chinese usage documentation says burned MP4 export is a real working flow. Current emulator evidence contradicts that claim.
- [STALE] Existing documents say Android SDK Platform 35 is the project build requirement; current Gradle configuration uses SDK 36.
- [STALE] Earlier toolchain-success statements do not prove runtime export success.
- [STALE] Earlier statements about emulator codec selection and software fallback should be re-verified with current APK and fresh logcat.

## 10. What The New Audit Session Should Check First

1. Read root `AGENTS.md`, this handoff, all root-level project-state/audit documents, and actual Git status before trusting this history.
2. Confirm which files in `third_party/ffmpeg-kit` are tracked, generated, or untracked, and whether any source-build experiment should be retained.
3. Confirm current Gradle dependency resolution, `compileSdk`, AGP compatibility warning, and Debug APK build from a clean low-risk build command.
4. Reproduce Media3 export once with fresh logcat, recording exact codec, Surface, and Transformer exception evidence.
5. Distinguish app-integrated behavior from code-only features: translation, Whisper recognition, save/open, SRT export, and edited-caption export.
6. Treat FFmpegKit only as an unapproved feasibility spike until an isolated AAR build and a minimal app integration are proven.
7. Establish a source-of-truth document set and Git checkpoint process; project-local state documents were missing at the start of this handoff.

## 11. Suggested Commit Message

`docs: add legacy LyricCaptioner handoff and current-state record`
