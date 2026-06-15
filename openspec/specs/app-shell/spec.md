# App Shell Specification

## Purpose

Defines the post-refactor top-level shell: route-gate, providers, sidebar sections, sticky
header, main outlet, auth bootstrap watcher. Replaces the 737-line monolithic `App.vue`
with a thin wrapper that delegates to `AppShell.vue` and to focused, typed SFCs under
`apps/web/app/src/components/layout/` and `apps/web/app/src/components/sidebar/`, plus three
shared composables under `apps/web/app/src/composables/`. No observable behavior change.

### Locked decisions (from proposal Open Questions)

1. Popovers: hand-rolled. 2. Theme control: only `ThemeToggle.vue` in the account menu —
   header pill and "Theme" row label deleted. 3. Language control: header pill only.
4. Composables: new `apps/web/app/src/composables/` directory.
5. Component placement: `components/layout/` (header) and `components/sidebar/`.
6. ARIA, focus restore, skip-to-content included in this change.
7. Tests: keep `App.test.ts` style; add per-component + per-composable tests.
8. i18n of Threads / Bluesky / Facebook labels: deferred.

---

### Requirement: Shell File Layout

| File | Role | Owns |
|------|------|------|
| `App.vue` | thin wrapper | `isAuthRoute` gate only (≤ ~10 lines) |
| `components/layout/AppShell.vue` | post-refactor shell | `TooltipProvider`, `SidebarProvider`, auth bootstrap watcher, composes sidebar + header + outlet |
| `components/sidebar/*.vue` | sidebar sections | per-section state, popovers; emit upward; no store mutations |
| `components/layout/AppHeader.vue` | sticky header | reads `route` + `settings`; renders trigger, title, status pill, language pill, outlet wrapper |
| `composables/*.ts` | shared behaviors | open-state + dismissal, queued-count derivation, transient message + timeout |

`AppShell.vue` is the only file that imports `TooltipProvider` and `SidebarProvider`.
Sidebar / header children do NOT import those providers.

#### Scenario: `App.vue` is reduced to a route gate

- GIVEN the refactor is complete
- WHEN the file is opened
- THEN `App.vue` is ≤ 10 lines
- AND it checks `isAuthRoute` and renders `<AuthView />` or `<AppShell />`
- AND it does not import `Sidebar*`, `TooltipProvider`, `ThemeToggle`, the lucide icons used in the sidebar/header, or the publishing/workspace stores

#### Scenario: `AppShell.vue` mounts providers at the root

- GIVEN the authenticated route is active
- WHEN `AppShell.vue` mounts
- THEN `TooltipProvider` and `SidebarProvider` wrap the sidebar + inset
- AND no child component imports them directly

#### Scenario: `App.test.ts` mocks remain valid

- GIVEN the existing mocks for `vue-i18n`, `vue-router`, `@/lib/auth-api`, `@/components/ui/tooltip`, `@/components/ui/sidebar`, `ThemeToggle.vue`, and lucide icons
- WHEN the refactor lands
- THEN the 3 avatar assertions pass without modification
- AND the only allowed mock addition is a stub for `AppShell.vue` (only if the new import path breaks the test). The existing mocks SHALL remain.

---

### Requirement: AppShell Composition Contract

`AppShell.vue` composes the sidebar and header in this order: `SidebarHeaderSection`,
`SidebarNavSection`, `SidebarChannelsSection`, `SidebarConnectSection`,
`SidebarAccountSection`, `SidebarRail`, then `AppHeader` and `<RouterView />` inside
`SidebarInset`. The shell owns all event handlers; sections only emit upward and never
mutate stores directly. State local to a section lives in that section.

#### Scenario: Sidebar sections are composed in order

- GIVEN `AppShell.vue` is rendered
- WHEN the DOM is inspected
- THEN `SidebarHeaderSection` is the first child of the sidebar header slot
- AND `SidebarNavSection`, `SidebarChannelsSection`, `SidebarConnectSection`, `SidebarAccountSection` appear in that order
- AND `SidebarRail` is the last child of the sidebar
- AND `AppHeader` and `<main>` containing `<RouterView />` are inside `SidebarInset`

