# Capability — External Contamination (P0-C)

## Purpose

Eliminar toda referencia cruzada a otros productos (CVIX,
`apps/portfolio`, `apps/blog`, `packages/testing-e2e`,
`profiletailors.resume`, bounded context `Resume`) dentro del
knowledge bundle canónico (`.agents/skills/**`). El bundle solo debe
mencionar el monorepo `profiletailors.com` y sus paths verificables.

## Authority

- Trazabilidad: bloque **P0-C** del `proposal.md`.
- Sin governance decision específica; aplica transversalmente el gate
  de `capability-automated-skill-doctor` (G-2).
- Excluidos explícitamente: ADR-0011 (histórico, KEEP) y
  `tmp/plans/2026-06-20-hexagonal-cleanup.md` (housekeeping aparte,
  fuera de scope).

## Scope

- Archivos modificados (DELETE puro):
  - `.agents/skills/frontend-platform/vue/SKILL.md` (footer `© 2024 CVIX`).
  - `.agents/skills/backend-platform/spring-boot/references/swagger-standard.md`
    (mención CVIX).
  - `.agents/skills/backend-platform/spring-boot/references/error-handling.md`
    (paquete `com.profiletailors.resume`, bounded context `Resume`,
    `ResumeExceptionHandler`, `InvalidResumeDataException`, etc.).
  - `.agents/skills/backend-platform/spring-boot/references/request-response-dtos.md`
    (`CreateResumeRequest`, `ResumeRequestMapper`, etc.).
  - `.agents/skills/languages-typing/kotlin/SKILL.md` (mención
    `profiletailors.resume`).
- No se hace rewrite de `testing/playwright/SKILL.md` aquí (eso vive
  en `capability-playwright-rebuild`, bloque P0-D). La regla
  "0 hits de contamination en el bundle completo" se valida al final
  del apply.

## Requirements

| REQ-ID                | Statement                                                                                                                                       |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-EC-001            | SHALL haber 0 hits del token `CVIX` en `.agents/skills/**` tras el apply.                                                                       |
| REQ-EC-002            | SHALL haber 0 hits del token `profiletailors.resume` en `.agents/skills/**` tras el apply.                                                     |
| REQ-EC-003            | SHALL haber 0 hits del bounded context `Resume` (typos como `ResumeExceptionHandler`, `ResumeRequestMapper`, `CreateResumeRequest`, `InvalidResumeDataException`) en `.agents/skills/**` tras el apply. |
| REQ-EC-004            | SHALL haber 0 hits de los tokens `apps/portfolio`, `apps/blog` o `packages/testing-e2e` en `.agents/skills/**` tras el apply combinado de P0-C y P0-D. |
| REQ-EC-005            | SHA历史 `tmp/plans/2026-06-20-hexagonal-cleanup.md` y `docs/architecture/adr/0011-reusable-lead-capture-waitlist.md` SHALL NO ser modificados por esta capability. |

## Scenarios

### Scenario: Vue SKILL.md sin CVIX footer

**REQ-EC-001**

- GIVEN `frontend-platform/vue/SKILL.md` línea ~316 contiene
  `<p>© 2024 CVIX</p>`
- WHEN se aplica esta capability
- THEN SHALL eliminarse esa línea; SHALL NO aparecer el token
  `CVIX` en ningún punto del archivo.

### Scenario: spring-boot references sin Resume bounded context

**REQ-EC-002, REQ-EC-003**

- GIVEN `spring-boot/references/{error-handling,
  request-response-dtos}.md` declaran `@RestControllerAdvice`
  para `com.profiletailors.resume` y ejemplos basados en
  `ResumeEntity` / `ResumeRequest`
- WHEN se aplica esta capability
- THEN SHALL reescribirse los ejemplos usando bounded contexts
  reales del monorepo (p. ej. `waitlist`, `invitations`,
  `governance`) y SHALL haber 0 hits de `Resume` y
  `profiletailors.resume` en esos archivos.

### Scenario: kotlin SKILL.md sin referencia a paquete inexistente

**REQ-EC-002**

- GIVEN `languages-typing/kotlin/SKILL.md` línea ~120 comenta sobre
  `profiletailors.resume` como ejemplo
- WHEN se aplica esta capability
- THEN SHALL sustituirse por un ejemplo con un paquete real del
  monorepo (p. ej. `com.profiletailors.smp.waitlist`) o eliminarse
  sin sustitución; SHALL NO contener `profiletailors.resume`.

### Scenario: ADR-0011 y tmp/plans/ intactos

**REQ-EC-005**

- GIVEN los archivos `docs/architecture/adr/0011-reusable-lead-capture-waitlist.md`
  y `tmp/plans/2026-06-20-hexagonal-cleanup.md` contienen menciones
  a `cvix-main`
- WHEN se aplica esta capability
- THEN SHALL NO modificarse ninguno de los dos archivos;
  SHALL documentarse en el `verify-report.md` que ambos quedan
  fuera de scope.
