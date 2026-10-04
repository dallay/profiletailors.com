# RPI Plan — 349 retention slice 1 (OAuth disconnect + rules + tombstones + dry-run)

Route: Delegated direct (Plan Mode RPI, fast track)
Issue: <https://github.com/dallay/profiletailors.com/issues/349> — P1/P2, fuera del critical path
beta privada
Estado: Working

## Contexto verificado

Solo corren 4 mecanismos sueltos, sin motor central:

- `BlobGarbageCollector` horario (huérfanos >7d, `MediaReconcilerScheduler`)
- `MediaAssetExpirationJob` cada 6h (PENDING/UPLOADING → FAILED)
- `FindExpiredRequestsJob` diario (DSR, hoy solo discovery, no borra)
- `PasswordResetTokenCleanupScheduler` (retención configurable)

Gaps para slice 1 (pa-006):

- `DELETE /api/publishing/{provider}/connections/{connectionId}` borra `social_accounts` +
  `secure_credentials` (DELETE duro) + `social_connections` en una transacción, pero no es
  idempotente (segundo llamado → 400/IllegalArgumentException), no deja tombstone, no tiene dry-run,
  no emite métricas de purga.
- `R2dbcProviderCredentialGateway.invalidateCredential` hace DELETE sin retry ni ledger.
- No hay regla config-controlada trazable a `data-inventory.yaml` pa-006 (duration Not established),
  ni override por provider más allá de
  `publishing.social-content.activity-cache-ttl/commenter-profile-cache-ttl/purge.*`.
- No hay job de purga de credenciales expiradas/revocadas (P30D metadata purge missing).

## Alcance slice 1

Dentro:

- Reglas de retención config-controladas para credenciales OAuth, trazables a pa-006, con override
  por provider.
- Disconnect idempotente + tombstone de borrado.
- Job programado de purga de credenciales expiradas (tenant-safe, resumable, observable) + modo
  dry-run/report.
- Tests: parcial failure, retries, idempotencia, aislamiento por tenant, dry-run sin writes.

Fuera (slice 2):

- Backup expiry y replay tras restore (requiere ledger generalizado + test de restore).
- Audit/log retention P1Y/P5Y, publishing delivery-logs P90D/P180D, API-key P90D purge,
  workspace/account erasure orquestada.
- API HTTP `/api/governance/retention/*` completa (rules/purges/holds/status). Slice 1 solo deja
  base domain/app + properties; expone dry-run vía parámetros del job existente y reporte en
  logs/eventos, sin nuevo controlador público salvo que el BDD lo exija.

## Tareas

### RPI-001 Reglas config-controladas pa-006 con override por provider

- Archivos probables: `server/smp/.../publishing/.../RetentionProperties.kt` o
  `CredentialRetentionProperties.kt`, `application.yaml` (`smp.retention.credentials.*`),
  `docs/compliance/data-inventory.yaml` (solo evidencia si cambia control_status con prueba).
- Aceptación: defaults globales + override `linkedin`/`threads`; validación non-negative;
  trazabilidad `activityId: pa-006` en código/config; sin `any`, sin suppressions.
- Verificación: unit properties/binding + `just backend-check` parcial.

Estado: Done
Evidencia: 2026-09-29 — `CredentialRetentionRule` + `CredentialRetentionProperties` + binding
`publishing.credentials.retention` + `application.yaml`; focused
`:server:smp:test --tests CredentialRetentionRuleTest --tests CredentialRetentionPropertiesTest`
PASS; `:server:smp:detekt` PASS; sin suppressions; diff mínimo.

### RPI-002 Disconnect idempotente + tombstone

- Archivos probables: `publishing/domain` (nuevo `CredentialDeletionTombstone` value/aggregate +
  ports), `publishing/application` (`DisconnectProviderConnectionHandler` idempotente),
  `publishing/infrastructure/persistence` (R2dbc tombstone repo + migration
  `publishing/024-credential-deletion-tombstones.yaml`), `ProviderCredentialGateway` con manejo de
  fallo parcial.
