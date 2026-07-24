# Tasks: Dynamic Channel Provider Catalog

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 700–1,000 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 backend contract/policy; PR 2 SPA catalog; PR 3 migration/verification |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main |

Decision needed before apply: No — approved stacked-to-main
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Resolved backend catalog and OAuth guard | PR 1 | Base main; unit, HTTP, BDD tests |
| 2 | Typed SPA catalog presentation | PR 2 | Base PR 1; Vitest coverage |
| 3 | Remove static UI and validate integration | PR 3 | Base PR 2; focused regression checks |

## Phase 1: Backend Contract and Policy

- [x] 1.1 RED: add policy/handler tests for `AVAILABLE`, `LOCKED` reasons, `HIDDEN` precedence, permissive entitlement/capacity seams, and existing-channel preservation.
- [x] 1.2 GREEN: create `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingProviderCatalog.kt` with catalog models and policy ports.
- [x] 1.3 GREEN: create `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingProviderCatalogHandlers.kt` with product ordering and workspace resolution.
- [x] 1.4 REFACTOR: wire global and permissive adapters in `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/PublishingApplicationConfiguration.kt` without plan inference.

## Phase 2: Backend HTTP and OAuth Enforcement

- [x] 2.1 RED: add WebFlux tests for ordered, non-hidden `GET /api/publishing/channels/providers` items, null limits, counts, and no plan/secrets.
- [x] 2.2 GREEN: update `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt` to map the resolved public catalog.
- [x] 2.3 RED: add handler tests proving stale `AVAILABLE` catalog data cannot initiate OAuth after `LOCKED` or `HIDDEN` policy.
- [x] 2.4 GREEN: update `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingConnectionHandlers.kt` to guard LinkedIn initiation before URL/state generation.
- [x] 2.5 BDD: extend `server/smp/src/test/resources/features/publishing-channels.feature` and glue with tagged catalog, headers, workspace isolation, capacity, OAuth denial, and existing auth/workspace scenarios.

## Phase 3: Typed SPA Catalog

- [x] 3.1 RED: add Vitest coverage for discriminated catalog types, LinkedIn label, known icons, and neutral unknown-provider fallback.
- [x] 3.2 GREEN: create `apps/web/app/src/shared/lib/provider-presentation.ts` and update `apps/web/app/src/shared/components/SocialProviderIcon.vue` to centralize typed labels, icons, actions, and fallback.
- [x] 3.3 RED: add store tests for catalog/channel parallel reloads, workspace switches, errors, and stale entries becoming non-actionable.
- [x] 3.4 GREEN: update `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts` and `apps/web/app/src/layouts/AppShell.vue` to load both workspace sources safely.

## Phase 4: Sidebar Migration and Verification

- [x] 4.1 RED: add `SidebarConnectSection` tests for available CTA, locked non-actionable reason, hidden omission, and absent static/coming-soon controls.
- [x] 4.2 GREEN: update `apps/web/app/src/layouts/sidebar/SidebarConnectSection.vue` to render catalog states and remove fake static provider UI.
- [x] 4.3 REFACTOR: remove obsolete static connect mappings/assets and ensure connected-channel fallbacks consume `provider-presentation.ts`.
- [x] 4.4 Run focused backend unit/WebFlux/BDD and frontend Vitest suites; verify every catalog, OAuth, and workspace-switch scenario above.
