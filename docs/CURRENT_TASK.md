# Current Task: V2-IMPORT-002 closure

## Current status

- Stage: `V2-IMPORT-002`
- Status: `IMPORT_SIMULATOR_VERIFIED / DEVICE_DEFERRED`
- Review baseline: `65ac915` with code-fix baseline `6b9fd99`
- Verification mode: `SIMULATOR_ONLY_TEMPORARY`
- Device: `emulator-5554` / Pixel_8 / `1080x2400` / `420 dpi`
- Next stage: `V2-E2E-002 / DEFERRED_DEVICE_GATE`

## Active execution contract

- The product video entry launches Android DocumentsUI through the visible import control. ADB may prepare emulator fixtures only; it does not select the product URI or copy media into App-private storage.
- Validate URI readability, real video track, non-empty content, duration, five-minute limit, and persisted permission or an explicit session-only result.
- Cancellation preserves the current project, captions, styles, preview, and derived output. New import clears captions and derived output. Relink preserves captions, confirmation state, and style while invalidating the old export.
- Save and external force-stop/relaunch restore the project. An unavailable URI exposes a rebind action and never reuses stale output.
- Export uses FFmpegKit and playback uses Media3. Product processing has no Demo fallback or runtime network/model download.
- Preserve source media, `third_party/ffmpeg-kit`, existing untracked content, models, archive semantics, and the existing navigation/processing architecture.
- No physical device may be connected or tested in this stage. Physical evidence remains reserved for `V2-E2E-002` and must not be reported as `DEVICE_VERIFIED`.

## Closure evidence (2026-08-02)

- `emulator-5554` entered DocumentsUI from the product, selected a real MP4, returned to the app, imported two SRT cues, changed subtitle style, saved a project, and produced a non-zero export.
- Export: `71,203` bytes, `4,011 ms`, H.264 `video/avc`, AAC `audio/mp4a-latm`; Media3 playback and controls were ready.
- Source SHA-256 before/after export: `68d080a8b5691442302f981ff645fe4073acb28eebd80b05fd37b9da4568709d` (unchanged).
- Valid saved-project restore crossed an external force-stop/relaunch boundary and returned `media=PERSISTED`, with Media3 play/pause/seek verification.
- Invalid URI restore exposed unavailable state; relink measured caption and style state equality and export invalidation; picker cancel measured an identical before/after state snapshot.
- Instrumentation results returned `INSTRUMENTATION_CODE: -1` for prepare, valid restore, and invalid/relink/cancel runs. No true device was used.
- Illegal-media product entry rejected non-video, empty, unreadable, and over-five-minute fixtures while preserving the baseline project/caption/style/export state; retained fixture hashes were unchanged.
- Real `ggml-small.en-q5_1.bin` JNI cancellation returned in `91,298 ms` with native abort/full-exit/cancel logs and deleted temporary WAV.

## Regression matrix

- `101` JVM tests: passed, 0 failures/errors/skips.
- `python tools\\asr_evaluate_test.py`: `6` passed.
- `lintDebug`: passed.
- `assembleDebug`: passed.
- `assembleDebug -PenableWhisperNative=true`: passed.
- `assembleDebugAndroidTest -PenableWhisperNative=true`: passed.

## Scope disposition

- Fixed in this closure: relink state retention after no-video/invalid restore, source-safe instrumentation hashing and fixture handling, export destination/source safety, failure/cancel destination preservation, bounded asynchronous SRT/lyrics reads, background model/ONNX work, and Whisper JNI cancellation.
- Deferred: physical-phone ARM64 selection, restart recovery, relink, and export evidence; these belong to `V2-E2E-002 / DEFERRED_DEVICE_GATE`.
- Not changed: Whisper/translation strategy, UI visual system, Media3/FFmpegKit business logic, AARs, dependencies, archive semantics, and `third_party/ffmpeg-kit`.

## Prior verified stages

- `V2-UI-001 / UI_SIMULATOR_VERIFIED`
- `V2-PREVIEW-001 / PREVIEW_SIMULATOR_VERIFIED`
- `V2-UI-002 / UI2_SIMULATOR_VERIFIED`
- `V2-ASR-002 / FIXTURE_REQUIRED`
