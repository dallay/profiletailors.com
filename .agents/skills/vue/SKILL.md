---
name: vue
description: Use when working with Vue components, composables, Pinia state, or Vue forms in Profile Tailors.
allowed-tools: Read, Edit, Write, Glob, Grep, Bash
metadata:
  category: frontend-platform
  family: vue
  source: local
  version: 2026-09-30
---

# Vue 3 Skill

## Overview

Use Vue 3 Composition API, TypeScript, and the current app or admin surface contracts. Read the
surface `PRODUCT.md` and `.agents/skills/frontend-architecture/SKILL.md` when the task crosses
feature boundaries or changes shared UI.

### Project boundaries

- The dashboard package is `app` at `apps/web/app`; its feature modules live under
  `src/modules/<feature>` and expose cross-feature APIs through their `index.ts` barrel.
- The admin SPA is a separate, intentionally flatter app under `apps/web/admin`. Do not import
  implementation from the dashboard or move app-specific behavior into shared packages.
- The shared Vue component package is `@profiletailors/vue-ui`. The apps also use shadcn-vue
  primitives; use the existing component and alias patterns in the surface being changed.
- Read dependencies and package scripts from the relevant `package.json`. Do not copy project
  versions into this skill.

## Changes

### Components and state

- Prefer `<script setup lang="ts">`, typed props and emits, and small components with one clear
  responsibility.
- Keep transient, view-local state in the component or a composable. Use Pinia for state that must
  be shared across components, routes, or a feature workflow; do not create a store for local form
  state.
- Keep feature behavior inside its module. Cross-feature consumers use that module's public
  `index.ts` barrel, not private stores or infrastructure.
- Use props down and emitted events up for parent-child communication. Use provide/inject only for
  a deliberate subtree contract.
- Keep forms aligned with the form and schema libraries already used by the surface. Validate on
  the interaction that matches the field and form behavior; do not enforce blur-only validation as
  a global rule.
- Prefer Vue and project abstractions over manual DOM access. Clean up subscriptions, timers, and
  other effects when their owner is disposed.
- Keep loading, success, empty, and error states explicit when a screen depends on asynchronous
  work. Preserve useful existing data during refresh when the product behavior calls for it, and
  prevent stale requests from overwriting newer state.
- Keep labels, validation messages, and user-facing status text in the surface's localization
  system. Do not add hard-coded copy when the feature already uses i18n.
- For dialogs and modals, follow the existing primitive and verify accessible naming, focus entry,
  focus return, Escape behavior, and keyboard operation. Prefer native or shared UI primitives
  over custom overlays.
- Give state one clear owner: local component state for view-only state, a composable for reusable
  behavior, and Pinia only for state shared across a workflow or route boundary.

## Usage

### Shared UI

Import shared Vue components from `@profiletailors/vue-ui` and use shadcn-vue primitives through
the existing local component paths. Do not invent package names or assume every shadcn-vue
component is re-exported from the shared package.

```vue
<script setup lang="ts">
import { Button, Card } from '@profiletailors/vue-ui'

const props = defineProps<{ title: string }>()
</script>

<template>
  <Card>
    <h2>{{ props.title }}</h2>
    <Button type="button">Continue</Button>
  </Card>
</template>
```

Confirm the exported components in `shared/vue-ui/src/index.ts` before using an import. Follow the
surface's existing keyboard, focus, localization, and accessibility patterns.

### Verification commands

The dashboard package is named `app`:

```sh
pnpm --filter app type-check
pnpm --filter app lint
pnpm --filter app test:run
```

Use `pnpm --filter @profiletailors/admin ...` for admin package scripts. Check `package.json` for
the available script names before running a command.

## Troubleshooting

If a shared component import is missing, confirm its export in `shared/vue-ui/src/index.ts` and
follow the surface's existing component path and localization pattern.

## References

- [Vue documentation](https://vuejs.org/guide/)
- [Pinia documentation](https://pinia.vuejs.org/)
- [Frontend architecture](../frontend-architecture/SKILL.md)
