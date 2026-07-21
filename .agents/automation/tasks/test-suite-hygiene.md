# Test Suite Hygiene Auditor

## Purpose

Audit test hygiene drift.

## Required Framework

Read and follow ../framework.md before repository work.

## Responsibility

test hygiene drift

## Allowed Scope

Files directly implicated by verified drift, plus this task state and report.

## Forbidden Scope

Production redesign, speculative refactors, unrelated domains, and another task state.

## Evidence Sources

test sources/configuration, CI, build definitions, suppressions

## Previous State

Read ../state/test-suite-hygiene.yaml as context and revalidate every finding.

## Inspection Procedure

1. Read task, framework, state, and report.
2. Gather evidence in framework order.
3. Identify only reproducible or directly supported drift.
4. Independently verify cross-task signals.
5. Review changed files and scan staged content for secrets.

## Detection Rules

Detect disabled, skipped, todo, xit, xdescribe, @Disabled, commented tests, empty assertions, sleeps, obsolete suppressions.

## Classification

Apply LOW, MEDIUM, or HIGH risk and persist concise unresolved, blocked, ignored, or risky findings.

## Decision Rules

Never weaken assertions, delete tests, or hide failures. If uncertainty persists, record it and continue safe unrelated work.

## Allowed Changes

Minimal evidence-backed corrections only. Update only ../state/test-suite-hygiene.yaml and ../reports/test-suite-hygiene.md.

## Prohibited Changes

Do not guess intent, fabricate results, add fake findings, weaken validation, bypass checks, or modify another task state.

## Risk Rules

Autonomously apply LOW only. MEDIUM requires unambiguous evidence and validation. HIGH is reported by default.

## Validation

Validate changed files, affected module/command, relevant tests, then justified repository checks. Prefer just. Record Passed, Failed, or Not run only.

## State

Owns ../state/test-suite-hygiene.yaml; use the framework schema and never set execution data without an actual run.

## Report

Owns ../reports/test-suite-hygiene.md; report facts, evidence, result, validation, unresolved findings, and risks without chain-of-thought or secrets.

## Pull Request

Create exactly one Draft Pull Request with framework sections. Suggested title: test: reconcile deterministic test hygiene.

## Completion Criteria

Inspection, evidence classification, permitted correction or documented no-op, validation, state/report update, changed-file review, secret check, commit, push, and Draft PR creation.
