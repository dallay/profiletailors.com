# Apply Progress: `apply-unit-2-publishing-controls`

**Change:** `private-beta-launch-readiness`
**Scope:** DALLAY-555/557, Phase 2 publishing controls
**Delivery:** Single PR with a user-approved size exception; the PR body records the review-budget rationale.

## Completed in this apply slice

- Phase 2.1 stale-work repository, handler, and worker behavior is implemented and covered.
- Phase 2.2 safe-off/readiness configuration and the private-beta runbook are present; the runbook references `infra/apps/smp/swarm/stack.yaml`.
- Phase 2.3 now includes `GET /api/admin/publishing/stale-jobs`, platform permission enforcement, positive ISO-8601 threshold validation, bounded limits, safe response mapping, controller tests, and tagged Cucumber coverage.
- Cucumber hooks and step state use the stale-jobs response directly and do not reset away platform role assignments.

## Verification evidence

- `node scripts/with-db-password-gradle.mjs :server:smp:test --tests 'com.profiletailors.smp.publishing.*' --tests 'com.profiletailors.smp.platformadmin.infrastructure.http.PublishingStaleJobsControllerTest' --no-daemon` — passed.
- `just backend-bdd-fast` — passed.
- `just backend-bdd-postgres` — passed.
- `just backend-test-fast` — passed.
- `just backend-check` — passed.
- `just backend-build` — passed.
- `just backend-lint` — passed.
- `git diff --check` — passed.
- `:server:smp:spotlessKotlinCheck` — passed after applying the repository formatter.
- `com.profiletailors.smp.ModularStructureTest` — passed after moving the admin HTTP adapter into `platformadmin.infrastructure.http`.

## Handoff and remaining work

- `state.yaml` is maintained by the orchestrator and now records Phase 2 verification and blocked QA.
- `verify-report.md` records `PASS WITH WARNINGS` for repository-local technical conformance; `qa-report.md` records `BLOCKED` because production is running the pre-change `v0.4.1` release.
- No product/operator acceptance is claimed. An approved production release procedure and change window are required before rerunning acceptance QA.
