# DALLAY-553: gates de verificación de CI

## Ruta

Delegated direct. Ejecutar los tres gates explícitamente requeridos por DALLAY-553 y registrar sus resultados reales. No hay cambio de comportamiento productivo ni un nuevo ciclo SDD.

## Tareas

- [x] RPI-001 Confirmar la ubicación actual de los artefactos OpenSpec y preservar los cambios ajenos del worktree.
- [x] RPI-002 Ejecutar `SMP_DB_TEST_PASSWORD=test just backend-check`.
- [x] RPI-003 Ejecutar `SMP_DB_TEST_PASSWORD=test just backend-build`.
- [x] RPI-004 Ejecutar `SMP_DB_TEST_PASSWORD=test just ci-local`.
- [x] RPI-005 Registrar los resultados en este plan y publicar la misma evidencia en Linear porque no existe un `verify-report.md`/`state.yaml` vigente para actualizar.
- [x] RPI-006 Revisar el diff final y validar la consistencia de la evidencia.

## Criterios de aceptación

- Cada gate solicitado queda reportado como PASS, FAILED, BLOCKED o NOT RUN, con evidencia exacta.
- Ningún fallo se oculta mediante cambios de código, tests, linters o configuración.
- La evidencia enlaza con el cambio PR2 de OpenSpec y no afirma checks que no se ejecutaron.
- La limitación de schema permanece abierta, tal como indica el estado histórico.

## Evidencia

- `backend-check`: PASS. Inicio `2026-09-23T10:40:50Z`; fin `2026-09-23T10:40:54Z`; exit `0`; `BUILD SUCCESSFUL`; 43 tareas accionables, 2 ejecutadas y 41 actualizadas desde cache. El primer intento tuvo un fallo del wrapper de logging por usar la variable reservada `status` de zsh después de que Gradle terminara correctamente; se repitió el gate con un wrapper corregido y se confirmó exit `0`.
- `backend-build`: PASS en la repetición posterior a la recuperación de Docker. Inicio `2026-09-23T11:30:24Z`; fin `2026-09-23T11:35:40Z`; exit `0`; `BUILD SUCCESSFUL in 5m 15s`. El contexto Docker `dory` estaba disponible y las tareas `bddFastTest` y `bddPostgresTest` completaron dentro del build. El intento anterior de `2026-09-23T10:42:07Z`–`10:42:22Z` quedó como bloqueo ambiental histórico: Testcontainers no encontró Docker y produjo 291 fallos de inicialización en cada suite, no 582 fallos funcionales independientes.
- `main`: sincronizado mediante fast-forward hasta `f13b55ab` (`docs: update spec format`), incluyendo `d3bbaa5d`, que actualiza `wrangler` de `3.90.0` a `3.114.17`. `pnpm install --frozen-lockfile` pasó y `pnpm why node-forge --depth 6` ya no encuentra la dependencia.
- `ci-local`: PASS después de sincronizar `main`. Inicio `2026-09-23T12:18:22Z`; fin `2026-09-23T12:21:57Z`; exit `0`; contexto Docker `dory`. El gate confirmó el chequeo de licencias, 150 archivos y 1756 tests del dashboard, 14 archivos y 120 tests de admin, 17 archivos y 151 tests de marketing, Detekt, tests backend y build backend; terminó con `CI Pipeline Simulation Complete`.
- Licencia: la actualización a `wrangler@3.114.17` eliminó la ruta transitive `selfsigned@2.4.1` → `node-forge@1.4.0`. La comprobación directa `pnpm licenses list --json | node scripts/check-frontend-licences.mjs` también pasó.
- OpenSpec: la ruta indicada por la issue está obsoleta. El cambio PR2 fue archivado en el commit `4662510c` bajo `openspec/changes/archive/2026-08-05-linkedin-company-pages-community-inbox/` y luego fue eliminado durante la limpieza posterior del repositorio en el commit `5e0bc649`. El checkout actual conserva las capability specs sincronizadas, pero no contiene un reporte de verificación ni un estado vigente que actualizar. El estado histórico sigue siendo `implementation_status: partial` y `verify_result: PASS_WITH_WARNINGS`; Task 2.2 continúa bloqueada por schema y las Tasks 3.1, 3.2, 5.1 y 5.2 permanecen abiertas.
- Linear: la evidencia inicial se publicó en el comentario `3fd33532-ec82-4f09-b643-f2d642319fe7` y la actualización previa en `858fb1f1-596f-40a1-8bcd-6d900e7714e6` de DALLAY-553. Después de sincronizar `main`, se debe publicar la evidencia final de `ci-local` en verde.
- Engram: el proyecto correcto es `profiletailors.com`. Se registró la decisión inicial de mantener el bloqueo sin debilitar el gate bajo `routing/ci-verification-gates`; la evidencia posterior demuestra que `main` ya contiene la remediación y que el gate pasa. Los conflictos semánticos reportados contra resúmenes históricos fueron clasificados como `related`.

## Estado

- `backend-check`: PASS.
- `backend-build`: PASS después de recuperar Docker.
- `ci-local`: PASS después de incorporar desde `main` la actualización existente de Wrangler.
- Los gates locales requeridos quedan en verde sobre `f13b55ab`; no se modificó el checker ni se añadió una excepción de licencia.
- No se creó ni restauró un `verify-report.md` o `state.yaml` eliminado; la limitación del schema y el estado histórico parcial permanecen visibles.

## Próximo paso

Publicar la evidencia final de los gates en Linear y mantener visible la limitación histórica del schema. No hacer commit ni push en este trabajo.
