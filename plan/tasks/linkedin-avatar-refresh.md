# LinkedIn avatar refresh (C) — Plan

**Ruta:** Direct inline
**Goal:** El avatar de LinkedIn no vuelve a romperse: el preview degrada a iniciales si la imagen falla y el backend refresca la URL firmada al cargar canales.
**Architecture:** Frontend espeja el patrón `avatarLoadFailed` de `ComposerChannelSelector`. Backend añade comando `RefreshChannelAvatarsCommand` (CQRS honesto: el GET de listar sigue puro) con puerto `LinkedInAvatarFetcher`, `upsert` existente por claves naturales, evento `CONNECTED_CHANNEL_UPDATED` al cambiar, fallos aislados por cuenta.
**Tech Stack:** Vue 3 + Vitest, Kotlin + coroutines, R2DBC, Cucumber BDD.

---

## Tarea A: fallback en LinkedInPostPreview

**Archivos:**

- Modificar: `apps/web/app/src/modules/publishing/presentation/components/composer/LinkedInPostPreview.vue`
- Modificar: `.../composer/LinkedInPostPreview.test.ts`

- [ ] Paso 1: Test que falla — monta con `authorAvatarUrl: 'https://media.licdn.com/x.jpg'`, dispara `error` en `img[data-testid="linkedin-preview-avatar"]`, espera `div[data-testid="linkedin-preview-avatar-fallback"]` con iniciales y que el `img` desaparezca. Segundo test: al cambiar `authorAvatarUrl` tras el error, el `img` vuelve.
- [ ] Paso 2: Corre el test, espera FAIL.
- [ ] Paso 3: Implementa `avatarFailed = ref(false)`, `showAvatar = computed(() => !!preview.authorAvatarUrl && !avatarFailed.value)`, `onAvatarError` que lo pone en true, `watch(() => preview.authorAvatarUrl, () => avatarFailed.value = false)`, `@error` en el `img`, testids en `img` y en el `div` de iniciales.
- [ ] Paso 4: Corre `pnpm --filter app test:run src/modules/publishing/presentation/components/composer/LinkedInPostPreview.test.ts`, espera PASS.

## Tarea B: puerto + comando + resultado

**Archivos:**

- Modificar: `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingProviderContracts.kt`
- Modificar: `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt`
- Modificar: `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingRepositories.kt`

- [ ] Paso 1: Añade `fun interface LinkedInAvatarFetcher { suspend fun fetchAvatarUrl(accessToken: String): String? }` con contrato: null si el provider no devuelve foto o el transporte falla. Sin throws nuevos.
- [ ] Paso 2: Añade `RefreshChannelAvatarsCommand : CommandWithResult<RefreshChannelAvatarsResult>` y `RefreshChannelAvatarsResult(refreshedAccountIds: List<String>, skippedAccountIds: List<String>, failedAccountIds: List<String>)` con derivados `refreshed/skipped/failed: Int`.
- [ ] Paso 3: Añade `suspend fun listActiveByWorkspace(workspaceId: String): List<SocialAccount>` a `SocialAccountRepository`. Actualiza los fakes de tests que implementen la interfaz (buscar `SocialAccountRepository` en `src/test`).
- [ ] Paso 4: Checkpoint de revisión.

## Tarea C: infraestructura avatar fetcher

**Archivos:**

- Crear: `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/LinkedInAvatarFetcherImpl.kt`
- Modificar: `LinkedInPublishingWiring.kt` (reusar fetcher en el complete-connection + bean)
- Test: `LinkedInAvatarFetcherImplTest.kt`

- [ ] Paso 1: Tests que fallan: 200 con `picture` → URL; 200 sin `picture` → null; 401 → null; timeout/IO → null. Fake de `LinkedInHttpTransport`.
- [ ] Paso 2: Implementa con `httpTransport.send` GET a `${apiBaseUrl}/v2/userinfo`, parsea `LinkedInUserInfoResponse`, reutiliza la misma regla `sanitizeAvatarUrl` (muévela aquí). Captura solo `IOException` (+ `HttpTimeoutException` ya es subtipo) y no-2xx → null con log. Sin `catch (e: Exception)`.
- [ ] Paso 3: En el wiring del complete-connection sustituye su bloque userinfo-inline por el fetcher donde aplique sin cambiar comportamiento (mismo `displayName()`, misma sanitización). Registra `@Bean fun linkedInAvatarFetcher(...)`.
- [ ] Paso 4: Corre tests + Detekt del módulo.

## Tarea D: handler + endpoint + BDD

