# LyricCaptioner V3 project state

## Authoritative current state

- Repository: `D:\DevEnv\Projects\lyric-captioner-android`
- Branch: `migration/lyric-captioner-history`
- Current task: `V3-DEC-001 / AWAITING_USER_RESPONSES`
- V2 functional code baseline: `8a48d88`
- V2 archive: `docs-v2/`
- Current gate: `PLANNING_ONLY`
- Implementation authorization: not granted; user requested review of the V3 task before development
- Next permitted action: collect row-by-row answers from `CURRENT_TASK.md`, reconcile them into the V3 architecture, then generate the first executable Developer Prompt

## Version transition

- The user explicitly accepted V2 after manual verification on `fcf4b0cb / 25098PN5AC / ARM64 / 1220x2656 / 520 dpi` and waived further V2 operations.
- The prior active `docs/` was archived to `docs-v2/`.
- The former `docs-v3/` architecture draft was promoted into the new active `docs/` and supplemented with the required three-document state surface.
- V2 code was not changed during this transition. `third_party/ffmpeg-kit`, models, media, test assets, and existing untracked content remain outside the documentation commit.

## Confirmed V3 direction

- Add a process-level cache for the current Whisper model context; keep model state separate from task state and serialize use of one context.
- Redesign interaction before visual styling.
- After successful subtitle generation, navigate to subtitle editing and keep the subtitle list inside that section.
- Use one project-level caption text box within the active video image and cue-level style overrides.
- Resolve subtitle position and font size in source-video coordinates so preview, fullscreen, aspect-ratio changes, and export agree.
- Prefer system gallery/media experiences for default import and export, with an advanced location override.
- Replace test-workbench presentation with a product UI and remove visible development/version labels.
- Preserve the existing cloud structured-caption enhancement proposal behind explicit API/backend/privacy decisions.

## Evidence and claim boundaries

- Whisper caching may improve repeated-task startup time but does not itself improve core inference speed or recognition accuracy.
- User-observed V2 recognition usability is accepted as product feedback, not fixture-backed WER/CER evidence.
- OPUS-MT works locally but its Chinese naturalness is not accepted as final product quality.
- GPU availability on the phone does not authorize a GPU backend; any GPU work requires an independent Spike and regression evidence.
- Preview/export visual equivalence must exclude letterbox/pillarbox regions and use the source video as the common coordinate system.

## Preserved state

- Do not clean, reset, stage, or commit the existing dirty `third_party/ffmpeg-kit` state.
- Do not commit models, `.emulator-test-assets/`, `tools/opus-mt-en-zh/`, `._cache_adb.exe`, or unrelated untracked content.
- Do not push unless the user explicitly requests it.
- Do not start V3 business-code implementation until `V3-DEC-001` is resolved.

## Stage routing

| Stage | State |
|---|---|
| V2 | `USER_ACCEPTED / ARCHIVED_IN_DOCS_V2` |
| V3-DEC-001 | `AWAITING_USER_RESPONSES` |
| V3-ASR-CACHE-001 | `PLANNED` |
| V3-UX-001 | `PLANNED` |
| V3-MEDIA-001 | `PLANNED` |
| V3-UI-001 | `PLANNED` |
| V3-API-001 | `BLOCKED_BY_DECISION` |
| V3-AI-001 | `BLOCKED_BY_DECISION` |
| V3-E2E-003 | `PLANNED` |
