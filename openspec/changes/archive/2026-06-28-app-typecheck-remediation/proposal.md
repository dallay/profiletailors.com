# Proposal: App Type-Check Remediation

## Intent

Restore a deterministic zero-error `apps/web/app` type-check by eliminating all 21 errors across 12 files without changing runtime or product behavior. Remediation is grouped around configuration typing, date/scheduler model alignment, browser `File` stream test doubles, strict test indexing/imports, media lifecycle/presentation mapping, workspace nullability, and timer typing.

## Scope

### In Scope
- Correct Vite/Vitest configuration types without weakening compiler settings.
- Align composer dates/modes and scheduler route/activity fixtures with existing contracts; preserve the day-route week fallback.
- Use DOM-compatible `File.stream()` doubles and strict-safe test imports/index access.
- Preserve the media UI’s in-progress presentation through a typed mapping of `PENDING_UPLOAD`/`UPLOADING`, and guard absent workspace context.
- Apply environment-neutral timer typing and verify two zero-error type-check runs plus focused/full tests.

### Out of Scope
- Broad refactors, shared-model redesigns, or relaxed/excluded type-check coverage.
- Product behavior, API, route, or lifecycle changes.
- E2E expansion or modification/staging of unrelated CAS E2E PR 1 files.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
None. This remediation restores static contract conformance while preserving specified behavior.

## Approach

Apply minimal contract-aligned fixes by error group, using existing patterns and focused regression tests. Keep API lifecycle values distinct from presentation labels, canonicalize the day route to its current week behavior, and avoid broad casts or non-null assertions. Treat unrelated CAS E2E PR 1 work as read-only and independently verify its mocked gate.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `apps/web/app/vite.config.ts` | Modified | Vitest-aware config typing |
| `apps/web/app/src/components`, `src/composables` | Modified | Date, scheduler, stream, and timer-safe contracts |
| `apps/web/app/src/i18n`, `src/lib`, `src/stores` | Modified | Strict tests and workspace guard |
| `apps/web/app/src/views` | Modified | Typed media mapping and current scheduler fixture |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Accidental runtime drift | Medium | Focused behavior tests and exact contract fixes |
| CAS PR 1 contamination | Medium | Do not edit or stage its files; inspect final diff/status |
| Tooling type change breaks tests | Low | Load config and run focused/full suites |

## Rollback Plan

Revert only remediation-owned hunks/files; leave CAS E2E PR 1 work untouched. Re-run the original type-check to confirm the prior baseline is restored.

## Dependencies

- Existing Vue, Vitest, Vite, router, publishing, and media contracts; no new packages.

## Success Criteria

- [ ] All 21 deterministic errors across 12 files are eliminated in two consecutive `pnpm --filter app type-check` runs.
- [ ] Focused and full unit tests pass with runtime/product behavior unchanged.
- [ ] CAS mocked E2E gate passes, with no unrelated PR 1 file modified or staged.
