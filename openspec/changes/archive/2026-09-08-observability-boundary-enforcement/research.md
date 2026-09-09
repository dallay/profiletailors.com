# gentle-ai.sdd-research/v1 — observability-boundary-enforcement / observability-boundary-evidence

- revision: 2
- outcome: blocked
- lane: observability-boundary-evidence
- change: observability-boundary-enforcement
- exploration_ref: openspec/changes/observability-boundary-enforcement/exploration.md
- artifact_store: openspec

## Questions (retained selected intent, not evidence)

- Q1: Per-module ArchUnit import-ban precedent for the requested enforcement approach.
- Q2: Key-safe operational event attribute practice for bounded low-cardinality attributes.
- Q3: Log-sink redaction policy precedent for the requested key-allowlist approach.

## Admission

- requested_classes: ["documentation"]
- declared_grants: documentation=[]; open-web=[]
- observed_grants: none admitted
- verdict: denied for the requested documentation class. Open-web was explicitly not requested for this lane. Persistence access, generic tooling, filenames, and inherited unnamed tools were not treated as evidence capability. No evidence source of any class was accessed.

## Sources

None. Admission denial admits no sources, so no source records exist.

## Validated claims

None. A blocked outcome excludes unvalidated claims, and admission denial emits no source claims.

## Contradictions

None recorded. No admitted sources exist to compare.

## Uncertainty and freshness

- Q1, Q2, and Q3 are fully unresolved.
- Anything unresolvable from admitted sources is recorded here as uncertainty, never as claims.
- Freshness is not applicable because no sources were accessed.

## Product choices (non-authoritative, separate from evidence)

Retained pre-proposal handoff tokens only: research=run_research, key_never, reclassify, redact_followup, deprecate_followup, per_module_blocking. These are pending orchestrator-owned decisions, not validated findings.

## Recovery

Blocked recovery state is this file. Re-entry requires the orchestrator to relaunch sdd-research for this lane with explicit non-empty evidence grants for the requested classes. Proposal readiness remains blocked until selected research reaches done with valid evidence.
