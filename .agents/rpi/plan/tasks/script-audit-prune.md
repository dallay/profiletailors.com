# Auditoría y poda pragmática de `scripts/`

## Ruta

Delegated direct. Auditoría de scripts del repositorio y sus consumidores, sin ciclo SDD.

## Objetivo

Reducir fricción y complejidad innecesaria en `scripts/` conservando únicamente scripts con un propósito vigente y consumidores demostrables o valor local claro. Eliminar solo lo que la evidencia permita retirar de forma segura.

## Tareas

- [x] Inventariar los 30 scripts originales; tras la poda quedan 27, clasificados por propósito y evidencia de consumidor activo.
- [x] Revisar referencias desde `Justfile`, `Makefile`, manifiestos, workflows, hooks, docs y código, separando usos activos de menciones históricas.
- [x] Revisar los grupos relacionados de desarrollo/servidores, contexto de worktree/Compose, wrappers de ejecución, release y cobertura; no se encontró equivalencia segura para consolidar sin perder comportamiento.
- [x] Clasificar cada script restante: 13 operativos, 11 con consumidor activo y 3 pruebas; ningún candidato adicional con evidencia suficiente.
- [x] Eliminar `scripts/check-just-postgres-boundaries.sh`, `scripts/check-doc-last-updated.mjs` y su test, además de retirar el check de fechas de CI y la receta/invocaciones activas en Justfile y Makefile.
- [x] Completar la revisión del resto de los scripts; no quedó poda adicional respaldada por evidencia.
- [x] Preservar cambios preexistentes; no tocar la eliminación staged `plan/tasks/sentry-integration.md`.

## Evidencia

- Inventario de referencias y usos activos.
- Diff final limitado a esta auditoría y sus artefactos directamente relacionados.
- Pruebas/checks pertinentes ejecutados, con resultados explícitos.

## Estado

Auditoría lista — retirados los dos checks confirmados como prescindibles y sus referencias activas. `just -l`, dry-runs de `just ci-local`, `make ci-local` y `make ci`, parseo YAML del workflow y `git diff --check` pasaron. Los 27 scripts restantes tienen usos operativos o consumidores activos; no se encontró otra poda/consolidación segura. No se ejecutó la suite completa porque no cambió código de producto.
