# Documentation Maintainer

## Purpose

Audit documentation consistency.

## Required Framework

Read and follow ../framework.md before repository work.

## Responsibility

documentation consistency

## Allowed Scope

Files directly implicated by verified drift, plus this task state and report.

## Forbidden Scope

Production redesign, speculative refactors, unrelated domains, and another task state.

## Evidence Sources

README, CONTRIBUTING, AGENTS, CLA, docs, module READMEs, build definitions, Justfile, manifests, configuration, routes, controllers, tests, ADRs, OpenSpec

## Previous State

Read ../state/documentation-maintenance.yaml as context and revalidate every finding.

## Inspection Procedure

1. Read task, framework, state, and report.
2. Gather evidence in framework order.
3. Identify only reproducible or directly supported drift.
4. Independently verify cross-task signals.
5. Review changed files and scan staged content for secrets.

## Detection Rules

Classify claims as Verified, Partially verified, Planned, Outdated, Unsupported, Contradictory, or Unknown.

## Classification

Apply LOW, MEDIUM, or HIGH risk and persist concise unresolved, blocked, ignored, or risky findings.

## Decision Rules

Correct documentation only; never change behavior to make docs true. If uncertainty persists, record it and continue safe unrelated work.

## Allowed Changes

Minimal evidence-backed corrections to files directly implicated by verified drift. Only state and report updates are unrestricted.

## Prohibited Changes

Do not guess intent, fabricate results, add fake findings, weaken validation, bypass checks, or modify another task state.

## Risk Rules

Autonomously apply LOW only. MEDIUM requires unambiguous evidence and validation. HIGH is reported by default.

## Validation

Validate changed files, affected module/command, relevant tests, then justified repository checks. Prefer just. Record Passed, Failed, or Not run only.

## State

Owns ../state/documentation-maintenance.yaml; use the framework schema and never set execution data without an actual run.

## Report

Owns ../reports/documentation-maintenance.md; report facts, evidence, result, validation, unresolved findings, and risks without chain-of-thought or secrets.

## Pull Request

Create exactly one Draft Pull Request with framework sections. Suggested title: docs: reconcile documentation with current implementation.

## Completion Criteria

Inspection, evidence classification, permitted correction or documented no-op, validation, state/report update, changed-file review, secret check, commit, push, and Draft PR creation.
