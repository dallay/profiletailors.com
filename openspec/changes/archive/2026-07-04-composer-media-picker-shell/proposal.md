# Proposal: Composer Media Picker Shell

## Intent

Give authors a clear, accessible shell for choosing reusable workspace media from the post composer. The current composer supports local files and previews, while the media library is a separate destination; users need an in-context picker foundation before selection and attachment behavior can be completed.

## Scope

### In Scope
- Add a composer-triggered modal shell using the existing dialog primitives.
- Provide localized header, close action, search/filter controls, asset-grid region, and loading, empty, error, and disabled states.
- Define typed inputs/events so later work can connect media-library data and selected assets without redesigning the shell.
- Add focused component tests for open/close, accessibility labels, state rendering, and emitted interactions.

### Out of Scope
- Fetching, pagination, upload, deletion, or mutation of media assets.
- Persisting selected assets to a draft/publication or changing backend APIs.
- Multi-asset selection, provider imports, and non-composer reuse.

## Capabilities

### New Capabilities
- `composer-media-picker`: Accessible composer modal shell and its presentation/interaction contract for browsing reusable media.

### Modified Capabilities
- None.

## Approach

Create a presentational Vue component under the composer boundary, composed from existing shadcn-vue/Reka dialog and form controls. Keep data ownership in the parent through typed props and emitted events; reuse media asset types and i18n conventions. Verify behavior with Vitest/component tests, leaving API/store integration for a follow-up change.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `apps/web/app/src/components/composer/` | New | Media picker shell and typed contract |
| `apps/web/app/src/components/CreatePostModal.vue` | Modified | Trigger and shell composition |
| `apps/web/app/src/i18n/index.ts` | Modified | English and Spanish picker copy |
| `apps/web/app/src/components/**/*.test.ts` | Modified | Component interaction and state coverage |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Shell contract mismatches later store integration | Med | Reuse existing asset types and keep props/events minimal |
| Modal focus or keyboard regression | Low | Use established dialog primitives and test accessible interactions |
| Scope drifts into media workflows | Med | Exclude fetching, selection persistence, and backend changes explicitly |

## Rollback Plan

Remove the composer trigger, shell component, translations, and focused tests. No schema, API, or persisted-data rollback is required.

## Dependencies

- Existing dialog/UI primitives, composer modal, media asset frontend types, and i18n setup.

## Success Criteria

- [ ] Authors can open and dismiss the picker shell by pointer and keyboard without losing composer state.
- [ ] The shell renders localized controls and deterministic loading, empty, error, and disabled states.
- [ ] Typed events expose search/filter/close interactions without direct store or API coupling.
- [ ] Focused component tests pass for accessibility and state transitions.
