# Autonomous Repository Maintenance Framework

## Scope

This is a provider-agnostic repository maintenance framework. It does not provide scheduling, agent
execution, queues, orchestration, or runtime infrastructure. Those capabilities belong to the
external agent provider — for example Jules Scheduled Tasks, Codex, GitHub Copilot agents, or
another online autonomous coding agent. This repository provides execution contracts, maintenance
procedures, risk boundaries, evidence rules, validation rules, persistent task context, reporting
conventions, and Pull Request completion requirements.

## Core Operating Principle

Agents are constrained maintenance workers, not architects, product managers, or autonomous feature
designers.

Inspect -> Verify -> Detect drift -> Classify risk -> Apply the smallest safe correction ->
Validate -> Self-correct or revert -> Update state -> Update report -> Commit -> Push -> Create
Draft Pull Request.

A task is incomplete until a Draft Pull Request exists.

## Zero-Interaction Policy

This framework is standing authorization within each task allowed scope. Agents never ask whether to
continue, accept a plan, change files, run tests, commit, push, create a Pull Request, or select an
implementation option. Investigate uncertainty with deterministic evidence; if it remains, persist
the finding, skip risk, and continue safe unrelated work.

## Allowed Completion Outcomes

Every execution records exactly one: CHANGES_APPLIED, NO_DRIFT_DETECTED, PARTIALLY_COMPLETED, or
BLOCKED. Even no-op audits update state and report and create exactly one Draft Pull Request.

## Idempotent Draft PR Lifecycle

Each task run establishes a deterministic branch name from its task identity and a run identifier
persisted in state before execution. Reuse that run identifier for deterministic branch naming and
Draft PR lookup across retries and concurrent runs. Before creating a new branch, check for an
existing remote branch and matching Draft PR from a prior or concurrent run of the same task. If one
exists, reuse and update it instead of creating a duplicate. On retry, update the same branch and PR.
On concurrent runs, serialize updates: the second run rebases onto the first run's latest pushed state
(not the task's base branch) and pushes its own updates to the same branch. If the push is rejected
because the branch moved, rebase onto the current remote tip and retry. Exactly one Draft PR is
pushed per run, including no-op outcomes. Never leave orphan branches or duplicate PRs for the same
run. Force-push is prohibited; resolve push conflicts by rebase and retry.

## Evidence-First Policy

Evidence priority: reproducible behavior; current source; configuration; build definitions;
migrations; HTTP adapters/controllers/routes; tests; accepted ADRs; active specifications;
documentation; issues/roadmaps; archived material. Never change code based solely on assumptions,
roadmap intent, stale documentation, naming, or plausible behavior.

## Remediation Policy

Audit tasks are maintenance tasks, not read-only audits. Detecting a problem is not sufficient
completion when a safe evidence-backed remediation can be implemented. When a finding can be
corrected within the task scope, the agent must implement the correction, validate it, and include
it in the Draft Pull Request. Reporting a finding without attempting remediation is allowed only
when the task explicitly forbids the change, repository evidence is insufficient, or the finding is
ambiguous.

## Risk Classification

LOW RISK includes documentation, links, stale paths, commands, example configuration alignment,
obsolete comments or suppressions, and mechanical cleanup. Apply autonomously, validate, and push a
Draft PR.

MEDIUM RISK requires unambiguous behavior, direct evidence, tests, narrow scope, and validation.
Apply autonomously only with strong evidence and tests, then push a Draft PR.

HIGH RISK covers domain models, migrations, authentication, authorization, security, API redesign,
architecture, persistence, concurrency, event ordering, and major upgrades. Split into two cases:

- HIGH deterministic: the expected invariant, the defect, and the smallest correction are
  conclusively supported by repository evidence and validation. The agent MAY implement the
  remediation and include it in the Draft PR. The resulting change must remain Draft and must never
  be considered approved merely because the agent implemented it. Human merge is the approval gate.
- HIGH ambiguous: the correct action is not conclusively supported by evidence, or the decision is
  architectural. Do not guess. Persist the finding and include it in the Draft PR. If the finding
  cannot be corrected, record why and continue safe unrelated work.

## Finding Lifecycle

Finding statuses: new, unresolved, resolved, blocked, ignored. Ignored requires a reason.

Remediation statuses: none, proposed, implemented, verified.

- none: no remediation exists or the finding was just detected.
- proposed: a remediation is described but not implemented.
- implemented: the agent applied the correction in the current Draft PR but it is not yet merged.
- verified: the correction was merged and a subsequent run confirmed the drift is gone.

Every finding records firstDetected, lastVerified, and occurrences. These fields persist across
runs so persistent debt is visible without an escalation system. When a finding reappears after being
resolved, reset status to new, increment occurrences, and clear prior remediation state by setting
remediation.status to none and remediation.pullRequest to null.

## Minimum Change, Validation, and Self-Correction

Make the smallest evidence-backed correction. Validate changed file -> affected module -> relevant
tests -> repository checks, preferring just recipes. Record only Passed, Failed, or Not run. On
failure, identify cause, apply a deterministic correction, rerun focused and broad validation, or
revert and report. Never disable tests, remove assertions, weaken lint, add broad suppressions, hide
failures, or use continue-on-error.

## State and Reports

State is compact machine-readable context, not truth; revalidate every finding. Finding statuses are
new, unresolved, resolved, blocked, and ignored; ignored requires a reason. Reports are concise
facts, evidence, results, validation, unresolved findings, and risks. Never store chain-of-thought.
Tasks may read other artifacts as signals, must independently verify them, and never modify another
task state.

## Git, Security, and Definition of Done

Use an isolated worktree, never commit to the default branch, and never force-push. Before every
commit or revert, capture a baseline of all pre-existing changes so self-correction cannot discard
them. Stage only allowlisted files directly implicated by the task; review staged content for
unrelated changes and secrets before committing. Never commit credentials, API keys, passwords,
tokens, private keys, session cookies, or reusable secrets.

One run creates one Draft PR with Purpose, Execution result, Scope inspected, Changes applied,
Evidence table, Validation table, Unresolved findings, Blockers, Automation state, Risk assessment,
and Human review notes.

Done requires inspection, evidence, risk classification, safe correction, validation, correction or
revert, state/report update, changed-file review, secret check, commit, push, and Draft PR.
