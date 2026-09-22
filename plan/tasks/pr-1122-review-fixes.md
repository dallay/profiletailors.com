# PR 1122 review fixes — plan

Ruta: Direct inline. Tres fixes acotados sobre la rama fix/security-audit-deep-fixes, sin SDD.

## Tareas

- [x] 1. Analytics.test.ts — revertir al contrato window-flag, reparar estructura rota
- [x] 2. UserControlHandlers.kt — comparación self-target insensible a mayúsculas + test regresión
- [x] 3. StoragePathValidator.kt — traversal por segmentos en keys + test regresión

## Evidencia

- Review PR 1122 (3 hallazgos accionables)
- Rama: fix/security-audit-deep-fixes, worktree limpio al inicio
- Verificación por fix: suite enfocada + lint narrow sin nuevos findings
