## Exploration: DALLAY-472 — Modularization Phase 5 shared, layouts, and cleanup

### Current State
`apps/web/app` is already partially modularized after Phases 0–4. The app has active aliases for `@modules`, `@shared`, and `@layouts` in Vite and TypeScript config, but `src/shared/` and `src/layouts/` currently contain only `.gitkeep`. Feature code lives under `src/modules/{auth,workspace,settings,media,dashboard,publishing}`, while remaining root folders still hold cross-cutting layout, sidebar, composables, utilities, i18n, and the residual `views/VerifyEmailView.vue`.

### Affected Areas
- `apps/web/app/src/components/layout/AppShell.vue` — primary authenticated shell; should move to `layouts/` and update `App.vue` plus tests/mocks.
- `apps/web/app/src/components/layout/AppHeader.vue` — shell header used by `AppShell`; likely layout-owned.
- `apps/web/app/src/components/sidebar/*` — shell-specific sidebar components; best moved with layout rather than treated as generic shared components.
- `apps/web/app/src/components/UploadProgressToast.vue` — global app shell toast; candidate for `shared/components` if used outside layout, otherwise layout-owned.
- `apps/web/app/src/components/WorkspaceAvatar.vue` — reused by shell/sidebar and settings; candidate for `shared/components`.
- `apps/web/app/src/components/ThemeToggle.vue` — global UI utility component; candidate for `shared/components` or retain under `components/ui` only if shadcn-style.
- `apps/web/app/src/components/SocialProviderIcon.vue` — provider UI primitive; candidate for `shared/components` if still used.
- `apps/web/app/src/views/VerifyEmailView.vue` — residual root view imported by router; should move to `@modules/auth/presentation` because it is auth flow behavior.
- `apps/web/app/src/composables/useCalendarUrl.ts` — scheduler/publishing route-state composable used by scheduler and shell; candidate for `@modules/publishing/application` or `@modules/publishing/presentation` with careful shell dependency.
- `apps/web/app/src/composables/useQueuedCounts.ts` — publishing queue aggregation used by shell; candidate for publishing application shared export if domain-specific.
- `apps/web/app/src/composables/useLinkedInCallback.ts` — auth/integration callback flow used only by auth callback view; should move into auth module.
- `apps/web/app/src/composables/useFileHash.ts` — media upload hashing used by media service; should move into media module unless intentionally shared upload infrastructure.
- `apps/web/app/src/composables/useFocusTrap.ts` — generic UI accessibility behavior used by publishing modals; candidate for `shared/composables`.
- `apps/web/app/src/composables/usePopoverDismissal.ts` — generic UI behavior used by sidebar/account components; candidate for `shared/composables`.
- `apps/web/app/src/composables/useConnectMessage.ts` — shell/sidebar transient connection message; likely layout-owned unless reused elsewhere.
- `apps/web/app/src/lib/utils.ts` — shadcn `cn` helper imported by many UI components; should move to `shared/lib` only if all shadcn import paths are updated consistently, or remain as compatibility boundary until UI migration is explicit.
- `apps/web/app/src/lib/formatters.ts`, `string-utils.ts`, `provider-styles.ts`, `sse.ts`, `validation/schemas.ts` — cross-module utilities currently imported from modules and components; strong candidates for `shared/` with stable subfolders.
- `apps/web/app/src/i18n/index.ts` — cross-cutting app i18n plugin; candidate for `shared/i18n` with `main.ts` import updated.
- `apps/web/app/src/router/index.ts` — imports residual auth view and route components; must be updated but should remain app-level orchestration.
- `apps/web/app/src/components/ui/**` — shadcn-vue primitives remain intentionally under `components/ui`; do not remove `components/` unless UI primitives are included in a separate migration.

### Approaches
1. **Compatibility-first cleanup** — Move only non-UI root leftovers into `layouts/` and `shared/`, keep shadcn primitives under `components/ui`, and leave `@/components/ui/*` import compatibility intact.
   - Pros: Smaller blast radius; aligns with existing shadcn-vue convention; less chance of breaking generated component imports; satisfies layout/shared cleanup without forcing a UI primitive migration.
   - Cons: `components/` cannot be fully removed because `components/ui` remains valid; DoD wording about removing empty `components/` must be interpreted as removing only emptied legacy subfolders.
   - Effort: Medium

2. **Full root components migration** — Move layout/sidebar/global components plus `components/ui` into `shared/components` or equivalent and remove root `components/` entirely.
   - Pros: Strongest modularization purity; root folder cleanup is complete.
   - Cons: Very high import churn across many shadcn components/tests; may fight shadcn-vue default structure and future CLI generation; larger regression surface for little product value.
   - Effort: High

3. **Domain-first composable split with shared utility migration** — Pair approach 1 with strict composable evaluation: domain-specific composables move into owning modules, generic UI/runtime utilities move under `shared/`.
   - Pros: Matches issue scope exactly; improves ownership boundaries; reduces root `composables/`, `lib/`, `views/`, and `i18n/` leftovers while preserving UI primitive stability.
   - Cons: Requires many import/test mock updates; shell may depend on publishing-owned composables unless exports are deliberate.
   - Effort: Medium

### Recommendation
Use approach 3 with compatibility-first treatment for shadcn UI primitives. Move `AppShell`, `AppHeader`, and sidebar shell parts to `@layouts`; move generic utilities/composables/i18n to `@shared`; move auth/media/publishing-specific composables or views into their owning modules; keep `components/ui/**` in place unless a later dedicated UI-primitives migration is approved. This gives the cleanup Phase 5 needs without turning it into an unnecessary full UI rewrite.

### Risks
- Existing aliases for `@shared` and `@layouts` point to `.gitkeep` placeholders; moving files must update import paths and tests/mocks consistently.
- `components/ui/**` imports `@/lib/utils` in many generated shadcn files; moving `utils.ts` requires broad import changes or a temporary compatibility shim.
- `AppShell` currently depends on auth, workspace, publishing, calendar URL, queued counts, sidebar components, i18n, and global toast; relocating it is high-churn even without behavior change.
- `useCalendarUrl` is shared between scheduler routes and shell sidebar behavior; placing it only inside publishing creates cross-module dependency from layout to publishing, which should be explicit and stable.
- `just frontend-test`/`just frontend-lint` may target broader frontend scopes per repo docs; prior modularization memory notes used `pnpm --filter app test:run` and `pnpm --filter app lint` for app-focused verification, so proposal should clarify exact verification commands.
- Manual navigation flow includes multiple feature modules; import mistakes may only surface in dev server/browser navigation, not unit tests.

### Ready for Proposal
Yes — propose a non-behavioral relocation change bounded to `apps/web/app/src`, with explicit file placement rules, import-only implementation tasks, and verification via app tests/lint plus dev-server navigation.
