# Acortador de URL — Core V1 hardening

## Ruta

Delegated direct. Continuar verificación y hardening ligero del Core V1 ya
mergeado (PR #1305, commit `3178a883`). No abrir un ciclo SDD: este trabajo es
verificación y mantenimiento de lo que ya está desplegado.

## Contexto

Core V1 cubre lo que el producto necesita hoy: gestión del ciclo de vida de
enlaces, validación de destino, idempotencia durable, scope por workspace,
redirect público 302, semántica de estados coherente y pruebas unitarias, de
integración PostgreSQL y BDD que ya pasan en local.

Las capacidades adicionales (click events async, dominios custom, analítica,
QR, abuso/cuarentena automática, SLO/capacidad y pruebas de carga) fueron
exploradas con una especificación completa y están diferidas por ADR-0028. No
se reinicia ese ciclo hasta que aparezca un trigger (tráfico sostenido,
cliente externo, petición regulada, incidente operativo o solicitud de
producto). Mientras tanto, este RPI es el único trabajo activo del
acortador.

## Alcance

Endurecimiento ligero y verificación residual sobre Core V1:

- [x] RPI-001..010 Implementación inicial, pruebas y gates — Core V1 cerrado.
- [x] RPI-011 Cobertura ampliada y regresiones — cerradas antes del merge.
- [ ] RPI-012 Ejecutar `just backend-check` con la suite PostgreSQL cuando
      sea posible, dejando evidencia de fallos locales; verificar que el
      CI remoto tras el merge cumple Quality Gate, Codecov y BDD sin
      introducir cambios de código.
- [ ] RPI-013 Revisar Diff final una vez estabilizado el merge para
      confirmar que no quedaron supresiones, TODOs ni código comentado en
      shortlinks; sin hacer commit/push.
- [ ] RPI-014 Mantener la traza de este RPI coherente con `ADR-0028`: si
      cambian las capacidades diferidas, actualizar el ADR en lugar de
      reabrir el change archivado.

Fuera de este RPI quedan: click events async/outbox, reconciliación por
edge logs, Cloudflare for SaaS, APIs de analytics, QR, abuso/cuarentena,
SLO/capacidad y load tests. Esos temas solo vuelven vía un nuevo SDD cuando
se cumpla un trigger del ADR-0028.

## Evidencia

- `./gradlew :server:smp:test --tests 'com.profiletailors.smp.shortlinks.*' --no-daemon --console=plain` — `BUILD SUCCESSFUL in 27s`; cubre RPI-011 con la regresión de claim incompleto mapeada a `IdempotencyRequestInProgressException`.
- `./gradlew :server:smp:postgresIntegrationTest --tests 'com.profiletailors.smp.shortlinks.infrastructure.persistence.R2dbcShortLinksPostgresIntegrationTest' --no-daemon --console=plain` — `BUILD SUCCESSFUL in 33s`.
- `./gradlew :server:smp:bddFastTest --no-daemon --console=plain` — `BUILD SUCCESSFUL in 6m 3s`; 11 escenarios shortlinks pasan: replay 201, conflicto 409, invalidación miss 201→302, expiración 410 (incluido tras update) y deshabilitado 404.
- `node scripts/gradle-run.mjs :server:smp:check --no-daemon -x :server:smp:bddFastTest -x :server:smp:bddPostgresTest` — `BUILD SUCCESSFUL in 8m 50s` tras corregir `MaxLineLength` en `CreateLinkHandler.kt:61` sin supresiones.
- `./gradlew :server:smp:detekt :server:smp:spotlessCheck --no-daemon --console=plain` — `BUILD SUCCESSFUL`.
- `git diff --check` — sin errores.

Evidencia RPI-012 (pendiente):
- Quality Gate tras merge en PR #1305 y Codecov: confirmar estado sin
  introducir cambios locales.
- `just backend-test-postgres` cuando esté disponible la infra; no se
  requiere si el CI remoto ya aporta la misma cobertura.

## Estado

Ready — Core V1 verificado en local con gates requeridos. Pendiente solo CI
remoto y, en su caso, suite PostgreSQL local, sin cambios de código. Si
RPI-012/013 no producen hallazgos, este RPI queda cerrado y el seguimiento
continúa por ADR-0028 o por tickets puntuales.

## Riesgos y límites de evidencia

- `backend-check` excluye las suites BDD por diseño; `bddFastTest` se
  ejecutó y pasó por separado.
- La suite de integración PostgreSQL específica pasó localmente con
  Testcontainers. No se ejecutó la suite PostgreSQL BDD completa ni CI
  remoto en la última revisión.
- Los cambios ajenos del worktree, incluidas eliminaciones históricas de
  `plan/tasks/`, se preservan; no se hace commit/push.
- Este RPI no reabre capacidades diferidas; cualquier cambio de alcance
  requiere nuevo SDD o nuevo ADR, no modificación de este archivo.