# Tasks: Threads Provider Integration

## Review Workload Forecast

Estimated changed lines: 900–1,400 across four slices.
400-line budget risk: High
Chained PRs recommended: Yes
Delivery strategy: feature-branch-chain
Decision needed before apply: No
Chain strategy: feature-branch-chain using `feature/dallay-598-threads-integrate-threads-as-the-second-social-publishing`

| Unit | Goal | Base → branch | Dependency/metadata |
|---|---|---|---|
| 1 | Contracts/config/persistence | `trunk` → `dallay-598/u1-contracts` | position 1; Linear DALLAY-598 |
| 2 | OAuth/connections | `u1-contracts` → `dallay-598/u2-oauth` | position 2; parent PR1 |
| 3 | Publishing/media | `u2-oauth` → `dallay-598/u3-publishing` | position 3; parent PR2 |
| 4 | SPA/BDD/E2E/docs | `u3-publishing` → `dallay-598/u4-surface` | position 4; parent PR3 |

Stacked PRs must record `trunk`, `parent_branch`, `base`, `branch`, `position`, and issue metadata; each higher base is the immediate lower branch. Feature-chain uses an approved feature branch for Unit 1 and no Stack metadata. Size exception needs maintainer approval/rationale.

## Phase 1: Foundation / Migration / Configuration (strict RED → GREEN → REFACTOR)

- [x] 1.1 RED: test `THREADS`/`PERSONAL_PROFILE`, capabilities, disabled/invalid config, scopes, TTL, polling bounds, and encryption key.
- [x] 1.2 GREEN: add provider registries/contracts and typed binding under `publishing/{domain,application,infrastructure}`; invalid enabled config fails fast, disabled is `HIDDEN`.
- [x] 1.3 RED → GREEN: PostgreSQL tests and Liquibase changelog/master include for encrypted `secure_credentials`, expiry/refresh, remote IDs/statuses, atomic upsert, and secret-free reads.
- [x] 1.4 REFACTOR: run `just backend-test-fast`, architecture/Modulith checks, `just backend-lint`; inspect suppressions/baselines/secrets.

## Phase 2: OAuth / Connections (strict RED → GREEN → REFACTOR)

- [x] 2.1 RED: WebFlux tests for generic routes, signed provider/workspace/principal/redirect/nonce/expiry state, Threads scopes/no PKCE, policy re-check, auth/workspace errors, and LinkedIn aliases.
- [x] 2.2 GREEN: implement routing in `PublishingConnectionHandlers.kt`/controllers, HMAC state, short→long exchange, AES-GCM storage, refresh-ahead, reconnect, invalidation, and sanitized errors/logs/audits.
- [x] 2.3 RED → GREEN: fake-provider/typed adapter tests for denial, malformed/expired responses, timeout, replay, wrong scope/provider/workspace, and token/code redaction.
- [x] 2.4 REFACTOR: focused WebFlux/security tests, provider-neutral refresh/disconnect lifecycle coverage, Spotless, and Detekt passed; the equivalent backend check remains blocked by unrelated `ResourcePreviewEndpointPostgresIntegrationTest` connection failures.

## Phase 3: Publishing / Media / Worker (strict RED → GREEN → REFACTOR)

- [x] 3.1 RED: test text, single image/video, 2–20 mixed carousel, pre-I/O rejection, readiness, HTTPS URL TTL, polling, idempotency, ambiguity, and LinkedIn regression. Focused RED evidence captured for the media resolver and typed Threads adapter before implementation; focused contract and LinkedIn regression tests pass.
- [x] 3.2 GREEN: implement typed Threads container create→poll→finalize; wire scheduler/queue/worker/media/delivery; persist container/final IDs and `IN_PROGRESS`/`AMBIGUOUS` evidence. Delivery attempts retain provider operation references and the duplicate Liquibase column definition was removed.
- [x] 3.3 REFACTOR: run `just backend-test-postgres`, `just backend-build`, `just backend-check`, and security/secret scans. PostgreSQL integration, backend build, backend check, changed-file Gitleaks, focused Threads/worker/LinkedIn tests, migration duplicate-column review, and diff hygiene passed. Full-history Gitleaks and scoped Semgrep report pre-existing findings outside Unit 3 changed behavior: a historical test idempotency key, an ADR word match, and bcrypt fixtures.

## Phase 4: Frontend / BDD / E2E / Operations

- [x] 4.1 RED → GREEN: Vitest then implement catalog/connect and `/integrations/{provider}/callback` plus LinkedIn alias in `apps/web/app/src/modules/{auth,publishing}`, router, `AppShell.vue`, and sidebar; cover denial, missing params, unchanged state, retry, refresh, navigation, a11y, sanitized feedback. Provider-neutral Threads catalog/connect, callback handling, icon and composer/sidebar presentation, reconnect actions, sanitized initiation feedback, and focused Vitest coverage are complete; BDD, E2E, operations, docs, and broader Unit 4 checks remain outstanding.
- [ ] 4.2 RED → GREEN: Cucumber features/glue under `server/smp/src/test/resources/features/` and `bdd/glue/` for catalog, OAuth, policy, reconnect/disconnect, media, ambiguity, redaction; run `just backend-bdd-fast` and PostgreSQL BDD as needed.
- [ ] 4.3 RED → GREEN: mocked Playwright connect/callback/scheduler/publish E2E with no real secrets; run applicable app lane.
- [ ] 4.4 Update `apps/web/app/PRODUCT.md`, API/OpenAPI/examples, `docs/api-versioning*.md`, publishing runbook, env/deployment/compliance docs, and ADR index for owned changed claims.
- [ ] 4.5 Record migration/staging/config/Meta smoke/App Review/alerts/rollback evidence; preserve `external_evidence: not_run`; run app CI scripts, `git diff --check`, link/secret checks, and no-suppression/no-leak review.

## Acceptance Mapping

- [ ] OAuth: `specs/oauth-initiation-api/spec.md`, `specs/oauth-callback-ui/spec.md`.
- [ ] Catalog, credentials, lifecycle, capabilities, media, delivery, ambiguity, redaction: `specs/publishing/spec.md`.
- [ ] Record commands as Passed/Failed/Not run; external Meta evidence remains not run until supplied.
