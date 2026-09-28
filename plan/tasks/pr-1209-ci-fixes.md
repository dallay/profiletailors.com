# Plan — PR-1209 CI fixes

## Ruta

Delegated direct con `systematic-debugging`. Tres fallos independientes en la PR #1209, cada uno con causa raíz localizada. Sin commits ni push hasta verificar cada fix.

## Hipótesis (rankeadas antes de tocar código)

- H1-CI (fuerte): `spring-scrub-rg.mjs` exige `rg` externo, ausente en runners `ubuntu-latest`. Evidencia: log CI `ERROR: ripgrep (rg) not found in PATH`, exit 2 en los 4 tests nuevos; local pasa porque macOS trae `rg`.
- H2-LINT (fuerte): `docs/publishing-failure-modes.md` tiene `Last Updated: 2026-09-27` pero el commit `a80b0b81` lo tocó el 2026-09-28. Evidencia: log del step `Documentation date freshness check`.
- H3-E2E (fuerte): TH-03 registra un mock `page.route(POST **/api/publishing/publications)` que hace `fulfill` sin insertar en la lista stateful del fixture base; el GET lista no trae el post y `getByText(testText)` expira. Evidencia: la aserción `postedAccountId` (línea 183) pasa, solo falla la visibilidad en lista (línea 186); TC-05 sin mock propio pasa con la misma aserción.

## Tareas

- [x] RPI-001 Reescribir el matching de `spring-scrub-rg.mjs` en Node puro (sin `rg`), mantener CLI y marcadores legacy, correr los 4 tests.
- [x] RPI-002 Actualizar `Last Updated` en `docs/publishing-failure-modes.md` a 2026-09-28 y correr `just doc-check`.
- [x] RPI-003 Convertir el mock POST de TH-03 en observador pass-through (`route.fallback()`), correr el spec localmente.
- [x] RPI-004 Push + vigilar los tres checks en la PR.

## Criterios de aceptación

- `Skill governance regression tests` verde en CI.
- `Lint` verde en CI.
- `Dashboard E2E Mocked (3/4)` verde en CI.

## Estado

Checking — RPI-001/002/003 verificados en local; pendiente push y confirmación en CI.
