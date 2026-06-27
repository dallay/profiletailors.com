# ADR-0011: Reusable Lead Capture Waitlist Capability

- Status: Proposed
- Date: 2026-06-25
- Decision owners: Principal Architect
- Scope: Shared Modules (`shared/lead-capture`), `server/smp`, `apps/web/marketing`
- Supersedes: None
- Superseded by: None
- Related:
  - OpenSpec: `openspec/changes/reusable-lead-capture-waitlist/`
  - ADR-0010 Shared Kernel Governance
  - ADR-0002 Hexagonal Architecture
  - ADR-0004 CQRS via Mediator
  - ADR-0006 Resource Creation via POST

## Context

The marketing site (`apps/web/marketing`) advertises a waitlist and includes a `WaitlistForm.astro` component, but the flow is currently client-side only: no persistence, no anti-spam protection, no analytics, no multi-product reuse. The backend already ships a `RateLimitStrategy.WAITLIST` strategy and has scaffolded references to a `waitlist` controller/repository/handler pattern in internal docs, but no implementation exists yet. We need to ship a working MVP waitlist connected to marketing, while keeping the implementation reusable for future products in the same monorepo (e.g., new previews, beta programs, partner waitlists).

## Decision drivers

- Reusability across products inside the monorepo.
- Clean separation between form capture, waitlist business rules, and product-specific integration.
- Legal safety: explicit, separated consent for early-access notifications vs. marketing communications.
- Anti-enumeration and anti-abuse on a public endpoint.
- Hexagonal architecture and CQRS consistency with the rest of the backend.
- ADR-0010 framework-isolation rules for `shared/` modules.

## Decision

Implement a reusable **Lead Capture** capability composed of two Gradle modules:

1. `shared:lead-capture:common` — framework-free value objects: `EmailAddress`, `NormalizedEmail`, `CaptureSource`, `CaptureLocale`, `LeadMetadata`. **No** `ConsentSnapshot` here.
2. `shared:lead-capture:waitlist` — pure Kotlin domain + application layer implementing CQRS:
   - `Waitlist` aggregate with `WaitlistStatus` (`draft | active | paused | closed | archived`).
   - `WaitlistEntry` with `WaitlistEntryStatus` (`pending | invited | converted | cancelled`) and explicit timestamps (`joined_at`, `invited_at`, `converted_at`, `cancelled_at`).
   - `WaitlistConsent` value object **inside** the waitlist domain, with explicit `earlyAccess` and `marketing` flags and a `version` tag.
   - `JoinWaitlistCommand` / `JoinWaitlistHandler` (idempotent) and repository ports.

`server/smp` provides the infrastructure adapters:
- HTTP: `POST /api/waitlists/{waitlistKey}/entries` returning uniform `{ "message": "You're on the list.", "status": "accepted" }` for both new joins and duplicates.
- Persistence: R2DBC repositories implementing the shared ports, with tables `waitlists` and `waitlist_entries`.
- Configuration: seed `profile-tailors-launch` waitlist.
- Security: wire `RateLimitStrategy.WAITLIST`.

`apps/web/marketing` integrates by posting the `WaitlistForm.astro` payload to the new endpoint.

### Architecture invariants (MUST)

1. **Waitlist != source.** Source describes where the lead came from; the waitlist is a distinct entity.
2. **Waitlist != form.** A form is a capture surface; a waitlist is the receiving list.
3. **WaitlistEntry != subscriber.** A waitlist entry has lifecycle and ordering intent; a subscriber is a continuous relationship.
4. **Early access consent != marketing consent.** The endpoint MUST require `earlyAccess=true`. Marketing consent defaults to `false` and is never implicit from joining the waitlist.
5. **Duplicate join != public error.** Same email joining the same waitlist returns the same `accepted` response as a new join.
6. **Unknown `waitlistKey` returns 404.** `409 Conflict` is reserved for the waitlist existing but not accepting entries (`paused`/`closed`/etc.).
7. **`shared/lead-capture` MUST NOT depend on `server/smp`.** Dependence is strictly one-way.
8. **Email deduplication is per waitlist.** Use `UNIQUE(waitlist_id, normalized_email)`, not global email uniqueness.
9. **Email normalization is conservative.** Trim + lowercase + reasonable validation; preserve the original; **no** Gmail/provider canonicalization.
10. **Metadata is whitelisted.** Allowed keys: `utm_source`, `utm_medium`, `utm_campaign`, `utm_content`, `utm_term`, `referrer`, `page_path`, `user_agent_family`, `consent_version`. Unlisted keys are ignored or rejected.

