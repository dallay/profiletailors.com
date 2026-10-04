# Migrar artefactos OpenSpec y planes RPI

## Ruta

Delegated direct. Reubicar OpenSpec y los planes RPI a las rutas soportadas por el harness actual, preservando todo el contenido y actualizando las referencias activas necesarias.

## Tareas

- [x] Mover `openspec/` completo a `.agents/sdd/` sin sobrescribir contenido.
- [x] Mover `plan/tasks/docs-audit.md` a `.agents/rpi/plan/tasks/` y retirar `plan/tasks/` tras verificar.
- [x] Actualizar referencias activas a las rutas nuevas; conservar referencias históricas en artefactos archivados.
- [x] Verificar archivos, rutas de origen ausentes, conteos y referencias activas.

## Evidencia

- `.agents/sdd/` no existía; `openspec/` tenía 368 archivos, ahora en `.agents/sdd/`.
- `.agents/rpi/plan/tasks/` tenía 24 archivos; el `docs-audit.md` sin colisión ya está incorporado.
- Los archivos fuente `openspec/` y `plan/tasks/` ya no existen.
- Worktree limpio al comenzar.

## Estado

Ready
