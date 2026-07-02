# Proposal: Publication Edit Create-ID Sync

## Intent

Ship one follow-up PR that fixes the residual scheduler create→edit failure without reopening the already-landed fixes for #223, #224, and #225. The root problem is frontend reconciliation: authenticated create flows keep synthetic local publication IDs and stale schedule fields instead of adopting backend publication IDs and normalized scheduling data, which later causes PATCH 404s and invalid stale edit schedule state.

## Scope

### In Scope
- Reconcile authenticated create/quick-create/store sync flows with backend `PublicationResult` data.
- Preserve backend `publicationId`, `scheduleMode`, `scheduledFor`, `nextSlotAfter`, `status`, and `socialAccountId` in client state.
- Add regression coverage proving a freshly created publication can be reopened and edited successfully.

### Out of Scope
- Changing backend PATCH semantics or relaxing #225 not-found behavior.
- New scheduler UX beyond fixing incorrect stale edit-state initialization.

## Capabilities

### New Capabilities
- None

### Modified Capabilities
- `publishing`: require client edit/save flows to target persisted publication IDs and refresh state from server truth after authenticated creation.
- `visual-calendar`: require quick-create-created publications to refresh into editable calendar state using backend-normalized scheduling fields.

## Approach

Update frontend publishing store reconciliation so authenticated create paths replace optimistic placeholder records with backend results immediately. Use the reconciled publication model when opening edit mode so schedule prefill reflects persisted server values, not stale local placeholders.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `apps/web/app/src/stores/publishing.ts` | Modified | Reconcile create/quick-create/store sync with backend publication payloads |
| `apps/web/app/src/components/CreatePostModal.vue` | Modified | Initialize edit schedule state from reconciled publication data |
| `openspec/specs/publishing/spec.md` | Modified | Clarify create-to-edit reconciliation expectations |
| `openspec/specs/visual-calendar/spec.md` | Modified | Clarify quick-create refresh/editability contract |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Tests rely on synthetic `pub-*` IDs | Med | Update fixtures and add focused regression tests |
| Other scheduler views assume custom schedule semantics | Med | Verify queued/next-slot/calendar/edit flows against backend-normalized fields |

## Rollback Plan

Revert the frontend reconciliation changes in the follow-up PR, restoring prior create flow behavior while keeping #223, #224, and #225 intact.

## Dependencies

- Existing backend update-only PATCH contract from #225 remains authoritative.
- Already-verified fixes for #223 and #224/#225 stay unchanged.

## Success Criteria

- [ ] Authenticated create and quick-create flows replace placeholder IDs with persisted backend publication IDs.
- [ ] Editing a freshly created publication no longer triggers PATCH 404.
- [ ] Edit modal schedule state reflects backend-normalized values for queued, scheduled, and next-slot publications.
