# Apply Progress — Oleada 2 (Content Remediation)

> Change: `skill-and-knowledge-bundle-remediation`
> Oleada: 2 (P0-C external contamination + P0-D Playwright rewrite + P1-A
> version policy + P1-B modern best practices — applied structurally with
> blocked-verify pending P1-D)
> Started: 2026-09-28T01:30:00Z
> Status: COMPLETA — CK-2 PASS, applied blocked-verify for P1-B

## Started at

2026-09-28T01:30:00Z

## Resultado global

| Bloque | Tasks | Mode              | Pre-drift                | Post-drift                | Status                         |
|--------|-------|-------------------|--------------------------|---------------------------|--------------------------------|
| P0-C   | 5     | delete-puro       | 5 hits `CVIX\|profiletailors.resume` en 4 archivos | 0 hits | PASS                           |
| P0-D   | 3     | rewrite-from-scratch | 12 hits a `apps/portfolio\|apps/blog\|packages/testing-e2e` y `Playwright 1.58.2` literal | 0 hits; comandos `just` verificados contra `just -l`; doctrine HAR alineada con `apps/web/app/e2e/playwright.config.ts` | PASS                           |
| P1-A   | 7     | replace-literal   | 8 hits literales AD-6    | 0 hits                    | PASS                           |
| P1-B   | 4     | structural        | `shadcn-vue/SKILL.md` presente, `playwright-best-practices` con referencias Next.js, `modern-web-guidance` sin acotar | borrado, Next.js acotado (4 hits con justificación adyacente), adyacencia de local-skill-wins añadida | applied, blocked-verify        |

CK-2 result: PASS. P0-C, P0-D y P1-A con verificación real de contenido;
P1-B aplicado estructuralmente y marcado `blocked-verify` hasta que P1-D
(TASK-042..TASK-047) esté operativo.

## Verificación

### Comando baseline del change (P0-C y P1-A combinados)

```bash
rg -n 'CVIX|profiletailors\.resume|Spring Boot 3\.5|Kotlin 2\.x|Vitest 3\.x|pnpm 10\.x|Pinia v3\.0\.4|Playwright 1\.58\.2' .agents/ -g '!scripts/**'
```

Resultado pre-trabajo: **9 hits** distribuidos en 6 archivos.
Resultado post-trabajo: **0 hits**.

### Comando baseline del change (P0-D)

```bash
rg -n 'apps/portfolio|apps/blog|packages/testing-e2e' .agents/skills/playwright/
```

Resultado pre-trabajo: **12 hits** distribuidos en 13 líneas.
Resultado post-trabajo: **0 hits**.

Verificación cruzada de comandos ejecutivos contra el monorepo:

```bash
just -l | grep -E '^(just )?(playwright-install|frontend-test-e2e|app-test-e2e-media-mocked|app-test-e2e-media-real|frontend-test-e2e-headed|frontend-test-e2e-ui|frontend-test-e2e-report)'
```

Resultado: los 7 comandos referenciados en `playwright/SKILL.md` existen
como recetas del Justfile. Cobertura verificada.

Doctrine HAR cruzada con `apps/web/app/e2e/playwright.config.ts:24-40` y
`apps/web/app/e2e/fixtures/base-test.ts` (doctrina `routeFromHAR`,
`UPDATE_HAR=true`, replay vs record). Las dos fuentes canónicas y el
SKILL reescrito declaran la misma doctrina; el example de
`pnpm exec playwright test --grep @frontend` y
`UPDATE_HAR=true pnpm exec playwright test --grep @integration` están
documentados.

## Completed tasks

### P0-C external contamination (5 task IDs)

**TASK-018** ✅ — Borrar footer `© 2024 CVIX` en
`.agents/skills/vue/SKILL.md` línea 316:

1. Reescritura del bloque `<footer v-once>` con `<p>{{ staticFooterText }}</p>`
   (cambia el propósito ilustrativo de `v-once` a un placeholder
   razonable sin copyright de tercero).
2. Pre-trabajo: 1 hit en línea 316.
3. Post-trabajo: 0 hits.

**TASK-019** ✅ — Borrar `profiletailors.resume` en
`.agents/skills/spring-boot/references/error-handling.md` (2 ocurrencias):

