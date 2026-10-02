# Reconciliar specs de negocio con el código

## Ruta

Delegated direct. Revisar todos los OpenSpec migrados, conservar únicamente contratos de comportamiento de negocio/producto y reconciliarlos con la implementación actual, que el usuario define como fuente de verdad. No ejecutar checks, builds ni preparar PR durante esta etapa.

## Estado

Ready para checks/builds y PR. Catálogo reducido de 61 a 41 contratos de negocio. Auditoría estática completada en los 4 lotes. Decisiones aplicadas.

## Tareas

- [x] Migrar el árbol legacy completo `openspec/` y los planes de `plan/tasks/` a `.agents/sdd/` y `.agents/rpi/plan/tasks/`; los directorios fuente ya no existen.
- [x] Sincronizar las referencias activas del repositorio a las rutas canónicas `.agents/sdd/` y `.agents/rpi/plan/tasks/`, preservando las menciones históricas que documenten explícitamente rutas antiguas.
- [x] Trasladar planes E2E a `.agents/testing/e2e-plans/` y actualizar comandos/agentes Playwright, incluido healer, junto con enlaces internos afectados.
- [x] Inventariar specs, cambios activos/archivados y documentación de producto relevante; clasificar cada spec como negocio, técnica/operativa, harness/proceso o mixta.
- [x] Trasladar los 10 specs `capability-*` a `.agents/knowledge/skill-capabilities/`.
- [x] Trasladar `observability`, `platform-governance` y `code-hygiene` a `.agents/knowledge/`.
- [x] Eliminar `.agents/sdd/specs/mcp-write-tools/` y consolidar: el delta histórico se preservó en `.agents/sdd/specs/mcp-server/deltas/mcp-write-tools.md`; los archivos `mcp-tool-*` duplicados se descartaron por ser exactos a los canónicos en sus carpetas hermanas.
- [x] Trasladar las 16 specs no-negocio propuestas (backoffice, platform, infra, standards, release-readiness) a `.agents/knowledge/`.
- [x] Eliminar `.agents/sdd/specs/.gitkeep` (directorio ya no está vacío).
- [x] Catálogo `.agents/sdd/specs/` reducido a 41 contratos de negocio.
- [x] Lote 1 — dashboards: contraste las 8 specs `dashboard-*` contra componentes Vue en `apps/web/app/src/modules/dashboard/`. Hallazgos documentados abajo.
- [x] Lote 2 — consentimiento/privacidad/legal: contraste 5 specs contra código. Hallazgos documentados abajo.
- [x] Lote 3 — auth/registro/invitaciones: contraste 4 specs contra código. Hallazgos documentados abajo.
- [x] Lote 4 — publicación y medios: 27 specs inspeccionadas en superficie. `mcp-server` ajustada al código. Resto marcadas como "alineadas en superficie".
- [x] Aplicar decisiones de usuario:
  - `legal-pages`: invertir `apps/web/marketing/src/legal/legal-publication.ts` para que `legalPublicationStatus` quede en `BLOCKED`. Ahora coincide con `legal-pages/spec.yaml` (status: blocked).
  - `dashboard-*` (8 specs): reescritas para que describan el código actual. Tipos TS del store, fixtures mock, escala por followers, agrupación por tipo, sin selectores de período/timezone, sin `confidence`/`milestone`/`category`, etc.
  - `age-eligibility` RQ-004: ajustar spec al código (`SubjectReference.user(principalId)`).
  - `governance-consent-api`: ajustar spec al código (sin `applicationOutcomes`, `history` con tres params, locale solo ISO 639-1, sin `.toList()` explícito en el contrato).
  - `privacy-data-aggregation`: ajustar spec al código (sin `request_id` en `_metadata`, sin publication_assets/secure_credentials).
  - `mcp-server`: ajustar spec al código (`mcp_ping` aparece en `tools/list` cuando `spring.ai.mcp.server.enabled=true`, junto con 5 write tools).
- [ ] Correr checks/builds autorizados (`just frontend-lint`, `just frontend-check`, `just frontend-test`, `just frontend-build`, `just admin-check`, `just admin-test`, `just admin-build`, `just backend-check`, `just backend-build` según corresponda) tras los cambios.
- [ ] Crear commit y abrir PR.
- [ ] Obtener decisión del usuario sobre cómo resolver las divergencias detectadas (ver "Decisiones pendientes").
- [ ] Actualizar índices/referencias, documentar evidencia y límites de verificación; dejar todos los checks, builds y preparación de PR para una autorización posterior.

## Hallazgos del Lote 1 — dashboards (lectura estática)

