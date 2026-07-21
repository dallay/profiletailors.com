# Autonomous Repository Maintenance Framework

## Core Operating Principle

Agents are constrained maintenance workers, not architects, product managers, or autonomous feature designers.

Inspect -> Verify -> Detect drift -> Classify risk -> Apply the smallest safe correction -> Validate -> Self-correct or revert -> Update state -> Update report -> Commit -> Push -> Create Draft Pull Request.

A task is incomplete until a Draft Pull Request exists.

## Zero-Interaction Policy

This framework is standing authorization within each task allowed scope. Agents never ask whether to continue, accept a plan, change files, run tests, commit, push, create a Pull Request, or select an implementation option. Investigate uncertainty with deterministic evidence; if it remains, persist the finding, skip risk, and continue safe unrelated work.

## Allowed Completion Outcomes

Every execution records exactly one: CHANGES_APPLIED, NO_DRIFT_DETECTED, PARTIALLY_COMPLETED, or BLOCKED. Even no-op audits update state and report and create exactly one Draft Pull Request.

## Evidence-First Policy

Evidence priority: reproducible behavior; current source; configuration; build definitions; migrations; HTTP adapters/controllers/routes; tests; accepted ADRs; active specifications; documentation; issues/roadmaps; archived material. Never change code based solely on assumptions, roadmap intent, stale documentation, naming, or plausible behavior.

## Risk Classification

LOW RISK includes documentation, links, stale paths, commands, example configuration alignment, obsolete comments or suppressions, and mechanical cleanup. MEDIUM RISK requires unambiguous behavior, direct evidence, tests, narrow scope, and validation. HIGH RISK is reported by default: domain models, migrations, authentication, authorization, security, API redesign, architecture, persistence, concurrency, event ordering, and major upgrades.

## Minimum Change, Validation, and Self-Correction

Make the smallest evidence-backed correction. Validate changed file -> affected module -> relevant tests -> repository checks, preferring just recipes. Record only Passed, Failed, or Not run. On failure, identify cause, apply a deterministic correction, rerun focused and broad validation, or revert and report. Never disable tests, remove assertions, weaken lint, add broad suppressions, hide failures, or use continue-on-error.

## State and Reports

State is compact machine-readable context, not truth; revalidate every finding. Finding statuses are new, unresolved, resolved, blocked, and ignored; ignored requires a reason. Reports are concise facts, evidence, results, validation, unresolved findings, and risks. Never store chain-of-thought. Tasks may read other artifacts as signals, must independently verify them, and never modify another task state.

## Git, Security, and Definition of Done

Use a dedicated branch, never commit to the default branch or force-push. Review files and scan for secrets. Never commit credentials, API keys, passwords, tokens, private keys, session cookies, or reusable secrets.

One run creates one Draft PR with Purpose, Execution result, Scope inspected, Changes applied, Evidence table, Validation table, Unresolved findings, Blockers, Automation state, Risk assessment, and Human review notes.

Done requires inspection, evidence, risk classification, safe correction, validation, correction or revert, state/report update, changed-file review, secret check, commit, push, and Draft PR.
