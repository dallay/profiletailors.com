# Exploration: Initial Platform Admin Bootstrap

## Executive Summary

The approved bootstrap contract spans platform-admin invitations, identity registration and email verification, the admin SPA, backend security/CORS, and production operations. Existing platform-admin invitations are not directly reusable as-is: their registration context requires either an existing or new workspace, and completion returns a workspace and membership. Bootstrap must grant `PLATFORM_OWNER` only after the recipient sets a password and completes the existing email-verification flow, without creating workspace, membership, or customer access.

The production admin SPA is deployed to Cloudflare Pages. The documented Pages origin is `https://profiletailors-com-bx5.pages.dev`, and `.github/workflows/release-please.yml` deploys the `apps/web/admin` project. The deployment repository currently permits only `https://app.profiletailors.com` in `SMP_CORS_ALLOWED_ORIGIN`, which Compose passes to the backend as `SMP_CORS_ALLOWED_ORIGINS`. This is a concrete production integration gap for browser requests from the documented admin Pages origin, unless a separate production admin origin or additional configuration exists outside the inspected files.

## Current State and Verified Evidence

### Existing product and domain contracts

- `.agents/sdd/config.yaml` selects `openspec` persistence with `openspec-only` artifact policy.
- `.agents/sdd/README.md` limits OpenSpec specs to product, domain, user-facing, legal, privacy, security, and business-readiness contracts. Deployment plumbing belongs in deployment or operational documentation rather than a standalone OpenSpec capability.
- `.agents/sdd/specs/invitations/spec.md` defines the standalone `Invitation` aggregate. The aggregate stores a token hash, not a raw token or accept URL. Statuses are `ACTIVE`, `ACCEPTED`, `EXPIRED`, and `REVOKED`; only `ACTIVE` can transition. The repository provides locked reads and conditional lifecycle transitions.
- `docs/architecture/adr/0020-first-class-invitation-aggregate.md` records the same token boundary and the existing invitation persistence/concurrency design.
- `.agents/sdd/specs/email-verification/spec.md` requires secure, single-use email-verification tokens stored as SHA-256 hashes, associated with the user email, and expiring after 24 hours. The verification API is `POST /api/auth/verify-email`; the corresponding UI contract is in `.agents/sdd/specs/email-verification-ui/spec.md`.
- `apps/web/admin/PRODUCT.md` characterizes admin as an internal operations SPA, separate from the customer dashboard. It presently describes no onboarding flow.

### Backend behavior

- `PlatformRole` already includes `PLATFORM_OWNER`.
- Platform role assignment uses `AssignPlatformRoleHandler` and an audit publisher. The normal handler checks that the acting operator has `OPERATORS_MANAGE`; it is not an initial-owner bootstrap path.
- `AdminSessionController` returns `403` when the authenticated principal has no platform roles.
- `LocalAuthController` owns `/api/auth/register`. Existing registration and invitation integration are separate boundaries; `InvitationRegistrationContext` only models `EXISTING_WORKSPACE` or `NEW_WORKSPACE`, and `InvitationRegistrationResult` requires a workspace ID and membership status.
- The existing invitation completion coordinator is designed to activate registration in the workspace/membership flow. Reusing it unchanged would violate the approved bootstrap constraints.
- `IdentitySecurityConfiguration` allows unauthenticated `POST /api/auth/verify-email` and the existing public authentication endpoints. CORS allowed origins are configured through `app.security.cors.allowed-origins`, sourced from `SMP_CORS_ALLOWED_ORIGINS` in production.
- The security configuration permits `/actuator/health/**`; the production readiness probe uses `/actuator/health/readiness`. The approved contract explicitly forbids changing readiness for pending-bootstrap status.
- No existing `ApplicationRunner`, `CommandLineRunner`, Spring Shell command, or equivalent interactive bootstrap CLI was found in the backend source.

### Admin SPA and deployment

- `apps/web/admin/src/router/index.ts` currently defines `/login`, `/access-denied`, and authenticated platform-admin routes. Its route guard permits routes marked `public`; otherwise it requires authentication, platform access, and any route permission. There is no `/bootstrap` route currently.
- `docs/infrastructure/cloudflare-deployment.md` identifies `apps/web/admin` as the `profiletailors-admin` Cloudflare Pages project and documents its default Pages domain as `https://profiletailors-com-bx5.pages.dev`.
- `.github/workflows/release-please.yml` has a `deploy-admin` job that builds `apps/web/admin` and deploys it to the `profiletailors-admin` Cloudflare Pages project. Its documented production verification target defaults to `https://admin.profiletailors.com`.
- In the read-only deployment repository, `production/.env.example` currently sets `SMP_CORS_ALLOWED_ORIGIN=https://app.profiletailors.com`. `production/docker-compose.yml` interpolates that value into `SMP_CORS_ALLOWED_ORIGINS`. No admin origin is present in the inspected example.
- The deployment Compose file runs the backend as a non-root user with a read-only root filesystem and exposes readiness through its healthcheck. The inspected deployment setup supports operator access through Docker/Swarm operations, but the application repository has no existing interactive command mechanism.

