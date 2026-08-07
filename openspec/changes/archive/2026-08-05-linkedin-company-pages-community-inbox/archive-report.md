## Change Archived

**Change**: linkedin-company-pages-community-inbox
**Archived to**: `openspec/changes/archive/2026-08-05-linkedin-company-pages-community-inbox/`
**Verify verdict**: PASS WITH WARNINGS (2026-08-04)
**Implementation status**: partial — Task 2.2 remains schema-blocked and intentionally open; Tasks 3.1, 3.2, 5.1, and 5.2 remain open.

### Specs Synced

| Domain               | Action  | Details                                     |
|----------------------|---------|---------------------------------------------|
| social-content-sync  | Synced  | Main spec already identical to change delta (full spec, 0 add / 0 modify / 0 remove) |
| community-inbox      | Synced  | Main spec already identical to change delta (full spec, 0 add / 0 modify / 0 remove) |
| publishing           | Updated | 2 added, 0 modified, 0 removed requirements |
| visual-calendar      | Updated | 2 added, 0 modified, 0 removed requirements |

### What Was Implemented

PR2 read foundation for LinkedIn Company Page discovery and read-only social-content import:

- Version-1 workspace-scoped read contracts: `POST /api/publishing/social-content/sync`, `GET /api/publishing/social-content/calendar`, `GET /api/publishing/social-content/posts/{externalPostId}` with Bearer / `X-Workspace-Id` / `Accept: application/vnd.api.v1+json` (JSON `Content-Type` on sync).
- Mediator/Spring handler registration with workspace isolation derived from `ResourceContextProvider`; foreign-workspace reads fail closed.
- Bounded opaque-cursor pagination with range/limit validation (1–100) and invalid-input rejection before provider calls.
- Idempotent checkpointed sync: upsert by workspace/provider/actor/external-post identity, overlap deduplication, high-water-mark/lastSuccessfulAt advanced only after persistence, bounded rate-limit retries honoring `Retry-After`.
- Retention expiry plus full-sync-only tombstones; incremental pages never infer deletion.
- All-evidence safe-off gate (approval, `ADMIN`, `r_organization_social` + `r_organization_social_feed`, supported API version, retention-policy version) with typed denial before any provider call; discovery/import/inbox/replies/Page publishing default to disabled and fail closed.
- Community Management adapter kept provider-neutral and separate from personal OAuth/`RealLinkedInPublisher`; personal publishing semantics preserved.
- Imported Page posts are immutable calendar read models (`mutationAllowed = false`) and cannot enter publication writes.
- Tagged Cucumber coverage (`social-content-sync.feature`, `community-inbox.feature`) plus focused unit/wiring tests.

### Verification Evidence (PASS WITH WARNINGS)

- Focused wiring regression test: 4/4 PASS.
- `backend-bdd-fast`: 189 scenarios, 0 failures.
- `bddPostgresTest`: BUILD SUCCESSFUL, 189 scenarios, 0 failures.
- `backend-test-fast` and `backend-lint`: BUILD SUCCESSFUL.
- `compileTestKotlin`: BUILD SUCCESSFUL.
- `git diff --check`: clean.
- No CRITICAL findings in the implemented read/safe-off scope.

### What Remains Open

- **Task 2.2 — PARTIAL, schema-blocked (MUST NOT be closed by archive).** Atomic post/tombstone/checkpoint batch behavior is implemented and verified, but actor persistence, approval-evidence persistence, and provider-portable checkpoint mapping are blocked by the existing schema. No migration or simulated productive repository was added. This must be carried into a follow-up change with an approved persistence design before Community Management can be enabled.
- **Tasks 3.1, 3.2, 5.1, 5.2 — open in `tasks.md`.** Explicit version mappings/validation and application-context tests, plus the full `backend-check` / `backend-build` / `ci-local` gate record, remain to be completed and verified.
- Residual warnings: production R2DBC calendar cursor continuation is not implemented end-to-end (`findImportedPosts` does not yet use the cursor in SQL and returns `nextCursor = null`); imported-Page publication-write rejection and mixed personal/imported calendar scenarios lack active runtime coverage (write operations are out of PR2 scope by proposal).

### Archive Contents

- proposal.md ✅
- specs/ ✅ (social-content-sync, community-inbox, publishing, visual-calendar)
- design.md ✅
- apply-progress.md ✅
- tasks.md ⚠️ (6/11 complete, 1 partial, 4 open — intentionally not closed)
- verify-report.md ✅
- state.yaml ✅
- archive-report.md ✅

### Source of Truth Updated

The following specs now reflect the new behavior:

- `openspec/specs/social-content-sync/spec.md` (already in sync)
- `openspec/specs/community-inbox/spec.md` (already in sync)
- `openspec/specs/publishing/spec.md` (delta requirements merged)
- `openspec/specs/visual-calendar/spec.md` (delta requirements merged)

### SDD Cycle Complete

The change has been planned, implemented, verified, and archived. The SDD cycle is complete for the in-scope PR2 read foundation. Follow-up work (Task 2.2 schema persistence, Tasks 3.1/3.2/5.1/5.2, production cursor continuation, write-rejection/mixed-calendar runtime coverage) must be tracked as new changes.
