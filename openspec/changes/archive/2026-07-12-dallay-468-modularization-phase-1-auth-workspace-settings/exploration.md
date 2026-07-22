## Exploration: DALLAY-468 Modularization Phase 1 — Auth, Workspace, Settings

### Current State

Phase 0 foundation is present: `apps/web/app/src/modules`, `src/shared`, and `src/layouts` exist
with `.gitkeep` placeholders, and the Phase 0 architecture doc defines module layers plus aliases.
The relevant frontend code is still in legacy locations under `src/views`, `src/stores`, `src/lib`,
and `src/components/workspace`. shadcn-vue primitives remain under `src/components/ui` and should
stay there.

### Current File Inventory

Auth scope:

- `apps/web/app/src/views/AuthView.vue` — login/register view; imports validation schemas,
  `useAuthStore`, router, i18n, and shadcn `Button`.
- `apps/web/app/src/views/LinkedInCallbackView.vue` — LinkedIn OAuth callback UI; uses
  `usePublishingStore`, router, i18n, lucide icons, and shadcn `Button/Card`.
- `apps/web/app/src/stores/auth.ts` — Pinia auth store; imports auth API functions and
  `useWorkspaceStore` via relative `./workspace`.
- `apps/web/app/src/lib/auth-api.ts` — API helper for auth, workspace, configured providers,
  authenticated fetch, and `proxyImageUrl`.
- Tests: `src/views/AuthView.spec.ts`, `src/views/LinkedInCallbackView.spec.ts`,
  `src/stores/auth.spec.ts`, `src/stores/auth.test.ts`, `src/lib/auth-api.spec.ts`,
  `src/lib/auth-api.test.ts`.

Workspace scope:

- `apps/web/app/src/components/workspace/WorkspaceIconModal.vue` — workspace icon selector modal;
  imported only by `SettingsView.vue`.
- `apps/web/app/src/stores/workspace.ts` — Pinia workspace selection/store; imports
  `WorkspaceSummary` and `fetchWorkspaces` from `auth-api`.
- No dedicated workspace store test found in the scoped inventory.

Settings scope:

- `apps/web/app/src/views/SettingsView.vue` — settings page; mixes settings preferences, workspace
  rename/icon behavior, and LinkedIn channel connection controls.
- `apps/web/app/src/stores/settings.ts` — Pinia settings store; persists locale/theme and applies
  DOM/i18n side effects.
- Tests: `src/views/SettingsView.spec.ts`, `src/views/SettingsView.validation.spec.ts`,
  `src/stores/settings.test.ts`.

### Dependency / Import Map

Imports that must be updated after moving files:

- Router: `src/router/index.ts` imports `useAuthStore` and lazy-loads `AuthView`, `SettingsView`,
  `LinkedInCallbackView` from `../views/*`.
- App bootstrap: `src/main.ts` imports `useAuthStore` and `useSettingsStore` from legacy
  `@/stores/*`.
- Auth store consumers: `HomeView.vue`, `VerifyEmailView.vue`, `SettingsView.vue`,
  `MediaLibraryView.vue`, `CreatePostModal.vue`, `AppShell.vue`, `media-api.ts`, router tests, and
  several component/view tests import or mock `@/stores/auth`.
- Workspace store consumers: `SettingsView.vue`, `CreatePostModal.vue`, `AppShell.vue`,
  `media-api.ts`, and tests import or mock `@/stores/workspace`.
- Settings store consumers: `SettingsView.vue`, `ThemeToggle.vue`, `SidebarAccountSection.vue`,
  `main.ts`, and tests import `@/stores/settings`.
- Auth API consumers/mocks are broad: `auth.ts`, `workspace.ts`, `SettingsView.vue`,
  `VerifyEmailView.vue`, `MediaLibraryView.vue`, `CreatePostModal.vue`, `publishing.ts`,
  `media-api.ts`, composer/sidebar components, and many tests import or mock `@/lib/auth-api`.

Important coupling:

- `auth.ts` currently imports workspace store as `./workspace`; after moving to separate modules
  this must become `@modules/workspace/infrastructure/workspace.store` or a compatibility export.
- `auth-api.ts` currently mixes auth concerns with workspace DTOs/endpoints and cross-cutting fetch
  helpers (`resolveApiUrl`, `createApiFetch`, `proxyImageUrl`). Moving it wholesale to
  `modules/auth/infrastructure` will create many cross-feature imports from media/publishing/sidebar
  into auth infrastructure.