## Affected Areas

- `.agents/sdd/specs/invitations/spec.md` — existing lifecycle and token contract; bootstrap must not implicitly change workspace invitation semantics.
- `.agents/sdd/specs/email-verification/spec.md` and `.agents/sdd/specs/email-verification-ui/spec.md` — existing verification token and user-facing verification behavior to reuse or explicitly integrate.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/` — invitation aggregate/repository, platform-role assignment, audit publication, and infrastructure wiring are likely integration points.
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/` — registration, password credential creation, and email verification need a bootstrap-compatible path without customer provisioning.
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt` — public bootstrap-related API access and configured CORS behavior need review; no client API or readiness change is approved.
- `apps/web/admin/src/router/index.ts` and a new public bootstrap view/API client — preserve the user-selected public `/bootstrap` route while keeping other admin routes behind existing gates.
- `apps/web/admin/PRODUCT.md` — if bootstrap changes product behavior, clarify the onboarding exception to the current internal-only/no-onboarding constraints.
- `docs/infrastructure/cloudflare-deployment.md`, `.github/workflows/release-please.yml`, and deployment-repository `production/.env.example`/`docker-compose.yml` — verify Pages reachability, chosen origin, release deployment, and CORS configuration. The deployment repository was inspected read-only; it was not changed.
- Backend tests and Cucumber scenarios — the new registration/verification and owner-grant behavior is externally observable and requires tests at the relevant boundaries, including concurrency and audit secrecy.

## Approved Contract to Preserve

- Provide a private, interactive command inside the backend container. It confirms a normalized email and accepts no password in arguments or environment variables.
- Generate a random, single-use invitation with fixed 30-minute expiry. Persist only its hash. Show the link once on interactive stdout.
- The recipient sets a password and completes the existing email-verification flow. Grant `PLATFORM_OWNER` only after verification succeeds.
- Do not elevate an existing account. Do not create workspace, membership, or customer access.
- Do not mutate state if an active owner already exists.
- Replace a pending invitation only after explicit confirmation. Invalidate the prior invitation within the same transaction and use concurrency locking to prevent races.
- Audit the available actor, normalized email, action, IDs, time, result, and reason. Never audit token, URL, password, or token hash.
- Expose pending status only to the CLI and internal management. Do not change customer APIs or readiness.
- Preserve the public `/bootstrap` route in the admin SPA and include reachability and CORS in scope.

## Decisions Confirmed During SDD

- The production admin URL for Profile Tailors is `https://admin.profiletailors.com`. This is a deployment-specific value, not a product requirement or hard-coded domain; each self-hosted installation MUST configure its own admin public origin for invitation links and CORS.
- If the confirmed normalized email already belongs to an account, bootstrap issuance is rejected without mutation. The account is not elevated or modified.
- Deliver the bootstrap invitation only through the one-time link printed to interactive CLI stdout. Do not send a second bootstrap invitation by email. After password setup, use the existing email-verification flow and its normal delivery channel.
- Do not request age-eligibility confirmation during platform-owner bootstrap. Leave customer-registration behavior unchanged. This is a product decision, not a legal-compliance assessment. Current Terms acceptance is required and recorded as ID-free `PLATFORM` consent.

## Approaches and Tradeoffs

1. **Extend the existing invitation-registration path with a distinct bootstrap target**
   - Keep the invitation aggregate, token hashing, locked repository patterns, and existing email verification token flow.
   - Add a distinct bootstrap completion path that does not call workspace or membership provisioning and grants the owner role only after verification.
   - Pros: Reuses established invitation persistence and token security; aligns with the invitation contract and existing backend architecture.
   - Cons: Existing context/result types encode workspace assumptions; changes must preserve existing invitation behavior and avoid accidental customer provisioning. Cross-context role grant and identity verification transaction boundaries need careful design.
   - Effort: High.

2. **Create a separate bootstrap invitation model and lifecycle**
   - Model an initial-owner invitation independently from workspace invitations and route it through identity verification and final role grant.
   - Pros: Keeps bootstrap’s no-workspace semantics explicit; avoids overloading workspace registration types.
   - Cons: Duplicates invitation token, expiry, persistence, audit, and concurrency concerns already owned by the canonical invitation aggregate; risks a second token subsystem contrary to ADR-0020’s established boundary.
   - Effort: High; weaker fit unless a clean extension of the canonical aggregate is not feasible.

3. **Implement the private command as a one-shot Spring application runner/profile or separate CLI entry point**
   - The command is invoked interactively inside the container and must not run during normal server startup.
   - Pros: Can use the same application ports, transaction infrastructure, and configured database; avoids public command endpoints.
   - Cons: No existing runner/CLI pattern was found. A runner in the server process risks unintended startup execution; a separate entry point or image command may require packaging, container and operational changes. The deployed image is read-only and runs non-root, so input/output and one-shot execution must be verified in the actual container model.
   - Effort: Medium to High.

