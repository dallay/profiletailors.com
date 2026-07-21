# Proposal: Reusable Lead Capture Waitlist Capability

## Overview

### Intent

Ship a working MVP waitlist connected to the marketing site, designed as a reusable lead-capture capability for future products in the monorepo. The waitlist is the first business interpretation of a broader lead-capture bounded context.

### Changes

#### Scope

##### In Scope
- `shared/lead-capture/common` — framework-free value objects (`EmailAddress`, `NormalizedEmail`, `CaptureSource`, `CaptureLocale`, `LeadMetadata`).
- `shared/lead-capture/waitlist` — pure Kotlin domain + application layer (CQRS): `Waitlist` aggregate, `WaitlistEntry`, `WaitlistConsent`, `JoinWaitlistCommand`/`JoinWaitlistHandler`, repository ports.
- `server/smp` — HTTP adapter (`POST /api/waitlists/{waitlistKey}/entries`), R2DBC persistence, Liquibase migrations, rate limit wiring, configuration/seed.
- `apps/web/marketing` — connect `WaitlistForm.astro` to backend endpoint with full payload contract.

##### Out of Scope
- `forms` module — deferred to future bounded context.
- `newsletter` module — deferred.
- Lead scoring, segmentation, campaigns, admin automation.
- CAPTCHA / Turnstile (rate limit is sufficient for MVP).
- Per-email throttling (can be added if abuse patterns emerge).
- Separate microservice extraction (premature for MVP).

### Capabilities

#### New Capabilities
- `lead-capture-common`: Framework-free value objects shared across all lead-capture bounded contexts.
- `lead-capture-waitlist`: Waitlist domain and application layer with CQRS pattern, idempotent join, per-waitlist email deduplication, and explicit consent separation.

#### Modified Capabilities
None

### Approach

Two Gradle subprojects under `shared/lead-capture/` enforce framework isolation:

1. `common` contains only value objects — no domain logic, no consent, no Spring/R2DBC.
2. `waitlist` contains the aggregate, entity, value objects, commands, handlers, and ports — pure Kotlin, testable without any framework.

`server/smp` provides infrastructure adapters (HTTP, R2DBC, config). `apps/web/marketing` integrates the form.

The endpoint is idempotent: duplicate email joins return the same `accepted` response as new joins to prevent email enumeration. Early access consent is required; marketing consent defaults to false and is never implicit.

### Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `shared/lead-capture/common` | New | Framework-free VOs: `EmailAddress`, `NormalizedEmail`, `CaptureSource`, `CaptureLocale`, `LeadMetadata` |
| `shared/lead-capture/waitlist` | New | Domain aggregate, entity, consent VO, CQRS commands/handlers, repository ports |
| `server/smp/leadcapture/http` | New | `WaitlistController` with `POST /api/waitlists/{waitlistKey}/entries` |
| `server/smp/leadcapture/persistence` | New | R2DBC repositories implementing shared ports |
| `server/smp/leadcapture/config` | New | Seed `profile-tailors-launch`, rate limit wiring |
| `server/smp/src/main/resources/db/changelog/lead-capture/` | New | Liquibase migrations for `waitlists` and `waitlist_entries` tables |
| `apps/web/marketing/src/components/WaitlistForm.astro` | Modified | POST to backend endpoint with full payload contract |

### Rollback Plan

- Feature is behind no feature flag — it is new functionality with no existing behavior to revert.
- If the endpoint misbehaves, the marketing form can revert to client-side behavior by removing the `fetch` call.
- Database rollback: Liquibase changelog includes `dropTable` changesets.
- No data migration needed — no legacy waitlist data exists.

## Related

- ADR: [ADR-0011 Reusable Lead Capture Waitlist Capability](../../../../docs/architecture/adr/0011-reusable-lead-capture-waitlist.md)
- Linear Epic: DALLAY-436
- Linear Issues: DALLAY-437 through DALLAY-443
