# Tasks: Backoffice Admin Shell

## Review Workload Forecast

| Field                   | Value     |
|-------------------------|-----------|
| Estimated changed lines | 250–350   |
| 400-line budget risk    | Medium    |
| Chained PRs recommended | No        |
| Suggested split         | Single PR |
| Delivery strategy       | single-pr |
| Chain strategy          | pending   |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal                               | Likely PR | Notes                                                                 |
|------|------------------------------------|-----------|-----------------------------------------------------------------------|
| 1    | Full shell change + tests + checks | PR 1      | Single PR to main; Vitest + admin-check + admin-build + lint included |

## Phase 1: Foundation (registry + i18n keys)

- [x] 1.1 RED: add `apps/web/admin/src/router/nav-registry.spec.ts` asserting registry entries
  (key/routeName/path/permission/status) + `direct-invitations` live entry. Accept: test fails, no
  module yet.
- [x] 1.2 GREEN: create `apps/web/admin/src/router/nav-registry.ts` (`NavEntry`, `NAV_REGISTRY`,
  overview/users/waitlist/invitations/notifications/governance/configuration/audit seams). Accept:
  1.1 passes; live routes untouched.
- [x] 1.3 Add `nav.*` + `planned.message` keys to `apps/web/admin/src/i18n/index.ts`, `types.ts`
  (EN+ES). Accept: `tsc` passes; all entries labelled in both locales.

## Phase 2: Core Implementation

- [x] 2.1 Rewrite `apps/web/admin/src/layouts/AdminLayout.vue` nav from `NAV_REGISTRY` filtered by
  existing `hasPermission`. Accept: 4 live items render; unpermitted entries hidden;
  `direct-invitations` visible with `platform.invitations.read`.
- [x] 2.2 RED: extend `nav-registry.spec.ts` with nav-filtering per role (missing permission hides
  entry). Accept: fails before 2.1 filter, passes after.
- [x] 2.3 Generate planned children in `apps/web/admin/src/router/index.ts` from registry
  (`status==='planned'` → `PlannedAreaView`, `meta.permission` = nearest existing key); guard
  unchanged. Accept: planned paths resolve; direct nav without permission denied.
- [x] 2.4 Create `apps/web/admin/src/views/PlannedAreaView.vue` (static `t('planned.message')` +
  label; no fetch/store). Accept: renders message; zero API calls.
- [x] 2.5 RED→GREEN: add `apps/web/admin/src/views/PlannedAreaView.spec.ts` (inert render + no
  `request` call). Accept: fails pre-view, passes after.
- [x] 2.6 Additive mirror fix in `apps/web/admin/src/stores/auth.store.ts`: append
  `platform.publishing.stale.read` to OWNER + OPERATOR. Accept: `hasPermission` true for both; no
  other array changes. Reconcile registry doc 13 vs server 14 count in same edit.

## Phase 3: Testing / Verification

- [x] 3.1 Run Vitest guard/nav-filtering suites (`just frontend-test` scoped to admin). Accept: all
  new + existing admin specs pass.
- [x] 3.2 Run `admin-check` + `admin-build` (just recipes). Accept: both pass, evidence recorded.
- [x] 3.3 Run `just frontend-lint` on touched files. Accept: clean.
- [x] 3.4 Verify no backend/`shared-web`/telemetry diff (`git status` scoped). Accept: only
  `apps/web/admin/**` changed.

## Phase 4: Docs / Extensibility

- [x] 4.1 Document 1-entry extensibility (registry + 2 i18n labels; live needs view/route) as code
  comment or change note. Accept: reviewer can add a planned area in one entry.