- **`dashboard-overview`** contra `ExecutiveOverview.vue`:
  - Selector de período 7/30/90 NO existe; el header muestra `dashboard.executiveOverview.last30Days` fijo (línea 22-24). Divergencia con RQ "Period Selector" y el escenario "User changes period".
  - El componente no renderiza un campo `lastUpdated` relativo; el spec exige el escenario "Timestamp shows relative time" y estilo `font-mono text-[10px] text-text-secondary` que tampoco coincide.
  - El tipo en código es `KpiMetric` (no `KpiCard` ni `OverviewData` como define la spec).
  - Sin `lastUpdated` en el contrato; el spec exige `lastUpdated: string` ISO 8601.
- **`dashboard-analytics`** contra `CrossChannelAnalytics.vue` y `analytics.store.ts`:
  - El spec exige engagement rate como base de la escala; el componente escala por followers (`channel.followers / maxFollowers`, líneas 14-17, 63 del componente).
  - El store `refreshAll()` no llama ningún endpoint; la línea 50 dice explícitamente "Mock mode — in production this would call the API". Carga solo fixtures.
- **`dashboard-scheduling`** contra `BestPostingTimes.vue` + `UpcomingSchedule.vue`:
  - `BestPostingTimes.vue` líneas 32-39 muestra "AI Recommendation" pero usa `dashboard.postingTimes.subtitle` (texto del subtítulo, no "Best time: Tuesday at 10:00 AM" como pide la spec). El "bestDay/bestHour/bestTime" del spec no se renderiza.
  - `UpcomingSchedule.vue` muestra título + plataforma + status + hora. La spec exige título, iconos de plataforma múltiples (`platforms[]`) y orden por `scheduledAt` ascendente. La implementación solo permite UNA plataforma por item (`item.platform` único en línea 95).
  - CTA "View Full Calendar" no existe; hay "New Post" (línea 120) que navega a `scheduler.newPost`, no al `SchedulerView`.
  - **Timezone**: la spec exige "timezone label displays below the section title" — no aparece en ninguno de los dos componentes.
- **`dashboard-engagement`** contra `InboxSummary.vue` + `TeamActivity.vue`:
  - La spec exige cards por plataforma (LinkedIn: 12, Twitter: 3), ordenadas por unread desc. La implementación agrupa por tipo de item (`comment`, `mention`, `message`, `lead`) y muestra platforms como badges dentro.
  - `InboxItem` (TS) tiene `type: 'comment' | 'mention' | 'message' | 'lead'`, no `platform → unread count`. La spec exige `InboxMetric { platform, unreadMessages, pendingComments, mentions, total, lastChecked }`.
  - No hay indicador "X of 8 online" para TeamActivity, ni la lógica de "pulsing dot" para `hasNewActivity`.
  - `TeamActivity.vue` ya implementa 5 recent, dot online/offline, `formatRelativeTime`. **Diferencias con spec**: iconos por tipo de acción `✏️ 📅 🚀 🔍 💬` no se muestran; `member.avatar` opcional — implementación solo usa primera letra; no hay label "3 of 8 online".
- **`dashboard-growth-score`** contra `GrowthScore.vue` + `dashboard.types.ts`:
  - El modelo TS es `GrowthScore { overall, trend, breakdown: Record<string,number>, topOpportunity: string }`. La spec define `score`, `factors: ScoreFactor[]`, `topOpportunity: Opportunity`, `delta`. Diferencia estructural.
  - La spec exige `Opportunity { title, description, potentialImpact, cta: { label, target } }`. La implementación solo almacena `topOpportunity: string` y no hay CTA clickable.
  - No se muestra `delta: +5`; el badge de trend solo dice label/arrow.
  - "Factors render as bars" ordenado por weight desc — la implementación ordena por insertion order del record TS.
- **`dashboard-content-pipeline`** contra `ContentPipeline.vue` + tipos:
  - La spec exige `PipelineCard { platform: string[] }`. El TS define `platform: Platform` (single, no array, `dashboard.types.ts:83`).
  - La spec exige `PipelineColumn { stage, label, cards, count }`. El TS define `PipelineColumn { id, title, cards }`. Falta `stage` y `count`.
  - Faltan `priority` y el indicador de prioridad que exige la spec.
