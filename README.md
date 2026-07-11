# LyricCaptioner Android

Local-first Android MVP for importing short videos, generating bilingual subtitles,
editing caption timing/text, and exporting a captioned MP4.

Chinese usage, architecture, FAQ, and current progress:
[`docs/中文使用说明与开发进度.md`](docs/中文使用说明与开发进度.md)

## Product defaults

- Android only.
- Personal-use tool, optimized for strong local capability and stability.
- Video duration target: 5 minutes or less.
- Processing mode: local first.
- First version supports manual lyric import/alignment as the reliable path for
  English songs. Fully automatic original-lyric recovery is treated as a best
  effort, not a guarantee.

## Implementation plan

- Kotlin + Jetpack Compose for the app shell and editor UI.
- Media3 for preview and future Transformer-based MP4 export.
- whisper.cpp integration point for local English ASR.
- ML Kit Translate integration point for offline English-to-Chinese subtitles.
- Confidence-first correction flow so uncertain lyrics are highlighted instead
  of silently overwritten.

## Current state

This repository contains the Android project skeleton, UI flow, subtitle state
model, SRT import/export, lyric-to-timeline correction, project archive
import/export, per-cue timing controls, explicit lyric-correction candidates,
subtitle style settings, and local-processing interfaces with demo
implementations. Timing edits enforce minimum cue duration, neighboring cue
boundaries, and the known video duration. The video preview now displays the
active bilingual cue using the same font size, colors, and bottom margin used
for export.

The app currently uses demo recognition data so the editor flow stays usable
while the native ASR runtime is wired. Video export is no longer a demo: it
uses Media3 Transformer to burn time-synchronized bilingual text into an
H.264/AAC MP4 selected through Android's document save dialog.

The Gradle 8.9 wrapper, JDK 17 toolchain, and Android SDK 35 build path are
verified. Debug APK and unit-test builds pass. Runtime behavior still requires
validation on a connected Android device or emulator.

The local pipeline now includes a real Android `MediaExtractor`/`MediaCodec`
audio extractor. It streams decoded PCM through channel downmixing and linear
resampling into a temporary 16 kHz mono PCM16 WAV, then deletes the WAV after
recognition. The default pipeline enables this path automatically when both the
Whisper JNI library and `files/models/ggml-base.en.bin` are available.

The JNI bridge and CMake integration target official whisper.cpp `v1.9.1`.
Run `tools/setup-whisper-native.ps1`, install Android NDK `27.3.13750724` and
CMake `3.22.1`, then build with `-PenableWhisperNative=true`. The native build
was verified on 2026-07-10: the Debug APK includes
`liblyriccaptioner_whisper.so` for `arm64-v8a` and `x86_64`. Downloaded
whisper.cpp sources and all model files remain local and are ignored by Git.

## Next engineering steps

1. Install the Debug APK on a device and validate import, preview, project
   save/open, subtitle editing, and error recovery.
2. Validate the JNI bridge and local ASR on an ARM64 Android device.
3. Add model import/download UI and expose local-model readiness in the editor.
4. Validate Media3 subtitle placement, encoder compatibility, and exported
   playback on representative Android devices.
5. Move project archives from document import/export into app-private automatic
   persistence when the editor lifecycle is finalized.

## Native ASR boundary

`WhisperLocalSpeechRecognizer` is present but feature-gated by native library
availability. The native Debug build packages `lyriccaptioner_whisper` through
`externalNativeBuild`; the audio extractor writes a local PCM/WAV file path for
native code. Passing `content://` URIs directly to native Whisper is
intentionally rejected.