#### Scenario: Auth bootstrap watcher fires on token change

- GIVEN the user is unauthenticated at mount
- WHEN `auth.isAuthenticated` and `auth.accessToken` become truthy
- THEN `workspace.loadWorkspaces(accessToken)` and `publishingStore.fetchChannels()` are called
- AND failures are caught and logged with `console.warn`, never thrown

#### Scenario: Section state stays local

- GIVEN a sidebar section holds popover open-state
- WHEN the section is unmounted
- THEN its open-state ref is GC'd
- AND no parent component references that ref

---

### Requirement: SidebarHeaderSection (workspace switcher)

File: `components/sidebar/SidebarHeaderSection.vue`. Renders the workspace trigger (button
with active `WorkspaceAvatar` + name) and, when open, a popover listing workspaces + an
"Add workspace" placeholder button. Uses `usePopoverDismissal`. ARIA: trigger has
`aria-haspopup="menu"`, `aria-expanded`, `aria-controls="sidebar-workspace-menu"`; panel has
matching `id` and `role="menu"`; items have `role="menuitem"`.

| Aspect | Value |
|--------|-------|
| Props | `activeWorkspace: { name; icon? } \| null`, `options: WorkspaceSummary[]`, `isLoading: boolean` |
| Emits | `select: [workspace: WorkspaceSummary]` |
| Local state | only the `usePopoverDismissal` return value |
| Stores touched | `useWorkspaceStore` (read-only, via props from shell) |

#### Scenario: Closed state shows trigger only

- GIVEN `options` is non-empty
- WHEN the popover is closed
- THEN the trigger button renders the active workspace name + avatar
- AND no popover panel is in the DOM

#### Scenario: Open state shows panel with options

- GIVEN `options` has ≥ 1 workspace
- WHEN the user activates the trigger
- THEN the panel renders with `role="menu"` and `id="sidebar-workspace-menu"`
- AND each option is a `button` with `role="menuitem"`
- AND the active option has distinct visual treatment (`border-border-visible bg-bg-primary`)

#### Scenario: Selecting a workspace emits `select` and closes

- GIVEN the popover is open with 2+ workspaces
- WHEN the user clicks a non-active workspace
- THEN `select(workspace)` is emitted
- AND the popover closes

#### Scenario: Escape and route change close the popover

- GIVEN the popover is open
- WHEN Escape is pressed OR `route.path` changes
- THEN the popover closes
- AND focus is restored to the trigger

---

### Requirement: SidebarNavSection (Workspace + System groups)

File: `components/sidebar/SidebarNavSection.vue`. Renders nav groups; Dashboard item shows
`totalQueuedCount` zero-padded (`00`–`09`, then raw `10`+). No internal state.

Exports types:

```ts
export interface NavItem { labelKey: string; to: string; icon: Component; badge?: string; items?: { title: string; to: string }[] }
export interface NavGroup { label: string; items: NavItem[] }
```

| Aspect | Value |
|--------|-------|
| Props | `groups: NavGroup[]`, `totalQueuedCount: number` |
| Emits | `navigate: [to: string]` |
| Local state | none |
| Stores touched | none (purely presentational) |

#### Scenario: Groups render with items

- GIVEN `groups` has a Workspace group with 3 items and a System group with 1 item
- WHEN the section mounts
- THEN both group labels render
- AND 4 items render, each with icon, label, and optional badge

#### Scenario: Dashboard badge reflects queue count

- GIVEN `totalQueuedCount` is 7
- WHEN the Dashboard item renders
- THEN its badge is `"07"`
- AND when `totalQueuedCount` is 12, the badge is `"12"`

#### Scenario: Activating an item emits `navigate`

- GIVEN the section is rendered
- WHEN the user clicks a nav item
- THEN `navigate(to)` is emitted
- AND the shell handles the actual router push

---

### Requirement: SidebarChannelsSection (All channels + per-channel rows)

File: `components/sidebar/SidebarChannelsSection.vue`. Renders "All channels" + per-channel
rows (delegated to `SidebarChannelRow`). Avatar fallback state (`avatarLoadFailedMap`) is
OWNED by this component, not by `AppShell`. When `publishingStore.channels` changes
(new reference), the map is reset to `{}`.

