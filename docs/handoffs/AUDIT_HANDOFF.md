# LyricCaptioner Audit Handoff

## Audit result

- Baseline: `618f36ee50ecfef7302faa974d6b0e9e494614b9` on `chore/adopt-codex-workflow`, with a pre-existing dirty working tree.
- Gate: `ARCHITECTURE_REVIEW`.
- Classification: **C — confirmed implementation/integration failure; architecture not disproved**.
- No product code, dependency, system environment, or architecture was changed.

## Highest-confidence facts

- Effective stack: Kotlin/Compose, Media3, Android MediaCodec, ML Kit Translate, optional whisper.cpp JNI.
- Debug and Release unit tests were rerun: 24/24 each, no failures/errors/skips.
- Standard and native Debug APK builds succeed; native APK contains ARM64 and x86_64 Whisper JNI libraries.
- Default caption generation falls back to fixed Demo ASR when model/JNI readiness is false. Local correction still uses a no-op Demo corrector.
- FFmpegKit is an unfinished modified gitlink experiment and is not integrated into the app.
- ADB 37.0.0 works by absolute path but no device is connected in this audit.

## Confirmed failure boundary

Current project state and the legacy handoff consistently record that the integrated `Media3SubtitleExporter` repeatedly failed on the agreed Pixel_8 API 36.1 x86_64 emulator with `Video frame processing error`, leaving a zero-byte destination. This audit could not reproduce it because no device/emulator was connected. The checked-in `final-bilingual-subtitle.mp4` has no traceable evidence that it came from the Android exporter and must not be treated as acceptance.

Earliest supported boundary: video frame-processing/device integration. Root cause remains unknown; codec, Surface/OpenGL, input format and Media3 implementation hypotheses are still open.

## Next action

`docs/NEXT_TASK.md` is **Proposed**, not approved. After explicit user approval, use `evidence-first-debugging` for one same-emulator reproduction and evidence capture only. Do not fix, replace Media3, resume FFmpegKit, or continue product development without a later approval.

## Canonical continuation documents

- `docs/PROJECT_BRIEF.md`
- `docs/REQUIREMENTS.md`
- `docs/ENVIRONMENT_REPORT.md`
- `docs/CURRENT_SYSTEM_MAP.md`
- `docs/CURRENT_TECH_STACK.md`
- `docs/FEATURE_STATUS.md`
- `docs/PROJECT_STATE.md`
- `docs/MID_PROJECT_AUDIT.md`
- `docs/NEXT_TASK.md`
