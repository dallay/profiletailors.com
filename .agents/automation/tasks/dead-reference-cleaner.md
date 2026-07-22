# Dead Documentation and Reference Cleaner

## Purpose

Audit dead repository references.

## Required Framework

Read and follow ../framework.md before repository work.

## Responsibility

dead repository references

## Allowed Scope

Files directly implicated by verified drift, plus this task state and report.

## Forbidden Scope

Production redesign, speculative refactors, unrelated domains, and another task state.

## Evidence Sources

docs, OpenSpec, ADRs, diagrams, images, commands, scripts, routes, endpoints, anchors, paths

## Previous State

Read ../state/dead-reference-cleanup.yaml as context and revalidate every finding.

## Inspection Procedure

1. Read task, framework, state, and report.
2. Gather evidence in framework order.
3. Identify only reproducible or directly supported drift.
4. Independently verify cross-task signals.
5. Review changed files and scan staged content for secrets.

## Detection Rules

Detect nonexistent files, directories, modules, routes, endpoints, ADRs, OpenSpec artifacts,
diagrams, images, commands, scripts, anchors.

## Classification

Apply LOW, MEDIUM, or HIGH risk and persist concise unresolved, blocked, ignored, or risky findings.

## Decision Rules

Correct deterministic replacements only; never guess. If uncertainty persists, record it and
continue safe unrelated work.

## Allowed Changes

Minimal evidence-backed corrections to files directly implicated by verified drift. Only state and
report updates are unrestricted.

## Prohibited Changes

Do not guess intent, fabricate results, add fake findings, weaken validation, bypass checks, or
modify another task state.

## Risk Rules

Autonomously apply LOW only. MEDIUM requires unambiguous evidence and validation. HIGH is reported
by default.

## Validation

Validate changed files, affected module/command, relevant tests, then justified repository checks.
Prefer just. Record Passed, Failed, or Not run only.

## State

Owns ../state/dead-reference-cleanup.yaml; use the framework schema and never set execution data
without an actual run.

## Report

Owns ../reports/dead-reference-cleanup.md; report facts, evidence, result, validation, unresolved
findings, and risks without chain-of-thought or secrets.

## Pull Request

Create exactly one Draft Pull Request with framework sections. Suggested title: docs: remove dead
repository references.

## Completion Criteria

Inspection, evidence classification, permitted correction or documented no-op, validation,
state/report update, changed-file review, secret check, commit, push, and Draft PR creation.
