# Capability — Version Policy (P1-A)

## Purpose

Eliminar toda versión literal hardcoded del knowledge bundle
(`SKILL.md` y `references/`), sustituyéndola por referencias a los
manifiestos canónicos del monorepo: `gradle/libs.versions.toml`
para Kotlin/Spring backend, y `package.json` por workspace para
frontend. La fecha del campo `metadata.updated` o similar SHALL
reflejar la última modificación real verificable, no un timestamp
de fabricación.

## Authority

- Trazabilidad: bloque **P1-A** del `proposal.md`.
- Sin governance decision específica; el gate de
  `capability-automated-skill-doctor` valida la invariante en CI.

## Scope

- Archivos modificados:
  - `.agents/skills/spring-boot-cache/SKILL.md`
    ("3.5+" → ref a `gradle/libs.versions.toml`).
  - `.agents/skills/spring-boot-security/references/{jwt-quick-reference,jwt-complete-configuration}.md`
    ("3.5.x" → ref a `gradle/libs.versions.toml`).
  - `.agents/skills/hexagonal-architecture/references/kotlin-clean-architecture.md`
    ("Kotlin 2.x" → ref a `gradle/libs.versions.toml`).
  - `.agents/skills/playwright/SKILL.md` ("Playwright
    1.58.2", "@axe-core/playwright 4.11.1" → refs al `package.json`
    correspondiente, p. ej. `apps/web/app/package.json`).
  - `.agents/skills/vitest/SKILL.md` (frontmatter `version:
    3.x` → ref a `package.json`).
  - `.agents/skills/pnpm/SKILL.md` (frontmatter `version: 10.x` → ref
    a `package.json` raíz o de pnpm).
  - `.agents/skills/pinia/SKILL.md` ("v3.0.4" → ref
    a `apps/web/app/package.json` o equivalente).
  - Frontmatter de `.agents/skills/{kotlin,typescript,zod-4}/SKILL.md`
    (`updated: 2026-01-28` → ref a la fecha de la última
    actualización real verificable).
- Excluidos: menciones descriptivas tipo "Vue 3" que describen
  identidad del proyecto, no una versión stale.

## Requirements

| REQ-ID                | Statement                                                                                                                                       |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-VP-001            | SHALL haber 0 ocurrencias de versiones literales hardcoded (`x.y.z`, `x.y`, `x.y+`) en el cuerpo de `SKILL.md` y `references/`, salvo que la versión sea descriptiva de identidad (p. ej. "Vue 3") y esté marcada explícitamente como identidad en `design.md`. |
| REQ-VP-002            | Toda mención de versión que deba aparecer SHALL sustituirse por una referencia neutral del estilo "(see `gradle/libs.versions.toml`)" o "(see `<workspace>/package.json`)". |
| REQ-VP-003            | El campo de fecha en frontmatter (típicamente `updated:` o equivalente) SHALL ser la fecha real de la última modificación verificable del archivo, no un timestamp fabricado. |
| REQ-VP-004            | El skill-registry SHALL regenerarse vía `sdd-init`; SHALL NO quedar con una columna `version` stale tras el apply.                              |
| REQ-VP-005            | Tras el apply SHALL existir un comando de validación ejecutable en worktree (parte de `capability-automated-skill-doctor`) que falle si detecta patrones de versión literal nuevos. |

## Scenarios

### Scenario: Spring Boot referencias removidas

**REQ-VP-001, REQ-VP-002**

- GIVEN `spring-boot/cache/SKILL.md` línea 13 dice "Spring Boot 3.5+"
  y `spring-boot/security/references/{jwt-quick-reference,
  jwt-complete-configuration}.md` línea 3 menciona "Spring Boot 3.5.x"
- WHEN se aplica esta capability
- THEN SHALL sustituirse por "the current Spring Boot version (see
  `gradle/libs.versions.toml`)" y SHALL NO aparecer "3.5+" ni
  "3.5.x" en ningún archivo del paquete `backend-platform/spring-boot/`.

### Scenario: Kotlin y Playwright referencias a manifests

**REQ-VP-001, REQ-VP-002**

- GIVEN `hexagonal-architecture/references/kotlin-clean-architecture.md`
  menciona "Kotlin 2.x" y `testing/playwright/SKILL.md` menciona
  "Playwright 1.58.2" y "@axe-core/playwright 4.11.1"
- WHEN se aplica esta capability
- THEN SHALL sustituirse por referencias a
  `gradle/libs.versions.toml` (Kotlin) y a
  `apps/web/app/package.json` (Playwright); SHALL NO haber
  patrones `\d+\.\d+(\.\d+)?` literales en el cuerpo de esos
  archivos (excepto fechas ISO o ejemplos ilustrativos no
  sensibles a versión).

### Scenario: Frontmatter `updated` refleja realidad

**REQ-VP-003**

- GIVEN las skills de `languages-typing/{kotlin,typescript,zod-4}`
  declaran `updated: 2026-01-28`
- WHEN se aplica esta capability
- THEN SHALL establecerse a la fecha real de la última edición
  del archivo (comprobable con `git log -1 --format=%cs <archivo>`),
  no al valor histórico.

### Scenario: CI rechaza versiones literales reintroducidas

**REQ-VP-005**

- GIVEN un PR introduce "Spring Boot 3.5" en una skill de backend
- WHEN corre el gate `skill-doctor`
- THEN SHALL fallar el job de deterministic scan (regex de versiones
  literales hardcoded) y SHALL bloquear el merge.
