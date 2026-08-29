---
name: final-diff-hygiene
description: Clean the current change's comments, documentation, and change summary so they describe only the final implementation and verified rationale. Use for final cleanup, deslop, comment cleanup, or PR/MR text cleanup; not for correctness review or behavior changes.
---

# Final Diff Hygiene

Clean only the current task's change surface. Preserve intended behavior, frozen acceptance criteria, required evidence, and repository state records.

## Decide what stays

- Keep a comment when it explains a verified non-obvious reason, external constraint, or condition that invalidates the implementation.
- Remove comments that mirror the code, record intermediate attempts, compare against an implementation that no longer exists, or speculate about future work.
- Keep required task, state, migration, and acceptance records. Their historical evidence is not disposable commentary.
- If the reason for a statement cannot be verified from the current task, source, or owning contract, do not invent one. Leave the artifact unchanged when deleting it could hide a real constraint, and report the evidence gap.

## Describe the final change

- State the observable final behavior first.
- Add only rationale or trade-offs that a reviewer cannot recover from the final diff.
- Do not describe temporary code, abandoned experiments, rejected alternatives, or states absent from the final diff.
- Do not list routine test, lint, or build output in PR/MR text unless a repository template, the frozen acceptance matrix, or a risk-specific manual result requires it.
- Do not remove validation evidence from the task's completion report when repository instructions require that evidence.

## Finish proportionately

Inspect the final scoped diff once after edits. Use the validation level already required by the current task: exact diff for S0, focused validation for S1, and the frozen evidence-first path for S2. Do not add a broad audit or test run solely because this skill was invoked.

Report changed artifacts, retained non-obvious rationale, unresolved evidence gaps, and the validation actually required by the task.

Method adapted for LyricCaptioner from `liby/dotfiles` at commit `9bcb53abfc1c26bec1de918f9dd520430fe720ad`; wording and workflow are project-specific.