| Aspect | Value |
|--------|-------|
| Props | `channels: SidebarChannel[]`, `activeProvider: string \| null`, `totalQueuedCount: number` |
| Emits | `selectAll: []`, `selectChannel: [channel: SidebarChannel]` |
| Local state | `avatarLoadFailedMap: Ref<Record<string, boolean>>` + private helpers |
| Stores touched | `usePublishingStore` (read-only — derives `sidebarChannels`; does not mutate) |

`SidebarChannel extends Channel` adds `badge: string` and `queuedCount: number`.

#### Scenario: All-channels row renders with badge

- GIVEN `totalQueuedCount` is 4
- WHEN the section mounts
- THEN the "All channels" row renders first
- AND its badge is `"04"`

#### Scenario: One row per channel

- GIVEN `channels` has 3 entries
- WHEN the section mounts
- THEN 3 `SidebarChannelRow` components render in order
- AND each row receives its channel, active state, and queued count

#### Scenario: Avatar map resets on channel reload

- GIVEN `avatarLoadFailedMap` has `{ 'ch-1': true }`
- WHEN `publishingStore.channels` is replaced with a new array reference
- THEN `avatarLoadFailedMap` is reset to `{}`
- AND all rows re-attempt avatar loads

#### Scenario: Activating the All-channels row emits `selectAll`

- GIVEN the section is rendered
- WHEN the user clicks the "All channels" row
- THEN `selectAll()` is emitted
- AND the shell clears filters and pushes `/scheduler`

#### Scenario: Activating a channel row emits `selectChannel`

- GIVEN the section is rendered with 2 channels
- WHEN the user clicks the second channel row
- THEN `selectChannel(channel)` is emitted
- AND the shell sets `filterChannel` and pushes `/scheduler`

---

### Requirement: SidebarChannelRow (single channel row)

File: `components/sidebar/SidebarChannelRow.vue`. Each row owns its OWN
`avatarLoadFailed: Ref<boolean>` so a sibling row's failure does not leak. Parent MAY also
keep a parallel `avatarLoadFailedMap` for reset-on-reload; the row's own ref is source of
truth for its rendered state.

| Aspect | Value |
|--------|-------|
| Props | `channel: SidebarChannel`, `isActive: boolean`, `queuedCount: number` |
| Emits | `select: []`, `avatarError: []` |
| Local state | `avatarLoadFailed: Ref<boolean>` |

`<img>` continues to call `proxyImageUrl(channel.avatarUrl!)` from `@/lib/auth-api`.
`<span>` fallback shows `channel.badge`. `@error` sets the local ref to `true` AND emits
`avatarError` upward.

#### Scenario: Avatar renders when `avatarUrl` is set

- GIVEN `channel.avatarUrl` is `"https://example.com/a.jpg"`
- WHEN the row mounts
- THEN `<img>` renders with `src` = `proxyImageUrl('https://example.com/a.jpg')` and `alt` = `"{channel.name} avatar"`

#### Scenario: Fallback badge renders when `avatarUrl` is missing

- GIVEN `channel.avatarUrl` is `null` or `undefined`
- WHEN the row mounts
- THEN no `<img>` is rendered
- AND a `<span>` shows the channel badge text

#### Scenario: Fallback shows after avatar load error

- GIVEN the `<img>` is rendered
- WHEN `<img>` fires `error`
- THEN the local `avatarLoadFailed` becomes `true`
- AND the `<img>` is removed from the DOM
- AND a `<span>` fallback renders
- AND `avatarError()` is emitted upward

#### Scenario: Activating the row emits `select`

- GIVEN the row is rendered
- WHEN the user clicks the row
- THEN `select()` is emitted

---

### Requirement: SidebarConnectSection (connect channels + message)

File: `components/sidebar/SidebarConnectSection.vue`. Renders connect list (LinkedIn,
Threads, Bluesky, Facebook) + "More" button. Owns the transient `connectMessage` ref via
`useConnectMessage`. The message paragraph is wrapped in `aria-live="polite"`. The section
does NOT start `setTimeout` itself — the composable owns the timer.