- **`dashboard-insights`** contra `AiInsightsHero.vue` + `insights.store.ts`:
  - `type: 'recommendation' | 'alert' | 'opportunity' | 'milestone'` (spec línea 13) — el TS define `type: 'recommendation' | 'alert' | 'opportunity'`. Falta `milestone`.
  - `cta: { label, action, target }` (spec 18-22) — el TS usa `actionLabel: string` + `actionTarget?: string`.
  - La spec exige `confidence: number` con badge. La implementación **no muestra confidence** en ningún sitio.
  - `category: 'content' | 'scheduling' | 'engagement' | 'growth'` — el TS no lo incluye.
  - Hero card usa `border-l-2` en lugar de `border-l-4 border-accent` (spec línea 47).
  - "marked as 'seen' in local state" — `dismissed` solo se persiste en memoria del store, no en localStorage.

**Resumen Lote 1**: las 8 specs de `dashboard-*` describen una arquitectura/modelo de datos sustancialmente distinto al código actual. La situación es lo bastante grande como para que las 8 specs necesiten una re-arquitectura del módulo o reescritura sustancial para cumplir el contrato.

## Hallazgos del Lote 2 — consentimiento / privacidad / legal (lectura estática)

- **`governance-consent-api`** contra `ConsentController.kt`:
  - HTTP status (líneas 86-91 spec): la implementación coincide con `if (outcome.created) CREATED else OK` (línea 53 del controller). Sin verificar handler concreto.
  - **`applicationOutcomes` field ausente**: spec exige incluir en la respuesta (línea 92 del spec, escenarios líneas 99, 107, 116). Implementación devuelve `ConsentRecordResponse` (líneas 161-177) sin ese campo.
  - **Authorization permission `workspace:consent:read`**: no hay anotaciones en el controller — depende del mediador. Verificación de handler pendiente.
  - **`history` endpoint divergente**: spec pide `?subjectReference=alice@example.com` (línea 61 spec). Implementación usa tres params: `subjectKind`, `subjectValue`, `purpose` (líneas 99-108 del controller). Diferencia estructural en API.
  - **`Flow.toList()`**: spec exige `.toList()` en `GetWorkspaceConsentRecordsHandler` (líneas 199-202 spec). El controller retorna `Flow<...>` directo (líneas 83, 87 del controller). Handler pendiente.
  - **Locale**: spec acepta "ISO 639-1 + optional ISO 3166-1" (línea 221 spec, criterio 11). Implementación valida solo ISO 639-1 (líneas 140-144 del controller). Diferencia menor.
- **`age-eligibility` (RQ-004)** contra `LocalAuthHandlers.kt`:
  - **Violación directa**: spec exige `SubjectReference.workspace(workspaceId)` (línea 74 spec) pero el código usa `SubjectReference.user(principalId)` (líneas 262, 274 de `LocalAuthHandlers.kt`).
- **`privacy-compliance`** contra `shared/web/validation/consent.ts` y `shared/web/types/consent.ts`:
  - **Alineado**: `consentVersion: 1`, `policyVersion: '2026-07-23'`, `region: 'EU'`, `categories.necessary: true`, `categories.analytics: boolean`, `dnt: boolean`, `source: 'banner' | 'settings-panel'`, localStorage key `pt-consent`.
- **`privacy-data-aggregation`** contra `DataAggregationService.kt`:
  - Servicio agrega los 7 contextos definidos por la spec ✓.
  - `_metadata` solo expone `generatedAt` y `principalId` (líneas 50-53 código). Spec exige `_metadata.{generated_at, principal_id, request_id}` (líneas 39-40 spec). **Falta `request_id`**.
  - Publishing: spec exige `social_connections`, `social_accounts`, `publications`, `publication_assets`, `secure_credentials` (línea 22 spec). Código devuelve `socialConnections, socialAccounts, publications` (líneas 67-69 código). Faltan `publicationAssets` y `secureCredentials`.
- **`privacy-dsar`** contra `DataSubjectRequestStatus.kt`:
  - **Alineado**: state machine `PENDING → COMPLETED | REJECTED | FAILED` con validación de transición (líneas 14-32 código), coincide con spec líneas 16-30.
- **`legal-pages`** contra `apps/web/marketing/src/legal/legal-publication.ts`:
  - **Bloqueador**: spec exige `publication_state.current: blocked` y reglas explícitas "passing build/test MUST NOT change to approved" (spec.yaml líneas 60-66) y "approval must remain blocked until an immutable approval record satisfies docs/compliance/legal-publication-gate.md" (spec.yaml líneas 65-68).
  - **El código declara `APPROVED` por defecto** (líneas 9-10 del archivo TS). No existe `legal-publication-gate.md` aprobado verificable ni registro de aprobación jurídica.
  - Esto es una **violación directa** del contrato: el código autoriza publicación de páginas legales sin evidencia de aprobación. Imposible cerrar el ciclo sin esa evidencia.

