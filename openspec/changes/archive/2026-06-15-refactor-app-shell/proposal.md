# Proposal: Refactor `App.vue` into a Composable App Shell

## Intent

`apps/web/app/src/App.vue` is 737 lines and mixes six unrelated concerns: route-gating, sidebar
shell (workspace switcher, navigation, channels list, connect subpanel, account menu), header
(trigger, section title, status pill, language/theme pills), global click-outside / escape /
route-watcher dismissal logic, the main outlet, and the auth bootstrap watcher. The file has
become the de-facto scratchpad of the SPA — every cross-cutting UI change lands here, and
ownership of the shell is implicit rather than enforced.

This blocks three classes of work that we already have on the roadmap: dashboard enhancements
that need the header, new sidebar sections (drafts, mentions), and accessibility improvements
that require ARIA-correct popovers. The proposal does not change observable behavior; it
decomposes the shell so each concern lives in its own file with a typed contract, mirroring the
pattern already established by `components/dashboard/DashboardLayout.vue` and its section
children (`ExecutiveOverview.vue`, `AiInsightsHero.vue`, etc.).

## Why now

- **Cost of staying:** every reviewer must read 737 lines to touch one menu. Touch points
  overlap (avatar reset, route-watcher, click-outside), so PRs to unrelated areas collide in
  `App.vue`. New contributors can't tell where state belongs.
- **Accessibility debt compounds:** the existing hand-rolled dropdowns lack `aria-haspopup`,
  `aria-expanded`, focus restoration, and skip-to-content. The longer we wait, the more
  components re-implement these gaps.
- **Established pattern, not a new one:** the dashboard refactor (2026-06-14) shipped exactly
  the same component-per-section + typed-props + named-emits pattern. Extending it to the
  shell is the path of least resistance.

## Scope

### In Scope

- Extract the 13 identified sections of `App.vue` into focused Vue 3 SFCs under
  `apps/web/app/src/components/layout/` and `apps/web/app/src/components/sidebar/`.
- Introduce `apps/web/app/src/composables/` with the three shared behaviors
  (`usePopoverDismissal`, `useConnectMessage`, `useChannelSelection`).
- Consolidate the three theme controls and two language controls to a single canonical control
  each.
- Preserve every observable behavior: routing, gating, dismissals, message timing, avatar
  fallback, store side effects, header/sidebar visual layout.
- Preserve `App.test.ts` avatar assertions by keeping the `<img>` markup shape and `@error`
  handler on the channel list, and re-mount the new `App.vue` against the same channel fixture.
- Address the accessibility findings as part of the same change, since the affected popovers
  and toggles are being rewritten anyway.

### Out of Scope

- Visual or design-system changes (no token, font, color, or spacing edits).
- Replacing `proxyImageUrl` or the `usePublishingStore` channel model.
- i18n catalog additions beyond the bare minimum required to keep behavior identical
  (e.g., mirroring the existing untranslated "Threads / Bluesky / Facebook" labels is a
  separate change — see Open Question h).
- Replacing hand-rolled dropdowns with `reka-ui` `DropdownMenu` (see Open Question a).
- Migrating avatar-loading or the `proxyImageUrl` CORS pipeline.
- Splitting the `publishing` store, the `workspace` store, or the `settings` store.
- Adding new tests beyond keeping the avatar tests green and adding a small unit test for each
  new composable.

## Capabilities

### New Capabilities

- `app-shell`: top-level layout composition — providers, sidebar + header + outlet, route
  gating. Owns the auth bootstrap watcher (must stay in the shell because it re-fetches
  workspace and channels when the access token changes).
- `app-sidebar`: the sidebar shell — `SidebarProvider`, `Sidebar`, `SidebarHeader`,
  `SidebarContent`, `SidebarFooter`, `SidebarRail`. Composes the sidebar children but exposes
  no per-section state.
- `app-header`: the sticky header — `SidebarTrigger`, section title, status pill, language
  toggle, theme toggle.
- `use-popover-dismissal`: shared composable for open-state + click-outside + Escape +
  route-change dismissal, ref-based, with `aria-expanded` synced. Used by both popovers.
- `use-connect-message`: shared composable for the transient `connectMessage` ref + the
  `setTimeout` cleanup-on-unmount pattern.

### Modified Capabilities

- None — no existing spec is broken. `App.test.ts` continues to assert avatar markup; the new
  shell keeps that contract.

## Approach

