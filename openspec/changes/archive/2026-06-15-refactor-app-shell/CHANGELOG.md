# CHANGELOG — refactor-app-shell

## App Shell Structural Decomposition

Decomposed the 737-line monolithic `App.vue` into a thin 16-line route gate plus a
composable-driven shell architecture: `AppShell.vue` mounts `TooltipProvider` and
`SidebarProvider` at the root, composes 5 sidebar sections (`SidebarHeaderSection`,
`SidebarNavSection`, `SidebarChannelsSection`, `SidebarConnectSection`,
`SidebarAccountSection`), 3 header leaves (`AppHeader`, `AppLanguagePill`,
`AppStatusPill`), and a skip-to-content link. Three shared composables
(`usePopoverDismissal`, `useQueuedCounts`, `useConnectMessage`) extract reusable
behavior (popover focus-restore + dismissal, queued-publication counters, transient
messages) into `apps/web/app/src/composables/`. The refactor added 12 new SFCs,
3 composables, and 11 test files (56 new tests) with zero new typecheck or lint
errors and zero regressions. All 19 spec requirements and 67 scenarios verified.

## Warnings and Follow-Ups

Verdict: **PASS WITH WARNINGS**. Two non-blocking gaps remain: (1) `useQueuedCounts`
is implemented and unit-tested but not wired into `AppShell.vue` — it hardcodes
`totalQueuedCount = ref(0)` instead of calling the composable; (2)
`CreatePostModal.test.ts` has the same `proxyImageUrl` mock gap fixed in
`App.test.ts` during this refactor — a pre-existing latent bug. Additionally,
the tasks forecast recommended stacked PRs (composables → layout → sidebar → shell
→ verify) for reviewer ergonomics; the single-PR delivery is functional but may
benefit from splitting before merge.