4. **Provide an internal management endpoint for creation and status**
   - Pros: Reuses the running service and could support an internal management UI.
   - Cons: The approved contract requires a private interactive in-container command and restricts pending status to CLI/internal management. An HTTP endpoint adds exposure, authentication, authorization, and routing surfaces, and must not become public. This is not the recommended creation mechanism unless the user explicitly broadens the approved contract.
   - Effort: High; not recommended for issuance.

## Recommendation

Prefer extending the canonical invitation and verification infrastructure with an explicitly separate bootstrap registration target/use case that skips workspace and membership provisioning, then assigns `PLATFORM_OWNER` only after the verified-email transition. Keep the grant and final invitation transition atomic where the current transaction boundaries permit; otherwise define and test a recoverable state transition before implementation. Implement issuance through a dedicated, explicitly invoked in-container CLI/one-shot entry point rather than a public endpoint. Keep the public `/bootstrap` SPA route narrowly scoped to recipient registration/verification, not administrative management.

Treat CORS/reachability as a deployment integration task. The production admin URL for Profile Tailors is confirmed as `https://admin.profiletailors.com`; each installation must configure its own admin origin. The deployment example still needs the corresponding configuration. Live reachability and CORS verification remain outside this local change.

## Risks and Open Blockers at Exploration

- **Owner-active definition and race protection:** The exact definition of “active owner” is not found in the inspected contracts. The design must identify the authoritative role-assignment query and serialize issuance against concurrent first-owner creation, not only lock an invitation row.
- **Transaction boundary:** Email verification currently belongs to identity; platform role assignments belong to platformadmin. The design must establish how verification and the owner grant are coordinated atomically or recoverably without violating module boundaries.
- **Existing account policy:** The approved behavior forbids elevating an existing account. Define the observable response when the normalized email already belongs to an account, including whether it is rejected before creating an invitation and what reason is audited without revealing unnecessary account data.
- **Pending replacement confirmation:** Define how explicit confirmation is obtained from the interactive command, and ensure the previous invitation is invalidated in the same transaction as the replacement while concurrent commands cannot both succeed.
- **CLI runtime and deployment invocation:** No in-repository interactive command framework exists. Choose and prove an invocation compatible with the deployed image, non-root user, read-only filesystem, secrets/configuration, Swarm service lifecycle, and operator access. The command must not be automatically executed on normal server startup.
- **Admin route exposure and ownership:** `/bootstrap` must be reachable without admin authentication, but must expose only the invitation recipient flow. Avoid redirecting it through the existing auth guard or exposing internal pending-invitation status publicly.
- **CORS origin mismatch:** The documented admin Pages URL is not included in the deployment example’s only allowed origin. The workflow also documents a custom production URL as its verification target. Confirm actual production hostname/reachability and configure the correct allowed origin(s); this repository cannot verify live Cloudflare, DNS, or deployed CORS behavior.
- **Email delivery vs. one-time stdout link:** The approved flow requires a link shown once on interactive stdout. Define the recipient’s route and the boundary between CLI link creation and the SPA’s submission of the raw token, while ensuring logs, audit, shell history, and telemetry do not capture it.
- **No artifact/change directory existed at exploration time:** The named change folder was absent before this request. The requested artifact can be created, but no proposal/spec/design/task state is available to establish earlier phase decisions.
- **Existing local modifications:** The main worktree had pre-existing modifications in `.gitignore` and two admin invitation test files. They were not touched.

## Initial Exploration Outcome

Yes, with explicit blockers to resolve in proposal/design: confirm the production admin browser origin, define the private command’s invocation model, define active-owner serialization and existing-account behavior, and specify the identity-verification-to-role-grant transaction/recovery boundary. Preserve all approved requirements above; do not introduce a public bootstrap API, alter customer APIs/readiness, or create customer workspace/membership access.

## Replan and Apply History (2026-11-02)

- The measured implementation candidate is approximately 3,360 changed lines, excluding planning/ADR files and the `.gitignore` modification. Delivery uses `feature-branch-chain` with 16 monorepo units and separate deployment-repository unit D01; estimates are in `tasks.md`, with U11 and U14 provisional.
- U01 is committed locally as `7fe2f7d9` on `feature/bootstrap-u01-planning`. U02 uses U01 as its base and `feature/bootstrap-u02-history` as its local ref. The tracker branch remains based on `main`.
- Local conventional commits per unit are authorized. Do not push, open a PR, run live checks, or deploy to the VPS.
- Latest test evidence and unresolved gates are recorded in `apply-progress.md`. `just backend-bdd-fast` had 2 failures in 329 scenarios, followed by a Gradle aggregation `NoSuchFileException`. `just backend-test-postgres` was still running when last checked; no result is claimed.
