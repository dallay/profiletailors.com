# Capability — Playwright Rebuild (P0-D)

## Purpose

Reescribir desde cero `.agents/skills/playwright/SKILL.md`
anclada a la realidad del monorepo `profiletailors.com`: las tres
superficies reales (`apps/web/app/`, `apps/web/admin/`,
`apps/web/marketing/`), sus configs Playwright cuando existan, el
consent contract compartido (`shared/web`), y el runner canónico
(`scripts/run-playwright.mjs`). Cada comando citado por la skill
debe ser ejecutable en el worktree actual.

## Authority

- Trazabilidad: bloque **P0-D** del `proposal.md`.
- Sin governance gate específico. The skill is maintained through
  ordinary review against the current Playwright configs and commands.
- Decisión cerrada: rewrite (no update parcial), anclado a la
  superficie `apps/web/{app,admin,marketing}` + `shared/web`.

## Scope

- Archivos modificados:
  - `.agents/skills/playwright/SKILL.md` (rewrite desde cero).
  - Adyacente: `.agents/skills/playwright-best-practices/SKILL.md`
    se reescribe para usar ejemplos Vue/Astro (no Next.js); este
    cambio queda registrado en `capability-modern-best-practices`
    (P1-B) como parte de la coherencia de skills testing, pero la
    regla "0 contaminación portfolio/blog/testing-e2e" se cumple aquí.
- Verificaciones cruzadas sobre `apps/web/app/e2e/playwright.config.ts`,
  `apps/web/marketing/playwright.config.ts`, `shared/web/`,
  `scripts/run-playwright.mjs`.

## Requirements

| REQ-ID                | Statement                                                                                                                                       |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-PR-001            | El SKILL.md SHALL declarar los tres directorios de E2E reales: `apps/web/app/e2e/specs/`, `apps/web/marketing/tests/e2e/`, y declarar explícitamente el gap de `apps/web/admin/` (sin E2E hoy). |
| REQ-PR-002            | El SKILL.md SHALL referenciar el runner compartido `scripts/run-playwright.mjs` y SHALL documentar el contrato HAR-based API mocking presente en `apps/web/app/e2e/playwright.config.ts`. |
| REQ-PR-003            | El SKILL.md SHALL distinguir `testDir: ./specs` (apps/web/app) versus `testDir: ./tests/e2e` (apps/web/marketing).                               |
| REQ-PR-004            | Todo comando citado en el SKILL.md SHALL ser ejecutable en el worktree; SHALL corresponder a `just frontend-test-e2e`, `just app-test-e2e-media-mocked`, `just app-test-e2e-media-real`, o `pnpm` directo sobre un config existente, y SHALL listar el consent contract de `shared/web` (key `pt-consent`, campo `consentVersion`) cuando aplique a tests de marketing o app. |
| REQ-PR-005            | SHALL haber 0 hits de los tokens `apps/portfolio`, `apps/blog`, `packages/testing-e2e`, `seed-portfolio.spec.ts`, `seed-blog.spec.ts` en el SKILL.md reescrito. |
| REQ-PR-006            | El SKILL.md SHALL documentar el contrato de consent en `shared/web` (`pt-consent`, `consentVersion`, `policyVersion`, `EXPECTED_CONSENT_VERSION`) como input a los E2E que rendericen surfaces de marketing o app. |
| REQ-PR-007            | El SKILL.md SHALL declarar que `apps/web/admin/` no tiene suite E2E actualmente y SHALL listar qué pasos faltan para habilitarla (configurar `playwright.config.ts`, instalar runner, etc.) sin fabricar paths. |

## Scenarios

### Scenario: Tres surfaces correctas, paths falsos eliminados

**REQ-PR-001, REQ-PR-005**

- GIVEN el SKILL.md actual nombra `apps/portfolio/`, `apps/blog/`,
  `packages/testing-e2e/`
- WHEN se aplica el rewrite
- THEN SHALL existir exactamente tres secciones de surfaces:
  `apps/web/app/`, `apps/web/admin/` (gap), y
  `apps/web/marketing/`; SHALL NO aparecer ninguno de los paths
  falsos ni los seeds asociados.

### Scenario: Comandos ejecutables en el worktree

**REQ-PR-002, REQ-PR-004**

- GIVEN el SKILL.md declara sus comandos en formato
  `just frontend-test-e2e`, `just app-test-e2e-media-mocked`, etc.
- WHEN se valida cada uno con `just -l` y con lectura del config
  correspondiente
- THEN SHALL existir la receta en `Justfile` o SHALL existir el
  config de Playwright invocado; SHALL NO citar comandos que
  apunten a surfaces inexistentes.

### Scenario: HAR-based mocking documentado

**REQ-PR-002**

- GIVEN `apps/web/app/e2e/playwright.config.ts` configura
  `use: { ... HAR files ... }`
- WHEN el SKILL.md se reescribe
- THEN SHALL contener una subsección "API mocking con HAR" referenciando
  ese config y SHALL explicar cuándo preferible sobre `route.fulfill`.

### Scenario: Consent contract referenciado

**REQ-PR-006**

- GIVEN el consent contract vive en `shared/web` con clave
  `pt-consent` y campo `consentVersion`
- WHEN se reescribe la skill
- THEN SHALL mencionarse el contrato cuando aplique a tests E2E
  que visiten `/` (marketing) o rutas autenticadas (app), y SHALL
  referenciar el `EXPECTED_CONSENT_VERSION` desde
  `shared/web/validation/consent.ts`.

### Scenario: Gap de admin declarado, sin paths fabricados

**REQ-PR-007**

- GIVEN `apps/web/admin/` no tiene `playwright.config.ts` ni
  carpeta `e2e/`/`tests/e2e/`
- WHEN el SKILL.md documenta esa surface
- THEN SHALL declarar el gap como tal (sin inventar specs) y SHALL
  listar el setup mínimo necesario.
