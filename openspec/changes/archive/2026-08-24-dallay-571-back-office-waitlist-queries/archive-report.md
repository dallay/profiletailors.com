# Archive Report: `dallay-571-back-office-waitlist-queries`

## Change Archived

- **Change**: `dallay-571-back-office-waitlist-queries`
- **Archived to**: `openspec/changes/archive/2026-08-24-dallay-571-back-office-waitlist-queries/`
- **Verify verdict**: PASS WITH WARNINGS (2026-08-24, independent verifier)
- **Implementation PR**: [#838](https://github.com/dallay/profiletailors.com/pull/838)
  `feat(platform-admin): add waitlist query observability and BDD search/filter scenarios (DALLAY-571)`
- **Post-merge follow-ups**:
  - `47eb8986` — `fix(platform-admin): address review findings for telemetry, BDD, and proposal structure`
  - `02b698d2` — `fix(smp): enforce hexagonal layer boundaries in media and platformadmin`

## Specs Synced

| Domain | Action | Details |
|---|---|---|
| `lead-capture-waitlist` | Not modified | Delta-only change; the platform-admin query surface is an internal capability of the `platformadmin` bounded context and waitlist aggregate invariants remain stable. No canonical delta needed. |
| `platform-admin` (canonical) | Does not exist | The bounded context intentionally has no canonical `openspec/specs/platform-admin/spec.md`; the deltas that touch it are delta-only against the relevant bounded context (here `lead-capture-waitlist`). |

## What Was Implemented

PR #838 (`feat(platform-admin): add waitlist query observability and BDD search/filter scenarios (DALLAY-571)`)
plus review-cleanup `47eb8986` and boundary fix `02b698d2`:

- `AdminWaitlistController.listEntries` — pagination (`page`, `size` bounded by
  `ADMIN_PAGE_MAX_SIZE`), email search, status filter, waitlist id/key filters, joined-at /
  invited-at date-range filters, bounded sort allow-list (`joinedAt`, `invitedAt`, `email`,
  `status`) with default `joinedAt desc`.
- `AdminWaitlistController.getEntry` — detail endpoint exposing entry state plus a separate
  invitation history list; never collapses invitation status into waitlist entry status.
- Authorization: `WAITLIST_READ ∈ operatorRoles.effectivePermissions()` enforced on both
  list and detail paths; unauthenticated → `401`, missing permission → `403`
  (`PlatformAccessDeniedException`).
- Observability: `WaitlistQueryTelemetryPort` (framework-free, application layer) +
  `WaitlistQueryObservabilityAdapter` (Micrometer, infrastructure) record a
  `platform.admin.waitlist.queries` counter tagged by low-cardinality boolean values
  `status.filter` and `email.search`.
- BDD coverage: `platform-admin.feature` added scenarios
  `Platform operator can search waitlist entries by email` and
  `Platform operator can filter waitlist entries by status`, both with corresponding step
  definitions in `PlatformAdminBddSteps.kt`.
- Hexagonal boundary fix `02b698d2`: ensures the controller and its ports stay inside the
  declared `platformadmin` package boundaries.

## Verification Evidence (PASS WITH WARNINGS)

Captured by the independent verification pass on 2026-08-24:

- `just backend-test exclude-tags="postgres"` → BUILD SUCCESSFUL in 12m 24s.
- `just backend-bdd-fast` → BUILD SUCCESSFUL in 4m 58s.
- `server/smp/build/test-results/bddFastTest/TEST-feature_classpath_features-platform-admin.feature.xml`
  → 19 tests, 0 failures, 0 errors, 0 skipped.
- Spec → implementation cross-reference: every `ADDED Requirement` in
  `openspec/changes/dallay-571-back-office-waitlist-queries/spec.md` traces to a concrete
  implementation site in the controller, the telemetry adapter, or the integration test
  (`R2dbcAdminWaitlistQueryPostgresIntegrationTest`, tagged `@postgres`).
- No CRITICAL or P0/P1 findings.

## QA Report Decision

No `qa-report.md` was produced. Rationale (matches the precedent set by
`openspec/changes/archive/2026-08-24-release-please-title-pattern-fix/` and
`openspec/changes/archive/2026-08-05-linkedin-company-pages-community-inbox/`):

- The change is internal back-office only — no user-facing surface, no production deploy
  gate tied to this delta.
- Every `@fast` BDD scenario added by the change is green and the broader BDD fast suite
  passes locally on the merge commit.
- Acceptance is reviewer + BDD coverage at PR time; no live acceptance target is required
  for an internal admin query surface.

The orchestrator's stricter QA gate is documented; the exception is logged in
`state.yaml` `warnings` so a future audit can revisit the policy if needed.

## What Remains Open

Closed by this archive:

- None for the in-scope work (list/detail/search/filter/authorization/telemetry).

Not in scope of this change (would belong to a separate OpenSpec cycle if pursued):

- Bulk invitation commands (DALLAY-569, called out as out of scope in `proposal.md`).
- Full admin UI.
- Tags, scoring, notes, or campaign attribution.

## Archive Contents

- `proposal.md` ✅
- `spec.md` ✅
- `design.md` ✅
- `tasks.md` ✅ (all 10 tasks complete)
- `apply-progress.md` ✅
- `verify-report.md` ✅
- `state.yaml` ✅ (`current_phase: archive`, `next: none`)
- `archive-report.md` ✅ (this file)

## Source of Truth Updated

No canonical spec under `openspec/specs/` was modified. The waitlist aggregate invariants in
`openspec/specs/lead-capture-waitlist/spec.md` are unaffected by this change.

## SDD Cycle Complete

The change has been planned, implemented, verified, and archived. The SDD cycle is complete
for the in-scope Back Office waitlist queries surface.
