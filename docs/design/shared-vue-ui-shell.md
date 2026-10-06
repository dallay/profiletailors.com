# Shared Vue UI foundation and shell

## Overview

`@profiletailors/vue-ui` owns the Vue presentation shared by the dashboard and platform-admin SPAs: design tokens, font assets, small visual utilities, reusable controls, and the navigation shell. Both SPAs share visual and interaction contracts without sharing routes, permissions, stores, or product-specific content.

Design tokens, fonts, and common utility classes are public CSS imports under `@profiletailors/vue-ui/styles`. The package shell and page header are implemented in `shared/vue-ui/src/shell`; their public exports are available from `@profiletailors/vue-ui/shell`. Sidebar primitives are exported from `@profiletailors/vue-ui/shell/sidebar`.

## Ownership boundaries

- `DashboardShell` owns `SidebarProvider`, `Sidebar`, sidebar header/content/footer regions, `SidebarInset`, and the tooltip provider.
- `DashboardHeader` owns the shared page title layout and sidebar toggle. Applications supply localized labels, section title, and any trailing actions.
- The shared page header replaces the full-height desktop rail toggle so sidebar behavior uses one visible, keyboard-accessible control on both surfaces.
- Each application owns its route registry, permission checks, route links, localized labels, account behavior, identity details, and page content.
- Applications render their own `RouterLink` elements in the `content` slot. The shared package does not import app routers or stores.
- `NavPort` supplies the navigation groups to the shell. `NavGroup` and `NavItem` are transport shapes; applications remain the source of navigation policy.
- Slots named `header`, `content`, `account`, `pwa`, `footer`, and `inset` let applications supply surface-specific content. The `content` slot receives the normalized `groups`; the `account` slot receives `signOut` and `openSettings` callbacks.
- PWA UI is optional and stays in the dashboard app. The admin app may use its own account/footer presentation.
- The admin sidebar footer groups sign-out as a full-width navigation action above a compact build-metadata block. Sign-out remains available in icon-collapsed mode; version details may be hidden there.
- Reusable Vue controls, brand tokens, fonts, and repeated visual utilities belong here when both SPAs share their behavior and appearance.
- Admin and dashboard feature workflows, data presentation, route policy, and surface-specific components stay in their owning app even when their markup resembles another screen.
- Do not import `apps/web/app` components into admin or the reverse. Cross-surface use goes through a public `@profiletailors/vue-ui` export.
- shadcn-vue and Reka UI are implementation tools. Keep wrappers in this package only when the wrapper is a stable shared UI contract; keep feature compositions local.

## Reuse inventory

- The app and admin had identical local `VersionBadge.vue` files, but both already used the package's `VersionBadge`; the unused local copies have been removed.
- `AdminButton`, `AdminInput`, and `AdminCard` overlap with package controls by purpose, but their sizes and visual contracts currently differ. Consolidate them only with an intentional app-wide visual decision.
- `AdminSelect` is a native select while the app's Select is a Reka/shadcn-vue composition. They are not interchangeable copies; align their interaction and accessibility contracts before moving either implementation.
- Admin `ConfirmDialog` owns its own native dialog behavior. If it is replaced by a shared confirmation pattern, preserve focus management, busy-state dismissal rules, and the consuming view's `v-model` contract.
- Keep these surface-specific views and interactions in their SPAs: a small shared package is preferable to making dashboard features depend on internal admin or app components.

## Usage

Import the shell and sidebar primitives from their public package entry points:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { DashboardShell } from '@profiletailors/vue-ui/shell'
import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuItem,
} from '@profiletailors/vue-ui/shell/sidebar'

const nav = computed(() => ({ groups: [] }))
</script>

<template>
  <DashboardShell :nav="nav">
    <template #header>
      <AppSpecificIdentity />
    </template>

    <template #content="{ groups }">
      <SidebarGroup v-for="group in groups" :key="group.key">
        <SidebarGroupLabel>{{ group.label }}</SidebarGroupLabel>
        <SidebarMenu>
          <SidebarMenuItem v-for="item in group.items" :key="item.key">
            <RouterLink :to="{ name: item.routeName }">{{ item.label }}</RouterLink>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarGroup>
    </template>

    <template #inset>
      <RouterView />
    </template>
  </DashboardShell>
</template>
```

The example omits app-specific navigation mapping and is illustrative; pass the actual typed `NavPort` assembled from each app's registry. Use the shared `SidebarTrigger` inside an app-owned mobile header when the app's layout needs an explicit mobile navigation control. Give it a localized accessible name appropriate to that app.

## Interaction and accessibility

The shared sidebar switches to the shared Sheet on mobile viewports and supports desktop collapse through its sidebar context. The Sheet provides dialog semantics and close behavior. Applications must retain a visible, keyboard-operable trigger and must not bypass permission-filtered navigation when supplying slot content. Test the actual shared Sheet path at the consuming application boundary when changing the shell integration.

## Troubleshooting

- If a consumer cannot resolve a shell component, confirm it imports from the package entry point rather than an app's former local sidebar path.
- If mobile navigation does not open, check that the consumer renders `SidebarTrigger` below the shared `SidebarProvider` and that the viewport is classified as mobile.
- If an entry appears for the wrong operator, fix the app's nav registry or permission adapter; the shared shell intentionally does not own authorization.
- Keep theme/locale controls and other surface-specific account behavior in the owning application unless their contract becomes genuinely shared by multiple consumers.

## References

- [Design tokens and visual rules](../../.agents/DESIGN.md)
- [Vue UI package](../../shared/vue-ui/package.json)
- [Shared design tokens](../../shared/vue-ui/src/styles/design-tokens.css)
- [Shared font imports](../../shared/vue-ui/src/styles/fonts.css)
- [Shared visual utilities](../../shared/vue-ui/src/styles/utilities.css)
- [Dashboard shell source](../../shared/vue-ui/src/shell/DashboardShell.vue)
- [Navigation port](../../shared/vue-ui/src/shell/ports/nav.ts)
- [Frontend architecture guidance](../../.agents/skills/frontend-architecture/SKILL.md)
