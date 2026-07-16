# Proposal: Reusable Lead Capture Waitlist

## Intent

Build a reusable lead-capture waitlist capability for the Profile Tailors MVP marketing waitlist while preserving clean boundaries for future products and lead-capture concepts.

## Scope

### In Scope
- `shared/lead-capture/common` and `shared/lead-capture/waitlist` reusable modules.
- `server/smp` HTTP, R2DBC persistence, config, migration, and seed adapters.
- Marketing waitlist form integration, rate limiting, tests, and docs.

### Out of Scope
- `forms` and `newsletter` implementations; document as future concepts only.
- Reusing waitlist email for marketing/newsletter without explicit marketing consent.
- Any dependency from `shared/lead-capture` to `server/smp`.

## Capabilities

### New Capabilities
- `lead-capture-common`: Shared lead-capture primitives for metadata whitelisting, source separation, email normalization contracts, and adapter-independent boundaries.
- `lead-capture-waitlist`: Waitlist domain, consent, dedupe, public join API contract, persistence expectations, and marketing form behavior.

### Modified Capabilities
- None

## Approach

Model lead capture as reusable shared capability modules and integrate them into Profile Tailors through `server/smp` adapters only. Keep invariants explicit: Waitlist != source, Waitlist != form, WaitlistEntry != subscriber, early access consent != marketing consent, duplicate join != public error. Use conservative email normalization: trim, lowercase, validate reasonably, preserve original, no provider canonicalization.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `shared/lead-capture/common` | New | Common metadata/source/email primitives without server dependency. |
| `shared/lead-capture/waitlist` | New | Waitlist, WaitlistEntry, statuses, consent, dedupe rules. |
| `server/smp` | Modified | HTTP endpoint, R2DBC repositories, migrations, config, rate limiting. |
| `apps/web/marketing` | Modified | MVP waitlist form posts to backend endpoint. |
| `openspec/specs` | New | New capability specs for common and waitlist behavior. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Shared module accidentally imports server code | Med | Enforce Gradle/module dependency direction and tests. |
| Consent semantics blur with newsletter | Med | Specify separate consent fields and prohibit marketing reuse. |
| Duplicate joins leak account existence | Low | Always return idempotent accepted response for duplicates. |

## Rollback Plan

Disable/remove the marketing form integration, unregister the `server/smp` route/config, and rollback waitlist migrations/seeds before deleting shared modules if no persisted entries must be retained.

## Dependencies

- Existing backend module conventions, PostgreSQL/R2DBC migration path, marketing app environment configuration.

## Success Criteria

- [ ] Public `POST /api/waitlists/{waitlistKey}/entries` returns accepted for new and duplicate joins.
- [ ] Unknown waitlist returns `404 waitlist_not_found`; closed/paused returns `409 waitlist_closed`; validation returns `400`; rate limit returns `429`.
- [ ] Dedupe uses `UNIQUE(waitlist_id, normalized_email)`.
- [ ] Metadata is limited to the approved whitelist.
- [ ] `shared/lead-capture` has no dependency on `server/smp`.
