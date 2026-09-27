# LinkedIn avatar refresh restore — Plan

**Ruta:** Direct inline
**Goal:** Reponer en `main` el refresh de avatares LinkedIn eliminado en `df5de9df`, adaptado a las interfaces actuales sin `listActiveByWorkspace`.
**Scope:** Backend publishing + store app + preview fallback + BDD. No tocar `threads`. No commit ni push.
**Base:** `df5de9df` en worktree `avatar`. Origen: `534f2e75`.

## Tareas

- [ ] RPI-001 Backend dominio y aplicación
  - `domain/PublishingProviderContracts.kt`: re-añadir `LinkedInAvatarFetcher`
  - `application/PublishingApi.kt`: re-añadir `RefreshChannelAvatarsCommand` y `RefreshChannelAvatarsResult`
  - `application/RefreshChannelAvatarsHandler.kt`: crear adaptado con `ConnectedSocialChannelReadRepository.listByWorkspace` más `SocialAccountRepository.findByWorkspaceAndId`, filtro `LINKEDIN` y `ACTIVE`, `RefreshAwareCredentialResolver`, `LinkedInAvatarFetcher`, `upsert` y evento `CONNECTED_CHANNEL_UPDATED`
- [ ] RPI-002 Backend infraestructura y HTTP
  - `infrastructure/linkedin/LinkedInAvatarFetcherImpl.kt`: crear `GET ${apiBaseUrl}/v2/userinfo` con `Bearer`, `null` en no-2xx o transporte incierto, misma regla HTTPS
  - `infrastructure/linkedin/LinkedInPublishingWiring.kt`: re-añadir import y `@Bean linkedInAvatarFetcher`, mantener `sanitizeAvatarUrl` actual
  - `infrastructure/http/PublishingControllers.kt`: re-añadir `POST /refresh-avatars` versión 1
  - Sin cambios en `PublishingRepositories.kt` ni SQL nuevo
- [ ] RPI-003 Frontend app
  - `infrastructure/publishing.store.ts`: re-añadir `RefreshChannelAvatarsResponse` y llamada `POST` tras `fetchChannels` con recarga única si `refreshed > 0`, silencioso si falla
  - `presentation/components/composer/LinkedInPostPreview.vue`: re-añadir `avatarFailed`, `showAvatar`, `onAvatarError`, `watch` y `testids`
- [ ] RPI-004 Tests
  - `RefreshChannelAvatarsHandlerTest.kt`: adaptar fakes a puertos actuales
  - `LinkedInAvatarFetcherImplTest.kt`: 200 con picture, 200 sin picture, 401, transporte incierto, aserción Bearer y URL
  - `PublishingControllersTest.kt`: dispatch del `POST`
  - `publishing.store.test.ts`: recarga única, sin recarga en cero, conserva canales si falla
  - `LinkedInPostPreview.test.ts`: imagen, fallback a iniciales, reintento al cambiar URL
  - `publishing-channels.feature` y `PublishingBddSteps.kt`: escenario de cero canales
- [ ] RPI-005 Verificación
  - `just backend-test-fast`, `just backend-lint`, `just backend-bdd-fast`
  - `pnpm --filter app lint`, `type-check`, `test:run` tocados
  - `git diff --check` y revisión de supresiones

## Evidencia

- Origen: `534f2e75 fix(publishing): graceful LinkedIn avatar degradation + refresh endpoint`
- Eliminación: `43c9e200` dentro de PR #1193, merge `df5de9df`
- Contrato vigente: `openspec/specs/publishing/spec.md:1048-1103`
- Producto: `apps/web/PRODUCT.md:16-29`
- Fallos: `docs/publishing-failure-modes.md:8-21`

## Estado

- `Ready` — implementado y verificado en worktree `avatar`, sin commit.
- Backend: `just backend-test-fast` PASS, `just backend-lint` PASS, `just backend-check` PASS.
- Nuevos tests: `RefreshChannelAvatarsHandlerTest` 7/7, `LinkedInAvatarFetcherImplTest` 5/5, `PublishingControllersTest` 25/25.
- BDD: `just backend-bdd-fast` BLOCKED ambiental, sin Docker válido en este entorno. Pendiente en CI.
- App: `pnpm --filter app lint` PASS, `type-check` PASS, `test:run` tocados 103/103, `just app-build` PASS.
- `git diff --check` PASS, sin supresiones nuevas.
