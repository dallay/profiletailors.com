# Delta for App Shell

## MODIFIED Requirements

### Requirement: Shell File Layout

| File                    | Role                     | Owns                                                                          |
|-------------------------|--------------------------|-------------------------------------------------------------------------------|
| `App.vue`               | thin wrapper             | `isAuthRoute` gate only                                                       |
| `layouts/AppShell.vue`  | authenticated shell      | providers, auth bootstrap watcher, sidebar/header/outlet composition          |
| `layouts/sidebar/*.vue` | sidebar sections         | section state and upward emits; no direct shell orchestration                 |
| `layouts/AppHeader.vue` | sticky header            | route/settings-derived title, status pill, language pill, main outlet wrapper |
| `@shared/composables/*` | generic shared behaviors | generic focus/popover behavior only                                           |
| module composables      | domain behaviors         | publishing/auth/media-owned route, callback, queue, upload logic              |

App shell placement MUST move shell-owned layout/sidebar files from `components/layout` and
`components/sidebar` into `@layouts`, while preserving route, shell, provider, and outlet behavior.
Generic shell-independent behavior MAY move to `@shared`; domain-owned behavior MUST stay in the
owning module.
(Previously: shell files were specified under `components/layout`, `components/sidebar`, and root
`composables`.)

#### Scenario: `App.vue` remains a route gate

- GIVEN Phase 5 cleanup is complete
- WHEN `App.vue` renders auth and non-auth routes
- THEN it SHALL still render auth routes without the authenticated shell
- AND non-auth routes SHALL still render the authenticated shell

#### Scenario: Shell providers and outlet behavior are preserved

- GIVEN an authenticated route is active
- WHEN the relocated `@layouts/AppShell.vue` mounts
- THEN `TooltipProvider` and `SidebarProvider` SHALL wrap the sidebar and inset as before
- AND `<RouterView />` SHALL remain inside the main outlet

#### Scenario: Layout imports use layout paths

- GIVEN shell-owned files have moved
- WHEN source, tests, or mocks import AppShell, AppHeader, or sidebar sections
- THEN imports MUST use `@layouts/*` or valid colocated relative paths
- AND they MUST NOT use moved `@/components/layout/*` or `@/components/sidebar/*` paths

### Requirement: AppShell Composition Contract

`@layouts/AppShell.vue` MUST compose the relocated sidebar and header in the same observable order:
`SidebarHeaderSection`, `SidebarNavSection`, `SidebarChannelsSection`, `SidebarConnectSection`,
`SidebarAccountSection`, `SidebarRail`, then `AppHeader` and `<main>` containing `<RouterView />`
inside `SidebarInset`. The shell owns orchestration handlers; sections only emit upward and never
mutate stores directly. State local to a section lives in that section.
(Previously: the same contract applied to files under `components/layout` and `components/sidebar`.)

#### Scenario: Sidebar sections are composed in order after relocation

- GIVEN the relocated shell is rendered
- WHEN the DOM is inspected
- THEN the sidebar sections SHALL appear in the same order as before
- AND `AppHeader` plus `<main>` SHALL remain inside `SidebarInset`

#### Scenario: Auth bootstrap watcher behavior is preserved

- GIVEN the user is unauthenticated at mount
- WHEN `auth.isAuthenticated` and `auth.accessToken` become truthy
- THEN workspace loading and publishing channel fetch SHALL still run
- AND failures SHALL still be caught without breaking render
