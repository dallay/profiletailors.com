# Design: Workspace Shortlinks for Publications and Click Metrics

## Technical Approach

Keep shortlink ownership in `server/smp/shortlinks`; add post-composer integration through the existing publishing UI/API boundary and a narrow workspace-scoped metric query. Core V1 already creates links with `ownerId` derived from `requireWorkspaceContext()`, and redirects resolve active links by host/code through `ResolveLinkHandler` and the cache. Do not make public resolution disclose management data or require a logged-in workspace.

For publication creation, default to an explicit opt-in in the composer. Create a link before submitting `CreatePublicationCommand`, then replace the URL only in the post body on explicit user confirmation and display the resulting short URL before create. If link creation fails, retain the original body and let the user retry or proceed with the original URL; never silently substitute. Do not mutate links on edit or reschedule. The existing shortlink-management API is authenticated/workspace-context based, but is not suitable as a general frontend client contract until its versioned media-type/auth behavior is confirmed in implementation tasks.

## Architecture Decisions

| Decision | Options / trade-off | Choice and rationale |
|---|---|---|
| Link lifecycle | Create during composer submit; create on publication worker; create at publish time | Create in composer before `CreatePublicationHandler`: that handler persists/enqueues immutable `bodyText`, and provider jobs consume the queued draft. It makes the final URL visible and avoids publishing an unshortened body then editing it asynchronously. On failure do not submit replacement text; allow original URL or retry. |
| Click meaning | Unique users/sessions; raw redirect attempts; deduplicated visits | Count one click per successfully resolved active-link redirect attempt. No identity, IP, user-agent, or deduplication data. Name the measure “recorded redirects”; make no unique-user or accuracy claim. |
| Recording | Await insert; fire-and-forget; durable outbox | Minimal first version: attempt a single click insert after successful active resolution and before redirect, with tracking failure not preventing redirect. Awaited database work adds latency; fire-and-forget has process-loss and lifecycle hazards, while outbox/reconciliation is excluded. Keep this as an explicit measured risk; if latency is unacceptable, stop for a separate durable asynchronous design rather than quietly detach work. |
| Tenant boundary | Filter client-side; enforce in query | Analytics query takes workspace from `ResourceContextProvider.requireWorkspaceContext()` and filters using link ownership in the database query; never accept owner/workspace ID from request body/query. For unknown/foreign link IDs return the same not-found result. Redirect remains public and reveals only status/Location, not owner or metrics. |

## Data Flow

`Composer explicit opt-in → existing shortlink create command (workspace context) → show short URL → submit publication body → CreatePublicationHandler persists/enqueues → provider publishes body`

`Public GET /{shortCode} → ResolveLinkHandler (cache/repository) → active resolution → best-effort click insert → existing 302/no-store response`

`Authenticated workspace request → current workspace context → analytics query filters links by owner → aggregate click count by link/time scope → response`

## File Changes

| File | Action | Description |
|---|---|---|
| `apps/web/app/src/modules/publishing/presentation/components/CreatePostModal.vue` and publishing feature API/client | Modify | Explicit shortlink choice, preview/result, safe fallback before create. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/shortlinks/{domain,application,infrastructure}` | Modify | Click persistence port/model, record operation integrated into successful redirect, workspace-filtered analytics query/controller. |
| `server/smp/src/main/resources/db/changelog/shortlinks/` | Create | Minimal click-record table keyed to link identity and timestamp; no visitor data or retention policy. |
| `server/smp/src/test/.../shortlinks`, publishing BDD, app publishing tests | Modify | Redirect/click behavior, cross-workspace denial, composer and publication flows. |
| `docs/architecture/adr/0028-defer-shortlinks-beyond-core-v1.md` | Modify | Append bounded-scope decision; preserve original decision/history and deferred capabilities. |

## Interfaces / Contracts

Metrics are a protected read endpoint within `/api/v1/links` (exact route finalized with existing controller conventions). Response contains link ID, recorded redirect count, and documented time scope; initial scope is all retained records, with no promised retention or precision. Workspace identity is derived from authenticated request context, never supplied by caller. Preserve API media-type/auth conventions after checking the controller security configuration.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Count semantics, failed tracking, workspace filtering | Application handler tests with fake ports. |
| Integration/BDD | Active redirect records; disabled/expired/not-found do not; cross-workspace metrics unavailable | `WebTestClient`, Liquibase-backed persistence and Cucumber scenarios. |
| Frontend | Explicit choice, preview, failed-shortening fallback, create payload | Publishing Vitest; critical composer E2E if feasible. |

## Migration / Rollout

Additive click table only; existing links remain valid and begin accumulating counts after rollout. No backfill, retention job, outbox, domain provisioning, QR, or personal data collection.

## Open Questions

- Confirm final metric time-scope presentation and management endpoint security/media-type wiring during task breakdown. The design recommends all stored records with no retention guarantee.
- Redirect write latency must be measured; if it is unacceptable, synchronous best-effort recording is not an acceptable final implementation and the scope needs a separate durable async decision.
