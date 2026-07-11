---
name: project-architecture-gate
description: Use before production coding for a new software or AI project, a new subsystem, or a major feature with unvalidated technical assumptions. Convert fuzzy requirements into measurable acceptance criteria, audit the development and target environments, compare technical routes, identify fatal assumptions, and design disposable feasibility spikes. Do not use for a small localized change whose architecture and target environment are already validated.
---

# Project Architecture Gate

## Objective

Prevent “write first, discover incompatibility later.” Produce evidence-backed project specifications and a technical-validation plan before substantial production implementation.

## Non-negotiable behavior

- Do not build the complete product in this workflow.
- Do not bulk-install or upgrade tooling before presenting the environment plan.
- Do not select a technology merely because it is popular or familiar.
- Do not convert unknowns into confident claims.
- A hard constraint is a veto condition, not a weighted preference.

## Step 1: Read durable context

1. Read `AGENTS.md` and any nested instruction files that apply.
2. Read `docs/PROJECT_STATE.md` if present.
3. Read existing requirement, architecture, decision, environment, and test documents.
4. Inspect the repository and current environment before proposing changes.

## Step 2: Convert the request into a testable specification

Create or update `docs/REQUIREMENTS.md` with:

- user outcome and real usage scenario;
- MVP functions;
- explicit non-goals;
- hard constraints;
- target runtime and minimum supported device/environment;
- privacy, network, cost, licensing, compatibility, resource, and maintenance constraints;
- functional acceptance criteria;
- performance and resource acceptance criteria;
- failure and recovery acceptance criteria;
- observability and diagnostic requirements;
- assumptions and unknowns.

Each acceptance criterion must contain:

- preconditions;
- input;
- action;
- expected observable result;
- measurement method;
- pass/fail threshold;
- target environment.

Do not invent precise thresholds without evidence. When a threshold is unknown, label it `TO BE BASELINED` and design a spike to measure it.

## Step 3: Audit environments

Create or update `docs/ENVIRONMENT_REPORT.md` with:

- development OS, architecture, hardware, available memory and storage;
- installed SDKs, compilers, runtimes, build tools, package managers, and relevant versions;
- network, proxy, permissions, certificates, and download restrictions;
- target runtime/device information;
- confirmed compatibility facts;
- missing components;
- version conflicts;
- proposed installation plan;
- changes that affect global state;
- rollback instructions.

Prefer read-only inspection first.

## Step 4: Compare technical routes

Create or update `docs/TECH_OPTIONS.md`.

Present at least three meaningful routes when alternatives exist:

- conservative route;
- balanced route;
- aggressive/experimental route.

Evaluate:

- compliance with hard constraints;
- target compatibility;
- feasibility of the critical path;
- performance and resource use;
- maturity and ecosystem;
- build and dependency risk;
- debugging and observability;
- privacy, security, licensing, and cost;
- maintenance and migration cost;
- rollback cost.

For every route, list:

- verified evidence;
- assumptions;
- unresolved risks;
- what would falsify the route;
- estimated blast radius if replaced later.

Disqualify routes that violate a hard constraint.

## Step 5: Rank fatal assumptions

Create a risk table using impact, uncertainty, verification cost, and reversibility. Prioritize assumptions that could invalidate the project or force architecture replacement.

## Step 6: Design disposable technical spikes

Create or update `docs/SPIKE_PLAN.md`.

For each high-risk assumption define:

- exact hypothesis;
- minimal isolated experiment;
- required environment and data;
- commands or prototype scope;
- expected evidence;
- quantitative success threshold or explicit qualitative decision rule;
- failure interpretation;
- whether failure invalidates the route or only the implementation;
- cleanup instructions.

The spike must be smaller than the MVP and may be discarded.

## Step 7: Produce a decision gate

Create or update `docs/PROJECT_STATE.md` with:

- current gate: `ARCHITECTURE_REVIEW`;
- canonical documents and their paths;
- recommended route and confidence;
- rejected routes and reasons;
- required spikes;
- unresolved questions;
- decisions requiring user approval;
- the only next permitted action.

Stop before substantial production implementation. Ask for approval of the technical route and spike plan.

## Completion criteria

This skill is complete only when the requirements, environment report, option comparison, spike plan, and project state are internally consistent and identify all unverified assumptions that could materially change the architecture.
