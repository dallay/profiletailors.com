# DALLAY-618: integración de Sentry con privacidad

## Objetivo

Agregar seguimiento de errores y correlación de releases a las aplicaciones Vue `app` y `admin` y al backend SMP, manteniendo Sentry fuera de `domain`/`application`, sin instrumentar Marketing y sin sustituir Prometheus/Grafana/Loki ni `OperationalEventSink`.

**Ruta:** Delegated direct (RPI), sin ciclo SDD. El issue #1273 y su comentario de seguimiento fijan el diseño y el alcance.

## Revisión y cadena de PR

El diff completo suma 2,289 líneas, por encima del presupuesto de revisión de 400. Estrategia elegida por el usuario: `feature-branch-chain`, con PRs ordinarios secuenciales y sin metadatos GitHub Stack. La rama de integración/seguimiento será `integration/sentry-issue-1273`; cada PR siguiente tomará como base la rama de trabajo del PR anterior.

El usuario fusionó las PR #1287–#1293 dentro de la cadena el 2026-10-02; solo queda abierta la #1286, que ahora contiene el stack completo rebasado sobre el `main` nuevo. Las ramas hijas locales se eliminaron tras confirmar que su contenido quedó preservado en la rama restante.

| Posición | Rama head | Base | Entregable | Líneas estimadas |
| --- | --- | --- | --- | ---: |
| 1/7 | `feat/sentry-shared-sanitizer` | `main` | Sanitizador independiente del SDK en `shared/web` y pruebas | 315 |
| 2/7 | `build/sentry-web-dependencies` | `feat/sentry-shared-sanitizer` | Dependencias Sentry web, lockfile y script trust | 320 |
| 3/7 | `feat/sentry-app-errors` | `build/sentry-web-dependencies` | Integración de errores y build de `app` | 380 |
| 4/7 | `feat/sentry-admin-errors` | `feat/sentry-app-errors` | Integración de errores y build de `admin` | 302 |
| 5/7 | `feat/sentry-smp-errors` | `feat/sentry-admin-errors` | SMP Sentry, sanitización y configuración local/prod | 355 |
| 6/7 | `ci/sentry-release-ops` | `feat/sentry-smp-errors` | CI/source maps, releases, despliegue self-hosted, ADR y runbook | 387 |
| 7/7 | `docs/sentry-compliance` | `ci/sentry-release-ops` | Inventario, matriz, ROPA, contratos y cierre RPI | 230 |

Cada capa incluye código/documentación necesarios para su entregable; las pruebas pesadas restantes se delegan a GitHub Actions según la decisión del usuario sobre memoria local.

## Decisiones de implementación

- Instrumentar navegación y rendimiento del navegador, pero no propagar trazas al API ni habilitar trazas de rendimiento de Sentry en SMP. El comentario del issue identifica la frontera CORS; Micrometer/Prometheus mantiene el monitoreo de rendimiento del backend.
- Colocar sanitización independiente del SDK en `shared/web`; los adaptadores e inicializadores de Sentry permanecen en cada aplicación.
- Usar el artefacto oficial de Sentry para Spring Boot 4, confirmado en la documentación actual, en lugar de copiar la sugerencia `starter-jakarta` del comentario, que corresponde a versiones anteriores de Spring Boot.
- Fijar `@sentry/vue@11.0.0` y `@sentry/vite-plugin@5.4.0`: ambas versiones superan la ventana de madurez de 72 horas de pnpm; `@sentry/vue@11.2.0` se rechazó por publicarse el mismo día.
- Permitir el script de `@sentry/cli` solo después de revisar su instalador: descarga el binario de Sentry desde su CDN y valida su SHA-256 contra el archivo de checksums del paquete. Es una dependencia de desarrollo para subir mapas, no se envía al navegador. Su licencia actual es FSL-1.1-MIT, que permite uso interno; la concesión MIT entra en vigor a los dos años.
- En builds de producción sin DSN, no inicializar Sentry ni generar mapas y dejar una advertencia de configuración ausente. Si hay DSN, exigir token, organización y proyecto, fallar ante configuración o carga inválida y eliminar mapas tras una carga exitosa.
- Mantener Replay apagado hasta completar validación de privacidad y cuota en Sentry; permitir únicamente la activación explícita del Replay en sesiones con error para `app`. Enmascarar texto/entradas, bloquear medios y desactivar captura de cuerpos de red. `admin` no usa Replay.
- `@sentry/replay@11` aplica `beforeAddRecordingEvent` solo a eventos custom, no a los eventos rrweb de tipo DOM/meta donde aparece la URL. Mantener Replay apagado si una captura sintética no demuestra que URLs iniciales y visitadas excluyen queries, fragments, emails, credenciales y segmentos de tokens.
- Registrar en compliance que existe integración condicional en el código, pero no declarar un procesador actual ni inventar región, retención, acuerdos o configuración de organización sin evidencia de producción.

