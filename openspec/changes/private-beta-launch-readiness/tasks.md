# Tasks: Private Beta Launch Readiness

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 500–800 across backend, frontend, tests, infra, docs |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1: 520/556; PR 2: 555/557; PR 3: 558; 559 operator gate |
| Delivery strategy | ask-on-risk |
| Chain strategy | GitHub stacked PRs |

Decision needed before apply: Resolved — GitHub stacked PRs selected
Chained PRs recommended: Yes
Chain strategy: GitHub stacked PRs
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Activation and scoped invitation acceptance | PR 1 | DALLAY-520/556; backend, BDD, contract tests |
| 2 | Observable, reversible publishing operations | PR 2 | DALLAY-555/557; code plus runbook; VPS proof separate |
| 3 | Invitee journey and final gate | PR 3 + gate | DALLAY-558/559; depends on Units 1–2 |

## Phase 1: Code — Activation and Invitation (DALLAY-520/556)

- [ ] 1.1 RED: add Kotlin tests under `server/smp/src/test/kotlin/com/profiletailors/smp/{platformadmin,tenancy}` for valid/expired/replayed tokens, atomic consume, one membership, and cross-workspace denial.
- [ ] 1.2 GREEN: add `AcceptInvitationCommand`/handler, identity and `WorkspaceMembershipProvisioner` ports, safe DTOs, and uniqueness-safe reconcile under `server/smp/src/main/kotlin/com/profiletailors/smp/{platformadmin,tenancy}`.
- [ ] 1.3 REFACTOR: retain hexagonal boundaries, deny-by-default context, hashed secrets, and token/PII-free responses/logs.
- [ ] 1.4 RED: add `@smoke @fast` Cucumber scenarios/steps in `server/smp/src/test/resources/features/` and `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/` for acceptance, first login, replay, and isolation.
- [ ] 1.5 GREEN/REFACTOR: wire steps and run `just backend-bdd-fast` plus `just backend-bdd-postgres`.

## Phase 2: Code — Publishing Controls (DALLAY-555/557)

- [ ] 2.1 RED: extend `PublishingWorkerTest`, `PublishingLifecycleLoggerTest`, and publishing BDD for typed failure, stale work, redaction, and safe-off with no provider call.
- [ ] 2.2 GREEN: modify `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/` and `infra/apps/smp/swarm/stack.yaml` for safe-off/readiness, stale visibility, lifecycle state, and rollback-safe config.
- [ ] 2.3 REFACTOR/VERIFY: run focused unit, WireMock, BDD, and Postgres tests; prove no raw exceptions, provider payloads, credentials, paths, or tokens leak.

## Phase 3: Code — Invitee Journey (DALLAY-558)

- [ ] 3.1 RED/GREEN/REFACTOR: add Vitest coverage under `apps/web/app/src/modules/{auth,publishing}` for redirect, workspace hydration/isolation, unavailable capability, canonical failure copy, and redaction.
- [ ] 3.2 RED/GREEN/REFACTOR: create `apps/web/app/e2e/specs/invitee-private-beta.spec.ts` for first login, workspace A, schedule/publish, unavailable state, safe failure, and no unsupported request.

## Phase 4: Managed VPS / Operator (not repository-verifiable)

- [ ] 4.1 Update `docs/infrastructure/private-beta-launch-readiness-runbook.md` and `docs/compliance/` with redacted evidence fields: UTC time, hostname, namespace, release, operator, scope, result, classification, retention, safe-off, backup/restore, rollback.
- [ ] 4.2 Record 520 activation/entry/invite/delivery/conversion and 556 acceptance/first login; missing provenance, delivery observation, secrets/PII redaction, or timestamps blocks acceptance.
- [ ] 4.3 Verify 557 public route/private readiness, PostgreSQL/9091/origin blocked, worker safe-off, backup/restore, and last-known-good rollback; local/CI cannot prove VPS or provider delivery.

## Phase 5: Final Gate (DALLAY-559)

- [ ] 5.1 Assemble dated test reports and ledger; keep code, operator-observed, and `USER_REPORTED_OPERATIONAL` evidence distinct—never provider-verified or `MULTI_USER_VERIFIED`.
- [ ] 5.2 Rehearse safe-off/rollback. GO only if 520/555/556/557/558 pass, evidence is redacted/provenanced, security boundaries pass, and the journey succeeds; otherwise NO-GO with owner/unblocker.
