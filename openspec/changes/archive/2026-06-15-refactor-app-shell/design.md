# Design: Refactor `App.vue` into a Composable App Shell

## 1. Goal & non-goals

Reduce `apps/web/app/src/App.vue` from 737 lines to a thin route gate, and split the shell into
focused, typed SFCs under `components/layout/` and `components/sidebar/`, plus three composables
under `composables/`. The refactor must preserve every observable behavior and the existing
`App.test.ts` avatar assertions, and add the a11y work (ARIA, focus restore, skip link) on the
same pass.

**Non-goals:** (1) no visual or design-system changes; (2) no new dependencies — `reka-ui` is
already a dep but the dropdown migration is deferred; (3) no i18n catalog additions — Threads /
Bluesky / Facebook stay untranslated; (4) no changes to `proxyImageUrl`, the `publishing` /
`workspace` / `settings` stores, or the router; (5) no new tests beyond the per-component /
per-composable files listed in the spec's Test Plan.

## 2. Architecture diagram (post-refactor file layout)

```
apps/web/app/src/
├── App.vue                                          # thin route gate, ~10 lines
├── components/
│   ├── ThemeToggle.vue                              # UNCHANGED, reused
│   ├── WorkspaceAvatar.vue                          # UNCHANGED, reused
│   ├── layout/
│   │   ├── AppShell.vue                             # providers + composition + bootstrap watcher
│   │   ├── AppHeader.vue                            # trigger + section title + status pill + language pill + outlet wrapper
│   │   ├── AppStatusPill.vue                        # static status text, role="status"
│   │   └── AppLanguagePill.vue                      # EN/ES radiogroup
│   └── sidebar/
│       ├── SidebarHeaderSection.vue                 # workspace popover
│       ├── SidebarNavSection.vue                    # nav groups (presentational)
│       ├── SidebarChannelsSection.vue               # all-channels row + per-channel rows
│       ├── SidebarChannelRow.vue                    # single row, owns avatar fallback
│       ├── SidebarConnectSection.vue                # connect list + transient message
│       └── SidebarAccountSection.vue                # account popover (ThemeToggle inside)
└── composables/
    ├── usePopoverDismissal.ts                       # open + click-outside + Escape + route watch + focus restore
    ├── useQueuedCounts.ts                           # total + byProvider derivation
    └── useConnectMessage.ts                         # transient message + setTimeout cleanup
```

Per-file tests co-located as `*.test.ts`. `AppShell.vue` is the only file importing
`TooltipProvider` and `SidebarProvider`; `ThemeToggle.vue` and `WorkspaceAvatar.vue` are
reused as-is (no edits). The existing `App.test.ts` stays at the same path.

## 3. Dependency graph

- `App.vue` → `views/AuthView`, `components/layout/AppShell`, `vue-router` (`useRoute`)
- `AppShell.vue` → `AppHeader`, `SidebarHeaderSection`, `SidebarNavSection`,
  `SidebarChannelsSection`, `SidebarConnectSection`, `SidebarAccountSection`, `TooltipProvider`,
  `SidebarProvider`, `SidebarRail`, `SidebarInset`, `useAuthStore`, `useWorkspaceStore`,
  `usePublishingStore`, `useSettingsStore`, `usePopoverDismissal` (only to typecheck helper
  imports — composables are used inside children, not here), `useQueuedCounts`
- `AppHeader.vue` → `SidebarTrigger`, `AppStatusPill`, `AppLanguagePill`, `useRoute`,
  `useSettingsStore`
- `AppLanguagePill.vue` → no stores (pure presentational, radiogroup)
- `AppStatusPill.vue` → no deps
- `SidebarHeaderSection.vue` → `WorkspaceAvatar`, `usePopoverDismissal`, `lucide-vue` icons
- `SidebarNavSection.vue` → `lucide-vue` icons
- `SidebarChannelsSection.vue` → `SidebarChannelRow`, `usePublishingStore` (read-only),
  `getProviderBadge`