## Tareas

- [x] RPI-001 Leer issue #1273, todos sus comentarios y verificar las restricciones técnicas y el estado limpio/base del worktree; preservar los archivos no rastreados existentes.
- [x] RPI-002 Sanitización compartida y pruebas implementadas; app/admin inicializan Sentry solo en producción con DSN, releases, tracing de navegación, Replay de errores opcional y sin identidad de usuario.
- [x] RPI-003 Integrar Sentry para Spring Boot 4 solo en SMP infraestructura/configuración y sanitizar datos antes del envío; la integración captura errores no manejados y evita el SDK en domain/application.
- [x] RPI-004 Agregar release names y carga/eliminación condicional de source maps a los builds Vite de Cloudflare Pages y la imagen de dashboard para Compose/Swarm; usar el formato documentado del secret de BuildKit.
- [x] RPI-005 Actualizar inventario/matriz/ROPA, contratos de observabilidad y runbook; registrar el límite duradero de Sentry en `ADR-0027` sin iniciar un ciclo SDD.
- [x] RPI-006 Ejecutar las verificaciones locales acotadas, revisar el diff y registrar que la suite backend completa queda delegada a GitHub Actions por el límite local de memoria.

## Criterios de aceptación

- App y Admin usan proyectos Sentry separados; SMP usa un proyecto separado cuando se configure la organización. Marketing permanece sin SDK.
- Sentry no emite eventos en desarrollo/pruebas por defecto; versión y SHA del componente definen el release.
- Las dos aplicaciones usan el mismo sanitizador probado, que elimina credenciales, cabeceras sensibles, cookies, cuerpos, emails y consultas/fragmentos de URL.
- Source maps solo se cargan desde el despliegue de release a Sentry y se eliminan del artefacto público. Un release con DSN falla si no puede subir mapas; sin DSN, Sentry queda desactivado con advertencia explícita.
- Replay solo se puede habilitar en `app` mediante activación explícita, para sesiones con error y tras validación externa; Admin queda sin Replay.
- SMP captura excepciones inesperadas con versión/ambiente, sin Sentry en los módulos domain/application y sin trazas de rendimiento Sentry.
- La documentación no afirma que Sentry ya sea procesador activo ni que la organización haya configurado scrubbing, IP, retención, región, proyectos o alertas sin evidencia.
- Prometheus/Micrometer, logs y `OperationalEventSink` mantienen su comportamiento existente.

## Evidencia inicial

