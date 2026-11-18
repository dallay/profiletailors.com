# Apply Progress

## Current Apply Slice

Unit 2 identity-link provisioning verification, tasks 2.11 through 2.19. Existing BFF/security implementation and surrounding changes were present in the worktree before this slice. This update records verification evidence only; it does not activate hard cutover or modify `principal_id` semantics.

## Commands Run

| Command | Result |
|---|---|
| `git status --short --branch`, `git diff --stat`, `git branch --show-current` | Exit 0. Branch is `feature/keycloak-auth-migration`, ahead of `origin/main` by 5. Worktree contains extensive prior modifications, staged deletions, and untracked artifacts; all were preserved. |
| `./gradlew :server:smp:test --tests '*ProvisionExternalIdentityLinkHandlerTest' --tests '*AdminUserControllerTest' --tests '*AdminAndFreshMfaAuthorizationWebFilterTest' --tests '*ExternalIdentityLinkProvisioningServiceTest'` | Exit 0; 48 tests, 0 failures, 0 errors, 0 skipped across the four classes. |
| `./gradlew :server:smp:postgresIntegrationTest --tests '*ExternalIdentityLinkGatewayPostgresTest' --tests '*IdentityLinkAuditAtomicityPostgresTest' --tests '*ExternalIdentityLinksPostgresSchemaTest'` | Exit 0; 10 tests, 0 failures, 0 errors, 0 skipped across the three classes. |
| `just backend-check` | Exit 0; Spotless, Detekt, unit, and PostgreSQL tasks passed. |
| `just backend-bdd-fast` | Exit 0; 45 suites, 339 scenarios, 0 failures, 0 errors, 0 skipped. The identity-link feature XML reports 8 scenarios, 0 failures, 0 errors, 0 skipped. |
| `git diff HEAD -U0` scans for `@Suppress`, `@file:Suppress`, `biome-ignore`, `eslint-disable`, `@ts-ignore`, `@ts-expect-error`, `@ts-nocheck`, `nolint`, `TODO`, `FIXME`, `HACK`, and explicit `any` | No new suppression directive, annotation suppression, lint bypass, TODO marker, or explicit `any` is introduced by this change. |
| `git status --short` scans for `detekt`, `biome.json`, and `tsconfig` paths | No static-analysis baseline or configuration file is modified. |
| `git status --short` scan for MCP-related paths | No MCP source, configuration, or contract file is modified by this change. |
| `just -l` | Exit 0; confirmed available backend checks including `backend-check`, `backend-test-postgres`, and `backend-bdd-fast`. |
| `./gradlew :server:smp:test --tests '*ProvisionExternalIdentityLinkHandlerTest' --tests '*ExternalIdentityLinkProvisioningServiceTest' --tests '*AdminUserControllerTest' --no-daemon` | Exit 0; targeted task execution completed. Gradle reported 37 actionable tasks, 3 executed, 34 up-to-date. The filtered result is not a broad test pass. |
| `just backend-test-postgres` (first run) | Terminated by execution timeout after 120 seconds (exit 143); rerun without changing tests. |
| `just backend-test-postgres` (rerun) | Exit 0; unfiltered PostgreSQL integration task `:server:smp:postgresIntegrationTest` passed. Gradle reported 33 actionable tasks, 3 executed, 30 up-to-date. |
| `just backend-check` | Exit 1 due to Detekt `MaxLineLength` on two previously existing changed test lines: `IdentityLinkProvisioningBddSteps.kt:166` and `ProvisionExternalIdentityLinkHandlerTest.kt:27`. Backend unit and PostgreSQL tasks executed as part of the recipe, but the recipe is failed overall. |
| `just backend-lint` after formatting those two lines | Exit 0; Detekt passed. |

## Task Evidence

