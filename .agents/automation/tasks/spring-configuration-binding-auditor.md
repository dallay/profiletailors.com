# Spring Configuration Binding Auditor

## Purpose

Audit Spring configuration binding.

## Required Framework

Read and follow ../framework.md before repository work.

## Responsibility

Spring configuration binding

## Allowed Scope

Files directly implicated by verified drift, plus this task state and report.

## Forbidden Scope

Production redesign, speculative refactors, unrelated domains, and another task state.

## Evidence Sources

application YAML, @ConfigurationProperties, config classes, validation, placeholders, tests,
.env.example

## Previous State

Read ../state/spring-configuration-binding.yaml as context and revalidate every finding.

## Inspection Procedure

1. Read task, framework, state, and report.
2. Gather evidence in framework order.
3. Identify only reproducible or directly supported drift.
4. Independently verify cross-task signals.
5. Review changed files and scan staged content for secrets.

## Detection Rules

Detect unused, missing, duplicated, mismatched, incorrectly documented properties.

## Classification

Apply LOW, MEDIUM, or HIGH risk and persist concise unresolved, blocked, ignored, or risky findings.

## Decision Rules

Security-sensitive config is HIGH RISK; never invent defaults or weaken validation. If uncertainty
persists, record it and continue safe unrelated work.

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

Owns ../state/spring-configuration-binding.yaml; use the framework schema and never set execution
data without an actual run.

## Report

Owns ../reports/spring-configuration-binding.md; report facts, evidence, result, validation,
unresolved findings, and risks without chain-of-thought or secrets.

## Pull Request

Create exactly one Draft Pull Request with framework sections. Suggested title: docs(config):
reconcile Spring binding evidence.

## Completion Criteria

Inspection, evidence classification, permitted correction or documented no-op, validation,
state/report update, changed-file review, secret check, commit, push, and Draft PR creation.
