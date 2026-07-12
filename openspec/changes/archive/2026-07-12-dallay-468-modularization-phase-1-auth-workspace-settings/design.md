# Design: DALLAY-468 Modularization Phase 1 — Auth, Workspace, Settings

## Technical Approach

Behavior-preserving relocation only. Move auth, workspace, and settings files from legacy `src/views`, `src/stores`, `src/lib`, and `src/components/workspace` into `src/modules/*` using Phase 0 aliases. Update every runtime import, test import, and `vi.mock()` path to the new `@modules/*` locations. Do not split APIs, change routes, alter Pinia IDs/state, or move shadcn-vue primitives.

## Architecture Decisions

| Decision | Choice | Alternatives considered | Rationale |
|---|---|---|---|
| Module layout | `presentation/` for Vue views/components; `infrastructure/` for Pinia stores/API helpers | Flat module folders | Matches Phase 0 convention and keeps UI vs adapters visible. |
| Import rewrite | Direct rewrite to `@modules/<feature>/...` | Legacy compatibility re-export shims | Avoids misleading legacy files and satisfies “files moved” success criteria. |
| `auth-api.ts` ownership | Move as-is to `modules/auth/infrastructure/auth-api.ts` | Split into shared/auth/workspace clients now | Splitting is out of scope and risks behavior changes; document as boundary exception. |
| Settings view | Move intact to settings presentation | Decompose workspace/auth/publishing UI now | Current behavior spans modules; decomposition is not required for physical modularization. |
| shadcn-vue | Keep `@/components/ui/*` unchanged | Move UI primitives to shared/modules | `components.json` owns that path; moving it would break tooling scope. |

## Data Flow

No runtime behavior changes.

```text
router/main/tests ──→ @modules/auth|settings|workspace
SettingsView ───────→ auth store + workspace store + publishing store
      │              → auth-api rename/icon helpers
      └──────────────→ WorkspaceIconModal
legacy consumers ───→ moved auth-api/store paths via direct imports
```

## File Changes

| File | Action | Description |
|---|---|---|
| `apps/web/app/src/views/AuthView.vue` | Move | `modules/auth/presentation/AuthView.vue` |
| `apps/web/app/src/views/LinkedInCallbackView.vue` | Move | `modules/auth/presentation/LinkedInCallbackView.vue` |
| `apps/web/app/src/stores/auth.ts` | Move | `modules/auth/infrastructure/auth.store.ts` |
| `apps/web/app/src/lib/auth-api.ts` | Move | `modules/auth/infrastructure/auth-api.ts` |
| `apps/web/app/src/components/workspace/WorkspaceIconModal.vue` | Move | `modules/workspace/presentation/components/WorkspaceIconModal.vue` |
| `apps/web/app/src/stores/workspace.ts` | Move | `modules/workspace/infrastructure/workspace.store.ts` |
| `apps/web/app/src/views/SettingsView.vue` | Move | `modules/settings/presentation/SettingsView.vue` |
| `apps/web/app/src/stores/settings.ts` | Move | `modules/settings/infrastructure/settings.store.ts` |
| `*.spec.ts`, `*.test.ts` for moved files | Move/Modify | Place beside target layer and rewrite imports/mocks. |
| `src/router/index.ts`, `src/main.ts`, app components/views/stores/lib tests | Modify | Rewrite legacy imports and `vi.mock()` strings. |

## Interfaces / Contracts

Moved symbols keep their current exported names and runtime contracts:

```ts
useAuthStore, useWorkspaceStore, useSettingsStore
login, register, refreshSession, createApiFetch, resolveApiUrl, proxyImageUrl
fetchWorkspaces, renameWorkspace, updateWorkspaceIcon
```

Key path contracts:
- `@modules/auth/infrastructure/auth.store`
- `@modules/auth/infrastructure/auth-api`
- `@modules/workspace/infrastructure/workspace.store`
- `@modules/settings/infrastructure/settings.store`

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Moved stores/API helpers | Preserve existing specs; update imports/mocks only. |
| Component | Auth, callback, settings, modal consumers | Preserve assertions; update mounted component imports and mocks. |
| Integration-ish | Router guard, AppShell, media/publishing/composer/sidebar consumers | Rewrite `@/stores/*` and `@/lib/auth-api` mocks to `@modules/*`. |
| E2E | Auth/settings route behavior | No path changes expected; rely on existing route-based specs if needed. |

## Migration / Rollout

No data migration required. Apply as one focused relocation PR. Verification commands from repo root:

```bash
pnpm --filter app exec vitest run
pnpm --filter app exec biome check src/modules src/main.ts src/router/index.ts src/views src/components src/stores src/lib src/composables
```

> Note: `just frontend-test` and `just frontend-lint` target the marketing app, not the Vue SPA.
> App-specific verification uses `pnpm --filter app ...` commands.

Optional confidence check after rewrite:

```bash
rg '@/\(stores/\(auth\|workspace\|settings\)\|lib/auth-api\|views/\(AuthView\|SettingsView\|LinkedInCallbackView\)\|components/workspace/WorkspaceIconModal\)' apps/web/app/src
```

## Boundary Exceptions

- `auth-api.ts` remains cross-feature until a later shared/API decomposition phase.
- `SettingsView.vue` may import auth, workspace, and publishing during Phase 1.
- Unmigrated legacy features may import module internals directly until public module barrels are introduced.

## Open Questions

None.
