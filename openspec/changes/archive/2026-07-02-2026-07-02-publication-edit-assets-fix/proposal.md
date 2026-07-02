# Proposal: Publication Edit Asset Preservation

## Intent

Fix publication editing so existing attachments are visible and are not silently removed when the user saves unrelated changes. Adopt explicit PATCH tri-state semantics for `assetIds` while preserving CREATE defaults and workspace isolation.

## Scope

### In Scope
- Make edit request, command, and handler asset IDs nullable: absent/null preserves, `[]` clears, and a non-empty list replaces.
- Keep CREATE asset behavior unchanged, safely defaulting to an empty list.
- Hydrate existing asset summaries when opening the composer in edit mode.
- Track edit-only asset-selection interaction and serialize the correct PATCH shape.
- Add regression tests first for all backend semantics, hydration, and untouched submission.
- Deliver within the grouped publication-edit-hardening change, preserving #224/#225 behavior.

### Out of Scope
- Media-library lifecycle or upload changes.
- Composer selection-store redesign.
- Separate PRs for each publication-edit issue.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `publishing`: Define PATCH tri-state asset semantics and composer attachment hydration/submission behavior.

## Approach

Separate CREATE and EDIT mapping at the HTTP boundary so CREATE converts null/absent assets to an empty list while EDIT carries null through the command. The edit handler preserves current IDs for null and applies exact replacement otherwise. In the SPA, hydrate summaries for attached IDs and maintain an edit-only touched flag: omit when untouched, send `[]` after explicit clear, or send selected IDs after replacement. Implement regression tests before production changes.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/.../publishing/infrastructure/http` | Modified | Nullable request mapping with create/edit-specific defaults |
| `server/smp/.../publishing/application` | Modified | Nullable edit command and preserve/replace handling |
| `apps/web/app/src/components/CreatePostModal.vue` | Modified | Hydration, touched state, PATCH serialization |
| Backend and frontend publishing tests | Modified | Contract and regression coverage |
| `openspec/specs/publishing/spec.md` | Modified | Publication edit asset requirements |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| CREATE accidentally inherits PATCH semantics | Med | Separate mapping and create regression test |
| Hydration races library loading or encounters deleted assets | Med | Await scoped fetches and handle missing summaries gracefully |
| Existing #224/#225 workspace/edit behavior regresses | Low | Retain workspace predicates and run existing focused suites |

## Rollback Plan

Revert the grouped change and restore non-null edit mapping plus prior composer submission. No migration or persisted-data rollback is required.

## Dependencies

- Existing workspace-scoped publishing and media read APIs.
- Grouped publication-edit-hardening delivery branch.

## Success Criteria

- [ ] PATCH absent/null preserves, `[]` clears, and `[ids]` exactly replaces assets.
- [ ] CREATE without assets still persists an empty list.
- [ ] Edit mode displays existing attachments and untouched saves omit `assetIds`.
- [ ] Explicit clear/replacement sends the correct payload.
- [ ] Regression suites pass without weakening workspace isolation or #224/#225 behavior.