| Aspect | Value |
|--------|-------|
| Props | `providers: ConnectChannel[]` |
| Emits | `connect: [channel: ConnectChannel]`, `more: []` |
| Local state | only the `useConnectMessage` return value |

`ConnectChannel.id` is `'linkedin' | 'threads' | 'bluesky' | 'facebook'`.

#### Scenario: Connect list renders

- GIVEN `providers` has 4 entries
- WHEN the section mounts
- THEN 4 connect buttons render (badge + label + "+ Connect")
- AND a "More" button renders below

#### Scenario: Activating LinkedIn emits `connect` with the linkedin channel

- GIVEN the LinkedIn row is rendered
- WHEN the user clicks it
- THEN `connect(linkedinChannel)` is emitted
- AND the shell sets `connectingLinkedIn` message and calls `publishingStore.connectLinkedInPersonalProfile()`

#### Scenario: Activating Threads/Bluesky/Facebook shows a transient "coming soon" message

- GIVEN the Threads row is rendered and no prior message is shown
- WHEN the user clicks it
- THEN `connect(threadsChannel)` is emitted
- AND the shell sets a "coming soon" message
- AND the message auto-clears after 3500 ms (composable)

#### Scenario: "More" button emits `more`

- GIVEN the section is rendered
- WHEN the user clicks "More"
- THEN `more()` is emitted
- AND the shell shows a "More channels coming soon" message for 3500 ms

#### Scenario: Section unmount clears the pending timer

- GIVEN a message is currently displayed
- WHEN the section is unmounted before 3500 ms elapses
- THEN the pending timeout is cleared
- AND no late callback mutates a stale ref

---

### Requirement: SidebarAccountSection (account menu)

File: `components/sidebar/SidebarAccountSection.vue`. Trigger (avatar + name + email) +
popover with: account settings link, the **only** theme control (`ThemeToggle.vue`), and
logout. The header does NOT render a theme control. The static "Theme" label row beside
`ThemeToggle` is REMOVED.

Uses `usePopoverDismissal` (focus restore, click-outside, escape, route-change close).
ARIA: trigger has `aria-haspopup="menu"`, `aria-expanded`,
`aria-controls="sidebar-account-menu"`; panel has `id="sidebar-account-menu"` and
`role="menu"`; items have `role="menuitem"`.

| Aspect | Value |
|--------|-------|
| Props | `user: { displayName; email; initials; isRefreshing }` |
| Emits | `openSettings: []`, `logout: []` |
| Local state | only the `usePopoverDismissal` return value |
| Stores touched | none directly (theme changes live inside `ThemeToggle.vue`) |

#### Scenario: Closed trigger shows user identity

- GIVEN the user is authenticated
- WHEN the popover is closed
- THEN the trigger renders initials avatar, display name, and email
- AND no popover panel is in the DOM

#### Scenario: Open popover shows three items, theme toggle only

- GIVEN the popover is open
- WHEN the DOM is inspected
- THEN the panel has `role="menu"` and `id="sidebar-account-menu"`
- AND it contains exactly: an "Account settings" menuitem, a `ThemeToggle`, and a logout menuitem
- AND no static "Theme" label, no duplicate theme pill, and no language control

#### Scenario: Escape, click-outside, and route change close the popover and restore focus

- GIVEN the popover is open
- WHEN the user presses Escape, clicks outside, or navigates
- THEN the popover closes
- AND focus returns to the account trigger

#### Scenario: Activating Account settings emits `openSettings`

- GIVEN the popover is open
- WHEN the user clicks "Account settings"
- THEN `openSettings()` is emitted
- AND the shell navigates to `/settings` and closes the popover

#### Scenario: Activating Logout emits `logout`

- GIVEN the popover is open
- WHEN the user clicks "Logout"
- THEN `logout()` is emitted
- AND the shell calls `auth.logout()` then `router.replace('/login')`, both in try/catch with `console.error`

---

### Requirement: AppHeader (sticky header bar)

File: `components/layout/AppHeader.vue`. Renders the sidebar trigger, section title
("Workspace" label + translated `nav.{currentSectionLabel}`), status pill, language pill
(`AppLanguagePill`), and main outlet wrapper (`<main id="main-content" tabindex="-1">`).
The header does NOT render a theme control. `currentSectionLabel` and `headerSummary` are
computed locally from `route` and `settings` — the only state the header reads.