- `gh issue view 1273 --repo dallay/profiletailors.com --json title,body,comments,state,url,author`: issue abierto; dos comentarios, incluido el seguimiento de investigación. Este último descarta trazas distribuidas entre orígenes y recomienda el sanitizador en `shared/web`.
- Worktree en `main` al día con `origin/main`; ya existían `?? .agents/skills/autofix/`, `?? .agents/skills/code-review/` y `?? skills-lock.json`; no modificarlos.
- `just -l` confirma recetas para `app-build`, `admin-build`, `admin-check`, `admin-test`, `backend-check`, `backend-build` y `frontend-test`.
- `apps/web/app` y `apps/web/admin` son paquetes Vite separados con versión/SHA de build; `.github/workflows/release-please.yml` construye desde tags de release y despliega los directorios `dist`.
- `.github/workflows/release-image.yml` también crea la imagen de dashboard para Compose/Swarm; su Dockerfile separado no pasaba DSN, SHA ni credenciales de source-map upload.
- SMP usa Spring Boot `4.0.8`, WebFlux y `spring-boot-build-info`; `OperationalEventSink` vive en infraestructura, con contratos puros compartidos.
- Documentación actual afirma que no hay integración Sentry; organización/proyectos/secretos y configuración efectiva de producción no han sido comprobados.
- RED del sanitizador: la prueba falló por el módulo ausente; GREEN: suite de `shared/web` pasó 71/71 y Biome pasó para los archivos nuevos.
- App: prueba RED falló por el inicializador ausente; GREEN pasó 5/5. Admin: RED falló por el inicializador ausente; GREEN pasó 3/3. `pnpm --filter app type-check`, `just admin-check` y Biome sobre los 15 archivos frontend implicados pasaron.
- pnpm rechazó inicialmente `@sentry/vue@11.2.0` por la espera mínima de 72 horas; se fijó `11.0.0`. Se revisó que `@sentry/cli` descargue su binario y valide SHA-256 antes de habilitar su script.
- Socket devolvió `No valid session` en dos intentos; no se pudo obtener el score de `@sentry/vue@11.0.0` ni de `sentry-spring-boot-4`. No se cambió la espera de madurez ni se añadió una excepción de licencia.
- La versión instalada de `@sentry/vue@11.0.0` no declara `sendDefaultPii`; no se fuerza esa opción. La reducción de datos se hace mediante callbacks de eventos, breadcrumbs, spans y Replay, más el scrubbing/IP scrubbing externo pendiente.
- El artefacto `sentry-spring-boot-4@8.59.0` publica configuración de Boot 4/WebFlux. Su manejador WebFlux captura excepciones no manejadas y excluye `ResponseStatusException`; el callback propio del SMP sanitiza evento, solicitud, mensajes y etiquetas.
- ADR-0021 mantiene `OperationalEventSink` como frontera de eventos operativos y deja trazas/exportadores fuera de alcance; ADR-0027 registra Sentry issue-tracking como decisión transversal distinta, sin alterar ADR-0021.
- `docs/architecture/adr/0027-conditional-sentry-error-tracking-boundary.md` documenta la frontera de Sentry, los límites de Replay y la condición de activación; `docs/architecture/adr/README.md` lo indexa. C4 queda sin cambios porque ningún recipient/proyecto Sentry de producción está activado o evidenciado.
- La prueba del callback backend pasó RED (sanitizador/cableado ausentes) y GREEN (5 escenarios), incluyendo URL/query/IP literal, cabeceras/cookies/cuerpo, usuario/email, breadcrumbs, etiquetas, credenciales embebidas y límites de longitud.
- Maven Central identifica `sentry-spring-boot-4@8.59.0` bajo MIT. Su auto-configuración exige la propiedad DSN; los perfiles `dev` y `test` fijan Sentry en estado desactivado.
- La documentación vigente de `docker/build-push-action` define `secrets` como entradas sin comillas `NAME=value`; se quitó el entrecomillado de la asignación en `release-image.yml`.
- `just backend-lint` pasó después de corregir hallazgos Detekt en el sanitizador; `./gradlew :server:smp:spotlessKotlinCheck --no-daemon` y el test enfocado de privacidad SMP (5 escenarios) también pasaron.
- El usuario indicó que la máquina local tiene 8 GB y puede agotar memoria con suites completas; autorizó abrir PRs con evidencia local acotada y dejar el gate completo a GitHub Actions. No ejecutar más suites pesadas locales.
- `pnpm --filter app test:run`: 168 archivos/1907 tests pasaron al repetir la suite sin concurrencia. El primer intento tuvo timeout en una prueba del router mientras corrían siete verificaciones a la vez; la prueba aislada pasó en 4,4 s y la repetición secuencial pasó. `pnpm --filter @profiletailors/admin test:run`: 18 archivos/134 tests pasaron; `pnpm --filter @profiletailors/shared-web test:run`: 5 archivos/71 tests pasaron.
- Marketing: `just frontend-lint`, `just frontend-check`, `just frontend-test` (17 archivos/153 tests) y `just frontend-build` pasaron. `just admin-check`, `just admin-build`, `pnpm --filter app type-check`, `just app-build` y ambos builds admin/app con DSN ausente pasaron.
- Builds de app/admin: release con el SHA completo presente, sin `.map` públicos ni variables CI en assets. El build con DSN presente pero sin token/organización falla como se requiere. Marketing no tiene dependencia ni import de Sentry.
- `docker buildx build --check -f infra/apps/smp/production/dashboard.Dockerfile .` pasó. `docker compose ... config --quiet` con `.env.example` y `just swarm-config` con valores locales de validación pasaron.
- Validaciones documentales: `node tools/compliance/check-data-inventory.ts docs/compliance/data-inventory.yaml` pasó; markdownlint sobre los 12 documentos modificados no reportó issues; los enlaces relativos de esos documentos existen; los YAML de workflows, Spring y despliegues se analizaron sin errores.
- Validaciones posteriores al ADR: markdownlint sobre los 14 documentos afectados no reportó issues; todos sus enlaces relativos existen; inventario y YAML quedaron validados. `docker/build-push-action` requiere `secrets` en formato `NAME=value` sin comillas; se quitó el entrecomillado del token BuildKit.
- `just licence-check` generó los inventarios. Los paquetes Sentry frontend son MIT salvo `@sentry/cli` y su binario, que reportan FSL-1.1-MIT. Socket `depscore` no respondió con una sesión válida; no se obtuvo score para los nuevos paquetes.
- `pnpm --filter app lint` reportó un warning preexistente de variable `viewport` sin uso en `src/modules/publishing/presentation/components/mobile/SchedulerTimelineBody.vue`, archivo no modificado. `pnpm peers check` reportó incompatibilidades preexistentes entre `@vitest/ui` 3.2.7/Vitest 4.1.11 y `@codecov/vite-plugin`/Vite 7.3.6.
- `just backend-check` excedió el límite de 15 minutos; el intento de fondo fue cancelado por el reinicio del servidor. No hay resultado final del gate completo. `./gradlew :server:smp:bootJar --no-daemon` pasó como verificación de empaquetado acotada. `just backend-test-fast` terminó con BUILD SUCCESSFUL en 4m11s (37 tareas; excluye los tags `modularity,postgres`).
- Tras abrir la cadena (PR #1286–#1293), CI reportó: títulos en español rompen `Semantic PR` (revertidos a inglés convencional); `Quality Gate` de #1290 fue un flake de infra (HTTP 500 de services.gradle.org, reintentado sin cambios); cobertura <80% en #1286/#1288/#1289 y duplicación 14.1% en #1289. Fixes aplicados: helper `buildRedactedSpan` en `shared/web` (elimina el bloque duplicado de 21 líneas), tests que invocan los callbacks reales en app/admin, y `main.test.ts`/`main.spec.ts` para el bootstrap. Cobertura local verificada por JSON: `sentry.ts` y `main.ts` de ambas apps al 100% de statements/funciones. Toda la cadena se propagó por rebase y está en sync con `origin`.
- El usuario fusionó las PR #1287–#1293 el 2026-10-02; `main` avanzó 5 commits (#1274, #1283, #1284, #1285, #1292). La rama restante se rebasó sobre el `main` nuevo con un único conflicto en `gradle/libs.versions.toml`, resuelto conservando las versiones de `main` (caffeine 3.3.0, jackson 3.2.3, bouncycastle 1.86) y añadiendo `sentry 8.59.0`. GitHub reporta la #1286 `MERGEABLE`; el estado `BLOCKED` restante es solo `REVIEW_REQUIRED` (aprobaciones previas caducadas por los merges/rebases).
- No se comprobaron proyectos/DSN/token, reglas de scrubbing/IP, cuota Replay, carga real de source maps, alertas, evento real ni despliegue Sentry. No afirmar activación de producción.

## Estado

- RPI-001–RPI-006 completos para verificación local acotada; el gate backend completo queda explícitamente delegado a GitHub Actions por el límite de memoria de la máquina.
- Próximo paso: esperar CI de la #1286 rebasada, obtener tu aprobación (las anteriores caducaron con los merges) y fusionar. Después configurar/verificar Sentry externamente antes de activar DSN o Replay.

## Referencias

- [Issue #1273](https://github.com/dallay/profiletailors.com/issues/1273)
- [`docs/observability-contracts.md`](../../docs/observability-contracts.md)
- [`docs/observability-usage.md`](../../docs/observability-usage.md)
- [`docs/compliance/data-inventory.md`](../../docs/compliance/data-inventory.md)
- [`docs/compliance/controller-processor-matrix.md`](../../docs/compliance/controller-processor-matrix.md)