## Scope and boundaries

- `shared/lead-capture/common` — framework-free VOs only.
- `shared/lead-capture/waitlist` — domain + application, no Spring, no R2DBC.
- `server/smp` — HTTP controller, R2DBC repositories, configuration, rate limit wiring.
- `server/smp/src/main/resources/db/changelog/lead-capture/*` — Liquibase changelogs + seed.
- `apps/web/marketing/src/components/WaitlistForm.astro` — payload contract alignment.

`forms` and `newsletter` modules are **out of scope** for MVP. They are documented as future bounded contexts but not created.

## Alternatives considered

### Inline waitlist inside `server/smp/leadcapture`
- Advantages: Faster to ship; fewer Gradle modules.
- Disadvantages: Couples the capability to one backend; harder to extract or reuse for the next product.
- Reason rejected: Defeats the plug-and-play goal and bloats `server/smp`.

### Single shared `subscription` module reusing `cvix-main` patterns
- Advantages: Prior art.
- Disadvantages: `subscription` in `cvix-main` models paid subscriptions (plans, billing, lifecycle). It does not model early-access lifecycle, dedupe-by-waitlist, or consent separation. Copying it would import a different bounded context.
- Reason rejected: Different domain, different invariants.

### Microservice `server/lead-capture`
- Advantages: Maximum independence.
- Disadvantages: Auth between services, observability, versioned APIs, separate migrations, more DevOps — premature for MVP.
- Reason rejected: Too much operational cost before validating the capability.

## Consequences

### Positive
- Reusable capability shared by any future product in the monorepo.
- Explicit consent separation reduces legal risk.
- Idempotent endpoint prevents email enumeration.
- Hexagonal boundaries keep `shared/lead-capture` framework-free per ADR-0010.

### Negative
- More Gradle modules to bootstrap.
- Slight upfront cost to define domain vocabulary (`Waitlist`, `WaitlistEntry`, statuses).

### Risks
- Misuse of consent flags if not tested — mitigated by domain tests asserting earlyAccess required, marketing default false.
- Accidental cross-module dependency — mitigated by ArchUnit and Gradle dependency constraints.

### Accepted trade-offs
- Deferred `forms` and `newsletter` modules to keep MVP focused.
- Conservative email normalization (no Gmail canonicalization) accepted to avoid edge-case bugs.

## Compliance and enforcement

- Gradle: `shared:lead-capture:*` modules explicitly exclude Spring, R2DBC, and any server-side dependency.
- ArchUnit: tests forbid `shared/lead-capture/**` from importing any `org.springframework.*`, `io.r2dbc.*`, or `com.profiletailors.smp.*` packages.
- Unit tests assert that domain rejects invalid email, requires early-access consent, and produces the same public response on duplicate join.
- HTTP tests assert 200/202 for success and duplicate, 400 for validation, 404 for unknown key, 409 for closed waitlist, 429 for rate limit.

## Verification

- `just backend-test-fast` passes without `postgres` tagged tests.
- `just ci-local` passes (lint, fast backend, frontend tests, frontend build).
- Liquibase applies cleanly to PostgreSQL (`just backend-bdd-postgres` or smoke migration).
- Marketing E2E test submits the form and verifies success + invalid-email path.

## Migration or remediation

None required for MVP — there is no legacy waitlist data. The existing client-side `WaitlistForm.astro` will be swapped to call the new endpoint; no data backfill is needed.

## Follow-up actions

- [ ] Add `openspec/specs/lead-capture-common` and `openspec/specs/lead-capture-waitlist` as archived canonical specs after `sdd-archive`.
- [ ] Re-evaluate extraction to a separate microservice after at least two products adopt the capability.
- [ ] Revisit once `forms` module is introduced; map `WaitlistEntry` capture to `FormSubmission`.

## Revisit conditions

- More than two products require different waitlist lifecycles incompatible with the current statuses.
- Legal review requires double opt-in or jurisdictional consent granularity not captured today.
- Volume justifies extraction to a dedicated microservice.
