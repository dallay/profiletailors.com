# Withdrawal Rationale — publication-calendar-sse

## Why withdrawn

Kerrigan approved closing this change without implementation. The intent captured in `proposal.md` was sound, but the cost of carrying the change forward — three review units, strict TDD across backend and SPA, Playwright SSE-only coverage — no longer matched the priority the user assigned to it. Cancelling now is cheaper than letting the slice linger with `revised-pending-approval` tasks and an unauthorized apply phase.

## Status of phase

The change halted in `apply` phase. The full sequence `explore → propose → spec → design → tasks` was completed and produced the durable spec at `openspec/specs/calendar-publication-sse/spec.md`. Tasks were revised against the 13-point review and against the decided Option A delete boundary; `tasks.md` carries status `revised-pending-approval`. `apply` was never authorized, no implementation work was started, and there are no source-code, test, migration, or documentation traces to revert. No `verify-report.md`, `qa-report.md`, or `apply-progress.md` was produced because those phases never started.

## Relationship to other changes

This withdrawal does not block `openspec/changes/skill-and-knowledge-bundle-remediation/`. R3 is met: publication-calendar-sse does not touch `.agents/`, `AGENTS.md`, or `docs/architecture/adr/**`. The two changes operate on disjoint surfaces and can run concurrently or independently. The change `reactive-calendar-browser-sync` is referenced as the base dependency for the design substrate (single-coordinator invariant, `subscribeChannelEvents`, `BroadcastChannel` adapter) but is itself a separate, untracked-in-worktree change that publication-calendar-sse never modified.

## What to do with this directory

The directory moves intact to `openspec/changes/archive/withdrawn/2026-09-27-publication-calendar-sse/`. The `archive/withdrawn/` subdirectory is a new convention introduced for changes cancelled before implementation; regular `archive/YYYY-MM-DD-<name>/` continues to host changes that completed verify and qa. `state.yaml` is updated to reflect closure: `status: withdrawn`, `current_phase: withdrawn`, `completed_phases` lists the phases that actually ran, `next` is `null`, and `withdrawn_at` plus `withdrawal_rationale` are recorded. The durable spec at `openspec/specs/calendar-publication-sse/spec.md` is preserved unchanged as historical material; it is not promoted into the active `openspec/specs/` set and is not merged into any other capability, because no implementation ever validated its scenarios.