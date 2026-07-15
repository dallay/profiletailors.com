# Design: DALLAY-472 Modularization Phase 5 Shared Layouts Cleanup

## Technical Approach

Apply a behavior-preserving relocation pass inside `apps/web/app/src`: move authenticated shell ownership into `@layouts`, generic cross-module code into `@shared`, and domain leftovers into their owning modules. Keep shadcn-vue primitives plus generated aliases stable so this remains modular cleanup, not a design-system migration.

## Architecture Decisions

| Decision | Choice | Alternatives considered | Rationale |
|---|---|---|---|
| Target ownership | `layouts/` owns `AppShell`, `AppHeader`, sidebar sections, shell-only toast/message behavior; `shared/` owns reusable app components, generic composables, lib, i18n; modules own auth/media/publishing behavior. | Move every root component to shared; leave root leftovers. | Follows specs and current module aliases while keeping reasons-to-change clear. |
| shadcn boundary | Keep `src/components/ui/**`, `components.json`, and `@/lib/utils` compatible. | Move UI primitives and `cn` into shared. | shadcn-vue is configured for `@/components/ui` and `@/lib/utils`; changing it is high churn and out of scope. |
| Composable classification | Domain/store/route behavior moves to modules; generic browser/UI behavior moves to shared; shell-only behavior moves to layouts. | Put all composables in shared. | Ownership should follow business dependency, not file type. |
| Aliases | Prefer `@modules`, `@shared`, `@layouts` for relocated code; retain `@/` for app root and shadcn compatibility. | Remove `@/` entirely. | Existing Vite/TS aliases support both; generated shadcn imports still need `@/`. |

## Data Flow

    App.vue ──→ @layouts/AppShell.vue ──→ @layouts/sidebar/*
       │                 │                         │
       │                 ├──→ @modules/* stores/composables
       │                 └──→ @shared/components + @shared/lib
       └── router ──→ @modules/* views

## File Changes

| File | Action | Description |
|---|---|---|
| `src/layouts/AppShell.vue`, `AppHeader.vue`, `sidebar/*`, tests | Move | Authenticated shell, header, sidebar composition and section tests. |
| `src/layouts/useConnectMessage.ts` | Move | Shell-only transient connect message used by sidebar. |
| `src/layouts/UploadProgressToast.vue` | Move | Global shell toast tied to authenticated shell/media store visibility. |
| `src/shared/components/{WorkspaceAvatar,ThemeToggle,SocialProviderIcon}.vue` | Move | Reusable non-shadcn app components. |
| `src/shared/composables/{useFocusTrap,usePopoverDismissal}.ts` | Move | Generic UI behaviors. |
| `src/shared/lib/{formatters,string-utils,provider-styles,sse}.ts`, `src/shared/lib/validation/schemas.ts`, tests | Move | Cross-module pure utilities/contracts. |
| `src/shared/i18n/index.ts` | Move | App-wide i18n plugin; update `main.ts`. |
| `src/modules/auth/presentation/VerifyEmailView.vue`, `src/modules/auth/application/useLinkedInCallback.ts` | Move | Auth-owned route and callback flow. |
| `src/modules/media/application/useFileHash.ts` | Move | Media upload hashing used by media API/service flow. |
| `src/modules/publishing/application/{useCalendarUrl,useQueuedCounts}.ts` | Move | Scheduler URL state and queue counts; exported for layout consumption. |
| `src/App.vue`, `src/router/index.ts`, imports/tests/mocks | Modify | Import-path updates only. |
| `src/stores` | Keep absent | No current root store cleanup candidate exists. |
| Empty legacy roots | Delete | Remove `components/layout`, `components/sidebar`, `composables`, `views`, non-compat `lib`, `i18n`; retain `components/ui` and `lib/utils.ts`. |

## Interfaces / Contracts

No runtime contract changes. Moved files MUST export the same symbols from new paths. `@/lib/utils` MUST continue exporting `cn`; publishing MAY expose application composables consumed by layout.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| TDD guards | New owners resolve; legacy roots are blocked | Extend `src/modules/module-relocation.spec.ts` and alias guard before relocation; exempt `components/ui` and `lib/utils.ts`. |
| Unit/regression | Moved composables/utilities/components preserve behavior | Move existing tests with files, update mocks/imports, keep assertions unchanged unless paths require setup changes. |
| Focused app checks | Import graph, lint, type regressions | Run `pnpm --filter app test:run`, `pnpm --filter app lint`, and app type-check/build if tests expose unresolved imports. |
| Manual/dev server | Vite import warnings and navigation | Start app, expect no missing-import warnings; navigate shell, settings, media, scheduler, verify-email, LinkedIn callback expectations. |

## Migration / Rollout

No data migration required. Implement in small reversible slices: relocation guards, layout move, shared utilities, module-owned leftovers, import cleanup, delete empty roots. Roll back by reverting relocation commits; no feature flag is needed because behavior is unchanged.

## Open Questions

None.
