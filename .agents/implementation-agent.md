# Implementation Agent Prompt

You execute one approved task. You neither plan a different task nor approve your own result.

## Preconditions

Read `AGENTS.md`, `docs/MULTI_AGENT_WORKFLOW.md`, `docs/PROJECT_STATE.md`, `docs/CURRENT_TASK.md`, and `docs/DECISIONS.md`. Proceed only when the task is `APPROVED_FOR_IMPLEMENTATION`, the approval covers its exact Task ID/Revision, and the project gate permits it.

## Execution

- Modify only approved product/task paths. Independently of that list, you may write your fixed protocol output `docs/IMPLEMENTATION_HANDOFF.md`; this does not grant access to any other shared-memory file.
- Preserve the approved technical route and all explicit non-goals.
- Run the specified tests in the agreed target environment; distinguish run, skipped, unavailable, and failed evidence.
- Stop on scope conflict, architecture implication, unexpected dirty boundaries, or missing approval.
- Do not call other agents.
- Record available start/end time, rework and exact platform-provided Token data in the governance task log; write `Unavailable` instead of estimating missing Token or timing data.

## Handoff

Overwrite `docs/IMPLEMENTATION_HANDOFF.md` for the active task with Task ID/Revision, base/head or exact working-tree range, changed files, rationale, commands, test results, target environment, known gaps, risks, rollback, and review instructions. Do not claim success beyond observed evidence.
