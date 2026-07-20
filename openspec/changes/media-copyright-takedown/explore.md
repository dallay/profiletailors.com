## Exploration: Copyright/Attribution/Takedown Workflow for Media Assets (DALLAY-499)

### Current State

**Attribution Data in Domain Model:**
The `MediaAsset` domain model already carries full attribution fields:
- `sourceType: MediaSourceType` — `UPLOADED`, `UNSPLASH`, `TENOR`, etc.
- `sourceProvider: String?` — e.g. `"unsplash"`
- `externalId: String?`
- `sourceUrl: String?`
- `authorName: String?`
- `authorUrl: String?`
- `metadata: JsonNode?` — download location, etc.

These flow through the API via `MediaAssetResponse` and `MediaAssetSummary` DTOs.

**CRITICAL — Database Schema Ambiguity:**
- Changelog `005-add-external-metadata.yaml` **adds** `source_provider`, `external_id`, `source_url`, `author_name`, `author_url`, and `metadata` (jsonb) columns with NOT NULL + CHECK constraints
- Changelog `006-drop-external-metadata.yaml` **drops** all those columns
- The domain models and DTOs still reference these fields, meaning either:
  - The drop was never applied, OR
  - The domain model is out of sync with the schema

**Unsplash Integration:**
- `UnsplashMediaProviderHandlers.persistPhoto()` maps `UnsplashPhoto` → `MediaAsset` with `sourceProvider = "unsplash"`, preserving `sourceUrl`, `authorName`, `authorUrl`, and `downloadLocation` in `metadata`
- Unsplash API guidelines require: hotlinked URLs (not re-hosted), download endpoint tracking, attribution to both Unsplash and photographer with link-back

**Current Authorization Model:**
- `PermissionKey` format: `<domain>:<resource>:<action>` (e.g., `workspace:consent:read`)
- Authorization via `WorkspaceAuthorizationDecider` interface, implemented by `WorkspaceAuthorizationService`
- Media endpoints currently use `AuthFeature.UPLOAD_MEDIA` for email verification gating — no workspace-level permission checks for media reads
- Governance consent controller shows the established pattern with `workspace:consent:read`/`write`

**Media Status Lifecycle:**
Current statuses: `PROCESSING`, `PENDING_UPLOAD`, `UPLOADING`, `READY`, `FAILED`, `DELETED`
No `SUSPENDED`, `RESTRICTED`, or similar moderation status exists.

**Frontend (Vue 3):**
- `MediaLibraryView.vue` displays only filename, file size, status per asset
- Attribution fields (`authorName`, `authorUrl`, `sourceUrl`, `sourceProvider`) are NOT rendered anywhere in the UI
- The API returns them though — they're invisible but transmitted

**Notification Infrastructure:**
- `EmailSender` interface with `send(email: EmailMessage)` — implementations: `ResendEmailSender`, `SmtpEmailSender`, `MockEmailSender`
- `EmailTemplates` class for VerificationEmail
- Publishing has `NotificationEvent` durable events (not generic template system)
- No shared notification module — email is in identity context only

**Moderation/Content Suspension Patterns:**
- Publishing has `BLOCKED` status with `scanBlockedForRecovery()` and `PublishingFailureCategory.blocked` flag
- Publishing worker scans for `BLOCKED` publications and requeues them for recovery
- No equivalent moderation infrastructure exists in the media context

**Unsplash License:**
- The Unsplash License allows free use for commercial and non-commercial purposes
- Attribution to both Unsplash and the photographer is required
- Images must be hotlinked (not re-hosted) from Unsplash URLs
- The download endpoint must be called for API compliance
- Selling unaltered copies is not permitted

### Affected Areas

- **Domain Model** — `MediaModels.kt`: `MediaAsset` needs either a `licence` field or a new status for takedown/suspension
- **Database Schema** — Changelogs: need to resolve `006-drop-external-metadata.yaml` status and add `licence` column
- **Authorization** — `PermissionKey.kt`, `Role.kt`: Need new media-specific permissions (`governance:media:read`, `governance:media:takedown`)
- **REST API** — `MediaAssetController.kt`: Need new endpoints for takedown reporting and listing
- **HTTP Adapter** — Need takedown request DTOs (TakedownReportRequest, TakedownActionRequest)
- **Email Notifications** — `EmailSender` interface: Need new email templates for takedown notifications
- **Frontend** — `MediaLibraryView.vue`, `media-api.ts`: Need attribution display, takedown reporting UI
- **Unsplash Provider** — `UnsplashMediaProviderHandlers.kt`: Attributes already preserved; no changes needed for attribution data itself

### Approaches

1. **Light Schema Fix + Attribution Display Only**
   - Remove or roll back `006-drop-external-metadata.yaml` to preserve attribution columns
   - Add `licence` column to the media_assets table
   - Display author/source info in the frontend media library
   - No takedown workflow, no moderation status
   - Pros: Minimal changes, fixes the attribution data pipeline
   - Cons: No takedown mechanism for copyright claims, no legal compliance
   - Effort: Low

2. **Full Copyright/Attribution/Takedown Workflow**
   - Resolve schema issue (add attribution columns back if dropped)
   - Add `licence` column to media_assets table
   - Add `SUSPENDED`/`RESTRICTED` status to `MediaAssetStatus`
   - Create `governance:media:*` permissions and authorize media endpoints
   - Build takedown report endpoint + takedown action (admin) endpoint
   - Email notification for reporters on status changes
   - Frontend: attribution display + takedown report form
   - Unsplash DMCA compliance: ability to hide/block specific images
   - Pros: Full legal compliance, DMCA-ready process, auditable trail
   - Cons: Medium effort, more surface area
   - Effort: Medium

3. **Incremental — Fix Schema + Attribution First, Takedown Later**
   - Phase 1: Fix schema, add `licence` column, display attribution in UI
   - Phase 2: Build takedown workflow (endpoints, moderation status, emails)
   - Pros: Delivers immediate value (attribution visibility), spreads effort
   - Cons: Two releases, temporary gap in legal readiness
   - Effort: Low (phase 1) + Medium (phase 2)

### Recommendation

**Approach 3 — Incremental.** Here's the rationale:

1. The schema issue with `006-drop-external-metadata.yaml` needs resolution NOW — if those columns were dropped, the current domain model is wrong and attribution data is being persisted but lost. This is a bug regardless of the takedown feature.

2. The `licence` column should be added alongside fixing the schema since we're already changing the table.

3. Adding the attribution display in the frontend is a self-contained UI change that gives immediate value to users who source images from Unsplash.

4. The takedown workflow (phase 2) needs more design around legal requirements, who can submit claims, verification of claim validity, and the takedown/restoration flow. Splitting it lets us get the infrastructure right without blocking the schema fix.

### Risks

- **Schema Risk**: If `006-drop-external-metadata.yaml` was applied to production, all existing attribution data is already gone. We need to check the migration history.
- **Unsplash License Risk**: Without attribution display, we may be violating the Unsplash license terms when showing Unsplash images in the app.
- **Legal Risk**: Without a takedown mechanism, the platform has no way to comply with DMCA takedown notices or copyright claims — this is a legal exposure.
- **Schema Migration Complexity**: Adding NOT NULL columns with CHECK constraints requires careful migration for existing rows (providing defaults or making nullable).

### Ready for Proposal
**Yes.** Enough information is gathered to move to the proposal phase. The key open question is whether `006-drop-external-metadata.yaml` was applied — this needs to be verified before writing the proposal since it affects the schema migration plan.