- `SidebarChannelRow.vue` → `proxyImageUrl` from `@/lib/auth-api`, `getProviderBadge`
- `SidebarConnectSection.vue` → `useConnectMessage`, `lucide-vue` icons
- `SidebarAccountSection.vue` → `ThemeToggle`, `useAuthStore` (read-only), `usePopoverDismissal`,
  `lucide-vue` icons
- `usePopoverDismissal.ts` → `vue` (`ref`, `watch`, `onBeforeUnmount`, `nextTick`),
  `vue-router` (`useRoute`). NO store deps — operates on the refs the caller passes and on
  the route. Unit-testable with no Pinia setup.
- `useQueuedCounts.ts` → `vue` (`computed`), `usePublishingStore` (read-only when no arg passed)
- `useConnectMessage.ts` → `vue` (`ref`, `onBeforeUnmount`). NO store deps.

**Circular-import check:** no section imports `AppShell`; `AppShell` is the only consumer of
the sections. `usePopoverDismissal` does not import any section. Clean DAG, no cycles.

## 4. Implementation order

Each step ends with a focused test run. Test command is `pnpm test:run -- <pattern>` (the
`package.json` script is `vitest run`; the `--run` flag in the prompt maps to `pnpm test:run`).
For pattern filtering, use vitest's filename match: e.g. `pnpm test:run usePopoverDismissal`.

1. `composables/useQueuedCounts.ts` + `useQueuedCounts.test.ts` (pure derivation, no DOM)
2. `composables/useConnectMessage.ts` + `useConnectMessage.test.ts` (fake timers, no DOM)
3. `composables/usePopoverDismissal.ts` + `usePopoverDismissal.test.ts` — **riskiest; build and
   test first** (real DOM via jsdom, focus restoration assertions)
4. `components/layout/AppLanguagePill.vue` + `AppLanguagePill.test.ts`
5. `components/layout/AppStatusPill.vue` (no own test — trivial static render)
6. `components/sidebar/SidebarChannelRow.vue` + `SidebarChannelRow.test.ts` (the `<img>` `@error`
   story lives here; locks the markup shape that `App.test.ts` depends on)
7. `components/sidebar/SidebarNavSection.vue` + `SidebarNavSection.test.ts`
8. `components/sidebar/SidebarConnectSection.vue` + `SidebarConnectSection.test.ts`
9. `components/sidebar/SidebarHeaderSection.vue` + `SidebarHeaderSection.test.ts`
10. `components/sidebar/SidebarChannelsSection.vue` + `SidebarChannelsSection.test.ts`
11. `components/sidebar/SidebarAccountSection.vue` + `SidebarAccountSection.test.ts`
12. `components/layout/AppHeader.vue` + `AppHeader.test.ts`
13. `components/layout/AppShell.vue` (no own test — verified by `App.test.ts` end-to-end)
14. `App.vue` thin wrapper (regression check via `App.test.ts` — see §5)

## 5. Critical implementation notes

- **`usePopoverDismissal` focus restore.** The composable receives
  `container: Ref<HTMLElement | null>`
  and `trigger?: Ref<HTMLElement | null>`. On Escape: `open.value = false`, then
  `await nextTick(); trigger.value?.focus()`. On click-outside: only restore when the click
  target is outside BOTH `container.value` AND `trigger.value` (a click on the trigger is a
  toggle, not a dismiss — the trigger is the open source). On route change: set
  `open.value = false`, do NOT call `.focus()` — the browser shifts focus to the new route's
  `<h1>` / focusable ancestor; stealing focus would fight the navigation. Use `nextTick` to
  defer focus calls so the popover's removal from the DOM doesn't suppress the focus request.
  `onBeforeUnmount` removes the `document` listeners and stops the route watcher.