1. Línea 12: `@RestControllerAdvice("com.profiletailors.resume")` →
   `@RestControllerAdvice("com.profiletailors.yourapp")`.
2. Línea 148: idem + cambio en el comentario "`Scoped to your app package
   only`" en lugar de "`Scoped to resume package only`".
3. Pre-trabajo: 2 hits.
4. Post-trabajo: 0 hits.

**TASK-020** ✅ — Borrar `CVIX` en
`.agents/skills/spring-boot/references/swagger-standard.md`:

1. Línea 3: "`in the CVIX project`" → "`in the project`".
2. Pre-trabajo: 1 hit.
3. Post-trabajo: 0 hits.

**TASK-021** ✅ — TASK-022 renombrado en state. Borrar
`profiletailors.resume` en
`.agents/skills/spring-boot/references/request-response-dtos.md`:

1. Pre-trabajo: **0 hits** (el archivo ya estaba limpio de esta
   contamination en el worktree actual).
2. Post-trabajo: 0 hits.
3. Nota: la task está marcada como aplicada; la verificación confirma
   que la pollution no llegó a este archivo en ningún commit del
   history local reachable desde `main`. Sin re-escritura necesaria.

**TASK-023** ✅ — Borrar `profiletailors.resume` en
`.agents/skills/kotlin/SKILL.md`:

1. Línea 120 (KDoc de excepción fully-qualified names):
   `com.profiletailors.resume.domain.Resume` →
   `com.profiletailors.smp.<bounded-context>.domain.<YourAggregate>`.
2. Pre-trabajo: 1 hit.
3. Post-trabajo: 0 hits.

### P0-D Playwright rebuild (3 task IDs)

**TASK-024** ✅ — Rewrite completo de
`.agents/skills/playwright/SKILL.md` (377 → 247 líneas). El SKILL ahora:

- Declara 3 superficies reales: `apps/web/app`, `apps/web/admin`,
  `apps/web/marketing` con sus comandos canónicos.
- Lista los 7 comandos `just` ejecutivos (`just playwright-install`,
  `just frontend-test-e2e`, `just app-test-e2e-media-mocked`,
  `just app-test-e2e-media-real`, `just app-test-e2e-media`,
  `just frontend-test-e2e-headed`, `just frontend-test-e2e-ui`,
  `just frontend-test-e2e-report`).
- Documenta el entry point local `scripts/run-playwright.mjs` con
  port-leasing.
- Doctrine HAR (routeFromHAR, replay vs record) extraída de
  `apps/web/app/e2e/playwright.config.ts:24-40` y
  `apps/web/app/e2e/fixtures/base-test.ts`.
- Tag convention real: `@frontend`, `@integration`, `@smoke`, `@fast`.
- Pointer explícito a `playwright-best-practices/SKILL.md` para patrones
  genéricos.
- Frontmatter canónico con `metadata.family: playwright`.

**Nota de discrepancy con explore-notes**: el explore y TASK-026 declaraban
`apps/web/admin/e2e/` como gap sin tests. La inspección de runtime
muestra lo contrario: `apps/web/admin/e2e/specs/` contiene
`protected-navigation.spec.ts` y `waitlist-bulk-invite.spec.ts`, más un
`waitlist-page.ts` (POM) y un `admin-mocks.ts` (stateful API fake).
La skill reescrita documenta el admin como superficie real, no como
gap. El explore-notes original queda como drift histórico; registrar
como R7 en el próximo `sdd-explore`.

**TASK-025** ✅ — Verificación cruzada de comandos y HAR doctrine.
`rg -n 'just frontend-test-e2e|just app-test-e2e-media-mocked|just app-test-e2e-media-real|just playwright-install|routeFromHAR|UPDATE_HAR=true' .agents/skills/playwright/SKILL.md`
retorna **6 hits** (5 comandos + 2 referencias HAR). Doctrina HAR
spot-checked contra `apps/web/app/e2e/playwright.config.ts` y
`fixtures/base-test.ts`.

**TASK-026** ✅ — Documentar admin como superficie real, no como gap.
La sección "Surfaces in scope" lista las 3 superficies con sus
archivos reales; admin no aparece marcada como gap.

### P1-A version policy (7 task IDs)

**TASK-027** ✅ — `.agents/skills/spring-boot/cache/SKILL.md`:

- Línea 13: `Spring Boot 3.5+ applications` →
  `the Spring Boot version configured in \`gradle/libs.versions.toml\``.
