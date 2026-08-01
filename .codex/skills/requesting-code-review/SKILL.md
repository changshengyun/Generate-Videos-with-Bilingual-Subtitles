---
name: requesting-code-review
description: Use when completing tasks, implementing major features, or before merging to verify work meets requirements
---

# Requesting Code Review

Dispatch a code reviewer subagent to catch issues before they cascade. The reviewer gets precisely crafted context for evaluation — never your session's history.

**Core principle:** Review early, review often.

## When to Request Review

**Mandatory:**
- After each task in subagent-driven development
- After completing major feature
- Before merge to main

**Optional but valuable:**
- When stuck (fresh perspective)
- Before refactoring (baseline check)
- After fixing complex bug

## How to Request

**1. Get git SHAs:**
```bash
BASE_SHA=$(git rev-parse HEAD~1)  # or origin/main
HEAD_SHA=$(git rev-parse HEAD)
```

**2. Dispatch code reviewer subagent:**

Dispatch a `general-purpose` subagent, filling the template at [code-reviewer.md](code-reviewer.md)

**Placeholders:**
- `{DESCRIPTION}` - Brief summary of what you built
- `{PLAN_OR_REQUIREMENTS}` - What it should do
- `{BASE_SHA}` - Starting commit
- `{HEAD_SHA}` - Ending commit

**3. Act on feedback:**
- Fix Critical issues immediately
- Fix Important issues before proceeding
- Note Minor issues for later
- Push back if reviewer is wrong (with reasoning)

## Example

```
[Just completed Task 2: Add verification function]

You: Let me request code review before proceeding.

BASE_SHA=$(git log --oneline | grep "Task 1" | head -1 | awk '{print $1}')
HEAD_SHA=$(git rev-parse HEAD)

[Dispatch code reviewer subagent]
  DESCRIPTION: Added verifyIndex() and repairIndex() with 4 issue types
  PLAN_OR_REQUIREMENTS: Task 2 from docs/superpowers/plans/deployment-plan.md
  BASE_SHA: a7981ec
  HEAD_SHA: 3df7661

[Subagent returns]:
  Strengths: Clean architecture, real tests
  Issues:
    Important: Missing progress indicators
    Minor: Magic number (100) for reporting interval
  Assessment: Ready to proceed

You: [Fix progress indicators]
[Continue to Task 3]
```

## Common Rationalizations

| Excuse | Reality |
|--------|---------|
| "I'll just review the diff myself instead of dispatching a reviewer" | You're the coordinator — reviewing the diff inline burns the context window you need to keep driving the work. Dispatch a reviewer subagent: the diff and the evaluation live in its context, and only the findings come back to you. |
| "The reviewer needs my whole session history to understand the change" | Hand it precisely crafted context, never your session's history. That keeps the reviewer on the work product, not your thought process. |

## Red Flags

**Never:**
- Skip review because "it's simple"
- Ignore Critical issues
- Proceed with unfixed Important issues
- Argue with valid technical feedback

**If reviewer wrong:**
- Push back with technical reasoning
- Show code/tests that prove it works
- Request clarification

See template at: [code-reviewer.md](code-reviewer.md)

## LyricCaptioner Project Override

When this skill is used in `lyric-captioner-android`, the repository rules below override generic upstream workflow advice:

- The Orchestrator or user dispatches `.agents/code-review-worker.md`; the worker reviews independently and never dispatches another agent.
- Read `AGENTS.md`, `docs/DEVELOPMENT_ROADMAP.md`, `docs/CURRENT_TASK.md`, and `docs/PROJECT_STATE.md` before evaluating code. These files define scope, gates, and accepted evidence; archives and chat history do not override them.
- Review is read-only. Do not edit product code, tests, project state, Git index, HEAD, branch, device state, or user data. Do not create a worktree unless the user explicitly authorizes it. Return findings in the response rather than creating a review document.
- The upstream instruction to fix Critical or Important findings applies to a later authorized Implementation task, never to the Review Worker itself.
- For full-project review, inspect current HEAD plus relevant tracked working-tree changes. Preserve and normally exclude the known dirty `third_party/ffmpeg-kit` boundary and unrelated untracked assets unless the review request explicitly includes them.
- Apply Android/Kotlin-specific checks: lifecycle and coroutine ownership, Compose state and accessibility, SAF/content URI permissions and recovery, Media3/FFmpegKit resource cleanup, JNI/native/model failure paths, ONNX/local-model privacy, project archive compatibility, cancellation, source-file safety, and tests that prove product behavior rather than Demo or fallback behavior.
- Findings must cite exact files and lines, explain user impact and evidence, distinguish confirmed defects from risks or missing evidence, and end with exactly one advisory verdict: `PASS`, `CHANGES_REQUIRED`, or `BLOCKED`.
