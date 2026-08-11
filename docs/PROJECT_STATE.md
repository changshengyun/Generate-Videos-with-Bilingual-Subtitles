# LyricCaptioner V3 project state

## Authoritative current state

- Repository: `D:\DevEnv\Projects\lyric-captioner-android`
- Branch: `migration/lyric-captioner-history`
- Current task: `V3-EDITOR-002 / R2 / PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`
- V2 functional code baseline: `8a48d88`
- V2 archive: `docs-v2/`
- Current gate: `V3_EDITOR_002_R2_VERIFIED / BRAIN_REVIEW_PENDING`
- Process gate: `V3_EDITOR_002_R2_CANDIDATE_READY`
- Implementation authorization: granted for the bounded per-cue style-card UI, cue layout override, v5 archive migration, shared Compose/ASS resolver and tests
- Review workflow: Brain owns formal adjudication; Developer may return only a candidate result
- Next permitted action: Brain re-adjudication only; do not start DeepSeek subtitle enhancement, Provider or Prompt work.

## Current V3-EDITOR-002 / R2 implementation evidence

- Effective-video geometry is runtime-derived from Media3 `VideoSize` and the Compose container through shared `CaptionGeometryResolver`; normal/fullscreen Compose and ASS use the same normalized mapping and final resolved style.
- Focused geometry: 8/8. Focused ASS: 8/8. Full JVM: 212/212. ASR Python: 6/6. Lint: 0 errors/33 warnings. Ordinary Debug, native-enabled Debug and AndroidTest builds passed.
- APK artifacts: `app/build/outputs/apk/debug/app-debug.apk` = `417,446,841` bytes; `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` = `119,048` bytes.
- Boundary and candidate: no device, DeepSeek, Key, online lyrics, Provider or Prompt evidence; physical UI is waived. Candidate is `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`; Brain owns acceptance.

## Brain R1 re-adjudication (2026-08-11)

- Formal verdict remains `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / RENDER_INTEGRATION_REQUIRED`.
- Accepted R1 evidence: position-only clear, cueId-scoped style/layout clear, sibling isolation and scoped ASS Chinese colour are fixed.
- P1: Compose maps the overlay across the complete PlayerView container instead of the Media3 FIT effective video rectangle, so black bars change x/y/width relative to ASS.
- P1: Compose adds unmodelled horizontal padding inside the normalized width and therefore narrows/shifts the text area.
- P1: Compose and ASS still disagree on Chinese font size, bold/italic application and the non-bold English weight.
- P1: current tests cover helper values and ASS strings but do not exercise Compose effective-video geometry, black bars, aspect-ratio changes, normal/fullscreen parity or final bilingual style parity.
- R2 matrix and bounded Limbs parallel plan were defined in `CURRENT_TASK.md`; implementation is complete and the Developer candidate is recorded above for Brain re-adjudication.

## Current V3-EDITOR-002 / R1 implementation evidence