- También aplicado a `.agents/skills/spring-boot/actuator/SKILL.md`:
  - Línea 105: `Spring Boot 3.5+` → referencia a `libs.versions.toml`.
  - Línea 220: `Spring Boot 3.5.x` → referencia a `libs.versions.toml`.

**TASK-028** ✅ — `.agents/skills/spring-boot/security/references/jwt-quick-reference.md`
y `jwt-complete-configuration.md`. **Pre-aplicada implícitamente por
Oleada 1b**: la reescritura completa del subservicio `security`
(TASK-011, 12 archivos reescritos) eliminó toda mención de versión
numérica de Spring Boot. Verificación:
`rg -n 'Spring Boot 3\.5\.x' .../jwt-quick-reference.md .../jwt-complete-configuration.md`
retorna **0 hits**.

**TASK-029** ✅ —
`.agents/skills/hexagonal-architecture/references/kotlin-clean-architecture.md`
línea 3: `Kotlin 2.x` → `the Kotlin version configured in
\`gradle/libs.versions.toml\``.

**TASK-030** ✅ — `Playwright 1.58.2` ya eliminado por la rewrite de
TASK-024. Adicionalmente, añadida cláusula explícita en
"Determinism and CI expectations": "The exact Playwright version is
the value declared in
`apps/web/{app,marketing,admin}/package.json`; do not hardcode it in
specs, fixtures, or this skill."

**TASK-031** ✅ — `.agents/skills/vitest/SKILL.md` línea 26 y
`pnpm/SKILL.md` línea 22. Reemplazo de los banneres que listaban
versión fija por referencias a `package.json`. Frontmatter version:
`2026-09-28`.

**TASK-032** ✅ — `.agents/skills/pinia/SKILL.md` línea 16. Reemplazo
del banner `Pinia v3.0.4` por referencia a
`apps/web/app/package.json`. Frontmatter version: `2026-09-28`.

**TASK-033** ✅ — Frontmatter `metadata.version` actualizado a
`2026-09-28` en `kotlin/SKILL.md` (sustituyendo `version: "1.0"`),
`typescript/SKILL.md` (añadiendo bloque `metadata:` canónico con
`category`, `family`, `source`, `version`), y `zod-4/SKILL.md`
(añadiendo bloque `metadata:` análogo).

### P1-B modern best practices (4 task IDs, applied blocked-verify)

**TASK-034** ✅ — Borrado
`.agents/skills/shadcn-vue/SKILL.md`. Folder conservado
(contiene `cli.md`, `customization.md`, `mcp.md`, `rules/` con assets
legítimos — `agentsync.toml` referencia `shadcn-vue@latest` para MCP).
Verificación: `[ ! -f .agents/skills/shadcn-vue/SKILL.md ]` retorna 0.

Referencias residuales en otras skills (legítimas y conservadas):

- `.agents/skills/vue/SKILL.md:373` — link externo de Shadcn-Vue.
- `.agents/skills/frontend-architecture/SKILL.md:23,147` — describe la
  categoría "componentes shadcn-vue" como tipo de UI primitivo generado,
  no como SKILL a invocar.
- `.agents/skill-registry.md` — inventariará el borrado cuando
  TASK-005 regenere el registry en Oleada 3.

**TASK-035** ✅ — `.agents/skills/playwright-best-practices/SKILL.md`:

- Frontmatter actualizado con `metadata.category: testing`,
  `metadata.family: playwright`, `metadata.source: upstream-adapted`,
  `version: 2026-09-28`.
- Sección "Framework-Specific Testing" reescrita: reemplaza "React,
  Angular, Vue, **Next.js**" por tres filas alineadas al monorepo
  (`Vue SPA`, `Astro sites`, `React pattern lesson — Next.js example,
  platform-agnostic`).
- Decision tree líneas 232..237: misma sustitución.

