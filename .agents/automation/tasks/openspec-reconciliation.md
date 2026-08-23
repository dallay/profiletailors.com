# OpenSpec Implementation Reconciliation

## Purpose

Audit OpenSpec versus implementation reconciliation.

## Required Framework

Read and follow ../framework.md before repository work.

## Responsibility

OpenSpec versus implementation reconciliation

## Allowed Scope

Files directly implicated by verified drift, plus this task state and report.

## Forbidden Scope

Production redesign, speculative refactors, unrelated domains, and another task state.

## Evidence Sources

OpenSpec, code, tests, ADRs, and repository workflow

## Previous State

Read ../state/openspec-reconciliation.yaml as context and revalidate every finding.

## Inspection Procedure

1. Read task, framework, state, and report.
2. Gather evidence in framework order.
3. Identify only reproducible or directly supported drift.
4. Independently verify cross-task signals.
5. Review changed files and scan staged content for secrets.

## Detection Rules

Classify NOT_STARTED, PARTIALLY_IMPLEMENTED, IMPLEMENTED, IMPLEMENTED_NOT_VERIFIED, STALE, BLOCKED,
or UNKNOWN.

## Classification

Apply LOW, MEDIUM, or HIGH risk and persist concise unresolved, blocked, ignored, or risky findings.

## Decision Rules

Never implement merely to satisfy a spec; archive only when conclusive. If uncertainty persists,
record it and continue safe unrelated work.

## Allowed Changes

Minimal evidence-backed corrections only. Update only ../state/openspec-reconciliation.yaml and
../reports/openspec-reconciliation.md.

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

Owns ../state/openspec-reconciliation.yaml; use the framework schema and never set execution data
without an actual run.

## Report

Owns ../reports/openspec-reconciliation.md; report facts, evidence, result, validation, unresolved
findings, and risks without chain-of-thought or secrets.

## Pull Request

Create exactly one Draft Pull Request with framework sections. Suggested title: docs: reconcile
OpenSpec implementation evidence.

## Completion Criteria

Inspection, evidence classification, permitted correction or documented no-op, validation,
state/report update, changed-file review, secret check, commit, push, and Draft PR creation.