| Aspect | Value |
|--------|-------|
| Props | `currentSectionLabel: string`, `headerSummary: string`, `currentLocale: 'en' \| 'es'` |
| Emits | `setLocale: [locale: 'en' \| 'es']` |

#### Scenario: Header renders section label

- GIVEN the user is on `/analytics`
- WHEN the header renders
- THEN the eyebrow label is `"Workspace"`
- AND the `<h1>` shows the translated `nav.analytics` text (or the raw key if missing)

#### Scenario: Header renders status pill with summary

- GIVEN `headerSummary` is `"dark mode / EN"`
- WHEN the header renders
- THEN `AppStatusPill` shows that string verbatim
- AND it is hidden on viewports < `lg`

#### Scenario: Header renders the language pill

- GIVEN `currentLocale` is `"en"`
- WHEN the header renders
- THEN `AppLanguagePill` renders with `EN` active
- AND no theme pill or theme toggle is in the header

#### Scenario: Header does not render a theme control

- GIVEN the header is rendered
- WHEN the DOM is inspected
- THEN there is no element with an `aria-label` containing "theme" and no two-button group for `dark`/`light`
- AND the only theme control in the app is inside the account menu popover

---

### Requirement: AppLanguagePill (EN / ES switcher)

File: `components/layout/AppLanguagePill.vue`. Two buttons (EN, ES) inside a
`role="radiogroup"` container with `aria-label="Language"`. The active option has
`aria-checked="true"`. The pill only emits — it does NOT call `settings.setLocale`.

| Aspect | Value |
|--------|-------|
| Props | `current: 'en' \| 'es'` |
| Emits | `change: [locale: 'en' \| 'es']` |

#### Scenario: Active option has `aria-checked`

- GIVEN `current` is `"es"`
- WHEN the pill renders
- THEN the container has `role="radiogroup"` and `aria-label="Language"`
- AND the ES button has `aria-checked="true"`, EN has `aria-checked="false"`

#### Scenario: Activating an option emits `change`

- GIVEN the pill is rendered
- WHEN the user clicks EN while ES is active
- THEN `change('en')` is emitted
- AND the shell calls `settings.setLocale('en')`

---

### Requirement: AppStatusPill (status summary pill)

File: `components/layout/AppStatusPill.vue`. Static presentation. No state, no emits.

| Aspect | Value |
|--------|-------|
| Props | `summary: string` |

Root has `role="status"` and `aria-label="Session status"`.

#### Scenario: Pill renders the summary

- GIVEN `summary` is `"light mode / ES"`
- WHEN the pill renders
- THEN the text is exactly `"light mode / ES"`
- AND the root has `role="status"` and `aria-label="Session status"`

---

### Requirement: Skip-to-Content Link

`AppShell.vue` renders a visually-hidden skip link as the FIRST focusable element in the
shell, targeting the main outlet's `id="main-content"`. The main outlet (`<main>` inside
`SidebarInset`) has `id="main-content"` and `tabindex="-1"` so it can receive programmatic
focus.

#### Scenario: Skip link is the first focusable element

- GIVEN the authenticated shell is rendered
- WHEN the user presses Tab from the document body
- THEN focus lands on the skip link
- AND the link is visually hidden but accessible (e.g. `sr-only` class that becomes visible on focus)

#### Scenario: Activating the skip link moves focus to the main outlet

- GIVEN focus is on the skip link
- WHEN the user activates it
- THEN focus moves to `<main id="main-content" tabindex="-1">`
- AND the page scrolls the main outlet into view (browser default)

---

### Requirement: Auth Route Gate

`App.vue` (post-refactor) computes `isAuthRoute` and renders `<AuthView />` (existing,
unchanged) when on `/login` or `/register`. Otherwise it renders `<AppShell />`. This is the
only logic `App.vue` retains.

#### Scenario: Auth route renders the auth view

