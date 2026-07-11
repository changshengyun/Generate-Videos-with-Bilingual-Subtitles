---
name: evidence-first-debugging
description: Use for build failures, runtime crashes, failing tests, dependency conflicts, integration failures, device incompatibility, performance regressions, or unexpected outputs. Reproduce the failure, preserve evidence, localize the failing layer, test discriminating hypotheses, apply the smallest fix, and require proof before proposing architecture replacement.
---

# Evidence-First Debugging

## Objective

Diagnose and repair the current failure without speculative rewrites or premature technology switching.

## Freeze scope

- Pause feature expansion.
- Do not change the primary architecture, framework, language, build system, runtime, or core dependency strategy during diagnosis.
- Preserve the failing state and create a Git checkpoint when safe.

## Step 1: Establish the failure

Record in `docs/debug/DEBUG_REPORT.md`:

- expected behavior;
- actual behavior;
- exact reproduction steps;
- target environment/device;
- relevant versions and configuration;
- complete error text, logs, stack trace, exit code, and timestamps;
- whether the failure is deterministic;
- last known working state and relevant diff.

Do not paraphrase away important log details.

## Step 2: Localize the failing layer

Classify the failure, allowing multiple layers when evidence supports it:

- environment or permissions;
- toolchain/build system;
- dependency resolution or ABI/API mismatch;
- configuration/secrets/network;
- application logic;
- data/input;
- external service/API;
- OS/device/hardware compatibility;
- concurrency/resource exhaustion;
- performance or timeout;
- output validation/playback/consumer compatibility.

Identify the earliest confirmed failing boundary, not merely the last visible symptom.

## Step 3: Build a hypothesis table

For each plausible root cause record:

- hypothesis;
- supporting evidence;
- contradicting evidence;
- confidence;
- cheapest discriminating experiment;
- expected result if true;
- expected result if false;
- risk of the experiment.

Order experiments by information gain, cost, and safety. Prefer read-only checks and minimal isolated reproductions.

## Step 4: Run one discriminating experiment at a time

- Change one major variable at a time.
- Preserve command output and measurements.
- Update the hypothesis table after every experiment.
- Distinguish a workaround from a root-cause fix.
- Do not interpret one failed patch as proof that the architecture is invalid.

## Step 5: Apply the smallest justified fix

Before editing, state:

- confirmed root cause or best-supported cause;
- files/configuration to change;
- why the change addresses the cause;
- regression risk;
- rollback method.

Apply the minimum change, then rerun the original reproduction and relevant regression checks.

## Step 6: Validate resolution

A fix is accepted only when:

- the original reproduction no longer fails;
- relevant tests pass;
- the agreed target environment/device is verified when applicable;
- output integrity is checked;
- no silent bypass or hidden fallback was introduced;
- measurements satisfy the relevant acceptance criteria.

## Architecture-change threshold

Propose architecture replacement only when evidence demonstrates at least one of the following:

- a hard constraint cannot be met by the current route;
- the target platform lacks a required capability with no acceptable implementation path;
- measured performance/resource use remains outside the threshold after reasonable implementation-level corrections;
- dependency, licensing, privacy, security, or maintenance constraints fundamentally invalidate the route.

If the threshold is met, create `docs/decisions/CHANGE_PROPOSAL.md` containing:

1. current failure and reproduction;
2. evidence and confirmed root cause;
3. why implementation-level fixes are insufficient;
4. alternatives considered;
5. files, modules, data, and environments affected;
6. migration cost and risks;
7. rollback plan;
8. recommendation and confidence;
9. explicit approval request.

Do not execute the architecture change before approval.

## Final report

Update `docs/debug/DEBUG_REPORT.md` and `docs/PROJECT_STATE.md` with:

- verified facts;
- evidence-backed inferences;
- rejected hypotheses;
- remaining unknowns;
- fix and validation results;
- whether the failure was implementation-level or architecture-level;
- next permitted action.
