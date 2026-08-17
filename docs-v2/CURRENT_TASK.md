# Current Task: V2 final closure

## Current status

- Stage: `V2-E2E-002`
- Status: `USER_ACCEPTED / DEVICE_PATH_VERIFIED`
- Review baseline: `65ac915` with code-fix baseline `6b9fd99`
- Functional code baseline: `8a48d88`
- Verification mode: `USER_MANUAL_ACCEPTANCE`
- Device: `fcf4b0cb` / `25098PN5AC` / ARM64 / `1220x2656` / `520 dpi`
- Next version: `V3 / PLANNING`

## User acceptance record (2026-08-02)

- The user manually completed the real-device path: local video import -> Local Whisper -> local OPUS-MT English-to-Chinese translation -> FFmpegKit export -> Media3 playback.
- The product entry used a real phone-local video and retained persistent read permission after returning to the app.
- The user judged English recognition usable and visibly improved over prior experience. This is a user acceptance observation, not fixture-backed WER/CER evidence; the historical `V2-ASR-002 / FIXTURE_REQUIRED` limitation remains recorded.
- The local translation path worked, but the user judged the Chinese wording too machine-like for a final product-quality claim.
- No code change, new commit, or push was performed during the manual validation. The functional code baseline remains `8a48d88`.
- The user explicitly closed V2 and waived further device, persistence, relink, source-integrity, or automation evidence collection.

## Archived execution contract

- The product video entry launches Android DocumentsUI through the visible import control. ADB may prepare emulator fixtures only; it does not select the product URI or copy media into App-private storage.
- Validate URI readability, real video track, non-empty content, duration, five-minute limit, and persisted permission or an explicit session-only result.
- Cancellation preserves the current project, captions, styles, preview, and derived output. New import clears captions and derived output. Relink preserves captions, confirmation state, and style while invalidating the old export.
- Save and external force-stop/relaunch restore the project. An unavailable URI exposes a rebind action and never reuses stale output.
- Export uses FFmpegKit and playback uses Media3. Product processing has no Demo fallback or runtime network/model download.
- Preserve source media, `third_party/ffmpeg-kit`, existing untracked content, models, archive semantics, and the existing navigation/processing architecture.
- This contract is archived with V2 and no longer authorizes additional device work.

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
- Historical simulator closure deferred physical-phone selection, restart recovery, relink, and export evidence. The user later completed the main path manually and explicitly closed V2 without requesting those additional evidence cases.
- Not changed: Whisper/translation strategy, UI visual system, Media3/FFmpegKit business logic, AARs, dependencies, archive semantics, and `third_party/ffmpeg-kit`.

## Prior verified stages

- `V2-UI-001 / UI_SIMULATOR_VERIFIED`
- `V2-PREVIEW-001 / PREVIEW_SIMULATOR_VERIFIED`
- `V2-UI-002 / UI2_SIMULATOR_VERIFIED`
- `V2-ASR-002 / FIXTURE_REQUIRED`
