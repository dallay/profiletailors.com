# Plan — Skill and Knowledge Bundle Remediation

## Ruta

Explicit SDD — Oleada 3 aplicada al cambio activo `skill-and-knowledge-bundle-remediation`. El cambio sigue en fase `verify` para una pasada final antes de `sdd-qa`.

## Tareas

- [x] RPI-001 Reconciliar el layout plano de `.agents/skills/`, incluyendo las skills Spring y la eliminación de `shadcn-vue`.
- [x] RPI-002 Implementar y probar `skill-doctor.mjs` con metadata, identidad, paths internos, contaminación, `--fail-on`, JSON y códigos de salida.
- [x] RPI-003 Normalizar los 66 entrypoints `SKILL.md`, corregir `astrolicious-astro` y generar el registro canónico.
- [x] RPI-004 Añadir workflow, ADR-0025, índice ADR y evidencia de la Oleada 3.
- [x] RPI-005 Ejecutar scanner, doctor, pruebas enfocadas y sincronización AgentSync; registrar bloqueos remotos.
- [ ] RPI-006 Confirmar que la pasada de `sdd-verify` posterior refleje los nuevos resultados del doctor y el scrub reactivo.

## Criterios de aceptación

- Existe una única skill por carpeta top-level bajo `.agents/skills/`, sin la jerarquía disciplinar retirada.
- Cada `SKILL.md` tiene frontmatter canónico, identidad consistente y metadata válida.
- El doctor detecta de forma determinística contaminación, metadata ausente/incorrecta, identidad y referencias rotas; soporta selección de fallos y JSON.
- El workflow de CI ejecuta doctor, scanner y revisión contextual con filtros de paths.
- Los artefactos OpenSpec reflejan la evidencia real y los cambios de base de datos no relacionados permanecen sin modificar.

## Evidencia

- `node .agents/scripts/skill-doctor.mjs --skills-dir .agents/skills` → exit 0 (`Inspected 66 skills; PASS`).
- `node .agents/scripts/skill-doctor.mjs --skills-dir .agents/skills --fail-on contamination --fail-on missing-frontmatter --fail-on missing-metadata --fail-on broken-paths --fail-on identity --fail-on invalid-values` → exit 0.
- `node .agents/scripts/skill-comment-scan.mjs --paths .agents/skills --allowlist .agents/scripts/skill-comment-allowlist.json` → exit 0 (`clean`).
- `node .agents/scripts/regen-skill-registry.mjs --skills-dir .agents/skills --output .agents/skill-registry.md --check` → exit 0 (`Registry is current: 66 top-level skills`).
- `node --test .agents/scripts/__tests__/*.test.mjs` → 12 tests pass (skill-doctor, skill-comment-scan, regen-skill-registry, spring-scrub-rg).
- `node .agents/scripts/spring-scrub-rg.mjs .agents/skills/<subservice>` → exit 0 para los 15 subservices.

## Estado

Verify — Oleada 3 aplicada; CK-1..CK-5 verdes localmente; pendiente re-verificación formal y QA.

## Siguiente paso

Ejecutar `sdd-verify` contra los nuevos artefactos y, si pasa, encadenar `sdd-qa` antes de archivar.
