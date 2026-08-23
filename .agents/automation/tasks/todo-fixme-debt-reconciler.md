# TODO and FIXME Debt Reconciler

## Purpose

Audit source debt markers.

## Required Framework

Read and follow ../framework.md before repository work.

## Responsibility

source debt markers

## Allowed Scope

Files directly implicated by verified drift, plus this task state and report.

## Forbidden Scope

Production redesign, speculative refactors, unrelated domains, and another task state.

## Evidence Sources

TODO, FIXME, HACK, XXX, TEMP, code, tests, history, ADRs, issues

## Previous State

Read ../state/todo-fixme-debt-reconciler.yaml as context and revalidate every finding.

## Inspection Procedure

1. Read task, framework, state, and report.
2. Gather evidence in framework order.
3. Identify only reproducible or directly supported drift.
4. Independently verify cross-task signals.
5. Review changed files and scan staged content for secrets.

## Detection Rules

Classify STILL_RELEVANT, ALREADY_RESOLVED, STALE, ACTIONABLE_LOW_RISK, REQUIRES_PRODUCT_DECISION,
REQUIRES_ARCHITECTURAL_DECISION, or UNKNOWN.

## Classification

Apply LOW, MEDIUM, or HIGH risk and persist concise unresolved, blocked, ignored, or risky findings.

## Decision Rules

Implement mechanical LOW RISK only. If uncertainty persists, record it and continue safe unrelated
work.

## Allowed Changes

Minimal evidence-backed corrections to files directly implicated by verified drift. Only state and
report updates are unrestricted.

## Prohibited Changes

Do not guess intent, fabricate results, add fake findings, weaken validation, bypass checks, or
modify another task state.

## Risk Rules

LOW: apply autonomously, validate, Draft PR. MEDIUM: apply with strong evidence and tests, Draft
PR. HIGH deterministic: MAY implement remediation in the Draft PR; human merge is the approval
gate. HIGH ambiguous: persist the finding, do not guess. See framework.md.

## Validation

Validate changed files, affected module/command, relevant tests, then justified repository checks.
Prefer just. Record Passed, Failed, or Not run only.

## State

Owns ../state/todo-fixme-debt-reconciler.yaml; use the framework schema and never set execution data without an
actual run.

## Report

Owns ../reports/todo-fixme-debt-reconciler.md; report facts, evidence, result, validation, unresolved findings,
and risks without chain-of-thought or secrets.

## Pull Request

Create exactly one Draft Pull Request with framework sections. Suggested title: chore(debt):
reconcile deterministic TODO debt.

## Completion Criteria

Inspection, evidence classification, permitted correction or documented no-op, validation,
state/report update, changed-file review, secret check, commit, push, and Draft PR creation.