- GIVEN `route.name` is `"login"` or `"register"`
- WHEN `App.vue` renders
- THEN `<AuthView />` is rendered
- AND `<AppShell />` is not in the DOM
- AND `TooltipProvider` / `SidebarProvider` are not mounted

#### Scenario: Non-auth route renders the shell

- GIVEN `route.name` is anything other than `"login"` or `"register"`
- WHEN `App.vue` renders
- THEN `<AppShell />` is rendered
- AND `<AuthView />` is not in the DOM

---

### Requirement: usePopoverDismissal Composable

File: `composables/usePopoverDismissal.ts`.

```ts
export function usePopoverDismissal(opts: {
  container: Ref<HTMLElement | null>
  trigger?: Ref<HTMLElement | null>
}): { open: Ref<boolean>; toggle: () => void; close: () => void; openIt: () => void }
```

Behavior: registers `document.click` (close when target is outside `container.value`),
`document.keydown` (Escape closes), and a `useRoute().path` watcher (close on change). On
close via Escape/click-outside, restores focus to `trigger.value` if provided. On close via
route change, does NOT steal focus (browser handles navigation focus shift). On
`onBeforeUnmount`, all listeners and the watcher are torn down.

#### Scenario: Click on trigger toggles open

- GIVEN a component uses `usePopoverDismissal` and binds `toggle` to its trigger's click
- WHEN the user clicks the trigger
- THEN `open.value` becomes `true`
- AND no focus restore runs (toggle is the source of the open)

#### Scenario: Click outside closes the popover

- GIVEN `open.value` is `true`
- WHEN a `click` fires on `document` with a target outside `container.value`
- THEN `open.value` becomes `false`
- AND focus is restored to `trigger.value` if `trigger` was provided

#### Scenario: Escape closes the popover and restores focus

- GIVEN `open.value` is `true` and a `trigger` ref is provided
- WHEN a `keydown` fires on `document` with `key === 'Escape'`
- THEN `open.value` becomes `false`
- AND `trigger.value` receives focus

#### Scenario: Route change closes the popover

- GIVEN `open.value` is `true`
- WHEN `useRoute().path` changes
- THEN `open.value` becomes `false`
- AND no focus restore runs

#### Scenario: Listeners are torn down on unmount

- GIVEN the consuming component is using `usePopoverDismissal`
- WHEN the component is unmounted
- THEN no further `document` listeners fire for that popover
- AND no late callbacks mutate a stale ref

---

### Requirement: useQueuedCounts Composable

File: `composables/useQueuedCounts.ts`.

```ts
export function useQueuedCounts(
  publications?: ComputedRef<readonly Publication[]> | Ref<readonly Publication[]>,
): { total: ComputedRef<number>; byProvider: ComputedRef<Map<string, number>> }
```

When called with no args, reads `usePublishingStore().publications`. When called with a
`publications` ref, uses that ref (testability hook). Walks the list. For each publication
with `status === 'QUEUED'`, increments `total` by 1 and the count for each provider in
`publication.channels`. Non-QUEUED entries are ignored.

#### Scenario: Counts total QUEUED publications

- GIVEN 3 QUEUED entries and 2 PUBLISHED entries
- WHEN `useQueuedCounts` is read
- THEN `total.value` is `3`

#### Scenario: Counts by provider

- GIVEN 2 QUEUED entries with `channels: ['linkedin']` and 1 QUEUED entry with `channels: ['linkedin', 'threads']`
- WHEN `useQueuedCounts` is read
- THEN `byProvider.value.get('linkedin')` is `3`
- AND `byProvider.value.get('threads')` is `1`

#### Scenario: Non-QUEUED publications are ignored

- GIVEN 1 PUBLISHED entry with `channels: ['linkedin']`
- WHEN `useQueuedCounts` is read
- THEN `total.value` is `0`
- AND `byProvider.value.get('linkedin')` is undefined or `0`

---

### Requirement: useConnectMessage Composable

File: `composables/useConnectMessage.ts`.

```ts
export function useConnectMessage(opts?: { defaultDurationMs?: number }): {
  message: Ref<string>
  show: (text: string, durationMs?: number) => void
  clear: () => void
}
```