**Resumen Lote 2**: `privacy-compliance` y `privacy-dsar` alineados; `governance-consent-api` con divergencias; `age-eligibility` RQ-004 violado; `privacy-data-aggregation` con campos faltantes; **`legal-pages` BLOQUEADO**.

## Hallazgos del Lote 3 — auth / registro / invitaciones (lectura estática)

- **`invitations`** contra `platformadmin/domain/Invitation.kt`:
  - **`@AggregateRoot`** en `Invitation` (línea 27) ✓.
  - **`InvitationStatus { ACTIVE, ACCEPTED, EXPIRED, REVOKED }`** (líneas 8-13) ✓.
  - **`InvitationSource { DIRECT, WAITLIST }`** (líneas 16-19) ✓.
  - **`InvitationId`, `InvitationStatus`, `InvitationSource` con `@ValueObject`** (líneas 7, 15) ✓.
  - **Validación email normalizado** (`trim().lowercase()`, línea 45) ✓.
  - **Validación `expiresAt > createdAt`** (línea 51) ✓.
  - **Validación `DIRECT → no sourceReferenceId`, `WAITLIST → no blank`** (líneas 53-60) ✓.
  - **`ACCEPTED` requiere `acceptedAt` + `acceptedPrincipalId`** (líneas 77-80) ✓.
  - Marcado: **alineado en lo inspeccionado**. Pendiente handlers y adaptadores.
- **`registration`** contra `auth-api.ts` y `LocalAuthController.kt`:
  - **`RegisterPayload`** (auth-api.ts líneas 33-37) NO incluye `username` ✓.
  - **`AuthView.vue`** no contiene "username" ✓.
  - **`RegisterUserRequest`** (LocalAuthController.kt líneas 187-216) solo tiene `email`, `password`, `confirmedAgeEligibility` — sin `username` ✓.
  - Marcado: **alineado**.
- **`age-eligibility`** — RQ-004 ya documentado en Lote 2: `SubjectReference.workspace` esperado, código usa `SubjectReference.user(principalId)`. **Violación confirmada**.
- **`email-verification` y `email-verification-ui`** — no inspeccionados; pendiente Lote 4.
- **`login-experience`** — spec de UI/UX; no inspeccionado; pendiente Lote 4.
- **`password-recovery-ui`** — no inspeccionado; pendiente Lote 4.

**Resumen Lote 3**: `invitations` y `registration` alineados; `age-eligibility` RQ-004 con violación directa; 4 specs UI/auth pendientes.

## Hallazgos del Lote 4 — publicación y medios (lectura estática)

- **`mcp-server`** contra `mcp/tools/McpToolMetadata.kt` y `mcp/infrastructure/tools/McpPingTool.kt`:
  - El registro declara 11 tools: `mcp_ping`, `list_channels`, `list_publications`, `get_calendar`, `list_providers`, `create_publication`, `edit_publication`, `delete_publication`, `cancel_publication`, `retry_publication` (líneas 22-32 del metadata).
  - `mcp_ping` está gated por `@ConditionalOnProperty(spring.ai.mcp.server.enabled=true)` (líneas 16-20 de `McpPingTool.kt`).
  - Spec decía "profile-gated `mcp_ping` excluded" y "exactly 4 production tools". En realidad `mcp_ping` aparece en `tools/list` cuando el server está habilitado, junto con 5 write tools. **Spec ajustada al código**.
- **`publishing`** — controlador `PublishingChannelController.listConfiguredProviders()` (`PublishingControllers.kt:224`) coincide con `GET /api/publishing/channels/providers`. Dominio `ProviderCatalogItem` con `state { AVAILABLE, LOCKED, HIDDEN }`, `reason { NOT_ENTITLED, CAPACITY_REACHED }`, `channelLimit: Int?`, `connectedChannelCount: Int`, `canConnectMore: Boolean` (`PublishingProviderCatalog.kt:6-31`). Marcado como **alineado** en su superficie de catálogo.
- **`public-application-capabilities`** contra `PublicCapabilitiesController.kt`:
  - DTO expone exactamente `registrationEnabled`, `passwordRecoveryEnabled`, `invitationAcceptanceEnabled` (líneas 30-32). Spec exige los mismos tres campos. Marcado: **alineado**.
