# PR 1122 review fixes — plan

Ruta: Direct inline. Fix acotado de seguridad sobre la rama fix/security-audit-deep-fixes, sin SDD.

Esta iteración añade la remediación de CWE-770 en el rate limiter: la admisión de identificadores nuevos debe permanecer acotada incluso bajo concurrencia.

## Tareas

- [x] 1. Analytics.test.ts — revertir al contrato window-flag, reparar estructura rota
- [x] 2. UserControlHandlers.kt — comparación self-target insensible a mayúsculas + test regresión
- [x] 3. StoragePathValidator.kt — traversal por segmentos en keys + test regresión
- [x] 4. AuthRateLimitWebFilter.kt — admisión serializada y limitada para CWE-770 + test regresión
- [x] 5. WaitlistRateLimitIntegrationTest.kt — isolate the waitlist limiter from the global authentication filter

## Evidencia

- Review PR 1122 (3 hallazgos accionables)
- Rama: fix/security-audit-deep-fixes, worktree limpio al inicio
- Verificación por fix: suite enfocada + lint narrow sin nuevos findings
- CI remoto: Backend Unit, Backend Postgres y Quality Gate fallan en los tests de waitlist porque el filtro global puede responder antes que Bucket4j