- R1-01/R1-02/R1-05 pass: position-only overrides are clearable; clear by stable cueId removes both style and layout overrides from only the target cue and restores default layout.
- R1-03/R1-04 pass: ASS applies an explicit scoped Chinese `secondaryColorHex` override; Compose and ASS share normalized coordinate and vertical-anchor semantics.
- R1-06 artifacts: app APK `417446841` bytes at `app/build/outputs/apk/debug/app-debug.apk`; AndroidTest APK `119048` bytes at `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
- R1-07 verification: focused JVM 39/39, full JVM 203/203, ASR Python 6/6, lint 0 errors/33 warnings, ordinary/native Debug and AndroidTest builds passed.
- No physical UI, DeepSeek, Key, online lyrics, Provider or Prompt work was performed. Developer candidate: `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`; Brain owns acceptance.

## Brain adjudication of V3-EDITOR-002 (2026-08-11)

- Formal verdict: `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / RENDER_INTEGRATION_REQUIRED`.
- P1: pure `layoutOverride` does not enable the clear action because UI override state checks only `styleOverride`; S08 is not satisfied.
- P1: ASS emits English and Chinese in one Dialogue without a Chinese text-color override, while Compose uses `secondaryColorHex`; S10 color parity is not satisfied.
- P1: Compose and ASS use different y/anchor and x/width geometry semantics; sharing a resolver value object does not prove render parity, so S10 geometry parity is not satisfied.
- Accepted partial evidence: cueId-scoped writes, v5 migration/round-trip and cue isolation show no additional blocker. Developer regression/build claims remain evidence, not formal acceptance.
- Artifact correction: current App APK is `417,446,841 bytes`; current AndroidTest APK is `119,048 bytes`. The returned `119,027 bytes` value was stale.
- Physical UI remains waived by user for this stage. R1 must not perform device work or touch DeepSeek, real Key, online lyrics, Provider or Prompt.

## Current product findings (2026-08-10)

- DeepSeek caption enhancement is not implemented in the product path. Production currently has secure BYOK storage and a body-free `GET /models` authentication probe only.
- There is no production `CaptionEnhancementProvider`, `/chat/completions` call, model selection, system prompt, user prompt, messages payload or response parser. Whisper results are committed directly; the Chinese action uses local OPUS-MT.
- `V3-AI-001` therefore remains `NOT_IMPLEMENTED / PRODUCTION_PROMPT_ABSENT / SEPARATE_STAGE_REQUIRED`; a successful Key connection test must not be described as AI caption processing.
- The screenshot-confirmed editor mismatch is the active task: two detached style panels must be removed and all per-cue controls moved into the corresponding caption card.

## FINAL_PHYSICAL_DEVICE_VERIFICATION_BACKLOG

- Policy: `WAIVED_BY_USER_FOR_CURRENT_DEVELOPMENT / EVIDENCE_NOT_MEASURED`. Existing failure and missing-data evidence stays recorded, but the backlog no longer blocks later component work. It must not be rewritten as measured PASS.
- ASR A13: real native consecutive recognition, cold/hot execution and handle reuse remain unverified.
- ASR A15: real cold/hot context load, inference, total, peak RSS, temperature, empty-result and crash data remain unverified.
- Editor UI: physical-device editor navigation, cue editing, preview and export UI evidence remain unverified; simulator/JVM/build/static evidence does not upgrade this boundary.
- Editor physical UI verification is recorded in this backlog; simulator/JVM/build/static evidence remains allowed now but cannot upgrade the physical boundary.

## Current V3-EDITOR-001 matrix summary

- E01–E18 are frozen in `docs/CURRENT_TASK.md`: manual editor entry, success/failure gating, navigation stability, editor-only cue list, normalized project layout, default/override style inheritance, V2 migration, V3 archive round-trip, shared Compose/ASS resolution, invalid-data safety and regression coverage.
- Highest result with complete component and simulator evidence is `PARTIAL_PASS / EDITOR_COMPONENT_VERIFIED / PHYSICAL_DEVICE_UI_DEFERRED_BY_USER`.
- Missing shared Compose/ASS integration is `PARTIAL_PASS / EDITOR_MODEL_VERIFIED / RENDER_INTEGRATION_REQUIRED`; unsafe V2 migration is `BLOCKED / PROJECT_MIGRATION_SAFETY_REQUIRED`.

## Current V3-ASR-SESSION-001 matrix summary

- A01–A16 are frozen in `docs/CURRENT_TASK.md`; they cover cold creation, 3-minute reuse, 5-minute expiry, strict serialization, task isolation, path/size/SHA-256 invalidation, active-safe model switching, cancellation ordering, memory-pressure release, failure recovery, idempotent close, process isolation, real-native handle reuse, cue validity, performance diagnostics and regression coverage.
- The runtime is process-level and single-model. Idle time starts only after a task fully ends and uses an injectable monotonic clock/scheduler. The same context may run only one inference at a time.
- Cancellation is conservative: abort is requested, native inference must fully return and its thread must end before cleanup/free, and the cancelled context is never reused.
- Only `fcf4b0cb / 25098PN5AC / arm64-v8a / API 36` is authorized for physical-device evidence, using repository/test-owned non-private audio only.
- Highest Developer candidate with complete evidence is `PASS / WHISPER_SESSION_CACHE_VERIFIED / PHYSICAL_DEVICE_RUNTIME_VERIFIED`; missing physical native evidence caps the result at `PARTIAL_PASS / WHISPER_SESSION_COMPONENT_VERIFIED / PHYSICAL_DEVICE_RUNTIME_REQUIRED`.
- Unsafe native lifetime is `BLOCKED / NATIVE_LIFETIME_SAFETY_REQUIRED`; failure to prove second-task reuse is `BLOCKED / CACHE_REUSE_NOT_PROVEN`.
- Cache evidence may claim only lower repeated context-loading cost. It must not claim accuracy, WER/CER, or core inference-speed improvement.

## Current V3-ASR-SESSION-001 implementation evidence

- Process-level `WhisperProcessSession` owns one `WhisperSessionRuntime`; model identity is canonical path + file size + SHA-256, idle timeout is 5 minutes from full task completion, and a `Mutex` serializes inference.
- Native uses opaque registry handles and explicit create/transcribe/requestAbort/free. Free is idempotent and waits for the native inference mutex; cancellation/failed transcription marks the handle non-reusable before Kotlin releases it after worker return.
- Focused runtime: 14 passed. Full JVM: 169 passed, 0 failures/errors/skipped. ASR Python: 6 passed. Lint: 0 errors/33 warnings. Normal Debug, native-enabled Debug for arm64-v8a+x86_64, and AndroidTest builds passed.
- App APK is 383,030,793 bytes; AndroidTest APK is 118,877 bytes. Secret scan found 0 app Key tokens, 4 existing synthetic AndroidTest tokens, 0 credential-bearing Bearer values and 0 stage-source credential tokens.
- A01–A12 pass at component level; A14 and A16 pass at component/regression level. A13 and A15 remain required because the authorized device disconnected before install and stayed absent through three checks. No device run or performance numbers are claimed.
- Checkpoint is `3aec389`. Brain formally accepted `PARTIAL_PASS / WHISPER_SESSION_COMPONENT_VERIFIED / PHYSICAL_DEVICE_RUNTIME_DEFERRED_BY_USER`; A13/A15 are in the final physical-device backlog and no physical runtime claim is made.

## Current R1 live-key evidence

- Physical device: `fcf4b0cb / 25098PN5AC / arm64-v8a / API 36 / qcom`. The real Key was manually entered only inside the App and was never supplied through chat, terminal, environment variables, ADB input, clipboard automation, source, test files or screenshots.
- The production probe uses only `GET https://api.deepseek.com/models`, follows no redirects, sends no body/user content and reads or records no response body. Initial save, restart-time saved-key connection testing and same-key rotation each returned sanitized `HTTP 2xx`; a synthetic invalid replacement returned sanitized `HTTP 401`.
- The first encrypted record was 147 bytes and contained no plaintext `sk-` prefix. Same-key rotation changed both record SHA-256 and the 12-byte GCM IV. The failed synthetic-invalid replacement preserved the rotated record and IV exactly, after which the old real Key still authenticated with `HTTP 2xx`.
- Delete removed the production record; restart returned `UNCONFIGURED` with no masked Key. Because production health returns `NEEDS_REENTRY` when an alias remains without a record, this proves both production record and alias absence. The App was force-stopped afterward.
- R1 focused JVM: 31 passed; full JVM: 155 passed with 0 failures/0 errors/0 skipped; ASR baseline: 6 passed; lint: 0 errors/33 warnings; normal Debug, native-enabled Debug and AndroidTest builds passed. Physical-device synthetic Keystore/UI instrumentation passed.
- Final stripped app APK: 382,081,973 bytes; AndroidTest APK: 91,700 bytes. Secret scan found 0 app-APK Key tokens, 0 disallowed AndroidTest/source/test-output/log tokens, 0 credential-bearing Bearer values, 0 DeepSeek query URLs and no new screenshot artifact. The production record is absent.
- Checkpoint `1567402` froze the matrix. Brain formally accepted `PARTIAL_PASS / SECURE_BYOK_VERIFIED / DEEPSEEK_AUTH_VERIFIED / LIVE_LYRICS_FLOW_DEFERRED`; the verdict covers the physical-device BYOK security path and minimal DeepSeek authentication, but not online lyrics, song matching, cue enhancement, the complete product flow or formal product PASS.