| Task | State | Evidence |
|---|---|---|
| 2.11 | Complete | Application handler tests cover exact target principal/linking behavior, permission denial, target absence, non-disclosing duplicate conflict, no transfer/overwrite, audit metadata without authentication artifacts, persistence failure, and cancellation. `just backend-test-postgres` passed, including `R2dbcExternalIdentityLinkGatewayPostgresTest` (duplicate and concurrent pair conflicts, single ownership retained, multiple links per principal accepted, principal local subject preserved, and no email-based resolution) plus `IdentityLinkAuditAtomicityPostgresTest` (link insert rolls back if success-audit write fails). The implementation and tests do not add `principal_id` uniqueness/cardinality enforcement. |
| 2.14 | Complete | Same successful unfiltered PostgreSQL integration run proves unique `(issuer, subject)` behavior under duplicate and concurrent submissions and allows multiple external identity links per principal. The test confirms local principal `subject` remains unchanged; there is no constraint on the principal's number of links. |
| 2.12 | Partial | `R2dbcExternalIdentityLinkGatewayPostgresTest` and `ExternalIdentityLinksPostgresSchemaTest` prove repeated and concurrent submissions retain the first mapping, reject duplicates, preserve the local principal subject, and do not resolve by email. Still needed: assertions that provider/user-identity fields remain unchanged on conflict and that the HTTP conflict body is indistinguishable across owners. |
| 2.15 | Partial | Handler and filter unit tests separately prove `USERS_MANAGE`, `pt-admin` `acr=2`, fresh OTP, and audit outcomes without representative authentication artifacts. Still needed: an endpoint-level test combining authorization and step-up, plus persisted success-audit field assertions at the endpoint seam. |
| 2.16 | Complete in code; combined endpoint evidence pending | `ProvisionExternalIdentityLinkHandler` enforces `USERS_MANAGE` and audits through `AdministrativeAuditPublisher` with `EXTERNAL_IDENTITY_LINK_PROVISIONED` and fixed reason categories. `AdminAndFreshMfaAuthorizationWebFilter` enforces admin ACR and fresh MFA. Focused handler/filter tests pass; no single endpoint-level test combines both enforcement seams. |
| 2.17 | Previously complete; new scenario partially verified | The feature now contains nine scenarios, including missing `Idempotency-Key`. The prior full BDD run passed the original eight scenarios. In the latest filtered run, the new scenario passed; eight other identity-link scenarios were skipped. A post-change full `just backend-bdd-fast` run remains required. |
| 2.18 | Partial | The controller validates the required `Idempotency-Key` and uses existing idempotency behavior; controller tests cover replay, changed-payload conflicts, and missing-header status. The new BDD scenario passes for a missing key. The Problem Details body assertion is present in BDD glue but the filtered execution is the only post-change evidence so far; full-suite verification remains pending. |
| 2.19 | Partial | Existing BDD and controller tests cover status mappings and no-link mutation on rejection. Still needed: an HTTP assertion that identity-pair `409` bodies do not reveal the owning principal and a full-application Problem Details assertion for a missing header. The latest focused BDD run passed the new missing-header scenario and two matching shortlink idempotency scenarios; it skipped the remaining scenarios. |
| 4.4 | Partial | `docs/architecture/iam-platform.md` describes the Keycloak BFF and implemented MCP bearer-JWT contract, with operational gates explicit. `docs/architecture/data-model/erd-identity.html` and its catalog now include `external_identity_links`; `README.md` records the verified manual-maintenance process because no generator exists. `just docs-lint` and `git diff --check` passed locally. A final consistency pass remains; `just docs-links` previously failed on two pre-existing FTC URL 404s outside the changed files. ADR-0035's MCP API-key requirement still conflicts with the implementation and needs an explicit decision.

## TDD Evidence

No new behavior was added during this verification slice, so no RED/GREEN cycle was performed. Existing tests were run against the in-progress implementation. The requested identity-link conflict, authorization, audit, and persistence-failure behaviors have already been test-driven in the prior apply work; no new behavioral change was introduced here.

## Delivery Boundary

- **Forecast**: High.
- **Chain strategy**: `size-exception`, approved in current `tasks.md` for one PR above 400 lines.
- **Layer boundary**: trunk=`main`; parent=`main`; base=`main`; branch=`feature/keycloak-auth-migration`; position is the single coherent delivery boundary; issue=none.
- **Implemented slice**: verification of Unit 2 identity-link application, persistence, authorization, audit, and BDD tasks 2.11 through 2.19. No commits, push, PR, cutover, or `principal_id` change.
- **Budget concern**: none beyond the already-approved size exception.

## Remaining Work

- Unit 2 BFF/security tasks 2.1–2.7 remain under gap assessment against the active design, covering client routes, safe returns, callback failure, `401` versus browser redirect, mixed credentials, chain isolation, session-creation gating, CSRF, admin LoA2, and validated-claims trust wiring.
- Task 2.6 (retirement of local human login, refresh, signup, verification, and recovery, `pt_refresh` invalidation, and removal of BFF-to-local-token exchange) is bound to the hard cutover and stays gated.
- Task 4.1 (separate `pt-admin` login, one-OTP step-up, and denial of direct admin API access for app-only sessions) requires admin SPA coverage.
- Task 4.4 (auth, lifecycle, API, deployment, and operations contract sync) requires a final consistency pass over the contract documents.
- Contract contradiction requiring an explicit decision: ADR-0035 states that "Local MCP API-key authentication MUST remain independently authenticated by Profile Tailors" and repeats an "MCP API-key exception" in tasks 4.6 and the consumer inventory, but no MCP API-key authentication exists in the codebase. `McpSecurityConfiguration` authenticates MCP requests with a bearer JWT validated for audience, workspace, and scope (`McpJwtConverter`), and no MCP API-key credential, table, or filter is present. The ADR was not rewritten to conceal the drift; the decision is whether MCP API-key authentication must still be implemented or the ADR wording must be amended to describe the MCP bearer-JWT contract. `docs/architecture/iam-platform.md` now describes the implemented MCP bearer-JWT contract instead of the unimplemented API-key one.
- Tasks 4.5 and 4.6 remain operational gates. The repository contains no Keycloak realm, client, or flow definition: the realm is an external deployment at `https://keycloak.ahome.quest/realms/pt`, so realm, client, OTP, DNS/TLS, proxy, callback, cookie, session, CSRF, and logout readiness cannot be validated from this codebase and require operational evidence plus explicit authorization.
- Unit 2 verification remains blocked until the remaining implementation and verification tasks are complete.
- Hard cutover task 4.6 remains gated and MUST NOT be activated by this apply slice.