- Reglas: hexagonal `domain <- application <- infrastructure`; puertos en domain; `@Service` de
  `com.profiletailors.common.domain.Service`; controlador delgado ya existente sin lógica;
  `X-Workspace-Id` + `Accept: application/vnd.api.v1+json` en BDD.
- Aceptación: segundo disconnect mismo `connectionId` → éxito idempotente (DELETED) sin borrar de
  más; tombstone con `workspaceId, provider, connectionId, credentialReference, deletedAt, reason`;
  reintento seguro; sin cross-workspace.
- Verificación: unit handler + integración R2dbc + BDD.

Estado: Done
Evidencia: 2026-09-29 — `CredentialDeletionTombstone` +
`CredentialDeletionTombstoneRepository/NoOp` + migración `024` +
`R2dbcCredentialDeletionTombstoneRepository` + handler idempotente (tombstone-first,
`CredentialRetentionRule` de dominio, sin import infra en application); tests
`CredentialDeletionTombstoneTest`, `DisconnectProviderConnectionIdempotencyTest` (4 escenarios),
`DisconnectProviderConnectionHandlerTest` existente PASS; `:server:smp:detekt` PASS tras corregir
MagicNumber (constante de dominio) y MaxLineLength; arch tests Not run (timeout en foco, se cubren
en `backend-check` de RPI-005).

### RPI-003 Job purga credenciales expiradas (tenant-safe, resumable, observable)

- Archivos: `CredentialPurgeRepository` (port en domain) + `R2dbcCredentialPurgeRepository`
  (huérfanas expiradas con `FOR UPDATE SKIP LOCKED`, excluye referenciadas por conexiones
  ACTIVE/REQUIRES_RECONNECT) + `CredentialRetentionJob` (por provider con thresholds del rule,
  métricas `credentials.purge.*`) + `CredentialRetentionScheduler` (interval/initial-delay config,
  kill-switch `enabled`).
- Alcance consciente: solo huérfanas; referenciadas por conexiones terminales van al slice 2 con la
  máquina de estados.

Estado: Done
Evidencia: 2026-09-29 — focused tests + `:server:smp:detekt` PASS (tras refactor).

### RPI-004 Dry-run/report mode

- Archivos: `CredentialRetentionJob.run(dryRun, batchSize)` + `CredentialPurgeResult` (counts,
  `sampleCredentialIds` sin secretos, flag `dryRun`); scheduler cablea `properties.dryRun`.
- Aceptación: `dryRun=true` no escribe, retorna reporte; evidencia sin secretos ni PII.

Estado: Done
Evidencia: 2026-09-29 — `CredentialRetentionJobTest` (dry-run sin writes, fallo parcial cuenta y
continúa, providers deshabilitados se saltan, batch inválido rechaza) PASS.

### RPI-005 Tests BDD + parcial failure/retries

- Archivos: `server/smp/src/test/resources/features/publishing-credential-retention.feature` (reusa
  steps existentes: idempotencia HTTP del disconnect), unit/integración por boundary en
  RPI-002/003/004.
- Escenarios: disconnect borra token y deja tombstone (replay idempotente HTTP 200); re-disconnect;
  restore-replay, mismatch y not-found a nivel unit; purga: dry-run, fallo parcial, providers
  deshabilitados.

Estado: Done
Evidencia: 2026-09-29 — `just backend-bdd-fast` BUILD SUCCESSFUL (9m 23s);
`publishing-credential-retention.feature` 1/1 PASS, 0 failures/errors/skipped.

### RPI-006 Sync docs y control register

- Archivos: `docs/compliance/retention-and-erasure-control-plan.md`,
  `docs/retention-framework-operations.md`, `docs/retention-framework-quick-reference.md`,
  `docs/retention-framework-acceptance-criteria.md`, `docs/compliance/data-inventory.yaml` (pa-006
  solo si hay evidencia ejecutada), ADR si la decisión de config + tombstone es durable
  cross-cutting (`docs/architecture/adr/` + index).
- Regla: no documentar planeado como implementado; usar `Planned/Not implemented` explícito.
- Verificación: diff exacto + links relativos + `just ci-local` mínimo afectado.
- Regla: no documentar planeado como implementado; usar `Planned/Not implemented` explícito.
- Verificación: diff exacto + links relativos + `just ci-local` mínimo afectado.

