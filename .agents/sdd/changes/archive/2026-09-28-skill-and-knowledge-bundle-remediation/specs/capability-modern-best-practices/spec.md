# Capability — Modern Best Practices (P1-B)

## Purpose

Re-revisar las skills de Kotlin, TypeScript, Vue, Pinia, Vitest, pnpm,
Playwright contra las reglas dogmáticas declaradas en AGENTS.md
(section "Static Analysis and Linter Compliance") y las convenciones
efectivas del monorepo, eliminando contradicciones internas. Eliminar
del bundle la skill `shadcn-vue` (el proyecto usa `@profiletailors/ui`
propio). Sustituir ejemplos Next.js en `playwright-best-practices`
por ejemplos Vue/Astro. Documentar `modern-web-guidance` como
MANDATORY solo cuando aplique HTML/CSS/JS no cubierto por skills
locales. Auditar reglas dogmáticas (ALWAYS / NEVER / REQUIRED) para
que tengan razón técnica fuerte.

## Authority

- Trazabilidad: bloque **P1-B** del `proposal.md`.
- Hard dependency: `capability-automated-skill-doctor` debe estar
  cerrada (gate activo) antes de poder verificar la transversalidad.
  Constraint heredado de REQ-KB-UMBRELLA-003.
- Governance **G-2** (gate de CI) aplica transversalmente.

## Scope

- Archivos modificados:
  - Eliminar `.agents/skills/frontend-platform/shadcn-vue/SKILL.md`
    y la carpeta `shadcn-vue/` (con sus `references/`).
  - Reescribir `.agents/skills/testing/playwright-best-practices/SKILL.md`
    para usar ejemplos de Vue/Astro (no Next.js).
  - Aclarar `.agents/skills/modern-web-guidance/SKILL.md` para que
    su "MANDATORY" aplique solo a HTML/CSS/JS no cubierto por las
    skills locales.
  - Auditar `kotlin`, `typescript`, `vue`, `pinia`, `vitest`, `pnpm`,
    `playwright`, `playwright-best-practices`, `modern-web-guidance`
    en busca de reglas dogmáticas sin justificación técnica.
- Excluidos: `pm-utils`, `pinned-tag`, `skill-registry`, `agents/`,
  `commands/`, `scripts/`.

## Requirements

| REQ-ID                | Statement                                                                                                                                       |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-MBP-001           | SHALL eliminarse del bundle la skill `frontend-platform/shadcn-vue/` (carpeta completa y archivo SKILL.md).                                       |
| REQ-MBP-002           | `playwright-best-practices/SKILL.md` SHALL NO contener ejemplos basados en Next.js; SHALL usar ejemplos Vue/Astro alineados con `apps/web/app/` y `apps/web/marketing/`. |
| REQ-MBP-003           | `modern-web-guidance/SKILL.md` SHALL declarar "MANDATORY only for HTML/CSS/JS contexts not already covered by a local skill in `.agents/skills/`"  y SHALL listar las skills locales que ya cubren los temas canónicos (Vue, Astro, Pinia, etc.). |
| REQ-MBP-004           | Toda regla etiquetada ALWAYS / NEVER / REQUIRED en las skills auditas SHALL tener una sección "Rationale" adjacente o link a un ADR / AGENTS.md / DESIGN.md que la justifique técnicamente. |
| REQ-MBP-005           | Los tests asociados a las skills modificadas (Vitest de `apps/web/marketing/tests/`, Vitest de `shared/web/`) SHALL pasar tras el apply.       |
| REQ-MBP-006           | Esta capability SHALL marcarse cerrada solo cuando `capability-automated-skill-doctor` haya cerrado (gate de CI activo y bloqueando drift).   |

## Scenarios

### Scenario: shadcn-vue fuera del bundle

**REQ-MBP-001**

- GIVEN `.agents/skills/frontend-platform/shadcn-vue/SKILL.md` existe
  y el proyecto no consume shadcn-vue
- WHEN se aplica esta capability
- THEN SHALL eliminarse la carpeta completa de `shadcn-vue/` y SHALL
  NO aparecer `shadcn-vue` en `.agents/skill-registry.md` ni en
  `.agents/skills/` tras la regeneración.

### Scenario: Playwright best practices Vue/Astro

**REQ-MBP-002**

- GIVEN `playwright-best-practices/SKILL.md` cita fixtures, APIs
  o rutas de Next.js (p. ej. `app/`, `next/`, `pages/`)
- WHEN se aplica esta capability
- THEN SHALL reescribirse para usar fixtures de
  `apps/web/app/tests/` o `apps/web/marketing/tests/e2e/` y SHALL
  usar APIs de Vue/Astro cuando aplique; SHALL NO contener tokens
  como `next/`, `pages/`, `getServerSideProps`, `useRouter` (cuando
  sea específico de Next).

### Scenario: modern-web-guidance scoping

**REQ-MBP-003**

- GIVEN `modern-web-guidance/SKILL.md` actual se autodefine
  MANDATORY para "todos los proyectos HTML/CSS/JS"
- WHEN se aplica esta capability
- THEN SHALL declarar el scoping (solo donde no hay skill local) y
  SHALL enumerar las skills locales que ya cubren Vue, Astro, Pinia,
  Vitest, Biome, etc., con sus paths canónicos.

### Scenario: Rationale requerida

**REQ-MBP-004**

- GIVEN una skill del scope contiene un ALWAYS / NEVER / REQUIRED
  sin justificación
- WHEN se audita por el gate `skill-doctor` o por el sub-agente
- THEN SHALL requerir adyacente una sección "Rationale" que apunte
  a AGENTS.md, DESIGN.md o un ADR; SHALL fallar el gate si la regla
  dogmática carece de rationale.

### Scenario: Hard dependency del doctor

**REQ-MBP-006**

- GIVEN `capability-automated-skill-doctor` workflow aún no existe o
  no bloquea merges
- WHEN se intenta cerrar `capability-modern-best-practices`
- THEN SHALL quedar el estado "blocked" en `state.yaml` con nota
  explícita y SHALL NO permitir apply concurrente.

### Scenario: Tests de skills afectadas pasan

**REQ-MBP-005**

- GIVEN el apply modificó `playwright-best-practices/SKILL.md` y/o
  elimino `shadcn-vue/`
- WHEN se ejecuta `just frontend-test` y los Vitests de `shared/web`
- THEN SHALL pasar todas las suites sin nuevas fallas atribuibles a
  esta capability; cualquier falla preexistente SHALL reportarse
  en `verify-report.md` separadamente.