- **`SidebarChannelsSection` resetting `avatarLoadFailedMap`.** Use
  `watch(() => props.channels, () => { avatarLoadFailedMap.value = {} }, { deep: false })` —
  shallow reference comparison is enough; the store replaces the array reference when
  channels reload. Each `SidebarChannelRow` owns its own local
  `avatarLoadFailed: Ref<boolean>` (so a sibling's failure does not leak); the parent map is
  source of truth only for the reset, and is passed to / known by the parent, not the row.

- **`App.test.ts` mock set.** Lines 14-74 mock: `vue-i18n`, `@/i18n`, `vue-router`,
  `@/lib/auth-api`, `@/components/ui/tooltip`, `@/components/ui/sidebar`, `ThemeToggle.vue`,
  `@lucide/vue`. After the refactor, `App.vue` (thin) imports `AppShell` (new path). The new
  `AppShell.vue` imports the same `Sidebar*`, `TooltipProvider`, and lucide icons the test
  already mocks. Because the sidebar mocks forward slots (
  `<div class="sidebar-..."><slot /></div>`),
  the `SidebarChannelRow` chain inside the section still renders, so the `<img>` with
  `proxyImageUrl(channel.avatarUrl)` keeps appearing in the test DOM. **No new mock is
  required.** If a real-world run shows vitest trying to resolve the deep tree and choking,
  add a single `vi.mock('@/components/layout/AppShell.vue', () => ({ default: { template:
  '<div><slot /></div>' } }))` — but only as a fallback. The 3 assertions (img present, fallback
  span, `@error` swap) are the gates to verify.

- **`proxyImageUrl` usage.** Imported inside `SidebarChannelRow.vue` from `@/lib/auth-api`
  (the same module as today) and called at `<img :src="proxyImageUrl(channel.avatarUrl!)">`.
  The `<img>` keeps `class="... grayscale"`, `alt="${channel.name} avatar"`, and
  `@error="onAvatarError(channel.id)"`. `apps/web/app/src/lib/auth-api.ts` is NOT modified.

- **Skip-to-content link.** First focusable element in `AppShell.vue`'s template, before the
  `<TooltipProvider>`:

  ```html
  <a
    href="#main-content"
    class="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded focus:bg-bg-surface focus:px-3 focus:py-2"
  >Skip to main content</a>
  ```

  The main outlet inside `SidebarInset`: `<main id="main-content" tabindex="-1" ...>`. The
  `sr-only focus:not-sr-only` pattern is the standard Tailwind v4 recipe; works with the
  project's `@tailwindcss/vite` 4.3.1.

- **Theming consolidation.** REMOVE the header dark/light pill block (currently
  `App.vue` lines 696-711). REMOVE the static "Theme" label row + the `settings.currentTheme`
  text inside the account popover (lines 607-613). REPLACE that row with `<ThemeToggle />`
  alone. The toggle's animated sun/moon SVG already conveys state; the redundant label drops.

- **`App.vue` thin wrapper.** ~10 lines: `isAuthRoute` computed, `<RouterView v-if="isAuthRoute" />`
  else `<AppShell />`. No other imports.

## 6. Test strategy details

- **Mount helpers:** Reuse the `factory(overrides)` pattern from
  `apps/web/app/src/components/dashboard/shared/KpiCard.test.ts` (line 12-23) — a builder that
  returns the typed prop and lets each test override fields. Apply to `SidebarChannelRow` and
  `AppLanguagePill`.
- **Mock pattern (per test):** `vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (k) => k }) }))`
  for i18n. `vi.mock('vue-router', ...)` for `useRoute` / `useRouter`. For sections that
  touch stores, `setActivePinia(createPinia())` in `beforeEach` then `useXxxStore().field = ...`.
- **`usePopoverDismissal.test.ts`:** mount a tiny harness SFC that exposes
  `containerRef`, `triggerRef`, and a button bound to `toggle`. In each test:
    1. Open via `toggle`, assert `open.value === true` via the harness's data attr.
    2. Escape: `await trigger('keydown', { key: 'Escape' })` on the harness root; assert
       `open === false` and `document.activeElement === triggerRef.el`.
    3. Click outside: open, then dispatch a `MouseEvent` on `document.body`; assert `open === false`
       and `document.activeElement === triggerRef.el`.
    4. Click on trigger while open: assert it toggles closed (no focus restore because toggle
       is the source).
    5. Route change: open, push a new path via mocked `useRouter().push`, assert
       `open === false` and `document.activeElement` is NOT the trigger.
    6. Unmount: open, `wrapper.unmount()`, dispatch a click on document, assert no errors and
       the listener was removed (no late state mutation; verify by spying `removeEventListener`).

- **`SidebarChannelRow.test.ts`:** build a channel with `makeChannel(...)`, mount with
  `:channel :isActive :queuedCount`. Assert (a) `<img>` present with `src` = `proxyImageUrl`
  output and `alt` = `${name} avatar`; (b) with `avatarUrl: undefined`, no `img[src]` and the
  badge `<span>` text matches `getProviderBadge('linkedin')`; (c) trigger `error` on the img,
  await, assert img is gone and badge span is back. `proxyImageUrl` is the real function from
  `@/lib/auth-api` (no need to mock).
- **`AppHeader.test.ts`:** mount with `currentSectionLabel="analytics"`,
  `headerSummary="dark / EN"`,
  `currentLocale="en"`. Assert (a) the eyebrow is `"Workspace"`, the `<h1>` resolves
  `nav.analytics`; (b) the status pill text is exactly `"dark / EN"`; (c) the language pill
  renders with `EN aria-checked="true"`, `ES aria-checked="false"`; (d) no element with
  `aria-label` matching `/theme/i` exists; (e) clicking ES emits `setLocale` (note: prop is
  `current`, emit is `change` per spec — match the spec exactly).
- **`App.test.ts`:** run AFTER steps 1-14. No source edits. Expected outcome: 3 avatar
  assertions pass. If a real `import AppShell from '@/components/layout/AppShell.vue'` path
  trips the test runner, add ONE line near the existing mocks: a stub for `AppShell.vue` only.
  The existing `Sidebar*`, `TooltipProvider`, `ThemeToggle`, `@/lib/auth-api`, `@lucide/vue`
  mocks remain untouched.

## 7. Verification & rollout

- [ ] `pnpm test:run` (full suite) passes
- [ ] `pnpm test:run usePopoverDismissal` passes (the riskiest composable)
- [ ] `pnpm type-check` (which runs `vue-tsc --build`) passes
- [ ] `pnpm lint` (biome) passes
- [ ] Manual smoke: open `pt-app.localhost` (or `localhost:5173`); navigate `/`; open the
  workspace menu, press Escape (focus → trigger), click outside (same), navigate to
  `/scheduler` (menu closes, no focus steal). Open account menu, verify `ThemeToggle` is the
  only theme control, the static "Theme" label is gone, and the header has no theme pill.
  Tab to the language pill, change language, click outside — verify the menu closes.
- [ ] A11y smoke: Tab from a fresh load — first focus is the skip link; activate it — focus
  jumps to `<main id="main-content">` and the page scrolls. Screen-reader run on the account
  popover — `aria-haspopup`, `aria-expanded`, `role="menu"`, `role="menuitem"` all present.
- [ ] Visual: no regression in sidebar / header / outlet vs. pre-refactor build.
- [ ] Route smoke: visit every authenticated route — downstream views are untouched but a
  quick pass catches accidental header/sidebar coupling.

## 8. Open follow-ups (deferred, NOT in this change)

- `reka-ui` `DropdownMenu` migration for both popovers (keyboard nav + focus mgmt for free).
- i18n of the untranslated Threads / Bluesky / Facebook labels.
- Removing the now-superseded `connectTimeout` global in favor of `useConnectMessage` from
  any other caller that may appear.
- Additional a11y coverage (e.g. `axe-core` integration in the test suite, focus trap inside
  the popovers, arrow-key navigation through menu items) — not in the spec, not in this
  change.
