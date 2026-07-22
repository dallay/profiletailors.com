# Proposal: Copyright, Attribution & Takedown Workflow for Media

## Intent

Profile Tailors imports Unsplash images without visible attribution — violating Unsplash license
terms (attribution required for both platform and photographer). The platform also has no DMCA
takedown mechanism, creating legal exposure. This change adds attribution metadata display and a
complete takedown lifecycle.

## Scope

### In Scope

- Resolve dead `006-drop-external-metadata.yaml` (never applied — remove from tracking)
- Add `licence` column to `media_assets` table
- Display attribution (author, source, licence) in media library UI
- Add `SUSPENDED` status to `MediaAssetStatus` for distribution suspension
- Takedown report endpoint + review/action endpoint for authorized staff
- Email notifications for takedown lifecycle events
- Permission keys: `workspace:governance:media:read`, `workspace:governance:media:takedown`
- Audit events for takedown milestones

### Out of Scope

- Automated copyright detection / fingerprinting
- Public DMCA agent registration page
- Bulk takedown or automated counter-notice processing
- Third-party reporting API integration (Lumen, etc.)

## Capabilities

### New Capabilities

- `media-attribution`: Licence schema, attribution data model, frontend display
- `media-takedown`: Report submission → review → suspension → counter-notice → restore, with email +
  audit

### Modified Capabilities

- `media-library`: Extended DTOs with `licence` field; `SUSPENDED` status in lifecycle
- `email-notifications`: Takedown email templates (submission, suspension, counter-notice,
  restoration)
- `iam`: New permission keys for media governance

## Approach

**Incremental — two phases:**

**Phase 1 — Schema + Attribution (low effort):**

1. Remove dead `006-drop-external-metadata.yaml` from Liquibase tracking (columns intact)
2. Changelog `007-add-licence-column.yaml`: nullable `licence` VARCHAR on `media_assets`
3. Add `licence` to `MediaAsset` domain model, DTOs, repository mapping
4. Display author name, URL, source provider, licence in `MediaLibraryView.vue`
5. Feature flag for attribution display

**Phase 2 — Takedown Workflow (medium effort):**

1. `SUSPENDED` status in `MediaAssetStatus`
2. Takedown report domain model: `SUBMITTED` → `UNDER_REVIEW` → `RESOLVED` (approved/rejected)
3. REST: `POST /api/governance/media/takedown-reports`, `GET` (list), `POST .../{id}/action` (
   review)
4. Auth: `WorkspaceAuthorizationDecider` with `workspace:governance:media:takedown`
5. On approval: asset → `SUSPENDED`, audit event, email notification
6. Counter-notice: submit → review → restore or escalate
7. `SUSPENDED` assets excluded from composer/public views

## Affected Areas

| Area                                                | Impact                              |
|-----------------------------------------------------|-------------------------------------|
| `media/domain/MediaModels.kt`                       | `licence` field, `SUSPENDED` status |
| `media/application/MediaQueries.kt`                 | Exclude `SUSPENDED` from queries    |
| `media/infrastructure/http/MediaAssetController.kt` | Filter suspended assets             |
| `media/infrastructure/persistence/`                 | R2DBC mapping for `licence`         |
| `db/changelog/media/`                               | New 007 changelog, remove dead 006  |
| `authorization/domain/PermissionKey.kt`             | New governance media permissions    |
| `governance/` (new domain)                          | Takedown domain + app + HTTP        |
| `identity/.../EmailTemplates.kt`                    | Takedown notification templates     |
| `apps/web/app/src/modules/media/`                   | Attribution display in UI           |
| `apps/web/app/src/modules/governance/` (new)        | Takedown report/review UI           |

## Risks

| Risk                                      | Likelihood | Mitigation                                         |
|-------------------------------------------|------------|----------------------------------------------------|
| Schema columns missing if 006 was applied | Low        | Verified 006 was never applied; columns intact     |
| No attribution for pre-existing assets    | High       | `licence` is nullable; show what's available       |
| DMCA exposure during phase gap            | Med        | Phase 1 satisfies Unsplash terms; gap documented   |
| Spam/false takedown reports               | Med        | Auth-gated takedown actions; rate-limit submission |

## Rollback Plan

- **Phase 1**: `liquibase rollback count 1` reverts licence column. Feature flag off by default.
- **Phase 2**: Disable takedown endpoints via feature flag. Bulk-update `SUSPENDED` → `READY`.
  Liquibase rollback removes new tables.

## Dependencies

- Liquibase for schema migration
- `EmailSender` interface (exists) for notifications
- `WorkspaceAuthorizationDecider` (exists) for permission checks

## Success Criteria

- [ ] Unsplash media display attribution in library without new API calls
- [ ] Takedown report can be submitted and confirmation received
- [ ] Authorized staff can review, approve, or reject a report
- [ ] Approved takedown sets asset to `SUSPENDED` and notifies reporter
- [ ] `SUSPENDED` assets excluded from composer/picker views
- [ ] Counter-notice restores asset to `READY`
- [ ] Every takedown milestone is recorded in audit events table
