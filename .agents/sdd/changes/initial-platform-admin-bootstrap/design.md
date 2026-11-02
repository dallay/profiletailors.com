# Design: Initial Platform Admin Bootstrap

## Technical Approach

Bootstrap does not change customer registration. ADR-0031 owns ID-free `PLATFORM` consent; ADR-0030 is superseded. Require current Terms and verified email, but no age confirmation, workspace, membership, customer access, or existing-account elevation.

Move the private version source in `LocalAuthHandlers.kt` to an identity-owned query shared with registration and bootstrap metadata. Do not alter the frontend duplicate or behavior.

## Architecture Decisions

| Option | Tradeoff | Decision |
|---|---|---|
| Keep Terms private or duplicate in admin | Drift | Identity query exposes only `currentTermsVersion`. |
| Extend customer auth APIs | Couples bootstrap to customer contracts | Separate unauthenticated metadata and token-gated completion endpoints; customer APIs stay unchanged. |
| Attribute grant to recipient or human | False attribution | ADR-0032: `SYSTEM_BOOTSTRAP`, no UUID, empty roles; invitation issuance/replacement and initial-owner grant only. |
| Run `SmpApplication` in a CLI profile | Normal jobs stay discoverable | Dispatch early; use an isolated explicit-bean non-web context. |
| Grant after verification commits | Partial success | Preserve identity-owned completion port; consume token and write consent/invitation/grant/audit atomically. |
| Split implementation | Separate cross-module invariants | Owner-approved 500–800-line `size-exception`; no commit, push, PR, or VPS deployment. |

## Data Flow

```text
CLI -> terminal link -> /bootstrap#token -> clear fragment -> GET requirements
    -> POST completion -> pending identity + PLATFORM consent + existing verification email
Email -> existing /api/auth/verify-email -> identity port -> atomic verification and owner grant
```

Run the boot image with `docker compose -f infra/apps/smp/production/compose.yaml run --rm --no-deps -it backend platform-admin-bootstrap`. Require TTY; dispatch before `runApplication`; use explicit non-web beans without migrations, scan, jobs, events, workers, or server.

## Interfaces / Contracts

Unauthenticated `GET /api/bootstrap/requirements` returns only the current Terms version and no invitation, user, or status data. Identity owns this query and shares its version source with customer registration without changing the existing customer API.

Unauthenticated `POST /api/bootstrap/complete` accepts exactly `{invitationToken, password, acceptedTermsVersion}`; the invitation supplies email. Admin uses installation-specific `VITE_API_BASE_URL`. Clear the token fragment with `history.replaceState` immediately on route entry, before any request; require Terms acceptance and send no age field. Return empty `202` on success. Missing, invalid, expired, and used tokens all return identical `400` Problem Details with code `BOOTSTRAP_UNAVAILABLE`. No status route is added; stale Terms cause no writes.

At setup, reject existing-account email without mutation and bind the invitation to a new principal in a non-reassignable pending state. One transaction creates identity/password, `ConsentScope.Platform` consent, and verification-token hash, with no age consent or tenant rows. Publish the existing registration event after commit only; retain resend, and do not email bootstrap links or alter the verification destination/route. Delivery is not exactly once.

Platformadmin's coordinator owns the `AtomicTransactionRunner`, uses application contracts, and preserves the identity-owned verification-completion port and `POST /api/auth/verify-email`. Other identities are unchanged. Token consumption, verification, invitation completion, grant, and audit commit together; failure rolls them back while account and consent remain retryable. Backfill historical actors as human; operator authorization is unchanged.

Configure `SMP_ADMIN_PUBLIC_ORIGIN` per installation for terminal links and include it in `SMP_CORS_ALLOWED_ORIGINS`. Admin uses installation-specific `VITE_API_BASE_URL`; do not hardcode an origin.

## File Changes

| File | Action | Description |
|---|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt`; new `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/GetCurrentTermsVersionQuery.kt` | Modify/create | One identity-owned Terms source/query. |
| New `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/PlatformBootstrapController.kt`; new `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/CompleteBootstrapHandler.kt`; `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/PlatformAdminBootstrapConfiguration.kt` | Create/modify | Metadata/completion API and wiring. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/PlatformRoleAssignment.kt`; `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/AdminAuditEvent.kt`; `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcPlatformRoleAssignmentRepository.kt`; `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcAdminAuditRepository.kt`; `server/smp/src/main/kotlin/com/profiletailors/smp/governance/domain/ConsentScope.kt`; `server/smp/src/main/resources/db/changelog/governance/008-platform-consent-scope.yaml`; `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml` | Create/modify | Invitation, actor, `PLATFORM` consent, atomic grant. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/SmpApplication.kt`; new `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/PlatformAdminBootstrapCliConfiguration.kt`; `server/smp/src/main/resources/application.yaml`; `infra/apps/smp/production/compose.yaml` | Modify/create | Isolated CLI, admin origin, CORS. |
| `apps/web/admin/src/router/index.ts`; new `apps/web/admin/src/views/BootstrapView.vue`; `apps/web/admin/src/lib/api.ts` | Modify/create | Public recipient route, fragment cleanup, configured API base. |

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit/API | New `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/PlatformBootstrapControllerTest.kt` | Exact GET body; POST contract; identical `400`/`BOOTSTRAP_UNAVAILABLE` for absent, invalid, expired, and used tokens; stale Terms writes nothing; configured admin origin passes CORS. |
| PostgreSQL | New `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/integration/PlatformAdminBootstrapPostgresIntegrationTest.kt` | ID-free consent; no tenant rows; verified-only system grant; replay, concurrency, rollback. |
| BDD | New `server/smp/src/test/resources/features/platformadmin/bootstrap.feature` and `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/PlatformAdminBootstrapBddSteps.kt` | Terms acceptance, safe errors, existing verification route, unchanged customer registration, tenant isolation. |
| Admin | New `apps/web/admin/src/views/BootstrapView.spec.ts` and `apps/web/admin/e2e/specs/bootstrap.spec.ts` | Public route; `VITE_API_BASE_URL`; clear fragment before POST; exact fields; Terms required; no age field; safe errors. |
| Regression | `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt`; `server/smp/src/test/kotlin/com/profiletailors/smp/identity/integration/LocalAuthHandlersTransactionPostgresIntegrationTest.kt`; `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/email/EmailTemplatesTest.kt`; `server/smp/src/test/kotlin/com/profiletailors/smp/integration/LocalAuthEndpointIntegrationTest.kt`; `apps/web/app/e2e/specs/register-flow.spec.ts` | Preserve source, port, route, destination, and customer registration. |
| Image | Built `:server:smp:bootBuildImage` | Verify exact TTY invocation, no server/background work/migrations, and one terminal link. |

## Migration / Rollout

Add forward-only actor and `PLATFORM` migrations; apply before CLI use. Set `SMP_ADMIN_PUBLIC_ORIGIN`, matching CORS allowlist, and `VITE_API_BASE_URL` per installation. No live verification or deployment.

## Open Questions

- [ ] Verify TTY and startup isolation against the built image.
