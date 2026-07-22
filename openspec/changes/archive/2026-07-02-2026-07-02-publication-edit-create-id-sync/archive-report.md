# Archive Report: Publication Edit Create-ID Sync

## Change

- Change: `2026-07-02-publication-edit-create-id-sync`
- Mode: openspec
- Archive date: 2026-07-02
- Verification verdict: PASS WITH WARNINGS
- Critical blockers: None

## Archive Summary

This grouped follow-up resolves the browser-observed residual create->edit failure for the
publication-edit hardening work by reconciling authenticated create results from backend
`PublicationResult` into frontend state before edit flows use publication identity or scheduling
data.

It supports one PR closing/cleanly explaining the #223/#224/#225 relationship:

- #223 asset hydration/preservation remains intact: untouched media is hydrated and previewed, PATCH
  omits `assetIds`, explicit clear sends `[]`, and replacement sends selected IDs.
- #224 workspace isolation regression coverage is preserved by focused backend gates.
- #225 404 not-found contract is preserved: unknown update targets remain 404, and the synthetic-ID
  404 was fixed by backend ID reconciliation rather than backend fallback creation.

## Runtime Browser Evidence

Future scheduled post create with image -> edit text without touching media -> PATCH 200 -> reopen
with media retained.

The browser run PATCHed real backend ID `pub-b09e0924-4939-4509-b7d3-ce8be5664b36`, returned 200,
refreshed the calendar, and reopened with edited text plus preserved media preview.

## Hygiene Before Archive

- Out-of-scope `infra/wiremock/compose.yaml` diff was restored before archiving.
- Untracked `apps/web/app/e2e/.generated/` output was removed before archiving.

## Specs Synced

| Domain          | Action  | Details                                                                                                                       |
|-----------------|---------|-------------------------------------------------------------------------------------------------------------------------------|
| publishing      | Updated | Added 2 requirements: `Authenticated Create Reconciliation`, `Reconciled Composer Edit State`; 0 modified; 0 removed.         |
| visual-calendar | Updated | Modified 1 requirement: `Quick-Create from Cell`; added backend identity reconciliation and editability scenarios; 0 removed. |

## Source of Truth Updated

- `openspec/specs/publishing/spec.md`
- `openspec/specs/visual-calendar/spec.md`

## Archive Contents

- `proposal.md` ✅
- `specs/` ✅
- `design.md` ✅
- `tasks.md` ✅ (14/14 tasks complete)
- `verify-report.md` ✅
- `state.yaml` ✅ (archived)

## Verification Notes

The final verification report contained warnings but no critical blockers. The warnings were
reviewed in light of user-provided hygiene status:

1. Live NOW edit returned 409 after targeting a real backend ID. This remains a non-critical
   product/runtime behavior outside the synthetic-ID 404 regression.
2. `infra/wiremock/compose.yaml` out-of-scope diff was restored before archive.
3. `apps/web/app/e2e/.generated/` untracked output was removed before archive.

## Archive Path

`openspec/changes/archive/2026-07-02-2026-07-02-publication-edit-create-id-sync/`

## SDD Cycle Complete

The change has been planned, implemented, verified, synced into source-of-truth specs, and archived.