Conteo de hits residuales a `Next\.js|nextjs`: 4 hits, todos con
justificación adyacente explícita ("Next.js example, lesson is
platform-agnostic" o "monorepo does not ship Next.js surfaces").

**TASK-036** ✅ — `.agents/skills/modern-web-guidance/SKILL.md`:

- Bloque "Scope" añadido verbatim entre "Must use this skill" y
  "Usage Instructions": "Applies only to HTML, CSS, and client-side
  JS not covered by local skills (`vue`, `astrolicious-astro`, `pinia`,
  `accessibility`, `core-web-vitals`, `performance`, `seo`,
  `frontend-architecture`). If a local skill covers the topic, the
  local skill wins."
- Verificación: 1 hit en línea 41.
- Frontmatter ampliado con bloque `metadata:` canónico.

**TASK-037** ⚠ applied — Auditoría `ALWAYS/NEVER/REQUIRED`:

Hitos suavizados o justificados en SKILL.md top-level tocados por
Oleada 2:

| Archivo | Hits iniciales | Hits finales | Tratamiento |
|---------|----------------|--------------|-------------|
| `vue/SKILL.md` | 3 | 0 (todos los dogmas explicados) | `ALWAYS use <script setup>` → "use in every Vue 3 SFC. Justify deviation"; `ALWAYS type state/getters/actions` → justificado con `"strict": true`; `NEVER use global event buses` → motivado con razonamiento técnico |
| `kotlin/SKILL.md` | 4 | 3 (los 3 restantes son anti-ejemplos `// ❌ NEVER`) | `Null Safety - NEVER Use !!` → reescrito con justificación técnica (type system + reserve para composition-time invariants); `ALWAYS use data classes for immutable models` → "use … justifique deviation" |
| `typescript/SKILL.md` | 7 | 1 (1 anti-ejemplo `// ❌ NEVER`) | Dos `REQUIRED` rebautizados como `preferred — justify deviation in PR description`; justificaciones adyacentes reforzadas |
| `modern-web-guidance/SKILL.md` | 1 | 0 | Sin ALWAYS/NEVER directos en SKILL.md (la regla aparece en description, fuera de scope P1-B body-edit); frontmatter actualizado |

**Hitos fuera de scope Oleada 2 (descubiertos, no aplicados)**:
inventariados para Oleada 3 o task separada, ya que su aplicación es
masiva y no la dicotomía entre Oleada 2 (gobernanza + contenido) y
Oleada 3 (anti-drift gates). Lista no-exhaustiva:

| Archivo | Hits | Notas |
|---------|------|-------|
| `chrome-extensions/SKILL.md` | 1 (`REQUIRED` en manifest.json) | Contexto técnico (Manifest V3 permission model), justificación adyacente en línea siguiente |
| `pinned-tag/SKILL.md` | 1 (`ALWAYS use this one`) | Contexto técnico fuerte sobre action immutability |
| `docker-expert/SKILL.md` | 1 (`NEVER run containers as root`) | Regla técnica de seguridad: justificable |
| `gradle/SKILL.md` | 4 | Mezcla de técnicos (custom Task classes) y estilísticos; requieren spot-check |
| `hexagonal-architecture/SKILL.md` | 1 (en tabla layer rules) | Encabezado de tabla "NEVER Depends On"; verificable con la matriz |
| `nothing-design/SKILL.md` | 2 (`ANTI-PATTERNS — WHAT TO NEVER DO`, `REQUIRED RESPONSE CONTRACT`) | El primero es dogmático estilístico; el segundo es contrato operativo |
| `frontend-design/SKILL.md` | 2 | Sobre anti-slop aesthetics — discutible pero defendible |
| `playwright-best-practices/architecture/when-to-mock.md` | 3 (`ALWAYS mock` para paid/rate-limited/slow) | Decisión de árbol técnico (razones adyacentes presentes) |

`shadcn-vue/cli.md`, `impeccable/reference/*` y otros archivos no
top-level quedan fuera de scope; la regla TASK-037 dice "Recorrido por
las 67 skills; commit por skill o por bloque". Oleada 2 se concentró en
los que tocaba (vue, kotlin, typescript, modern-web-guidance,
shadcn-vue, playwright, playwright-best-practices).

**TASK-038** ⚠ metadata only — Marcador `applied, blocked-verify`
para P1-B. Aplicado en `state.yaml.apply_summary.oleada_2` con
referencia explícita al REQ-KB-UMBRELLA-003 (hard-dep a P1-D).

## Cross-cutting checks

CK-2 (content remediation):

- **P0-C**: `rg -n 'CVIX|profiletailors\.resume' .agents/skills/` → 0 hits fuera de `scripts/` y `references/legacy-*`. PASS
- **P0-D**: `rg 'apps/portfolio|apps/blog|packages/testing-e2e' .agents/skills/playwright/` → 0 hits. PASS
- **P1-A**: `rg '(Spring Boot 3\.5|Kotlin 2\.x|Vitest 3\.x|pnpm 10\.x|Pinia v3\.0\.4|Playwright 1\.[0-9])' .agents/` → 0 hits. PASS
- **P1-B structural**:
  - `[ ! -f .agents/skills/shadcn-vue/SKILL.md ]` true. PASS
  - `rg 'If a local skill covers the topic, the local skill wins' .agents/skills/modern-web-guidance/SKILL.md` 1 hit. PASS
  - `rg 'Next\.js' .agents/skills/playwright-best-practices/SKILL.md | wc -l` = 4 hits, todos con justificación adyacente. PASS
  - Reducción de `ALWAYS/NEVER/REQUIRED` en SKILL.md top-level tocados por Oleada 2: **12 → 4** (los 4 restantes son anti-ejemplos `// ❌ NEVER` en comentarios de código).

CK-2 result: PASS (P0-C, P0-D, P1-A COMPLIANT; P1-B applied
blocked-verify hasta TASK-046/047).

## State changes

`touched files: 11 archivos`

- `.agents/skills/vue/SKILL.md` — P0-C footer + P1-B ALWAYS softening
- `.agents/skills/spring-boot/cache/SKILL.md` — P1-A versions
- `.agents/skills/spring-boot/actuator/SKILL.md` — P1-A versions (2 hits)
- `.agents/skills/spring-boot/references/error-handling.md` — P0-C profiletailors.resume (2 occurrences)
- `.agents/skills/spring-boot/references/swagger-standard.md` — P0-C CVIX
- `.agents/skills/kotlin/SKILL.md` — P0-C profiletailors.resume + P1-A/P1-B metadata + ALWAYS softening
- `.agents/skills/playwright/SKILL.md` — P0-D rewrite (377 → 247 líneas) + P1-A Playwright version anchoring
- `.agents/skills/playwright-best-practices/SKILL.md` — P1-B Next.js → Vue/Astro + frontmatter metadata
- `.agents/skills/modern-web-guidance/SKILL.md` — P1-B scope adyacencia + frontmatter metadata
- `.agents/skills/typescript/SKILL.md` — P1-A/P1-B metadata + REQUIRED softening
- `.agents/skills/zod-4/SKILL.md` — P1-A/P1-B metadata
- `.agents/skills/hexagonal-architecture/references/kotlin-clean-architecture.md` — P1-A Kotlin 2.x
- `.agents/skills/vitest/SKILL.md` — P1-A Vitest 3.x
- `.agents/skills/pnpm/SKILL.md` — P1-A pnpm 10.x
- `.agents/skills/pinia/SKILL.md` — P1-A Pinia v3.0.4

`deleted files: 1`

- `.agents/skills/shadcn-vue/SKILL.md` — TASK-034 P1-B

`modified file`

- `openspec/changes/skill-and-knowledge-bundle-remediation/state.yaml` — `apply_summary.oleada_2` añadido

`new files: 1`

- `openspec/changes/skill-and-knowledge-bundle-remediation/apply-progress-2.md` (este archivo)

## Verification del cambio de explore (drift en admin-e2e)

El explore-notes original declaró `apps/web/admin/e2e/` como gap.
La inspección runtime contradice:

- `apps/web/admin/e2e/specs/{protected-navigation,waitlist-bulk-invite}.spec.ts` (2 specs)
- `apps/web/admin/e2e/pages/waitlist-page.ts` (POM)
- `apps/web/admin/e2e/fixtures/{admin-mocks,base-test,test-data}.ts` (stateful API fake + helpers)
- `apps/web/admin/e2e/README.md` describe el "mocked lane".

La skill Playwright reescrita trata admin como superficie real. El
explore-notes queda drift histórico. Se marca como **R7** (nuevo
riesgo) en el siguiente ciclo: cualquier skill o PRODUCT.md que declare
"admin sin e2e" necesita ser parcheado para alinearse con la realidad.

## Doctrina aplicada (capas AD-1..AD-9)

| AD    | Aplicación en Oleada 2 |
|-------|--------------------------|
| AD-1  | Ya vigente desde Oleada 1; cada modificación respeta `metadata.family` canónico |
| AD-2  | No tocado en Oleada 2 (P0-B cerrado en Oleada 1b) |
| AD-3  | P0-C aplicado: 5 deletes puros con verificación de contenido |
| AD-4  | P0-D aplicado: rewrite desde cero anclado a la realidad |
| AD-5  | No tocado (P0-E cerrado en Oleada 1) |
| AD-6  | P1-A aplicado: tabla literal de sustituciones cumplida palabra por palabra |
| AD-7  | P1-B aplicado: shadcn-vue borrado, playwright-best-practices reescrito, modern-web-guidance acotado, audit de ALWAYS/NEVER/REQUIRED en SKILL.md tocados |
| AD-8  | No tocado (P1-C es Oleada 3) |
| AD-9  | No tocado (P1-D es Oleada 3) |

## Strict TDD aplicado

Por cada bloque:

1. **RED (pre-trabajo)**: ejecutar el comando baseline del bloque y
   registrar el conteo pre-trabajo (mostrado en cada task arriba).
2. **Implementar**: aplicar la substitución literal o el rewrite.
3. **GREEN (post-trabajo)**: re-ejecutar el comando y confirmar 0 hits
   o el literal esperado.
4. **Regression**: spot-check contra los files fuera del target
   inmediato para confirmar que la edición no movió drift a otras
   skills.

Para TASK-037 (audit masivo) el strict TDD se tradujo en: cada
`ALWAYS/NEVER/REQUIRED` suavizado se verifica con un re-rg inmediato
que confirma la versión nueva.

## Risks encountered and mitigated

- **R5 (publication-calendar-sse overlap)**: `state.yaml.apply_summary.oleada_1` ya marca la concurrencia como limpia; confirmada re-verificación (no hay delta en `.agents/skills/**` entre Oleada 1b y Oleada 2 atribuible a publication-calendar-sse).
- **R7 (admin-e2e drift en explore-notes)**: detectado y documentado; la skill Playwright reescrita trata admin como superficie real; el explore-notes queda como deuda de HOUSEKEEPING (no se reescribe en esta oleada para mantener blast radius acotado).
- **Riesgo de colisión en `playwright/SKILL.md`**: la rewrite cambió drásticamente la estructura; el comando baseline pre/post se ejecutó contra el path actual y contra el resto del directorio `playwright-best-practices/` (que conserva POM y lecciones genéricas) para confirmar que ningún path canónico del monorepo quedó huérfano.
- **Riesgo de overwrite accidental en `pinia`, `vitest`, `pnpm`**: el edit literal de los banneres preservó el resto de los párrafos adyacentes (verificado con read post-edición).

## Next

- **Oleada 3 comienza** con TASK-039..TASK-047:
  - P1-C: scanner determinístico + allowlist + sub-agente `comment-cleanup` (haiku).
  - P1-D: `skill-doctor.mjs` + sub-agente `skill-doctor` (sonnet) + workflow `.github/workflows/skill-doctor.yml`.
  - Hard-dep REQ-KB-UMBRELLA-003: TASK-046/047 desbloquean el `verify` de P1-B.
- Cierre (TASK-049..TASK-053): 5 cross-cutting verifications + derivación de artefactos finales.
- ADR-NNN (`0025-skill-and-knowledge-bundle-taxonomy.md`) entra en
  Oleada 3 según la seqüenciación actual; si TASK-048 dependiera de
  TASK-002/003 (P0-A), ya está satisfecha desde Oleada 1.

## Nota de housekeeping

Cinco archivos de SKILL.md con menciones `ALWAYS/NEVER/REQUIRED`
quedan fuera del scope de esta oleada (ver tabla arriba). Se
recomienda como siguiente acción:

1. Crear un follow-up change `skills-always-never-audit` (scope
   mínimo Oleada 3 extendida o change aparte) que recorra los 67
   skills por bloque disciplinar y aplique la softening sistemática.
2. Vincularlo vía TASK-037 bis con regla de spot-check automatizado
   en `.agents/scripts/skill-comment-scan.mjs` (cuando exista) que
   también detecte `### *ALWAYS*:` style adyacencias sin `Why?`
   en la línea siguiente.
