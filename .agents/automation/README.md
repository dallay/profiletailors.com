# Autonomous Repository Maintenance

## Purpose

`.agents/automation` is a provider-agnostic repository maintenance framework.

It does not provide scheduling, agent execution, queues, orchestration, or runtime infrastructure.
Those capabilities belong to the external agent provider — for example Jules Scheduled Tasks, Codex,
GitHub Copilot agents, or another online autonomous coding agent.

This repository provides:

- execution contracts;
- maintenance procedures;
- risk boundaries;
- evidence rules;
- validation rules;
- persistent task context;
- reporting conventions;
- Pull Request completion requirements.

## Directory Structure

.agents/automation/

- README.md: orientation and scheduler entry point.
- framework.md: mandatory shared execution contract.
- tasks/: 20 task definitions.
- state/: 20 task-owned YAML state files.
- reports/: 20 task-owned operational reports.

## Lifecycle, Ownership, Results, and Risk

Every run inspects, verifies, detects drift, classifies risk, makes the smallest safe correction,
validates, self-corrects or reverts, updates state/report, commits, pushes, and creates one Draft PR
per the Idempotent Draft PR Lifecycle defined in framework.md. No-op runs are observable and also
result in exactly one reusable Draft PR. Tasks own only matching state/report; cross-task artifacts
are signals needing independent verification. Standard results are CHANGES_APPLIED,
NO_DRIFT_DETECTED, PARTIALLY_COMPLETED, and BLOCKED. Risk boundaries, remediation policy, and finding
lifecycle are defined by framework.md.

## Adding a Task

Add a lowercase kebab-case definition, matching empty state/report, exclusive ownership, evidence,
deterministic rules, risk boundaries, validation, and Draft PR completion.

## Scheduler Prompt

Execute the autonomous repository maintenance task defined in:

.agents/automation/tasks/<task-name>.md

Follow:

.agents/automation/framework.md

Read both files completely before making changes.

This is a zero-interaction scheduled execution. Do not ask for confirmation, approval, or
implementation decisions. Detect, remediate when permitted, validate, update task state/report,
commit, push, and create the required Draft Pull Request. Follow the Idempotent Draft PR Lifecycle:
acquire an atomic claim on the run identifier before any state write or Draft PR lookup, using
create-if-absent, compare-and-swap, or lease semantics so concurrent runs cannot persist different
identifiers; the winner owns the run identifier, branch, and Draft PR and persists it in state
before execution; losers reuse the winner's run identifier, branch, and Draft PR instead of
creating their own; use it for deterministic branch naming and Draft PR lookup across retries and
concurrent runs; check for an existing matching Draft PR to reuse or update instead of creating
duplicates; handle retries and concurrent runs by serializing updates to the same branch; resolve
push conflicts by rebase and retry, never force-push; push exactly one Draft PR per run, including
no-op outcomes. The human will review the Pull Request after execution.