## Current R1 security rework evidence

- Brain previously rejected the old implementation because production health materialized the complete Key, cancellation did not cover encryption/commit, alias-deletion partial success could collapse to `UNCONFIGURED`, and active Provider routing text conflicted with the frozen DeepSeek route.
- The current delta uses a separate AES-GCM empty-plaintext authentication tag whose AAD binds the Key ciphertext, IV and mask; a rollback-capable prepare/commit transaction checks coroutine cancellation before and after durable commit; missing-record/present-alias and delete-failure states remain `NEEDS_REENTRY`.
- R1 focused JVM: 27 passed; full JVM: 148 passed with 0 failures/0 skipped; ASR baseline: 6 passed. Lint reports 0 errors/33 warnings; normal Debug, native-enabled Debug and AndroidTest builds passed.
- Production instrumentation passed only with a synthetic Key on the `Pixel_8 / emulator-5554 / sdk_gphone64_x86_64 / API 36` emulator: AES-256-GCM AndroidKeyStore, distinct Key IVs, empty-plaintext AAD health, 142-byte test record, corruption/alias-loss recovery, alias-delete partial failure retained as `NEEDS_REENTRY`, commit cancellation write count 0, UI clears, and final record+alias absence. No physical device was connected.
- Final APK scan covered the stripped 382,081,953-byte native-enabled app APK and 91,551-byte AndroidTest APK: 0 disallowed Key tokens, 0 credential-bearing Authorization headers and 0 private runtime paths; only four synthetic test sentinels were present in AndroidTest.
- Checkpoint `bbb9761` froze the delta matrix and captured three expected old-implementation failures: delete-after-validation write-back, swallowed delete failure and plaintext decrypt during status/cancel.
- Security/BYOK fix commit `935ff92` contains the accepted delta implementation and evidence baseline.
- Brain formally accepted `PARTIAL_PASS / SECURE_BYOK_COMPONENT_VERIFIED / LIVE_KEY_TEST_REQUIRED`. This is a component-level security verdict, not a formal product PASS, and it does not prove a real DeepSeek Key, live authentication, DeepSeek network behavior, lyrics matching, a physical device run or the complete device product flow.