`show(text, durationMs)` sets `message.value = text` and schedules a `setTimeout` that
clears it. Default duration is `3500` ms. `clear()` cancels any pending timeout and empties
the message. On `onBeforeUnmount`, the pending timeout is cancelled.

#### Scenario: `show` sets the message

- GIVEN the composable is mounted with no prior message
- WHEN `show('Connecting...')` is called
- THEN `message.value` is `'Connecting...'`

#### Scenario: Message auto-clears after the duration

- GIVEN a message is shown with a 100 ms duration
- WHEN 100 ms elapse (vitest fake timers)
- THEN `message.value` is `''`

#### Scenario: Subsequent `show` cancels the prior timer

- GIVEN a message is shown with a 100 ms duration (timer pending)
- WHEN `show('New', 200)` is called
- THEN the prior 100 ms timer is cleared
- AND only the new 200 ms timer runs

#### Scenario: Unmount cancels the pending timer

- GIVEN a message is shown with a pending timer
- WHEN the consuming component is unmounted
- THEN the timer is cleared
- AND no late callback mutates a stale ref

---

### Requirement: Cross-Cutting Accessibility (R-A11Y-1 through R-A11Y-6)

**R-A11Y-1**: workspace and account popover triggers expose `aria-haspopup="menu"`,
`aria-expanded` bound to the popover open state, and `aria-controls` linking to the panel
id (`sidebar-workspace-menu`, `sidebar-account-menu`).

**R-A11Y-2**: both popover panels expose `role="menu"` and a stable id. Items use
`role="menuitem"`.

**R-A11Y-3**: Escape or click-outside closes the popover AND restores focus to the trigger
(handled by `usePopoverDismissal`). Route change closes the popover but does NOT steal
focus.

**R-A11Y-4**: header language pill exposes `role="radiogroup"` with `aria-label="Language"`
and `aria-checked` on the active option.

**R-A11Y-5**: skip-to-content link is the first focusable element in the shell, targeting
the main outlet's `id="main-content"`. The main outlet has `tabindex="-1"`.

**R-A11Y-6**: the connect-message paragraph lives inside an `aria-live="polite"` region.
The status pill has `role="status"`.

#### Scenario: Both popover triggers carry full ARIA attributes

- GIVEN the popovers are closed
- WHEN the DOM is inspected
- THEN the workspace trigger has `aria-haspopup="menu"`, `aria-expanded="false"`, `aria-controls="sidebar-workspace-menu"`
- AND the account trigger has `aria-haspopup="menu"`, `aria-expanded="false"`, `aria-controls="sidebar-account-menu"`

#### Scenario: Popover panels expose `role="menu"` and stable ids

- GIVEN either popover is open
- WHEN the panel is inspected
- THEN it has `role="menu"` and the matching id
- AND its child items have `role="menuitem"`

#### Scenario: Escape and click-outside close the popover and restore focus

- GIVEN a popover is open
- WHEN the user presses Escape OR clicks outside
- THEN the popover closes
- AND focus is on the trigger

#### Scenario: Language pill radiogroup

- GIVEN the header is rendered
- WHEN the language pill is inspected
- THEN the container has `role="radiogroup"` and `aria-label="Language"`
- AND the active button has `aria-checked="true"`
- AND the inactive button has `aria-checked="false"`

#### Scenario: Skip-to-content link focuses the main outlet

- GIVEN the shell is rendered
- WHEN the user focuses the skip link and activates it
- THEN focus moves to `<main id="main-content" tabindex="-1">`

#### Scenario: Connect message is announced politely

- GIVEN the connect message is non-empty
- WHEN a screen reader is active
- THEN the message is announced in a polite live region

---

### Requirement: Test Plan

