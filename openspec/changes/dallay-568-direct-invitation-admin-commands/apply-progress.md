# Apply Progress — DALLAY-568 Direct Invitation Admin Commands

## Layer

PR 2 of `github-stacked-prs` chain — SPA UI slice.

| Field | Value |
|-------|-------|
| Base branch | `dalay-verify-pr-links` (current HEAD) |
| Parent branch (PR 1 target) | `feature/dallay-563-administrative-authorization-boundary` |
| Trunk | `main` |
| Position | Layer 3 (top of stack) |
| Delivery strategy | `github-stacked-prs` |
| Scope implemented | Admin SPA UI for direct invitation create + revoke |

## Completed Tasks (this batch)

- [x] 2.11 Create Invitation screen (route + view with form, server-error mapping, success card)
- [x] 2.12 Revoke dialog (reusable component, 404/409/403 mapping, expectedVersion flow)
- [x] 2.13 UI permission guards (`platform.invitations.read` for route; `create`/`revoke`/`resend` on action buttons)

## Previously Completed (PR 2 handlers + controller)

- [x] 2.1 `CreateInvitationCommand`
- [x] 2.2 `RevokeInvitationCommand`
- [x] 2.3 `CreateInvitationResult`, `RevokeInvitationResult`, `ResendInvitationResult`
- [x] 2.4 `InvitationRepository.hasActiveInvitationFor`
- [x] 2.5 `Invitation.revoke(expectedVersion)`
- [x] 2.6 `CreateInvitationHandler`
- [x] 2.7 `RevokeInvitationHandler`
- [x] 2.8 `AdminInvitationController` (`POST /direct`, `POST /{id}/direct-revoke`, `POST /{id}/direct-resend`, `GET /{id}`)
- [x] 2.9 `InvitationNotificationAdapter`
- [x] 2.10 Wiring in `PlatformAdminBootstrapConfiguration`
- [x] 2.14 `ResendInvitationHandler`
- [x] 2.15 `POST /{id}/direct-resend`

## Files Created

| File | Purpose |
|------|---------|
| `apps/web/admin/src/views/DirectInvitationsView.vue` | List/create form combined view |
| `apps/web/admin/src/views/DirectInvitationsView.spec.ts` | Vitest spec for view (6 tests) |
| `apps/web/admin/src/components/RevokeInvitationDialog.vue` | Reusable revoke dialog |
| `apps/web/admin/src/components/RevokeInvitationDialog.spec.ts` | Vitest spec for dialog (6 tests) |

## Files Edited

| File | Change |
|------|--------|
| `apps/web/admin/src/router/index.ts` | Registered `/direct-invitations` route with `platform.invitations.read` meta |
| `apps/web/admin/src/i18n/index.ts` | Added `directInvitations` namespace in EN + ES |
| `apps/web/admin/src/i18n/types.ts` | Typed `directInvitations` schema |
| `openspec/changes/dallay-568-direct-invitation-admin-commands/tasks.md` | Marked 2.11, 2.12, 2.13 complete |
| `openspec/changes/dallay-568-direct-invitation-admin-commands/state.yaml` | Recorded `apply-pr2-ui` and advanced `next` to `verify` |

## Verification

| Command | Result |
|---------|--------|
| `pnpm --filter admin type-check` | PASS |
| `pnpm --filter admin lint` | PASS |
| `pnpm --filter admin test:run` | PASS — 26 tests (12 new + 14 pre-existing) |

## Notes

- vue-i18n 11 message compiler requires `{'@'}` escaping for literal `@` in i18n strings.
- Permission meta gates on the route; action buttons in the view use `authStore.hasPermission`.
- The dialog accepts `expectedVersion` and surfaces `INVITATION_NOT_FOUND`, `INVITATION_VERSION_CONFLICT` (or similar), and `PLATFORM_ACCESS_DENIED` from the existing `errors.*` namespace.
- The view uses the same `errorMessage(res)` pattern as `WaitlistEntryView.vue` lines 99-107.
- Worktree diff is bounded: 95 inserted lines across 3 SPA files, 2 new components, 2 new spec files.
