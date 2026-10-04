# Plan — Skill Knowledge Bundle Hardening

## Ruta

Explicit SDD — completar el cambio activo `skill-knowledge-bundle-hardening` por sus cinco slices,
preservando historia archivada y usando repository reality como autoridad.

## Tareas

- [x] SDD-001 Confirmar y documentar la línea base de PR 1: topología plana, manifiestos, OpenSpec y
  gates actuales.
- [x] SDD-002 PR 2: endurecer recursivamente todos los bundles Spring, reescribir guía incompatible
  y validar bundles reales en CI. Evidence: recursive Node scanner, scoped negative-guidance
  regression fixtures, active Spring asset cleanup, current Spring AI MCP patterns, CI invocation,
  focused tests, manual active-token scan, and real-root scan pass; ready for verification.
- [x] SDD-003 PR 3: alinear Vue, shadcn-vue, `@profiletailors/vue-ui`, diseño, seguridad web,
  modern-web-guidance y Kotlin. Evidence: vue skill updated `@profiletailors/vue-ui` and `app`
  filter; best-practices scrubbed X-XSS-Protection, polyfill.io, npm/yarn, JSON.parse/stringify;
  modern-web-guidance demoted from MANDATORY to fallback; kotlin coroutines rule clarified;
  nothing-design activates from DESIGN.md not explicit invocation; frontend-design defers to
  DESIGN.md; playwright security test fixed. All 16 script tests pass, spring-scrub-rg clean,
  skill-doctor 66/66 PASS, comment-scan clean, registry regenerated, git diff --check clean.
- [x] ~~SDD-004 PR 4: convertir Skill Doctor en validador recursivo de bundles, paths, metadata,
  familias y versiones.~~ Superseded by ADR-0026; scripts removed instead of extended.
- [ ] SDD-005 PR 5: agregar escenarios semánticos, ejecutar gates, regenerar registry y hacer
  auditoría independiente.
- [x] SDD-006 Eliminar los scripts deterministas y el workflow de CI; ADR-0026 registra la
  cancelación.

## Criterios de aceptación

- Las fuentes activas describen la topología plana `.agents/skills/<skill-id>/SKILL.md`.
- Las especificaciones canónicas expresan invariantes actuales y no snapshots de migración.
- La guía Spring activa usa Kotlin, coroutines, WebFlux, R2DBC, reactive security y MockK.
- La guía frontend usa `app`, `@profiletailors/vue-ui` y documenta correctamente shadcn-vue.
- `DESIGN.md` prevalece sobre skills genéricas y activa automáticamente la visualidad
  Nothing-inspired cuando corresponde.
- Skills se validan por code review; los scripts de gate de CI eliminados como solución permanente
  de drift.
- Los escenarios semánticos y gates relevantes pasan; cualquier check no ejecutado queda reportado
  explícitamente.

## Evidencia

Pendiente hasta completar cada slice. No se declarará éxito por ausencia de tokens ni por tests
unitarios aislados.

## Estado

Done — scripts deterministas eliminados por ADR-0026; PR 4 y PR 5 canceladas o pendientes. El estado
de git refleja la simplificación.