| Test file | Covers |
|-----------|--------|
| `App.test.ts` (unchanged assertions) | Full-tree: renders `<img>` for channel with `avatarUrl`, renders fallback `<span>` when missing, swaps to fallback on `<img>` `@error`. |
| `composables/usePopoverDismissal.test.ts` (new) | ≥ 3 scenarios: toggle opens, Escape closes + restores focus, click-outside closes + restores focus. |
| `composables/useQueuedCounts.test.ts` (new) | counts total, counts per-provider, ignores non-QUEUED. |
| `composables/useConnectMessage.test.ts` (new) | `show` sets, auto-clear after duration, unmount cancels timer. |
| `components/sidebar/SidebarChannelsSection.test.ts` (new) | renders "All channels" + per-channel rows; fires `selectAll` and `selectChannel`. |
| `components/sidebar/SidebarChannelRow.test.ts` (new) | renders avatar when `avatarUrl` set, swaps to fallback on `avatarError`, badge logic. |
| `components/layout/AppHeader.test.ts` (new) | renders section label, renders status pill text, language pill emits `change`. |
| `components/layout/AppLanguagePill.test.ts` (new) | radiogroup roles, `aria-checked` on active, emits `change`. |

#### Scenario: `App.test.ts` passes without modifying assertions

- GIVEN the refactor is complete
- WHEN tests run
- THEN the 3 avatar assertions pass
- AND no assertion in `App.test.ts` is modified
- AND the only permitted mock addition is a stub for `AppShell.vue` (only if the new import path breaks the test). Existing `Sidebar*`, `TooltipProvider`, `ThemeToggle`, and lucide mocks SHALL remain.

#### Scenario: Composables have unit tests

- GIVEN the 3 new composables exist
- WHEN tests run
- THEN each composable test file has ≥ 3 passing scenarios

#### Scenario: New component tests pass

- GIVEN the new sidebar/header components exist
- WHEN tests run
- THEN `SidebarChannelsSection.test.ts`, `SidebarChannelRow.test.ts`, `AppHeader.test.ts`, and `AppLanguagePill.test.ts` each have ≥ 1 passing scenario for their core contract

---

### Requirement: Migration Safety

- `proxyImageUrl` from `@/lib/auth-api` continues to be called inside the `<img>` rendering
  — it moves with the channel row to `SidebarChannelRow.vue`.
- The `<img>` markup shape (with `src`, `alt`, `@error`) matches the pre-refactor DOM so the
  existing `App.test.ts` assertions remain valid.
- `App.test.ts` mocks (lines 14–74 today) are updated ONLY if the new `App.vue` directly
  imports a NEW component not in the current mock set. The exact allowed additions are:
  1. A stub for `AppShell.vue` — because `App.vue` (the thin wrapper) renders `<AppShell />`.
     This is OPTIONAL — add it only if the existing `App.test.ts` breaks on the new import
     path.
  2. The existing `Sidebar*`, `TooltipProvider`, `ThemeToggle`, and lucide mocks SHALL stay
     because `AppShell.vue` still imports them.

No other file in `App.test.ts` is touched. No assertion is modified.

#### Scenario: Avatar markup is preserved

- GIVEN the refactor is complete
- WHEN `App.test.ts` mounts `AppComponent` with a channel that has `avatarUrl: 'https://example.com/avatar.jpg'`
- THEN the test finds `<img>` with that exact `src`
- AND `alt` is `'{channel.name} avatar'`

#### Scenario: `proxyImageUrl` continues to be called

- GIVEN the avatar image is rendered
- WHEN the DOM is inspected
- THEN `src` is the result of `proxyImageUrl(channel.avatarUrl)`
- AND `proxyImageUrl` is imported from `@/lib/auth-api` (the same module as before)

#### Scenario: `App.test.ts` mock changes are minimal

- GIVEN the refactor is complete
- WHEN `App.test.ts` is opened
- THEN no assertion is changed
- AND at most one new mock may be added (for `AppShell.vue` if needed)
- AND the existing `Sidebar*`, `TooltipProvider`, `ThemeToggle`, `auth-api`, and lucide mocks remain untouched

---

## Out of Scope

- Visual or design-system changes (no token, font, color, or spacing edits).
- Replacing `proxyImageUrl` or the `usePublishingStore` channel model.
- i18n for Threads / Bluesky / Facebook labels (deferred per locked decision 8).
- Replacing hand-rolled dropdowns with `reka-ui` `DropdownMenu` (deferred per locked decision 1).
- Migrating avatar-loading or the `proxyImageUrl` CORS pipeline.
- Splitting the `publishing`, `workspace`, or `settings` stores.
- Adding tests beyond the Test Plan requirement.