- `SettingsView.vue` spans all three target modules: settings presentation, workspace rename/icon
  actions, auth token access, and publishing channel connection.

### Proposed Module Targets

Preferred target layout for this phase:

- `src/modules/auth/presentation/AuthView.vue`
- `src/modules/auth/presentation/LinkedInCallbackView.vue`
- `src/modules/auth/infrastructure/auth.store.ts`
- `src/modules/auth/infrastructure/auth-api.ts` for the moved legacy API helper, keeping exports
  stable via import updates or temporary compatibility re-export.
- `src/modules/workspace/presentation/components/WorkspaceIconModal.vue`
- `src/modules/workspace/infrastructure/workspace.store.ts`
- `src/modules/settings/presentation/SettingsView.vue`
- `src/modules/settings/infrastructure/settings.store.ts`

Test files should move beside their targets or into the same module layer:

- Auth view specs to `modules/auth/presentation/`.
- Auth store/API specs to `modules/auth/infrastructure/`.
- Settings view specs to `modules/settings/presentation/`.
- Settings store test to `modules/settings/infrastructure/`.

Recommended import strategy:

- Use `@modules/<feature>/...` for moved feature code.
- Keep `@/components/ui/*` unchanged for shadcn-vue.
- Keep shared legacy imports like `@/lib/validation/schemas`, `@/i18n`, and currently unmigrated
  components/views stable unless a later phase moves them.
- Consider temporary compatibility re-export files only if the implementation wants to reduce churn,
  but the Linear DoD says files should be moved, so direct import updates are cleaner.

### Test Impact

Focused verification surface:

- Unit/spec files with path updates: auth view/store/API tests, settings view/store tests, router
  tests, AppShell/CreatePostModal/media/sidebar/composer tests that mock `@/stores/*` or
  `@/lib/auth-api`.
- E2E references likely remain route-based and should not need path updates:
  `e2e/specs/scheduler-auth.spec.ts`, `e2e/specs/scheduler-settings.spec.ts`.
- Required DoD commands after implementation: `just frontend-test` and `just frontend-lint`.

### Approaches

1. **Direct module move with import rewrite** — Move scoped files to target modules and update all
   imports/mocks to `@modules/*`.
    - Pros: Matches Phase 0 architecture and Linear DoD directly; no compatibility clutter; makes
      dependencies visible.
    - Cons: High import/test mock churn because `auth-api.ts` and auth/workspace/settings stores are
      widely consumed.
    - Effort: Medium

2. **Move files plus legacy compatibility re-exports** — Move real files into modules, leave thin
   legacy files under `src/stores`/`src/lib`/`src/views` re-exporting moved modules.
    - Pros: Smaller initial import churn; safer for broad `auth-api` consumers.
    - Cons: Violates the spirit of “all files moved” unless clearly treated as temporary shims;
      legacy tree remains misleading.
    - Effort: Low/Medium

3. **Split `auth-api.ts` during migration** — Move and decompose API helpers into
   shared/auth/workspace pieces.
    - Pros: Better architecture long-term; reduces auth module owning cross-cutting helpers.
    - Cons: Out of Phase 1 scope; changes behavior boundaries and increases risk.
    - Effort: High

### Recommendation

Use Approach 1 for DALLAY-468: direct moves into `modules/auth`, `modules/workspace`, and
`modules/settings`, with complete import/mock rewrites to `@modules/*`. Do not split `auth-api.ts`
in this phase; move it as-is to auth infrastructure and document its cross-feature coupling for a
later cleanup. This keeps Phase 1 focused on physical modularization rather than behavior or API
redesign.

### Risks / Blockers

- `auth-api.ts` is not purely auth-owned; it exports workspace endpoints and shared fetch/image URL
  helpers used by media, publishing, composer, sidebar, and tests. Moving it under auth may make
  dependency direction awkward until later shared/API modularization.
- `SettingsView.vue` is a mixed surface and will still depend on auth, workspace, and publishing
  after moving to settings presentation.
- Test mocks are heavily path-coupled to `@/lib/auth-api` and `@/stores/*`; missed mocks will fail
  Vitest even if runtime imports compile.
- `auth.ts` currently has a relative dependency on workspace store; moving across module folders
  requires careful import rewrite to avoid broken module resolution or circular import surprises.
- No blocker found for starting proposal/design; Phase 0 foundation exists and DALLAY-468 scope is
  clear.

### Ready for Proposal

Yes — proposal can proceed with direct module moves, import/mock rewrites, no shadcn-vue movement,
and verification via `just frontend-test` plus `just frontend-lint`.