## Historical test-first evidence

- Brain added four new JVM test files containing 20 tests for T01-T14 and did not modify production Kotlin.
- Focused Gradle execution reached `:app:compileDebugUnitTestKotlin` and failed on the intentionally absent V3 contract symbols, establishing the expected-red baseline.
- The Kotlin daemon encountered a user-local `AccessDeniedException`, but Gradle fallback compilation completed far enough to emit the expected `unresolved reference` contract errors.
- No live Provider, API key, network lyrics retrieval, UI change, Whisper cache, media change, cleanup, device test, Git commit or push was performed.

## Version transition

- The user explicitly accepted V2 after manual verification on `fcf4b0cb / 25098PN5AC / ARM64 / 1220x2656 / 520 dpi` and waived further V2 operations.
- The prior active `docs/` was archived to `docs-v2/`.
- The former `docs-v3/` architecture draft was promoted into the new active `docs/` and supplemented with the required three-document state surface.
- V2 code was not changed during this transition. `third_party/ffmpeg-kit`, models, media, test assets, and existing untracked content remain outside the documentation commit.

## Historical V3-AI-CONTRACT-001-R1 implementation evidence before security rework (2026-08-10)

- Checkpoint: `bfc7751`; feature commit: `69b991e`; neither was pushed.
- Focused four Brain contract tests, full `testDebugUnitTest`, `lintDebug`, normal Debug, native-enabled Debug (`-PenableWhisperNative=true`) and `assembleDebugAndroidTest` all completed successfully.
- `:app:assembleNativeDebug` is not a task in this checkout; the native-enabled Debug command ran the configured CMake path instead.
- Contract implementation is limited to provider-neutral DTO/service/state/error types, mapper/validator, coordinator fallback orchestration, atomic commit policy, processing metadata and V3 archive compatibility.
- Live Provider/API key/network lyrics/device/UI/media/model-cache/V2-cleanup work remains deferred or prohibited by this stage.
- R1 focused security/BYOK/UI tests (16), full JVM tests, lint, normal Debug, native-enabled Debug and AndroidTest builds passed. No real key or live DeepSeek probe was used.

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
- Current key route is `DEVICE_DIRECT_BYOK / ANDROID_KEYSTORE_REQUIRED`: a user-entered Provider key may only be stored as Android Keystore-wrapped AES-256-GCM ciphertext and must stay outside APK/Git/logs/ordinary preferences/project archives; live authentication remains deferred.
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
- DeepSeek with `DEVICE_DIRECT_BYOK / ANDROID_KEYSTORE_REQUIRED` is frozen. Brain has formally accepted the physical-device BYOK and minimal-authentication evidence; remaining later decisions/evidence are online-lyrics provenance/licensing, song matching, lyrics/cue integration and distribution strategy. Tests must not scrape or bundle real copyrighted lyrics.
- GPU availability on the phone does not authorize a GPU backend; any GPU work requires an independent Spike and regression evidence.
- Preview/export visual equivalence must exclude letterbox/pillarbox regions and use the source video as the common coordinate system.

