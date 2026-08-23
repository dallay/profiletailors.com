# Security Configuration Drift Auditor

## Purpose

Audit security configuration drift.

## Required Framework

Read and follow ../framework.md before repository work.

## Responsibility

security configuration drift

## Allowed Scope

Files directly implicated by verified drift, plus this task state and report.

## Forbidden Scope

Production redesign, speculative refactors, unrelated domains, and another task state.

## Evidence Sources

Spring Security, frontend guards, authorization metadata, CORS, CSRF, cookies, routes, exclusions,
properties, tests, docs

## Previous State

Read ../state/security-configuration-drift-auditor.yaml as context and revalidate every finding.

## Inspection Procedure

1. Read task, framework, state, and report.
2. Gather evidence in framework order.
3. Identify only reproducible or directly supported drift.
4. Independently verify cross-task signals.
5. Review changed files and scan staged content for secrets.

## Detection Rules

Treat runtime security behavior as HIGH RISK and classify exposure.

## Classification

Apply LOW, MEDIUM, or HIGH risk and persist concise unresolved, blocked, ignored, or risky findings.

## Decision Rules

Never weaken security; report unless a correction narrows exposure and is tested. If uncertainty
persists, record it and continue safe unrelated work.

## Allowed Changes

Minimal evidence-backed corrections only. Update only ../state/security-configuration-drift-auditor.yaml and
../reports/security-configuration-drift-auditor.md.

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

Owns ../state/security-configuration-drift-auditor.yaml; use the framework schema and never set execution
data without an actual run.

## Report

Owns ../reports/security-configuration-drift-auditor.md; report facts, evidence, result, validation,
unresolved findings, and risks without chain-of-thought or secrets.

## Pull Request

Create exactly one Draft Pull Request with framework sections. Suggested title: docs(security):
record configuration drift evidence.

## Completion Criteria

Inspection, evidence classification, permitted correction or documented no-op, validation,
state/report update, changed-file review, secret check, commit, push, and Draft PR creation.
