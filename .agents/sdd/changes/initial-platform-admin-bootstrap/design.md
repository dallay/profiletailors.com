# Design: Initial Platform Admin Bootstrap

## Technical Approach

Keep customer registration unchanged. Identity owns Terms; governance owns consent; platformadmin uses narrow application contracts and must not depend on `governance::domain`. Compose CLI through the approved wiring-only `com.profiletailors.smp.config` package; keep `SmpApplication` free of platformadmin-infrastructure imports.

## Architecture Decisions

| Option | Tradeoff | Decision |
|---|---|---|
| Read/write consent through governance domain types | Leaks domain internals across modules | Add DTO-only consent record/check operations to `governance::application`; map to domain types inside governance. Record this bootstrap use in narrow ADR-0034; keep ADR-0023's takedown decision unchanged and reuse its existing application-only dependency edge. |
| Import CLI infrastructure from `SmpApplication` or expose it broadly | Violates the current module boundary or expands the public API | Dispatch through a wiring-only `com.profiletailors.smp.config` composition entry point. Keep CLI implementation unexposed and config code free of bootstrap business rules. Do not add broad `ModuleMetadata` wildcards or weaken Modulith tests. |
| Start the full server for CLI use | Starts unrelated work | Dispatch before `runApplication` using the isolated explicit-bean non-web context. |
| Move verification and grant work to a platformadmin-owned transaction | Splits email-token consumption from the owner grant | Keep identity's existing `AtomicTransactionRunner` as the transaction owner. `VerifyEmailHandler` invokes the identity-owned completion port within that transaction; the platformadmin grant handler participates without opening a nested runner. |
| Attribute bootstrap actions to the recipient or a human operator | Misstates who performed a system bootstrap action | Attribute issuance, replacement, and owner grant to `SYSTEM_BOOTSTRAP`, with no human principal UUID; never use the recipient as actor. |

## Data Flow

```text
CLI -> terminal-only invitation link -> /bootstrap#token -> clear fragment -> review Terms -> completion
Email -> existing /api/auth/verify-email -> identity port -> verification and owner grant
```

Require TTY; use the isolated explicit-bean non-web context before server startup, without migrations, jobs, events, or workers. Preserve verification route and email delivery.

## Interfaces / Contracts

`GET /api/bootstrap/requirements` returns only the current Terms version. `POST /api/bootstrap/complete` accepts `{invitationToken, password, acceptedTermsVersion}` and returns empty `202`; unusable tokens share the same `400` Problem Details response. Issuance and status remain private. The admin SPA clears the fragment before requests and uses the installation-configured API base.

Governance application exposes narrow consent record/check operations with application-owned inputs, such as principal UUID and Terms version, and a simple check result. Neither contract exposes `ConsentScope`, `ConsentType`, `SubjectReference`, or other governance domain types. `CompleteBootstrapHandler` and `GovernanceBootstrapTermsConsentVerifier` call only this application API. Governance retains domain mapping and persistence access. No platformadmin type imports `governance::domain`.

At setup, reject an existing account without mutation. Create identity, platform consent, and verification token atomically; do not create workspace or membership rows or age consent. Do not change customer-registration behavior.

## File Changes

| File | Action | Description |
|---|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/ConsentRequests.kt`; `server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/ConsentHandlers.kt` | Modify | Add DTO-only consent operations; keep domain mapping in governance. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/CompleteBootstrapHandler.kt`; `platformadmin/infrastructure/GovernanceBootstrapTermsConsentVerifier.kt` | Modify | Remove governance domain imports and consume application operations only. |
| `docs/architecture/adr/0034-platform-bootstrap-uses-governance-application-port.md`; `docs/architecture/adr/README.md` | Create/modify | Record the narrow consent contract without rewriting ADR-0023. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/SmpApplication.kt`; new `server/smp/src/main/kotlin/com/profiletailors/smp/config/PlatformAdminBootstrapCommand.kt` | Modify/create | Keep early dispatch while moving CLI wiring behind the approved config exception. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt`; new `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/GetCurrentTermsVersionQuery.kt` | Modify/create | Share the identity-owned Terms version source. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/{http/PlatformBootstrapController.kt,PlatformAdminBootstrapConfiguration.kt,cli/PlatformAdminBootstrapCliConfiguration.kt}`; `apps/web/admin/src/{router/index.ts,views/BootstrapView.vue}`; bootstrap Liquibase and BDD files in the tasks plan | Create/modify | Implement the API, isolated CLI, recipient route, persistence, and scenarios without changing customer behavior. |

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit/API | Terms contract, safe token errors, consent record/check mapping, stale-Terms no-write behavior | `GetCurrentTermsVersionHandlerTest`, `CompleteBootstrapHandlerTest`, `CompleteBootstrapOwnerGrantHandlerTest`, `PlatformBootstrapControllerTest`, plus tests for the governance application operations. |
| PostgreSQL / BDD | Consent scope, isolation, replay, concurrency, rollback, verification and unchanged registration | `ConsentScopePostgresIntegrationTest`, `R2dbcPlatformBootstrapInvitationRepositoryPostgresIntegrationTest`, `LocalAuthHandlersTransactionPostgresIntegrationTest`; run `just backend-test-postgres`, `just backend-bdd-fast`, and `just backend-bdd-postgres` where applicable. |
| CLI / context | Dispatch before server start, isolated context, TTY and explicit bean configuration | `PlatformAdminBootstrapCommandDispatchTest`, `PlatformBootstrapContextTest`, `InteractivePlatformAdminBootstrapCliTest`, and `PlatformAdminBootstrapCliConfigurationTest`. |
| Architecture / static analysis | No platformadmin-to-governance-domain or root-to-platformadmin-infrastructure edge; no new Detekt debt | Keep `ModularStructureTest`, `ModularityVerificationTest`, `HexagonalArchTest`, and `ComponentScanArchTest` blocking; run `just backend-lint` and `just backend-check`. |
| Admin | Public route, fragment cleanup, Terms requirement, exact request | `just admin-check`, `just admin-test`, `just admin-build`, and `apps/web/admin/e2e/specs/bootstrap.spec.ts`. |

## Current Verification Evidence

The latest `just backend-check` result had one unidentified failure and two skipped tests. The captured summary does not identify the failing test, so no cause is assigned. Architecture verification remains required; the accepted direction is no `platformadmin -> governance::domain` dependency and CLI dispatch through wiring-only `com.profiletailors.smp.config`.

The latest `just backend-lint` report contains 17 findings. The task plan assigns them to work units; fix them without suppressions, baseline changes, or weakened rules. The latest `just backend-bdd-fast` run completed 329 scenarios with two failures, then Gradle result aggregation reported `NoSuchFileException`. The PostgreSQL recovery path and the actual `bootBuildImage`/TTY invocation remain unverified. The CLI's isolated context still has a configtree secret gap. Production and Swarm config checks remain blocked by missing local environment inputs, and `just docs-links` timed out.

The implementation candidate is approximately 3,360 changed lines. The current delivery forecast is approximately 5,000 monorepo lines plus D01 at approximately 90 lines; use the 23-unit feature-branch chain and stop any unit whose staged additions plus deletions exceed 400 lines.

## Migration / Rollout

Apply forward-only actor and `PLATFORM` consent migrations before CLI use. Configure origin, matching CORS, and API base per installation. Deliver as 23 monorepo units plus separate D01, at most 400 changed lines per unit, with authorized local conventional commits under `feature-branch-chain`. No push, PR, live checks, or VPS deployment.

## Open Questions

- [ ] Verify TTY and startup isolation against the built image.