## Preserved state

- Do not clean, reset, stage, or commit the existing dirty `third_party/ffmpeg-kit` state.
- Preserve all 41 untracked files: 31 under `.emulator-test-assets/`, 9 under `tools/opus-mt-en-zh/`, and 1 `._cache_adb.exe`; do not commit them or other unrelated content.
- Do not push unless the user explicitly requests it.
- Preserve the user-approved but uncommitted `AGENTS.md` acceptance-matrix rule and three-document updates when creating the stage checkpoint.
- Do not stage unrelated dirty/untracked content in the checkpoint or feature commit.
- Do not implement live Provider calls, non-Keystore API-key storage, model caching, unrelated UI/media changes or V2 cleanup inside `V3-AI-CONTRACT-001`; the bounded R1 Android Keystore storage and minimal settings UI are explicitly authorized.

## Stage routing

| Stage | State |
|---|---|
| V2 | `USER_ACCEPTED / ARCHIVED_IN_DOCS_V2` |
| V3-DEC-001 | `PASS` |
| V3-AI-CONTRACT-001 | `PARTIAL_PASS / SECURE_BYOK_VERIFIED / DEEPSEEK_AUTH_VERIFIED / LIVE_LYRICS_FLOW_DEFERRED`（Brain formal verdict; R1 closed） |
| V3-ASR-SESSION-001 | `PARTIAL_PASS / WHISPER_SESSION_COMPONENT_VERIFIED / PHYSICAL_DEVICE_RUNTIME_DEFERRED_BY_USER`（Brain formal verdict） |
| V3-EDITOR-001 | `PARTIAL_PASS / EDITOR_COMPONENT_VERIFIED / PRODUCT_UI_REWORK_REQUIRED` |
| V3-EDITOR-002 | `PARTIAL_PASS / PER_CUE_STYLE_EDITOR_VERIFIED / PHYSICAL_DEVICE_UI_WAIVED_BY_USER`（R2 Developer candidate / Brain review pending） |
| V3-MEDIA-001 | `PLANNED` |
| V3-UI-001 | `PLANNED` |
| V3-AI-001 | `NOT_IMPLEMENTED / PRODUCTION_PROMPT_ABSENT / SEPARATE_STAGE_REQUIRED` |
| V3-CLEAN-001 | `PLANNED` |
| V3-E2E-003 | `PLANNED` |

## V3-EDITOR-001 component evidence (2026-08-10)

- Candidate: `PARTIAL_PASS / EDITOR_COMPONENT_VERIFIED / PHYSICAL_DEVICE_UI_DEFERRED_BY_USER`.
- E01–E18 component evidence passed: focused editor JVM 25/25, full `testDebugUnitTest` 192/192, ASR Python 6/6, `lintDebug` 0 errors/33 warnings, ordinary Debug, native-enabled Debug and AndroidTest APK builds all passed.
- Native-enabled app APK: 417,446,841 bytes. AndroidTest APK: 119,027 bytes.
- Model/archive uses v4 with safe v1/v2/v3 migration, one normalized project layout, one default style, nullable per-cue overrides, and cue-preserving text/timeline/confirmation edits. Compose preview and ASS export consume the same `CaptionRenderResolver`.
- No physical device was connected, installed, polled or instrumented. Editor physical UI, ASR A13/A15 and performance evidence remain in `FINAL_PHYSICAL_DEVICE_VERIFICATION_BACKLOG`; no formal product PASS is claimed.
- Next action: wait for Brain to establish the next complete acceptance matrix; do not start the lyrics chain or physical verification autonomously.