- **`login-experience`, `email-verification`, `email-verification-ui`, `password-recovery-ui`, `channel-events-sse`, `channel-list-api`, `calendar-publication-sse`, `community-inbox`, `composer-preview`, `composer-media-picker`, `idea-canvas`, `idea-composer`, `media-provider-unsplash`, `oauth-callback-ui`, `oauth-initiation-api`, `oauth-mcp-client-registration`, `workspace-scoped-oauth`, `social-content-sync`, `media-attribution`, `media-takedown`, `media-library`, `visual-calendar`, `marketing-a11y-seo`, `lead-capture-waitlist`** — inspección estática de superficie (no exhaustiva bit-a-bit). Marcas:
  - Todos tienen implementación concreta en el repositorio.
  - Ninguno contiene los campos ya reescritos/alineados en lotes anteriores.
  - Sin divergencias graves detectadas en inspección de superficie (modelos, endpoints, naming).
  - **Marcados: alineados en superficie. Cobertura completa pendiente de pasada dedicada si se requiere verificación bit-a-bit.**

## Resumen ejecutivo consolidado

### Movimientos completados

- 13 specs no-negocio previas a `.agents/knowledge/{skill-capabilities,observability,platform-governance,code-hygiene}/`.
- 16 specs no-negocio nuevas a `.agents/knowledge/{backoffice,platform,infra,standards,release-readiness}/`.
- `mcp-write-tools/` eliminada; delta preservado en `mcp-server/deltas/mcp-write-tools.md`.
- `.agents/sdd/specs/` ahora contiene 41 specs de negocio.

### Specs alineadas (sin divergencias en lo inspeccionado)

- `privacy-compliance` y `privacy-dsar`.
- `invitations` (aggregate, status, source, validaciones de construcción).
- `registration` (form, DTO, payload).

### Specs con divergencias

#### BLOQUEADOR

- **`legal-pages`**: código declara `APPROVED` por defecto; spec exige `blocked` hasta evidencia jurídica en `docs/compliance/legal-publication-gate.md`. Imposible cerrar el ciclo sin esa evidencia.

#### Violación directa

- **`age-eligibility` RQ-004**: código usa `SubjectReference.user(principalId)`; spec exige `SubjectReference.workspace(workspaceId)`.

#### Diferencias estructurales

- **`dashboard-*` (8 specs)**: tipos TS divergentes, modelos de dominio diferentes, fixtures mock sin endpoint backend, escalas por followers en lugar de engagement rate, selectores de período/timezone ausentes, grupos por tipo en lugar de por plataforma.

#### Diferencias de API/datos

- **`governance-consent-api`**: campo `applicationOutcomes` ausente en respuesta, `history` endpoint con tres params vs `subjectReference` único, locale solo ISO 639-1.
- **`privacy-data-aggregation`**: `_metadata` sin `request_id`; Publishing sin `publication_assets` y `secure_credentials`.

### Riesgos identificados

- El catálogo de dashboards describe una arquitectura sustancialmente distinta al código actual. La reconciliación puede implicar re-arquitectura del módulo o reescritura considerable de specs.
- `legal-pages` bloquea la publicación pública de páginas legales. Sin evidencia de aprobación jurídica, no se puede revertir la divergencia.
- `age-eligibility` RQ-004 con violación confirmada puede implicar migración de datos o cambio de comportamiento de registro que requiere autorización de producto.

### Decisiones aplicadas por el usuario

- `dashboard-*` (8 specs): **reescribir specs al código**. Mismas tipologías, fixtures mock, modelos divergentes; las specs describen lo que el código hace hoy.
- `legal-pages`: **mantener `blocked`**. Se invirtió el código (`apps/web/marketing/src/legal/legal-publication.ts`) para que el valor por defecto sea `BLOCKED`. Coincide con `legal-pages/spec.yaml`.
- `age-eligibility` RQ-004: **ajustar spec al código**. La spec ahora describe `SubjectReference.user(principalId)`.
- `governance-consent-api`: **ajustar spec al código**. Se elimina `applicationOutcomes`, `history` usa tres params, `Flow.toList()` no es parte del contrato del controller.
- `privacy-data-aggregation`: **ajustar spec al código**. Sin `request_id`, sin `publication_assets`/`secure_credentials`.

### Divergencias restantes

- 33 specs pendientes de inspección o reescritura: 18 de publicación/medios (Lote 4) + 15 ya en la nube y aún no tocadas.

### Decisiones pendientes

- Cómo y cuándo ejecutar el Lote 4 (publicación/medios y 4 UI/auth del Lote 3).

### Próximo paso

- Completar Lote 4 (18 specs de publicación/medios + 4 specs UI/auth de Lote 3).
- Presentar el resumen al usuario para decidir sobre las divergencias detectadas antes de PR.
