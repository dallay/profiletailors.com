# Plan — PR-1209 CI fixes

## Ruta

Delegated direct con `systematic-debugging`. Fallos de CI en la PR #1209, cada uno con causa raíz
localizada antes de corregir. Sin push hasta verificar cada fix en local.

## Hipótesis (rankeadas antes de tocar código)

- H1-CI (fuerte): `spring-scrub-rg.mjs` exige `rg` externo, ausente en runners `ubuntu-latest`.
  Evidencia: log CI `ERROR: ripgrep (rg) not found in PATH`, exit 2 en los 4 tests nuevos; local
  pasa porque macOS trae `rg`.
- H2-LINT (fuerte): `docs/publishing-failure-modes.md` tiene `Last Updated: 2026-09-27` pero el
  commit `a80b0b81` lo tocó el 2026-09-28. Evidencia: log del step
  `Documentation date freshness check`.
- H3-E2E (fuerte): TH-03 registra un mock `page.route(POST **/api/publishing/publications)` que hace
  `fulfill` sin insertar en la lista stateful del fixture base; el GET lista no trae el post y
  `getByText(testText)` expira. Evidencia: la aserción `postedAccountId` (línea 183) pasa, solo
  falla la visibilidad en lista (línea 186); TC-05 sin mock propio pasa con la misma aserción.
- H4-BDD (fuerte, aceptada): flake de orden en el propio test glue. `occurred_at = clock.instant()`
  generado en app puede empatar en los dos inserts concurrentes; `ORDER BY occurred_at ASC` devuelve
  orden arbitrario y `first.previousMode` deja de ser `OPEN` (línea 150). Evidencia: `size==2` pasó
  (descarta polución/reintentos), falló la línea 150 y no la 151 (descarta lost-update real, que
  fallaría en la 151), ambos 200 y modo persistido válido pasaron (escrituras serializan bien). La
  PR no toca `server/smp/src/**`; los 50 changelogs del diff son renames de autor preexistentes.

## Tareas

- [x] RPI-001 Reescribir el matching de `spring-scrub-rg.mjs` en Node puro (sin `rg`), mantener CLI
  y marcadores legacy, correr los 4 tests.
- [x] RPI-002 Actualizar `Last Updated` en `docs/publishing-failure-modes.md` a 2026-09-28 y correr
  `just doc-check`.
- [x] RPI-003 Convertir el mock POST de TH-03 en observador pass-through (`route.fallback()`),
  correr el spec localmente.
- [x] RPI-004 Push + vigilar los tres checks en la PR.
- [x] RPI-005 Ordenar la aserción de auditoría concurrente por cadena (`previousMode==OPEN` es
  first) en vez de por timestamp, correr el escenario BDD.
- [x] RPI-006 Reescribir autoría de la rama a la identidad verificada
  (`Yuniel Acosta Pérez <33158051+yacosta738@users.noreply.github.com>`) + force-push;
  `cla-assistant` verde.
- [ ] RPI-007 (bloqueado en decisión de contrato) Escenario `publishing-threads.feature:14` espera
  400 pero el provider deshabilitado responde 409 tras el converter de `e7c938be`.

## Criterios de aceptación

- `Skill governance regression tests` verde en CI.
- `Lint` verde en CI.
- `Dashboard E2E Mocked (3/4)` verde en CI.
- `Backend BDD` (Postgres) verde en CI.

## Estado

Ready — RPI-001/002/003/005 verificados; `Backend BDD` verde con el fix de cadena; `cla-assistant`
verde tras reescritura de autoría. Nota: aparecieron en el árbol cambios ajenos a esta tarea
(`PublishingWebFluxConfiguration.kt`, `PublishingProviderPathBindingTest.kt`,
`WebFluxConfigurationTest.kt`, `plan/tasks/threads-provider-binding.md`) que se preservaron
intactos.