**Mirror the dashboard pattern.** `App.vue` becomes a thin shell (~80 lines) that mounts
`TooltipProvider` and `SidebarProvider`, routes to the auth flow, and composes
`AppSidebar` + `AppHeader` + `<RouterView />`. Each piece is a presentational SFC that takes
typed props and emits named events; cross-section state lives in composables, not in prop
drilling. This matches `DashboardLayout.vue` → `ExecutiveOverview.vue` and friends.

**Decomposition map (13 sections → ~8 SFCs + 2 composables + 1 shell):**

| Section                                   | Owner                                                        | Notes                                                                           |
|-------------------------------------------|--------------------------------------------------------------|---------------------------------------------------------------------------------|
| 1. Auth-route gate                        | `App.vue` shell                                              | `<RouterView v-if="isAuthRoute" />` stays inline                                |
| 2. TooltipProvider + SidebarProvider      | `App.vue` shell                                              | Must stay at root for context                                                   |
| 3. Workspace switcher popover             | `components/sidebar/SidebarWorkspaceSwitcher.vue`            | Uses `usePopoverDismissal`                                                      |
| 4. Navigation groups                      | `components/sidebar/SidebarNavigation.vue`                   | Receives `navigationGroups` + `totalQueuedCount` as props                       |
| 5. Connected channels + "All channels"    | `components/sidebar/SidebarChannels.vue`                     | Owns `avatarLoadFailedMap`, `onAvatarError`, `shouldShowAvatar`; emits `select` |
| 6. Connect subpanel                       | `components/sidebar/SidebarConnectChannels.vue`              | Uses `useConnectMessage`; emits `connect`                                       |
| 7. Account menu popover                   | `components/sidebar/SidebarAccountMenu.vue`                  | Uses `usePopoverDismissal`; emits `logout`, `navigate-settings`                 |
| 8. Click-outside + escape + route-watcher | `composables/usePopoverDismissal.ts`                         | Shared by sections 3 and 7                                                      |
| 9. Header trigger + section title         | `components/layout/AppHeader.vue` (composes trigger + title) | Composes `AppHeaderSectionTitle`                                                |
| 10. Header status pill                    | `components/layout/AppHeaderStatusPill.vue`                  | Receives `headerSummary` as prop                                                |
| 11. Header language + theme toggles       | `components/layout/AppHeaderToggles.vue`                     | Consolidates the three theme controls and two language controls                 |
| 12. Main outlet                           | `App.vue` shell                                              | Stays inline — it's a one-liner around `<RouterView />`                         |
| 13. Auth bootstrap watcher                | `App.vue` shell                                              | Must stay here; calls `workspace.loadWorkspaces` and `publishing.fetchChannels` |

**Component contracts (typed `defineProps` / `defineEmits`, no implicit shared state):**

- `SidebarWorkspaceSwitcher`: props `{ activeWorkspace, options, isLoading }`; emits `select(ws)`.
- `SidebarNavigation`: props `{ groups, totalQueuedCount }`; no events (links only).
- `SidebarChannels`: props `{ channels, activeProvider, totalQueuedCount }`; emits
  `select(channel)`, `show-all`. Owns avatar fallback state internally.
- `SidebarConnectChannels`: props `{ options }`; emits `connect(channel)`, `more`. Owns the connect
  message ref via `useConnectMessage`.
- `SidebarAccountMenu`: props `{ displayName, email, initials, isRefreshing, currentTheme }`; emits
  `logout`, `navigate-settings`.
- `AppHeader`: composes `AppHeaderSectionTitle`, `AppHeaderStatusPill`, `AppHeaderToggles`. Reads
  `route` and `settings` directly; this is acceptable for the header because it has no semantic
  state of its own — it reflects the route.
- `AppHeaderToggles`: props `{ currentLocale, currentTheme }`; emits `set-locale(locale)`,
  `set-theme(theme)`. Calls go up to the shell, which delegates to `useSettingsStore`.

**Consolidation policy:**

- **Theme:** keep the `ThemeToggle.vue` component (it already exists, is standalone, and is
  used in the account menu). Remove the two header pills and route the account menu theme row
  through the same component. Result: one canonical control, no duplication.
- **Language:** keep the header pill as the canonical control. It's already the discoverable
  one and is screen-visible; the account menu never had a duplicate.
- **Popovers:** keep hand-rolled implementation (see Open Question a). The two existing popovers
  become 2 SFCs; the dismissal logic becomes one composable.

**Accessibility — same change, not a follow-up:** since both popovers are being rewritten as
new SFCs, the ARIA and focus attributes land in the new files. Concretely:

