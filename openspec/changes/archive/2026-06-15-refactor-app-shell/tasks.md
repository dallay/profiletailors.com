---
change: refactor-app-shell
total_tasks: 17
estimated_files_added: 16
estimated_files_modified: 2
---

# Tasks: Refactor `App.vue` into a Composable App Shell

> Source of truth for WHAT: `openspec/changes/refactor-app-shell/specs/app-shell/spec.md`
> Source of truth for HOW: `openspec/changes/refactor-app-shell/design.md`
> Implementation order is dictated by **design §4**. Each task is one commit-sized unit of
> work that ends with the named test command green. Test command for every task:
> `pnpm test:run -- <pattern>` (per design §4; `--` forwards args to vitest's CLI).

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1400 (737-line `App.vue` removed; ~16 new SFCs/composables + ~16 new test files + thin `App.vue` + thin `AppShell.vue`) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR1 — composables (Tasks 1-3) · PR2 — layout leaves (Tasks 4-5) · PR3 — sidebar leaves (Tasks 6-11) · PR4 — header + shell + `App.vue` (Tasks 12-14) · PR5 — verification (Tasks 15-16) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Three composables + their tests | PR 1 | Pure-logic foundation; no DOM, no SFCs. Base: `main`. |
| 2 | `AppStatusPill` + `AppLanguagePill` | PR 2 | Two leaves; smallest scope. Depends on PR 1 (none strictly required). Base: `main`. |
| 3 | Six sidebar components | PR 3 | Depends on PR 1 (`usePopoverDismissal`, `useConnectMessage`). Base: `main`. |
| 4 | `AppHeader` + `AppShell` + thin `App.vue` | PR 4 | Final wiring. Depends on PRs 1, 2, 3. Base: `main`. |
| 5 | Full verification + `App.test.ts` reconcile | PR 5 | No code change expected. Base: `main`. |

---

## Phase 1: Composables

> No DOM, no SFC, no SFC-bound tests yet. Build and unit-test first; `usePopoverDismissal` is
> the riskiest composable (per design §4) — its test harness needs a real DOM (jsdom) and
> focus-restore assertions.

### 1. `useQueuedCounts` — total + byProvider derivation

- **Input**: design §4 step 1; spec R-`useQueuedCounts` (Requirement "useQueuedCounts Composable")
- **Output**: create `apps/web/app/src/composables/useQueuedCounts.ts` and
  `apps/web/app/src/composables/useQueuedCounts.test.ts`
- **Acceptance**: `pnpm test:run -- useQueuedCounts` passes; new file covers the three
  spec scenarios "Counts total QUEUED publications", "Counts by provider", "Non-QUEUED
  publications are ignored"
- **Notes**: The composable has TWO call shapes — `(publications?)` reading a passed-in ref
  for testability, and `()` reading `usePublishingStore().publications`. Both must return
  `{ total, byProvider }`. **Do not import any store at the top of the file** when an arg
  is passed (testability hook); only the no-arg path touches Pinia. see design §4 step 1.

### 2. `useConnectMessage` — transient message + setTimeout cleanup

- **Input**: design §4 step 2; spec Requirement "useConnectMessage Composable"
- **Output**: create `apps/web/app/src/composables/useConnectMessage.ts` and
  `apps/web/app/src/composables/useConnectMessage.test.ts`
- **Acceptance**: `pnpm test:run -- useConnectMessage` passes; new file covers the four
  spec scenarios (`show` sets, auto-clear after duration, subsequent `show` cancels prior
  timer, unmount cancels pending timer). Use `vi.useFakeTimers()`.
- **Notes**: The pending timer id MUST be stored in a local ref so both `show` (to cancel
  a prior pending) and `onBeforeUnmount` (to cancel) can clear it. `clear()` is a no-op
  when no timer is pending. Default `defaultDurationMs` is `3500` (matches
  design §5 "Theming consolidation" timing context). see design §5 + spec scenario
  "Subsequent `show` cancels the prior timer".

### 3. `usePopoverDismissal` — open + click-outside + Escape + route watch + focus restore ⚠️

- **Input**: design §4 step 3 (flagged riskiest); spec Requirement
  "usePopoverDismissal Composable"; design §5 "`usePopoverDismissal` focus restore"
- **Output**: create `apps/web/app/src/composables/usePopoverDismissal.ts` and
  `apps/web/app/src/composables/usePopoverDismissal.test.ts`
- **Acceptance**: `pnpm test:run -- usePopoverDismissal` passes; new file covers all six
  scenarios in design §6 "`usePopoverDismissal.test.ts`" (open/toggle, Escape, click-outside,
  click-on-trigger, route change, unmount cleanup)
- **Notes**: The THREE focus-restore rules in design §5 are non-negotiable: (1) on Escape —
  `await nextTick()` BEFORE `trigger.value?.focus()` so the popover is out of the DOM when
  the focus request fires; (2) on click-outside — only restore when the click target is
  outside BOTH `container.value` AND `trigger.value` (clicking the trigger is a toggle,
  not a dismiss); (3) on route change — DO NOT call `.focus()`, the browser shifts focus
  to the new route. `onBeforeUnmount` removes the `document` listeners and stops the
  route watcher. No store imports — this composable is unit-testable without Pinia.
  see design §5 "`usePopoverDismissal` focus restore".

---

## Phase 2: Leaf components (layout)

> No sidebar / shell dependencies. `AppStatusPill` is static (no own test per design §4
> step 5); `AppLanguagePill` is the radiogroup.

### 4. `AppStatusPill` — static status text

- **Input**: design §4 step 5; spec Requirement "AppStatusPill"
- **Output**: create `apps/web/app/src/components/layout/AppStatusPill.vue`
- **Acceptance**: `pnpm test:run -- AppStatusPill` passes (no own test file expected per
  design §4 step 5; covered indirectly by `AppHeader.test.ts` step 12). Visual-only
  check: a literal "light mode / ES" string renders inside a `role="status"` element with
  `aria-label="Session status"`.
- **Notes**: Pure presentational. Single prop `summary: string`. NO state, NO emits. The
  pill is hidden below the `lg` breakpoint — use the existing `hidden lg:flex` (or
  project-equivalent) utility. see design §4 step 5 + spec "AppStatusPill" requirement.

### 5. `AppLanguagePill` — EN/ES radiogroup

- **Input**: design §4 step 4; spec Requirement "AppLanguagePill"
- **Output**: create `apps/web/app/src/components/layout/AppLanguagePill.vue` and
  `apps/web/app/src/components/layout/AppLanguagePill.test.ts`
- **Acceptance**: `pnpm test:run -- AppLanguagePill` passes; new file covers spec scenarios
  "Active option has `aria-checked`" and "Activating an option emits `change`"
- **Notes**: **Emit name is `change`, NOT `setLocale`** (per spec R-AppLanguagePill
  contracts). The header does the actual `settings.setLocale`. The container MUST have
  `role="radiogroup"` and `aria-label="Language"`; the active button MUST carry
  `aria-checked="true"` and the inactive one `aria-checked="false"`. No store imports.
  see design §6 "`AppHeader.test.ts`" item (e) and spec Requirement "AppLanguagePill".

---

## Phase 3: Sidebar components (6 SFCs)

> Six files in dependency order: row first (locks `<img>` shape for `App.test.ts`),
> then nav, connect, header-section (popover), channels-section (composes row + map reset),
> account-section (popover + theme).

### 6. `SidebarChannelRow` — single row, owns avatar fallback

- **Input**: design §4 step 6; spec Requirement "SidebarChannelRow"
- **Output**: create `apps/web/app/src/components/sidebar/SidebarChannelRow.vue` and
  `apps/web/app/src/components/sidebar/SidebarChannelRow.test.ts`
- **Acceptance**: `pnpm test:run -- SidebarChannelRow` passes; new file covers all four
  spec scenarios (avatar renders when `avatarUrl` set, fallback badge when missing, fallback
  shows after `error`, activating the row emits `select`)
- **Notes**: **This file locks the `<img>` markup shape that `App.test.ts` depends on**
  (design §5 "`App.test.ts` mock set"). Keep: `class="... grayscale"`, `alt="${channel.name}
  avatar"`, `@error="onAvatarError(channel.id)"`. `proxyImageUrl` is imported from
  `@/lib/auth-api` (the same module as today — NO mock needed in the test). The row owns
  its OWN `avatarLoadFailed: Ref<boolean>` (local, per-row) — a sibling's failure MUST NOT
  leak. `@error` also emits `avatarError()` upward. see design §5 "`App.test.ts` mock set"
  and §5 "`proxyImageUrl` usage".

### 7. `SidebarNavSection` — nav groups (presentational)

- **Input**: design §4 step 7; spec Requirement "SidebarNavSection"
- **Output**: create `apps/web/app/src/components/sidebar/SidebarNavSection.vue` and
  `apps/web/app/src/components/sidebar/SidebarNavSection.test.ts`
- **Acceptance**: `pnpm test:run -- SidebarNavSection` passes; new file covers spec
  scenarios "Groups render with items", "Dashboard badge reflects queue count" (total=7 →
  "07", total=12 → "12"), "Activating an item emits `navigate`"
- **Notes**: Dashboard badge zero-pads 0–9 only: `(n < 10 ? '0' + n : '' + n)`. Emit name
  is `navigate` with payload `[to: string]` (shell does the actual router push). Pure
  presentational — NO store imports, NO state, NO popovers. Export the `NavItem` and
  `NavGroup` types so the shell can build the groups list. see design §4 step 7 + spec
  exports block.

### 8. `SidebarConnectSection` — connect list + transient message

- **Input**: design §4 step 8; spec Requirement "SidebarConnectSection"
- **Output**: create `apps/web/app/src/components/sidebar/SidebarConnectSection.vue` and
  `apps/web/app/src/components/sidebar/SidebarConnectSection.test.ts`
- **Acceptance**: `pnpm test:run -- SidebarConnectSection` passes; new file covers spec
  scenarios "Connect list renders", "Activating Threads/Bluesky/Facebook shows a transient
  'coming soon' message" (uses fake timers; 3500 ms), "Section unmount clears the pending
  timer"
- **Notes**: The section does NOT start `setTimeout` itself — `useConnectMessage` owns the
  timer (spec R-SidebarConnectSection "Local state: only the `useConnectMessage` return
  value"). The message paragraph MUST be wrapped in an `aria-live="polite"` region
  (R-A11Y-6). `more()` emits upward; the shell shows the "More channels coming soon"
  message. see design §4 step 8 + spec R-A11Y-6.

### 9. `SidebarHeaderSection` — workspace switcher popover

- **Input**: design §4 step 9; spec Requirement "SidebarHeaderSection"
- **Output**: create `apps/web/app/src/components/sidebar/SidebarHeaderSection.vue` and
  `apps/web/app/src/components/sidebar/SidebarHeaderSection.test.ts`
- **Acceptance**: `pnpm test:run -- SidebarHeaderSection` passes; new file covers spec
  scenarios "Closed state shows trigger only", "Open state shows panel with options",
  "Selecting a workspace emits `select` and closes", "Escape and route change close the
  popover"
- **Notes**: Uses `usePopoverDismissal` from Phase 1. Reuses `WorkspaceAvatar.vue` (no
  edit) for the trigger. Trigger MUST carry `aria-haspopup="menu"`, `aria-expanded`,
  `aria-controls="sidebar-workspace-menu"` (R-A11Y-1). Panel MUST have
  `id="sidebar-workspace-menu"` and `role="menu"`; items have `role="menuitem"`
  (R-A11Y-2). `select(workspace)` closes the popover. see design §4 step 9.

### 10. `SidebarChannelsSection` — all-channels + per-channel rows

- **Input**: design §4 step 10; spec Requirement "SidebarChannelsSection"
- **Output**: create `apps/web/app/src/components/sidebar/SidebarChannelsSection.vue` and
  `apps/web/app/src/components/sidebar/SidebarChannelsSection.test.ts`
- **Acceptance**: `pnpm test:run -- SidebarChannelsSection` passes; new file covers spec
  scenarios "All-channels row renders with badge", "One row per channel", "Avatar map
  resets on channel reload", "Activating the All-channels row emits `selectAll`",
  "Activating a channel row emits `selectChannel`"
- **Notes**: **The `avatarLoadFailedMap` reset rule** (design §5 "`SidebarChannelsSection`
  resetting `avatarLoadFailedMap`"): use
  `watch(() => props.channels, () => { avatarLoadFailedMap.value = {} }, { deep: false })`.
  Shallow reference comparison is enough; the store replaces the array reference on
  reload. The map is the source of truth for the reset; the row's own local
  `avatarLoadFailed` ref is the source of truth for its rendered state. The map is
  passed to / known by the parent only — the row does NOT consult the map. see design §5.

### 11. `SidebarAccountSection` — account popover (ThemeToggle inside)

- **Input**: design §4 step 11; spec Requirement "SidebarAccountSection"
- **Output**: create `apps/web/app/src/components/sidebar/SidebarAccountSection.vue` and
  `apps/web/app/src/components/sidebar/SidebarAccountSection.test.ts`
- **Acceptance**: `pnpm test:run -- SidebarAccountSection` passes; new file covers spec
  scenarios "Closed trigger shows user identity", "Open popover shows three items, theme
  toggle only" (asserts the popover contains exactly: account-settings menuitem, one
  `ThemeToggle`, logout menuitem — NO static "Theme" label, NO duplicate theme pill, NO
  language control), "Escape, click-outside, and route change close the popover and
  restore focus", "Activating Account settings emits `openSettings`", "Activating Logout
  emits `logout`"
- **Notes**: This file is where **theming consolidation** lands (design §5 "Theming
  consolidation"): remove the static "Theme" label row beside `ThemeToggle` — the
  component's animated sun/moon SVG already conveys state. Trigger MUST carry
  `aria-haspopup="menu"`, `aria-expanded`, `aria-controls="sidebar-account-menu"`
  (R-A11Y-1). Panel MUST have `id="sidebar-account-menu"`, `role="menu"`; items have
  `role="menuitem"` (R-A11Y-2). Reuse `ThemeToggle.vue` unchanged. see design §5
  "Theming consolidation" + R-A11Y-1/2.

---

## Phase 4: Shell (composition + thin wrapper)

> The two files that close the loop: `AppHeader` composes the three leaves (Tasks 4-5 +
  status pill via Task 4); `AppShell` mounts providers + bootstrap watcher + composes the
  sidebar sections (Tasks 6-11) + the header (Task 12); `App.vue` collapses to ~10 lines.

### 12. `AppHeader` — trigger + section title + status pill + language pill + outlet wrapper

- **Input**: design §4 step 12; spec Requirement "AppHeader"
- **Output**: create `apps/web/app/src/components/layout/AppHeader.vue` and
  `apps/web/app/src/components/layout/AppHeader.test.ts`
- **Acceptance**: `pnpm test:run -- AppHeader` passes; new file covers spec scenarios
  "Header renders section label" (eyebrow = "Workspace", `<h1>` resolves `nav.analytics`),
  "Header renders status pill with summary" (text verbatim, hidden < `lg`), "Header
  renders the language pill" (no theme pill/toggle), "Header does not render a theme
  control" (no element with `aria-label` matching `/theme/i`; no dark/light button pair)
- **Notes**: Header reads `route` + `settings` directly — acceptable per design §3
  (`AppHeader` is a horizontal bar that reflects the route). **The header MUST NOT render
  a theme control** (locked decision 2 + spec "Header does not render a theme control").
  Emit is `setLocale: [locale]` but the language pill inside emits `change` (Task 5);
  the header translates `change` → `setLocale` upward. see design §4 step 12 + design §6
  "`AppHeader.test.ts`".

### 13. `AppShell` — providers + bootstrap watcher + composition

- **Input**: design §4 step 13; spec Requirement "AppShell Composition Contract" +
  "Skip-to-Content Link"
- **Output**: create `apps/web/app/src/components/layout/AppShell.vue`
- **Acceptance**: `pnpm test:run -- App.test` passes after this task (covered by
  `App.test.ts` end-to-end per design §4 step 13). Manual: `AppShell` mounts
  `TooltipProvider` + `SidebarProvider` at the root only; children never import them.
- **Notes**: Three critical pieces:
  (1) **Skip-to-content link is the FIRST focusable element** in the template, BEFORE
  `<TooltipProvider>` — use the exact `class` from design §5 "Skip-to-content link"
  (`sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 ...`).
  Target `#main-content`.
  (2) The main outlet inside `SidebarInset` MUST be `<main id="main-content" tabindex="-1">`
  (R-A11Y-5).
  (3) **Auth bootstrap watcher** must stay in the shell (proposal §Approach). Use
  `watch` on `auth.isAuthenticated && auth.accessToken` → call
  `workspace.loadWorkspaces(token)` + `publishing.fetchChannels()`. Wrap in try/catch and
  `console.warn` (spec R-AppShell "Auth bootstrap watcher fires on token change"). see
  design §4 step 13 + design §5 "Skip-to-content link".

### 14. `App.vue` — thin route gate (~10 lines)

- **Input**: design §4 step 14; design §5 "`App.vue` thin wrapper"; spec Requirement
  "Shell File Layout" + "Auth Route Gate"
- **Output**: rewrite `apps/web/app/src/App.vue` (737 → ~10 lines)
- **Acceptance**: `pnpm test:run` passes; `App.test.ts` 3 avatar assertions pass without
  modification (per spec R-MigrationSafety). `wc -l apps/web/app/src/App.vue` ≤ 10.
- **Notes**: The only logic retained: `isAuthRoute` computed (matches `route.name === 'login'
  || route.name === 'register'`), `<RouterView v-if="isAuthRoute" />`, else `<AppShell />`.
  Remove ALL other imports: no `Sidebar*`, no `TooltipProvider`, no `ThemeToggle`, no
  lucide icons, no publishing/workspace stores. If `pnpm test:run App.test.ts` fails
  because the new `AppShell.vue` import path trips the runner, add ONE line near the
  existing mocks: `vi.mock('@/components/layout/AppShell.vue', () => ({ default: { template:
  '<div><slot /></div>' } }))` — but ONLY as a fallback (design §5 "`App.test.ts` mock
  set"). Do NOT modify any existing assertion. see design §5 "`App.vue` thin wrapper".

---

## Phase 5: Verification

> No code change expected. These two tasks are gates; run them after every other task and
> again at the very end.

### 15. Run full verification checklist (design §7)

- **Input**: design §7 "Verification & rollout"
- **Output**: none (verification only)
- **Acceptance** — ALL of the following must pass:
  1. `pnpm test:run` — full vitest suite passes (all 16+ test files green)
  2. `pnpm test:run -- usePopoverDismissal` — the riskiest composable passes
  3. `pnpm type-check` — `vue-tsc --build` passes with no new errors
  4. `pnpm lint` — biome passes
  5. **Manual smoke** (run the dev server, exercise the shell):
     - Open workspace menu, press Escape → focus returns to trigger
     - Click outside workspace menu → focus returns to trigger
     - Navigate `/` → `/scheduler` → workspace menu closes, no focus steal
     - Open account menu → verify exactly one `ThemeToggle` (account menu only), no static
       "Theme" label, no theme pill in the header
     - Tab from a fresh load → first focus is the skip link; activate it → focus moves to
       `<main id="main-content">` and the page scrolls
     - Visit every authenticated route → no accidental header/sidebar coupling
- **Notes**: This is the gate that decides the change is done. If any item fails, do NOT
  fix-and-merge; file a blocker and return. The visual regression check is qualitative —
  the sidebar, header, and outlet MUST look identical to the pre-refactor build (locked
  decision: no visual or design-system changes). see design §7.

### 16. Reconcile `App.test.ts` if mocks break

- **Input**: design §5 "`App.test.ts` mock set" + spec R-MigrationSafety
  "App.test.ts mock changes are minimal"
- **Output**: `apps/web/app/src/App.test.ts` — add at most one `vi.mock` line for
  `AppShell.vue` (only if Task 14 left the test red)
- **Acceptance**: `pnpm test:run -- App.test.ts` passes; `wc -l App.test.ts` is unchanged
  OR grew by ≤ 2 lines (one mock, one comment); zero existing assertion lines modified
- **Notes**: **Conditional task — only execute if Task 14's `App.test.ts` run fails.**
  The design expects the existing mocks to forward slots (`<div class="sidebar-...">
  <slot /></div>`) so the `SidebarChannelRow` chain inside the section still renders, so
  the `<img>` with `proxyImageUrl(channel.avatarUrl)` keeps appearing in the test DOM.
  If a real-world run shows vitest trying to resolve the deep tree and choking, add
  exactly one stub: `vi.mock('@/components/layout/AppShell.vue', () => ({ default: {
  template: '<div><slot /></div>' } }))`. The existing `Sidebar*`, `TooltipProvider`,
  `ThemeToggle`, `@/lib/auth-api`, `@lucide/vue` mocks MUST remain untouched. No
  assertion is modified. see design §5 "`App.test.ts` mock set" + spec R-MigrationSafety.

---

## Dependency graph

- **Task 1** (`useQueuedCounts`) → no downstream SFC dependency (used by `AppShell` only)
- **Task 2** (`useConnectMessage`) → **Task 8** (used by `SidebarConnectSection`)
- **Task 3** (`usePopoverDismissal`) → **Tasks 9, 11** (used by `SidebarHeaderSection`
  and `SidebarAccountSection`)
- **Task 4** (`AppStatusPill`) → **Task 12** (used by `AppHeader`)
- **Task 5** (`AppLanguagePill`) → **Task 12** (used by `AppHeader`)
- **Task 6** (`SidebarChannelRow`) → **Task 10** (used by `SidebarChannelsSection`); also
  consumed indirectly by `App.test.ts`
- **Task 7** (`SidebarNavSection`) → **Task 13** (mounted by `AppShell`)
- **Task 8** (`SidebarConnectSection`) → **Task 13**
- **Task 9** (`SidebarHeaderSection`) → **Task 13**
- **Task 10** (`SidebarChannelsSection`) → **Task 13**
- **Task 11** (`SidebarAccountSection`) → **Task 13**
- **Task 12** (`AppHeader`) → **Task 13** (mounted by `AppShell` inside `SidebarInset`)
- **Task 13** (`AppShell`) → **Task 14** (mounted by `App.vue`)
- **Task 14** (`App.vue` thin wrapper) → **Task 15** (full verification)
- **Task 15** (full verification) → **Task 16** (reconcile `App.test.ts` if red)

ASCII view:

```
[1 useQueuedCounts]      [4 AppStatusPill] ┐
[2 useConnectMessage] → [8 Connect]        │
[3 usePopoverDismissal]→[9 HeaderSection]  │ → [13 AppShell] → [14 App.vue] → [15 Verify] → [16 App.test reconcile]
[5 AppLanguagePill] ───→[12 AppHeader] ───→┘
[6 SidebarChannelRow] →[10 Channels] ─────→┘
[7 SidebarNavSection] ───────────────────→┘
[11 AccountSection] ─────────────────────→┘
```

Phase 1 (Tasks 1-3) can be merged as PR 1. Phase 2 (Tasks 4-5) as PR 2 (independent of
Phase 1; can be merged in parallel with PR 1). Phase 3 (Tasks 6-11) depends on Phase 1's
composables; can be PR 3. Phase 4 (Tasks 12-14) is PR 4 and depends on PRs 1, 2, 3.
Phase 5 (Tasks 15-16) is the verification PR and depends on PR 4.

---

## Rollback plan

1. `git revert` the merge commit. `App.vue` is restored to the 737-line pre-refactor
   state. No other module imports from `components/layout/AppShell.vue`,
   `components/sidebar/*.vue`, or `composables/*.ts` yet (those directories are
   brand-new), so reverting them produces no downstream breakage.
2. The new `apps/web/app/src/components/layout/`,
   `apps/web/app/src/components/sidebar/`, and `apps/web/app/src/composables/`
   directories are removed with the revert.
3. The change is purely structural — no API contract changes, no store migrations, no
   router changes, no i18n catalog additions, no env changes. The system is in the
   same state as before the change once reverted.

> **Data migration:** none. **Env changes:** none. **One-step revert:** yes.