**Archivos:**

- Crear: `.../publishing/application/RefreshChannelAvatarsHandler.kt`
- Test: `RefreshChannelAvatarsHandlerTest.kt` (fakes, sin Spring)
- Modificar: `.../infrastructure/http/PublishingControllers.kt`
- Modificar: `R2dbcPublishingConnectionRepositories.kt` (impl `listActiveByWorkspace`)
- BDD: `src/test/resources/features/publishing-channels.feature` + glue existente de publishing

- [ ] Paso 1: Tests que fallan: avatar cambiado → upsert + id en refreshed + evento publicado; avatar igual → skipped sin upsert; `ReconnectRequiredException` → skipped; fetcher null → skipped; segunda cuenta falla igual tras fallo de la primera (aislamiento).
- [ ] Paso 2: Implementa `@Service RefreshChannelAvatarsHandler(deps: ResourceContextProvider, SocialAccountRepository, RefreshAwareCredentialResolver, LinkedInAvatarFetcher, SocialAccountRepository.upsert, ChannelEventPublisher, Clock)`. Solo cuentas `ACTIVE` + `LINKEDIN`. Compara `trim()`; upsert con `copy(avatarUrl = fresh, displayName = freshName?)` — refresca displayName también con el `displayName()` del userinfo. Publica `ChannelEvent(CONNECTED_CHANNEL_UPDATED, ...)` solo si hubo cambio. Captura solo `ReconnectRequiredException` → skipped; el fetcher ya devuelve null ante fallos de transporte.
- [ ] Paso 3: Endpoint `POST /api/publishing/channels/refresh-avatars` versión 1 con `@Operation`, delega a `mediator.send(RefreshChannelAvatarsCommand())`.
- [ ] Paso 4: Escenario BDD fast: conecta canal (patrón existente), envejece `avatar_url`, llama al endpoint con WireMock userinfo si el proyecto ya lo usa para LinkedIn (verificar patrón en glue de publishing; si no hay seam, documentarlo y no forzar test malo).
- [ ] Paso 5: Corre `just backend-test-fast`, `just backend-lint`, `just backend-bdd-fast`.

## Tarea E: store llama al refresh

**Archivos:**

- Modificar: `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts`
- Modificar: `publishing.store.test.ts`

- [ ] Paso 1: Tests que fallan: `fetchChannels` con éxito dispara `POST /api/publishing/channels/refresh-avatars`; si `refreshed > 0` recarga canales una vez; si `refreshed == 0` no recarga; si el POST falla, los canales ya cargados se conservan y no se lanza.
- [ ] Paso 2: Implementa `refreshChannelAvatars()` con guarda `refreshInFlight` (sin bucles: máximo una recarga derivada). Tipos `RefreshChannelAvatarsResponse` junto a `ConnectedChannelsResponse`.
- [ ] Paso 3: Corre `pnpm --filter app test:run src/modules/publishing/infrastructure/publishing.store.test.ts`, `type-check`, `lint` del filtro app.

## Tarea F: verificación final

- [ ] Paso 1: Diff mínimo, sin secretos, sin supresiones, sin cambios no relacionados.
- [ ] Paso 2: Puertas: backend (`backend-check` o `backend-test-fast` + `backend-lint` + `backend-bdd-fast`) y app (`lint`, `type-check`, `test:run` tocados) con Pass/Fail exactos.
- [ ] Paso 3: Verificación manual en dev: composer con avatar caducado muestra iniciales; tras refresh vuelve la foto.

---

## Evidencia (causa raíz)

- `social_accounts.avatar_url` de 2026-08-30 con `e=1789603200` vencido frente a ahora; `curl` directo a licdn → 403; vía `/api/media/proxy` → 403. Sin redirects implicados.
- URL capturada una vez en `LinkedInPublishingWiring.kt:159` (`/v2/userinfo picture`), refrescada solo al reconectar (`avatar_url = EXCLUDED.avatar_url`).
- `LinkedInPostPreview.vue:62` sin `@error`; `ComposerChannelSelector.vue:67` sí lo tiene.
- `followRedirect(false)` (#1149) es inocente aquí pero el proxy sigue estricto por diseño (allowlist + jpeg/png/gif).

## Estado

- `Ready` — implementado y verificado. Puertas: backend-test-fast PASS, backend-lint PASS, backend-bdd-fast PASS (10/10 escenarios channels), R2dbc postgres 15/15, app lint/type-check PASS, vitest tocados 123+103 PASS. Sin commit (no autorizado).