- `aria-haspopup="menu"`, `aria-expanded`, `aria-controls` on both popover triggers.
- `role="menu"` and `role="menuitem"` on the popover panels.
- Focus restore to the trigger on Escape/click-outside (via the composable).
- `aria-live="polite"` on the `connectMessage` paragraph.
- `role="radiogroup"` + `aria-checked` on the language and theme pill groups.
- A visually-hidden skip link at the shell root targeting `<main>`.

## Affected Areas

| Area                                              | Impact    | Description                                                                                                                                              |
|---------------------------------------------------|-----------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `apps/web/app/src/App.vue`                        | Modified  | Drops from 737 → ~80 lines, shell only                                                                                                                   |
| `apps/web/app/src/components/layout/`             | New       | `AppHeader.vue`, `AppHeaderSectionTitle.vue`, `AppHeaderStatusPill.vue`, `AppHeaderToggles.vue`                                                          |
| `apps/web/app/src/components/sidebar/`            | New       | `AppSidebar.vue`, `SidebarWorkspaceSwitcher.vue`, `SidebarNavigation.vue`, `SidebarChannels.vue`, `SidebarConnectChannels.vue`, `SidebarAccountMenu.vue` |
| `apps/web/app/src/composables/`                   | New       | `usePopoverDismissal.ts`, `useConnectMessage.ts` (and `useChannelSelection.ts` if a third natural caller appears during spec)                            |
| `apps/web/app/src/App.test.ts`                    | Modified  | Minimal: re-import path only if `App.vue` moves; keep avatar assertions                                                                                  |
| `apps/web/app/src/components/ThemeToggle.vue`     | Unchanged | Reused by `SidebarAccountMenu` (no edit)                                                                                                                 |
| `apps/web/app/src/components/WorkspaceAvatar.vue` | Unchanged | Reused by `SidebarWorkspaceSwitcher` (no edit)                                                                                                           |
| `apps/web/app/src/lib/provider-styles.ts`         | Unchanged | Reused by `SidebarChannels`                                                                                                                              |

## Open Questions (user must decide before spec phase)

1. **Popover implementation.** Preserve hand-rolled dropdowns (status quo) or switch the two
   popovers to `reka-ui` `DropdownMenu`? reka-ui ships keyboard nav, ARIA, and focus management
   for free but is a behavior change (Esc semantics, focus order, animations). The dashboard
   refactor preserved hand-rolled. **Recommendation:** preserve hand-rolled for parity, and
   add the ARIA + focus-restore attributes in the new SFCs. Switching can be a separate
   follow-up.
2. **Theme control canonical location.** Keep the header pill as the only theme control, or
   keep both header pill and account-menu toggle? Today there are three (header pill, account
   menu, `ThemeToggle.vue`). **Recommendation:** delete the header pill and the account-menu
   row, expose a single `ThemeToggle` trigger inside the account menu. The header becomes
   quieter and the account menu becomes the single place for session-level controls. If the
   user disagrees, second-best is the inverse: keep header pill, remove the account-menu row.
3. **Language control canonical location.** Only the header pill exists today — confirm that we
   keep it as the only control and do not add a duplicate in the account menu. **Recommendation:**
   keep header pill only; language is a workspace-wide setting, not a session-level control.
4. **Composables directory.** Create `apps/web/app/src/composables/` (new convention) or
   co-locate each composable as a private helper inside the consuming SFC folder? **Recommendation:
   **
   create `composables/`. We have at least two (`usePopoverDismissal`, `useConnectMessage`)
   that will be unit-tested, and a third is plausible. A flat directory is the Vue 3 idiom
   and matches the `stores/` precedent.
5. **Component placement.** Use `components/layout/` + `components/sidebar/` (proposal
   default) or a single `components/shell/` directory? **Recommendation:** two directories.
   The header and the sidebar are not the same surface — the header is a horizontal bar that
   reflects the route, the sidebar is a vertical rail with its own routing groups.
6. **Accessibility scope.** Include the ARIA + focus-restore + skip-link fixes in this change,
   or split them out? **Recommendation:** include them. The two popovers are being rewritten
   anyway; splitting the ARIA into a separate change would mean touching the same files twice.
   The skip link is one new element at the shell root — trivial.
7. **Test updates.** Update `App.test.ts` mocks to track the new component tree
   (stub `SidebarWorkspaceSwitcher`, `SidebarChannels`, etc.), or keep `App.test.ts` mounted
   against the real `App.vue` and let the existing `App.test.ts` continue to work as a
   full-tree integration test? **Recommendation:** keep the current style. The avatar tests
   are full-tree and pass through Pinia state. Mocking each new SFC would fragment coverage
   and make the test brittle. Add a per-component test for `SidebarChannels` (avatar fallback
   path) and a composable test for `usePopoverDismissal`; leave `App.test.ts` largely as-is.
