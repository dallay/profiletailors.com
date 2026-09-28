# DALLAY-598 Threads provider integration

## Route

Explicit SDD using the OpenSpec phase DAG.

## Outcome

Integrate Meta Threads as the second first-class social publishing provider without duplicating the provider-neutral publication, scheduling, calendar, or delivery-attempt workflow.

## Tasks

1. Initialize and validate the OpenSpec change context.
2. Explore the existing backend publishing, OAuth, credential, media, scheduling, and frontend channel seams.
3. Produce the proposal and define the supported MVP boundary.
4. Produce product specifications and technical design.
5. Break the approved scope into implementation tasks with verification evidence.
6. Implement backend provider routing, Threads OAuth, credentials, capabilities, media-container publishing, error mapping, configuration, and tests.
7. Implement connected-channel/provider catalog and composer integration without a separate Threads workflow.
8. Verify implementation against proposal, specifications, design, tasks, architecture, and quality gates.
9. Run capability-driven acceptance QA and record any external Meta/deployment blockers.
10. Archive only after verification and QA gates are satisfied.

## Evidence

- OpenSpec artifacts under `openspec/changes/dallay-598-threads-provider-integration/`.
- Repository checks selected from the affected backend and dashboard surfaces.
- External Meta app-review and deployed smoke-test status recorded separately from local evidence.

## Status

Ready: apply Unit 4 tasks 4.2–4.5 complete with evidence 2026-09-28 (BDD 304 green; E2E threads 3/3 + lane 66+1 green with flake note; docs 4.4 with rulings; 4.5 evidence recorded). `state.yaml` still reads `current_phase: apply`, `next: apply` — verify/qa/archive phases not started. External blocker: Meta smoke + App Review (`external_evidence: not_run`) needs real Meta app + deployed env owner.