Estado: Done
Evidencia: 2026-09-29 — pa-006 a `partial` en inventario (evidencias + 4 rutas verificadas) y plan
de control; operations/quick-ref/acceptance actualizados a 6 mecanismos con alcance honesto
(huérfanas opt-in, sin API HTTP); `check-data-inventory.ts` PASSED; sin ADR (decisión contenida al
bounded context publishing, no cross-cutting durable).

### RPI-007 Doc-date check hardening (drive-by, pedido del usuario)

- Problema: `scripts/check-doc-last-updated.mjs` usa fecha de committer (`%cd`, se reescribe con
  rebase) y exige igualdad de día exacta (falla si editas 23:00 y commiteas 01:00).
- Cambio: fecha de autor (`%ad`, sobrevive rebase) + gracia de 3 días (cubre medianoche y squash de
  PRs cortos; sigue cazando cabeceras con semanas de atraso) + mensaje que explica el fix.
- Tradeoff honesto: con gracia, un miss real de 1 día (como el de hoy en c4/03 y c4/04) ya no falla;
  se corrige subiendo esas dos cabeceras al 29 en este mismo cambio.
- Tests: `scripts/check-doc-last-updated.test.mjs` (node:test, repo git temporal: medianoche pasa,
  10 días falla, committer-date tras rebase no afecta).
- Verificación: `node --test scripts/check-doc-last-updated.test.mjs` + `just doc-check`.

Estado: Done
Evidencia: 2026-09-29 — script usa `%ad` (fecha de autor, sobrevive rebase) + gracia 3 días +
mensaje accionable; cabeceras c4/03 y c4/04 subidas al 29 (miss real del commit f9694d81);
`scripts/check-doc-last-updated.test.mjs` 7/7 PASS (medianoche, rebase, stale 10d); Biome clean;
`just doc-check` PASS.

## Riesgos

- Borrado irreversible + restores que reintroducen: mitigado con tombstone e idempotencia desde
  slice 1, replay completo en slice 2.
- Acoplamiento con inventory/legal: no prometer periodos públicos; solo internos config-controlados
  hasta aprobación.
- Detekt/complexity en handlers: extraer responsabilidades de dominio en vez de partir funciones sin
  sentido; sin `@Suppress` nuevo.

## Progreso

- 2026-09-29: ruta Delegated direct RPI + slice 1 acordados; plan creado; sin writes de src aún.
- 2026-09-29: RPI-001 Done (reglas config-controladas pa-006 + override por provider, tests + Detekt
  PASS).
- 2026-09-29: RPI-002 Done (tombstone + disconnect idempotente + migración 024, tests + Detekt
  PASS).
- 2026-09-29: RPI-003 Done (job purga huérfanas expiradas + scheduler) + RPI-004 Done
  (dry-run/report, refactor LongMethod/LabeledExpression, tests + Detekt PASS).
- 2026-09-29: RPI-007 Done (doc-date check: autor-date + gracia 3d + tests 7/7 + `just doc-check`
  PASS; c4/03 y c4/04 al 29).
- 2026-09-29: RPI-005 Done (`backend-bdd-fast` SUCCESS 9m23s, feature 1/1 PASS); RPI-006 Done
  (docs + inventario validado).
- 2026-09-30: publicado como stack de 2 — PR1 #1249 (doc-check, ~190 líneas, en presupuesto) y PR2
  #1250 (slice retención, excepción de tamaño documentada) en draft. `backend-check` completo no
  corrió en local (reinicios del entorno); CI es autoritativo.
- 2026-09-30: CI en #1250 cazó `ValueObjectImmutabilityTest` (faltaba `init` en
  `ResolvedCredentialRetention`; `CredentialPurgeCandidate` no es VO sino read-model como `StaleJob`
  y se le quitó la anotación en vez de maquillar el test). Nuestros runs con `--tests` nunca lo
  ejecutaron — lección: el gate amplio local sí importa.
- Estado: Checking (fix en camino a #1250, vigilando ambas PRs hasta verde + sin hilos abiertos).
