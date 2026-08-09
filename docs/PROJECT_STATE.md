# LyricCaptioner V3 project state

## Authoritative current state

- Repository: `D:\DevEnv\Projects\lyric-captioner-android`
- Branch: `migration/lyric-captioner-history`
- Current task: `V3-AI-CONTRACT-001 / R1 / SECURITY_AND_BYOK_DELTA / MATRIX_DEFINED / IN_PROGRESS`
- V2 functional code baseline: `8a48d88`
- V2 archive: `docs-v2/`
- Current gate: `SECURITY_AND_BYOK_DELTA / MATRIX_DEFINED / IN_PROGRESS`
- Process gate: `ACCEPTANCE_MATRIX_REQUIRED_BEFORE_IMPLEMENTATION`
- Implementation authorization: granted for the bounded `V3-AI-CONTRACT-001` scope
- Review workflow: no independent Review window; Developer self-tests and returns matrix evidence to Brain for state adjudication
- Next permitted action: freeze R1 public interfaces/tests, dispatch bounded security/BYOK/UI agents, integrate and run the R1 matrix; real key testing remains deferred until explicit later authorization.

## Current test-first evidence

- Brain added four new JVM test files containing 20 tests for T01-T14 and did not modify production Kotlin.
- Focused Gradle execution reached `:app:compileDebugUnitTestKotlin` and failed on the intentionally absent V3 contract symbols, establishing the expected-red baseline.
- The Kotlin daemon encountered a user-local `AccessDeniedException`, but Gradle fallback compilation completed far enough to emit the expected `unresolved reference` contract errors.
- No live Provider, API key, network lyrics retrieval, UI change, Whisper cache, media change, cleanup, device test, Git commit or push was performed.

## Version transition

- The user explicitly accepted V2 after manual verification on `fcf4b0cb / 25098PN5AC / ARM64 / 1220x2656 / 520 dpi` and waived further V2 operations.
- The prior active `docs/` was archived to `docs-v2/`.
- The former `docs-v3/` architecture draft was promoted into the new active `docs/` and supplemented with the required three-document state surface.
- V2 code was not changed during this transition. `third_party/ffmpeg-kit`, models, media, test assets, and existing untracked content remain outside the documentation commit.

## V3-AI-CONTRACT-001 implementation evidence (2026-08-10)

- Checkpoint: `bfc7751`; feature commit: `69b991e`; neither was pushed.
- Focused four Brain contract tests, full `testDebugUnitTest`, `lintDebug`, normal Debug, native-enabled Debug (`-PenableWhisperNative=true`) and `assembleDebugAndroidTest` all completed successfully.
- `:app:assembleNativeDebug` is not a task in this checkout; the native-enabled Debug command ran the configured CMake path instead.
- Contract implementation is limited to provider-neutral DTO/service/state/error types, mapper/validator, coordinator fallback orchestration, atomic commit policy, processing metadata and V3 archive compatibility.
- Live Provider/API key/network lyrics/device/UI/media/model-cache/V2-cleanup work remains deferred or prohibited by this stage.

## Confirmed V3 direction (2026-08-09)

- Add a single-model process-level Whisper cache; retain context for 3-5 minutes after recognition, serialize one task per context, and release on idle timeout, model switch, severe memory pressure, or unsafe cancellation state.
- Redesign interaction before visual styling.
- After successful subtitle generation, stay on the current section and show only the success state; the user enters subtitle editing through the existing explicit action.
- Use one project-level caption text box within the active video image and cue-level style overrides.
- Resolve subtitle position and font size in source-video coordinates so preview, fullscreen, aspect-ratio changes, and export agree.
- Use Photo Picker as the only video import entry and MediaStore/system gallery as the only export destination; do not offer an alternate location picker.
- Remove the entire app top bar and visible development/version labels while preserving system status/navigation bars and Window Insets.
- Send complete local Whisper cue batches to an AI API for song/online-lyrics matching, per-cue English correction, and Chinese translation without re-running audio ASR or changing timestamps.
- Keep the current OPUS-MT/ONNX local translator as the real offline/network-failure fallback and label its output separately from cloud AI.
- Persist only non-sensitive API mode settings. Provider API keys stay outside the APK/Git/logs and must be configured in a backend secret/environment location before live testing.
- The final product keeps only the model-recognition main path and the local-translation fallback path. SRT import and other export branches are removed only in a later audit-first `V3-CLEAN-001` stage.
- The detailed `V3_PRODUCT_ARCHITECTURE.md` remains secondary reference; where its earlier draft conflicts with these three active documents, the active documents and latest user decisions control until the reference is synchronized.

## Development process rule

- Every new stage must define its acceptance matrix in `docs/CURRENT_TASK.md` before checkpoint creation or implementation.
- The required rows are: main user path, mandatory evidence, prohibited changes/fallbacks, exact PASS exit conditions, and deterministic incomplete states.
- A missing matrix forces `MATRIX_REQUIRED`. Missing device, fixture, screenshot, log, artifact, or runtime evidence must resolve to the state declared before implementation; code or build completion cannot silently raise the stage above that evidence ceiling.
- The matrix may only change before further implementation and with a recorded reason. It cannot be weakened retroactively to convert a failed result into PASS.
- Completion reports must map every claimed status to the corresponding matrix evidence.

## Evidence and claim boundaries

- Whisper caching may improve repeated-task startup time but does not itself improve core inference speed or recognition accuracy.
- User-observed V2 recognition usability is accepted as product feedback, not fixture-backed WER/CER evidence.
- OPUS-MT works locally but its Chinese naturalness is not accepted as final product quality.
- Cloud song/lyrics matching is not yet live-verified; the current stage can verify only contracts, validation, atomicity and deterministic fallback behavior.
- Online lyrics provenance/licensing and the concrete AI Provider/backend remain a later `HUMAN_DECISION` before live integration; tests must not scrape or bundle real copyrighted lyrics.
- GPU availability on the phone does not authorize a GPU backend; any GPU work requires an independent Spike and regression evidence.
- Preview/export visual equivalence must exclude letterbox/pillarbox regions and use the source video as the common coordinate system.

## Preserved state

- Do not clean, reset, stage, or commit the existing dirty `third_party/ffmpeg-kit` state.
- Do not commit models, `.emulator-test-assets/`, `tools/opus-mt-en-zh/`, `._cache_adb.exe`, or unrelated untracked content.
- Do not push unless the user explicitly requests it.
- Preserve the user-approved but uncommitted `AGENTS.md` acceptance-matrix rule and three-document updates when creating the stage checkpoint.
- Do not stage unrelated dirty/untracked content in the checkpoint or feature commit.
- Do not implement live Provider calls, API-key storage, model caching, UI/media changes or V2 cleanup inside `V3-AI-CONTRACT-001`.

## Stage routing

| Stage | State |
|---|---|
| V2 | `USER_ACCEPTED / ARCHIVED_IN_DOCS_V2` |
| V3-DEC-001 | `PASS` |
| V3-AI-CONTRACT-001 | `READY_FOR_BRAIN / COMPONENT_VERIFIED / LIVE_API_DEFERRED` |
| V3-ASR-SESSION-001 | `PLANNED` |
| V3-EDITOR-001 | `PLANNED` |
| V3-MEDIA-001 | `PLANNED` |
| V3-UI-001 | `PLANNED` |
| V3-AI-001 | `LIVE_API_DEFERRED` |
| V3-CLEAN-001 | `PLANNED` |
| V3-E2E-003 | `PLANNED` |
