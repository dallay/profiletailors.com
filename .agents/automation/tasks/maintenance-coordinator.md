# Maintenance Coordinator

## Purpose

Audit automation control plane aggregation.

## Required Framework

Read and follow ../framework.md before repository work.

## Responsibility

automation control plane aggregation

## Allowed Scope

Files directly implicated by verified drift, plus this task state and report.

## Forbidden Scope

Production redesign, speculative refactors, unrelated domains, and another task state.

## Evidence Sources

all task definitions, state, reports, automation paths

## Previous State

Read ../state/maintenance-coordinator.yaml as context and revalidate every finding.

## Inspection Procedure

1. Read task, framework, state, and report.
2. Gather evidence in framework order.
3. Identify only reproducible or directly supported drift.
4. Independently verify cross-task signals.
5. Review changed files and scan staged content for secrets.

## Detection Rules

Classify HEALTHY, NO_RECENT_EXECUTION, HAS_UNRESOLVED_FINDINGS, PARTIALLY_COMPLETED, BLOCKED,
STATE_REPORT_MISMATCH, MISSING_STATE, MISSING_REPORT, or UNKNOWN.

## Classification

Apply LOW, MEDIUM, or HIGH risk and persist concise unresolved, blocked, ignored, or risky findings.

## Decision Rules

Own only coordinator state/report, never other state, use AUTO- identifiers. If uncertainty
persists, record it and continue safe unrelated work.

## Allowed Changes

Minimal evidence-backed corrections only. Update only ../state/maintenance-coordinator.yaml and
../reports/maintenance-coordinator.md.

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

Owns ../state/maintenance-coordinator.yaml; use the framework schema and never set execution data
without an actual run.

## Report

Owns ../reports/maintenance-coordinator.md; report facts, evidence, result, validation, unresolved
findings, and risks without chain-of-thought or secrets.

## Pull Request

Create exactly one Draft Pull Request with framework sections. Suggested title: chore(automation):
update autonomous maintenance status.

## Completion Criteria

Inspection, evidence classification, permitted correction or documented no-op, validation,
state/report update, changed-file review, secret check, commit, push, and Draft PR creation.