8. **Untranslated connect-channel labels.** The current `connectChannels` list has
   `t('channels.linkedinProfile')` for LinkedIn and hardcoded English for Threads, Bluesky,
   Facebook. Fix the i18n keys in this change, or leave for a follow-up? **Recommendation:**
   leave for a follow-up — it is orthogonal to the structural refactor and would expand the
   diff beyond the "no behavior change" goal of this change.

## Risks

| Risk                                                                                                      | Likelihood | Mitigation                                                                                                                                                                       |
|-----------------------------------------------------------------------------------------------------------|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SidebarContext` / `TooltipProvider` break when sidebar children are extracted into nested SFCs           | Med        | Mount `TooltipProvider` + `SidebarProvider` in `App.vue` shell only; do not import them in children. Verified against existing `DashboardLayout.vue` precedent.                  |
| Avatar fallback behavior changes when `avatarLoadFailedMap` moves from `App.vue` to `SidebarChannels`     | Low        | Move the ref + watcher + `shouldShowAvatar` helper together; the new component is the only owner. Existing `App.test.ts` assertions are shape-compatible.                        |
| `handleLogout` / `navigateToSettings` accidentally coupled to shell-level `router.replace('/login')` flow | Low        | `SidebarAccountMenu` emits `logout` and `navigate-settings`; the shell owns the actual `auth.logout()` + `router.replace('/login')` chain. No logic changes — just the boundary. |
| Click-outside listener leaks across navigation if the composable cleanup is wrong                         | Low        | `usePopoverDismissal` returns a teardown that the composable registers via `onBeforeUnmount`; identical pattern to today's manual `removeEventListener`.                         |
| `connectTimeout` setTimeout leaks across HMR / route changes                                              | Low        | `useConnectMessage` clears the timeout on unmount via `onBeforeUnmount`, mirroring today's `onBeforeUnmount` block.                                                              |
| Two-developer collision on the new `components/layout/` and `components/sidebar/` directories             | Low        | Convention is documented in the spec; filenames follow the `Sidebar<Role>` / `AppHeader<Role>` pattern.                                                                          |

## Rollback Plan

1. `git revert` the merge commit. `App.vue` is restored to 737 lines.
2. The new `components/layout/`, `components/sidebar/`, and `composables/` directories are
   removed with the revert. No other module imports from them yet, so no downstream breakage.
3. The change is purely structural — no API changes, no store migrations, no router changes.
   The system is in the same state as before the change once reverted.

## Dependencies

- `reka-ui` shadcn-vue primitive set (already installed, used elsewhere) — not consumed by
  this change unless Question 1 is decided in favor of switching to `DropdownMenu`.
- `vue-i18n` translation catalog (EN + ES) — keys for `nav.*` and `channels.*` are already
  present; no new keys required by this change.
- `useSettingsStore` (`currentLocale`, `currentTheme`, `setLocale`, `setTheme`) — already
  exists, used by the new `AppHeaderToggles`.
- `useWorkspaceStore` and `usePublishingStore` — already exist; the auth bootstrap watcher
  keeps calling `loadWorkspaces` and `fetchChannels` from the shell.

## Success Criteria

- [ ] `App.vue` is under 100 lines and contains only the shell, the auth-route gate, the
  providers, the auth bootstrap watcher, and `<RouterView />`.
- [ ] Each new SFC is independently importable and renders in isolation (verifiable with
  `vue-test-utils` `mount`).
- [ ] All existing `App.test.ts` avatar assertions pass without modification of the assertions
  (only import path or mock updates permitted).
- [ ] `usePopoverDismissal` and `useConnectMessage` have unit tests covering open, click-outside,
  Escape, and unmount cleanup.
- [ ] Both popovers carry `aria-haspopup`, `aria-expanded`, `role="menu"`, and restore focus
  to the trigger on dismissal.
- [ ] The connect-message paragraph is wrapped in an `aria-live="polite"` region.
- [ ] The language and theme pill groups expose `role="radiogroup"` with `aria-checked` on
  the active option.
- [ ] A visually-hidden skip link is present at the shell root targeting `<main>`.
- [ ] `pnpm build` succeeds; `pnpm test` (or Vitest equivalent) passes; the dev server boots
  with the sidebar, header, and outlet rendering identically to the pre-refactor build.
- [ ] No new i18n keys required; no store schema changes; no router changes.
