# Proposal: Scheduler-first URL-state standard

## Intent

Standardize scheduler URL state so scheduler views are shareable, restorable, and canonical across refresh, deep links, and back/forward. Now is the right time because the route-first baseline already exists, but modal state, filter coverage, and durable guidance have drifted.

## Scope

### In Scope

- Extend scheduler URL state for iteration 1 only: `surface`, `date`, `timezone`, `status`, `q`, `channels[]`, and detail modal `postId`
- Make scheduler post detail modal route-driven; auto-close and canonicalize the URL when the selected post leaves the active context
- Reconcile scheduler route/query behavior with current canonical scheduler surfaces and document the standard durably for future app work

### Out of Scope

- Adopting `vue-qs` or replacing `vue-router` + domain composables
- Expanding the standard beyond scheduler in this iteration
- New backend filtering capabilities or non-scheduler deep-link models

## Capabilities

### New Capabilities

- `scheduler-url-state-standard`: Scheduler URL contract for route-owned filters, context restoration, and `postId` deep linking

### Modified Capabilities

- `visual-calendar`: Calendar/list behavior must honor the expanded query contract and canonical modal auto-close behavior

## Approach

Keep `vue-router` plus scheduler domain composables as the repo direction. Extend `apps/web/app/src/composables/useCalendarUrl.ts` into the single scheduler URL-state codec instead of adding `vue-qs` or splitting ownership. `SchedulerView.vue` should resolve modal state from `postId`, detect when date/filter/surface/timezone changes make that post unavailable in the current context, then `router.replace` to remove stale query state. Add durable guidance at `docs/architecture/scheduler-url-state-standard.md` and index it from `docs/README.md`.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `apps/web/app/src/composables/useCalendarUrl.ts` | Modified | Expand typed scheduler query contract and canonicalization rules |
| `apps/web/app/src/views/SchedulerView.vue` | Modified | Derive modal visibility from `postId` and auto-close invalid selections |
| `apps/web/app/src/router/index.ts` | Modified | Keep scheduler routes aligned with canonical surfaces |
| `apps/web/app/src/components/layout/AppShell.vue` | Modified | Stay aligned with route-owned `channels[]` behavior |
| `docs/architecture/scheduler-url-state-standard.md` | New | Durable URL-state modeling guidance for this app |
| `docs/README.md` | Modified | Add documentation index entry |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Modal closes too aggressively during refetch timing | Med | Define when to wait for route-backed data before canonicalizing |
| Multi-channel URL semantics drift from single-select UI assumptions | Med | Lock iteration-1 query semantics in specs/design before implementation |
| Route drift persists around legacy day behavior | Low | Require spec/design to state canonical scheduler surfaces explicitly |

## Rollback Plan

Revert the expanded scheduler query contract and restore local-only modal ownership in `SchedulerView.vue`; remove `postId` handling and new docs if the route-driven model causes instability.

## Dependencies

- Existing scheduler route-first baseline from archived `scheduler-url-addressable`
- Durable docs update under `docs/`

## Success Criteria

- [ ] Scheduler URLs round-trip `surface`, `date`, `timezone`, `status`, `q`, `channels[]`, and `postId`
- [ ] Opening a scheduler post is refreshable/shareable via URL and auto-closes when context changes invalidate it
- [ ] Docs explain how to model URL state in this app and are indexed from `docs/README.md`
