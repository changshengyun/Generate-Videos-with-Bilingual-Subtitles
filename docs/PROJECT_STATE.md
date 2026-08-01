# LyricCaptioner project state

## Authoritative current state

- Repository: `D:\DevEnv\Projects\lyric-captioner-android`
- Branch: `migration/lyric-captioner-history`
- Current task: `V2-IMPORT-002 / IMPORT_SIMULATOR_VERIFIED / DEVICE_DEFERRED`
- Review baseline: `65ac915` with code-fix baseline `6b9fd99`
- Current gate: `SIMULATOR_ONLY_TEMPORARY`
- Next permitted stage: `V2-E2E-002 / DEFERRED_DEVICE_GATE`
- Physical-device evidence: not complete; simulator evidence must not be promoted to `DEVICE_VERIFIED`.
- Required device for this stage: `emulator-5554` only.

## V2-IMPORT-002 closure facts (2026-08-02)

- Product entry used Android DocumentsUI to select a real MP4 and returned to the product with a playable preview and verified persistent read permission.
- SRT import produced two measured captions. Style state was measured as `#61D6FF / #F4E7A1 / #000000 / sans / 24 / 12`.
- FFmpegKit output was `71,203` bytes, `4,011 ms`, H.264/AVC plus AAC. Media3 playback and controls were ready.
- Source SHA-256 before and after export was identical: `68d080a8b5691442302f981ff645fe4073acb28eebd80b05fd37b9da4568709d`.
- External force-stop/relaunch restored a valid persisted project and Media3 playback. A separate invalid archive exposed unavailable state; relink preserved measured captions and style and invalidated stale export. Picker cancellation preserved an exact state snapshot.
- Instrumentation on Pixel_8 `1080x2400 / 420 dpi` returned `INSTRUMENTATION_CODE: -1` for the prepare, valid-restore, and invalid/relink/cancel runs.
- Illegal-media product entry rejected non-video, empty, unreadable, and over-five-minute inputs without changing the baseline project/editor state; retained fixture hashes were unchanged.
- Real `ggml-small.en-q5_1.bin` JNI cancellation returned in `91,298 ms`, exited `whisper_full` with `-6`, logged native cancellation, and deleted temporary audio.
- Regression matrix: `101` JVM tests, `6` evaluator tests, Lint, normal Debug, Native Debug, and AndroidTest build all passed.

## Review fixes included

- Relink intent is explicit through `requiresVideoAssociation`; no-video/invalid restore no longer routes a captioned project through new-video clearing semantics.
- Instrumentation never deletes or shell-interprets the caller-provided source path. SHA verification uses scanner-provided content URIs with scoped shell identity only for test inspection.
- Export rejects source/destination identity and never deletes a caller-owned destination on precondition, failure, or cancellation; only private temporary work files are owned by the exporter.
- SRT and lyrics reads are bounded, UTF-8 validated, exception-safe, and dispatched off the Compose main thread. Project archive and model operations are also moved off the main thread.
- ONNX preparation/inference runs on controlled background dispatchers. Whisper JNI now receives a cancellation token, configures the native abort callback, and has real emulator evidence for cancellation and native exit.

## Boundaries and preserved state

- Do not modify or clean `third_party/ffmpeg-kit`, models, AARs, Media3, archive semantics, or the existing untracked content.
- Do not connect or test a physical device in the current gate.
- Do not claim model quality improvement, device verification, or completion of the phone gate.
- Existing prior stages remain: `V2-UI-001 / UI_SIMULATOR_VERIFIED`, `V2-PREVIEW-001 / PREVIEW_SIMULATOR_VERIFIED`, `V2-UI-002 / UI2_SIMULATOR_VERIFIED`, and `V2-ASR-002 / FIXTURE_REQUIRED`.

## Stage routing history

| Stage | State |
|---|---|
| V2-ASR-001 | `PAUSED_WITH_ASSETS` |
| V2-PROD-001 | `ARM64_PRODUCT_PATH_VERIFIED` |
| V2-CLEAN-001 | `V2_CLEAN_SIMULATOR_VERIFIED` |
| V2-LOCAL-AI-001 | `LOCAL_AI_SIMULATOR_VERIFIED` |
| V2-ASR-002 | `FIXTURE_REQUIRED` |
| V2-UI-001 | `UI_SIMULATOR_VERIFIED` |
| V2-PREVIEW-001 | `PREVIEW_SIMULATOR_VERIFIED` |
| V2-UI-002 | `UI2_SIMULATOR_VERIFIED` |
| V2-IMPORT-002 | `IMPORT_SIMULATOR_VERIFIED / DEVICE_DEFERRED` |
| V2-E2E-002 | `DEFERRED_DEVICE_GATE` |
