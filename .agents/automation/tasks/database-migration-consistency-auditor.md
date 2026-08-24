# Database Migration Consistency Auditor

## Purpose

Audit schema and persistence consistency.

## Required Framework

Read and follow ../framework.md before repository work.

## Responsibility

schema and persistence consistency

## Allowed Scope

Files directly implicated by verified drift, plus this task state and report.

## Forbidden Scope

Production redesign, speculative refactors, unrelated domains, and another task state.

## Evidence Sources

migrations, R2DBC repositories, mappings, SQL, test DB/H2, schema docs, tests

## Previous State

Read ../state/database-migration-consistency-auditor.yaml as context and revalidate every finding.

## Inspection Procedure

1. Read task, framework, state, and report.
2. Gather evidence in framework order.
3. Identify only reproducible or directly supported drift.
4. Independently verify cross-task signals.
5. Review changed files and scan staged content for secrets.

## Detection Rules

Treat migrations and persistence semantics as HIGH RISK unless documentary/mechanical.

## Classification

Apply LOW, MEDIUM, or HIGH risk and persist concise unresolved, blocked, ignored, or risky findings.

## Decision Rules

Never create complex production migrations or destructive changes. If uncertainty persists, record
it and continue safe unrelated work.

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

Owns ../state/database-migration-consistency-auditor.yaml; use the framework schema and never set execution
data without an actual run.

## Report

Owns ../reports/database-migration-consistency-auditor.md; report facts, evidence, result, validation,
unresolved findings, and risks without chain-of-thought or secrets.

## Pull Request

Create exactly one Draft Pull Request with framework sections. Suggested title: docs(database):
record migration consistency evidence.

## Completion Criteria

Inspection, evidence classification, permitted correction or documented no-op, validation,
state/report update, changed-file review, secret check, commit, push, and Draft PR creation.
