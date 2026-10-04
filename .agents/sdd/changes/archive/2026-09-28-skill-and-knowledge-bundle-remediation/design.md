# Design — Skill and Knowledge Bundle Remediation

> Change: `skill-and-knowledge-bundle-remediation`
> Phase: design (post-spec)
> Inputs: `proposal.md`, `explore-notes.md`, `specs/**/spec.md` (10 capabilities, 78 REQs)
> Output: `tasks.md` will be authored by `sdd-tasks` against this document.
> Idioma del prose: español. Identificadores en inglés.

## Overview

Este diseño operacionaliza el "cómo" del remediation del bundle de conocimiento. Convierte las 78 REQs distribuidas en 10 capabilities (`umbrella-knowledge-bundle`, `skill-discovery-identity`, `backend-semantics`, `external-contamination`, `playwright-rebuild`, `ui-governance-precedence`, `version-policy`, `modern-best-practices`, `comment-cleanup`, `automated-skill-doctor`) en decisiones técnicas concretas, secuencia verificable, contrato de verificación por bloque P0/P1, y dependencias duras. No renegocia gobierno ni decisiones de usuario cerradas en `proposal.md`: las articula como `AD-1..AD-9`, cada uno anclado al `block` que ejecuta.

## Architecture decisions

### AD-1: Flat skill taxonomy (P0-A — REQ-SD-001..009)

- **Estructura final.** Una skill por top-level folder bajo `.agents/skills/<id>/SKILL.md`. Subcarpetas permitidas solo como excepción documentada:
  - `impeccable/{scripts,agents,reference}/` — ya vigente; el propio SKILL referencia `scripts/context.mjs`, `reference/new-work.md`, `reference/craft-floor.md`.
  - `astrolicious-astro/` y skills con `references/`, `fixtures/` con assets/SKILL.md companions (e.g. `pnpm/references/`, `playwright-best-practices/fixtures/`-style).
  - Subcarpetas estructurales de **jerarquía disciplinar** quedan prohibidas: ya no existen `backend-platform/`, `frontend-platform/`, `testing/`, `languages-typing/`, `design-pattern/`. Cada subcarpeta actual se eleva a top-level (ej. `backend-platform/spring-boot` → `spring-boot`; `frontend-platform/vue` → `vue`; `design-pattern/builder` → `builder`).
- **Subcarpetas que se conservan como agrupador lógico no-disciplina** (no rompen flatness): `impeccable/`, `astrolicious-astro/`, `pnpm/references/`, y una sola carpeta compartida `references-assets/` solo si tras el flatten dos o más skills necesitan el mismo archivo físico (evaluar caso por caso; por defecto NO se crea).
- **Frontmatter schema canónico (codificado en `skill-doctor.mjs`):**
  ```yaml
  ---
  name: skill-id                       # MUST == folder name
  description: <plain text, ~200 chars max>
  metadata:
    category: backend-platform | frontend-platform | testing | governance | languages-typing | design | devops | design-pattern | knowledge-format | none
    family: spring-boot | vue | playwright | vitest | typescript | kotlin | css | markdown | none
    source: local | upstream-adapted
    version: <YYYY-MM-DD of last substantive edit>
  ---
  ```
- **`skill-registry.md` se regenera** por `sdd-init` (script canónico `.agents/scripts/regen-skill-registry.mjs`, responsabilidad del propio `sdd-init`). **No se edita a mano.** Queda anotado en la cabecera del archivo como "Auto-generated".
- **Excepciones al match `name == folder`**: solo `impeccable` (folder `impeccable/`, name `impeccable` — sí match), `astrolicious-astro` (folder `astrolicious-astro/`, name `astrolicious-astro` — sí match). La regla nunca se rompe; las excepciones se listan solo cuando el frontmatter necesita `name` distinto del folder (este change **no requiere** ninguna excepción: todo encaja tras el flatten).
- **Naming de folders nuevos**: lowercase, kebab-case, sin prefijos de categoría (no `backend-postgres-r2dbc`, sí `r2dbc`).
- **Idempotencia**: el doctor trata `--skills-dir <path>` como árbol a validar; re-ejecución sin cambios produce mismo veredicto.

### AD-2: Backend doctrine alignment (P0-B — REQ-BS-001..013)

- **Wording exacto a sustituir en `.agents/AGENTS.md`** (este es el SHELL que `tasks.md` aplicará literalmente):
  - **Línea 384**, tabla de capas, fila "Application", columna "May depend on":
    - **Antes:** `Domain and inward-facing ports`
    - **Después:** `Domain, including domain-defined ports`
  - **Línea 394** (texto corrido "Put repository/gateway contracts on the inward-facing side..."):
    - **Antes:** `Put repository/gateway contracts on the inward-facing side and implementations in infrastructure; follow the existing context convention rather than importing an adapter into application code.`
    - **Después:** `Place repository/gateway ports/interfaces in domain; implement them in infrastructure; inject them into application services through composition. Follow the existing context convention rather than importing an adapter into application code.`
- **Patch a `docs/architecture/adr/0002-adhere-to-hexagonal-architecture.md`:**
  - **Frontmatter Status**: `Accepted` → `Accepted (amended)`. Añadir línea:
    `Updated: 2026-09-27 (amended by skill-and-knowledge-bundle-remediation)`
  - **Sección "Decision"**, añadir párrafo nuevo al final:
    > **Ports location (added 2026-09-27).** Repository/gateway ports/interfaces are defined in the domain layer as part of the domain's contract. Application services depend on those domain-defined ports and orchestrate use cases through them. Infrastructure adapters depend on application and implement those ports. Composition roots wire ports to adapters; no handler or controller may import a concrete persistence or external-client adapter to bypass the application use case.
- **`spring-boot/SKILL.md` "Local Architectural Markers":** la sección actual inventa `com.profiletailors.common.application.ApplicationService` que **no existe** (`find -name ApplicationService.kt` retorna vacío). Se reescribe completamente:
  - Referencia al marker real: `com.profiletailors.common.domain.Service` (single-class file en `shared/common/src/main/kotlin/com/profiletailors/common/domain/Service.kt:18`).
  - Mecanismo de discovery: `includeFilters` de `SmpApplication.kt` (`FilterType.ANNOTATION, com.profiletailors.common.domain.Service`); ver línea real en tiempo de tasks.
  - Prohibición explícita: `@Service`, `@Component`, `@Repository` de Spring en code de application. Permitidos solo en `infrastructure/` adapters.
- **Spring scrub línea por línea — estrategia reactiva:**
  - **No** se borran archivos. **No** se mueven a `archive/`. **No** se renombran a `legacy-`.
  - Por cada `references/*.md` bajo `.agents/skills/spring-boot/` (todas las subskills movidas a top-level por AD-1), se sustituyen los tokens incompatibles (HttpSecurity sin perfil reactiv, SecurityFilterChain bloqueante, OncePerRequestFilter servlet, MockMvc, AutoConfigureMockMvc, JpaRepository, spring.datasource.*, spring.jpa.*, spring-boot-starter-web sin matiz reactiv, spring-boot-starter-data-jpa, Lombok `@RequiredArgsConstructor`, `MockitoExtension`, `@MockBean`) por **plantillas reactivas** tomadas de `spring-boot/testing-webflux/SKILL.md` (ya canónico para la realidad del monorepo: `WebTestClient`, `StepVerifier`, MockK en vez de Mockito, `R2dbcRepository`, coroutine `runTest { }`).
  - **Atomicidad por archivo.** Cada `references/*.md` reescrito se commitea (cuando aplique) con su propio commit `docs(skills): rewrite spring-boot/<x>/references/<file>.md to reactive`. No se commitea como bloque masivo.
  - **Inventario reactivo:** el primer task de P0-B es ejecutar `rg -n 'HttpSecurity|SecurityFilterChain|OncePerRequestFilter|MockMvc|AutoConfigureMockMvc|JpaRepository|spring.datasource|spring.jpa|starter-web|starter-data-jpa|RequiredArgsConstructor|MockitoExtension|@MockBean' .agents/skills/spring-boot/references/` para producir el listado exacto que `tasks.md` enumerará.
- **Plantilla de sustitución** (extracto — se materializa en `tasks.md`):
  - En vez de `MockMvc + @WebMvcTest`: `WebTestClient + @WebFluxTest`.
  - En vez de `suspend fun` con `MockMvc.perform()`: `suspend fun` con `webTestClient.post().uri(...).exchange().expectStatus().isCreated`.
  - En vez de `MockitoExtension + @Mock`: clase Anon o `mockk<T>()` (MockK ya está en `libs.versions.toml`).
  - En vez de `JpaRepository`: `R2dbcRepository<W, Id>` o `CoroutinesR2dbcRepository` cuando exista; mapear aggregate IDs.
  - En vez de `spring.datasource.*`: `spring.r2dbc.*` (pool, url, credentials via R2DBC).

### AD-3: External contamination removal (P0-C — REQ-EC-001..006)

Delete puro en exactamente estos 6 archivos. Cada uno como commit independiente con mensaje `chore(skills): remove external-contamination from <path>`. Cero dependencia entre borrados — pueden aplicarse en cualquier orden, pero el orden canónico es:

1. `.agents/skills/vue/SKILL.md` — borrar footer `© 2024 CVIX` (última sección del archivo).
2. `.agents/skills/spring-boot/references/swagger-standard.md` — borrar 1 hit `profiletailors.resume`.
3. `.agents/skills/spring-boot/references/error-handling.md` — borrar 1 hit `profiletailors.resume`.
4. `.agents/skills/spring-boot/references/request-response-dtos.md` — borrar 1 hit `profiletailors.resume`.
5. `.agents/skills/kotlin/SKILL.md` — borrar 1 hit `profiletailors.resume`.
6. ~~Playwright~~ — vive en P0-D (rewrite). **No** se borra parcialmente aquí.

**Verificación post-delete por archivo:** `rg 'CVIX|profiletailors\.resume' <archivo>` debe retornar 0 hits. Si retorna, no se marca el task como done. Sin excepciones ni allowlist.

### AD-4: Playwright rewrite (P0-D — REQ-PW-001..008)

- **Replacement strategy.** Borrar `.agents/skills/testing/playwright/SKILL.md` actual y `.agents/skills/testing/playwright/references/` (si existe). Reescribir desde cero anclado a la realidad del monorepo:
  ```text
  apps/web/app/
  ├── e2e/
  │   ├── playwright.config.ts           # base
  │   ├── playwright.media-mocked.config.ts
  │   ├── playwright.media-real.config.ts
  │   ├── playwright.scheduler.config.ts
  │   ├── playwright.pwa.config.ts
  │   ├── coverage-config.ts
  │   ├── fixtures/                       # base-test.ts con routeFromHAR
  │   ├── pages/                          # Page Object Models
  │   ├── scripts/
  │   └── specs/                          # *.spec.ts
  apps/web/admin/
  ├── e2e/                                # gap — sin tests hoy; skill lo declara
  └── package.json
  apps/web/marketing/
  ├── tests/e2e/
  │   ├── accessibility.spec.ts
  │   ├── consent.spec.ts
  │   ├── landing-page.spec.ts
  │   ├── seo.spec.ts
  │   └── waitlist-form.spec.ts
  ├── playwright.config.ts
  └── tests/e2e/README.md
  shared/web/                             # consent contract fixtures
  scripts/run-playwright.mjs              # entry point local, ya existe
  ```
- **Nuevo `SKILL.md` secciones obligatorias:**
  1. Cuándo invocar esta skill (triggers).
  2. Mapa de superficies E2E reales (las 4 de arriba) con su comando canónico.
  3. Comandos ejecutables verificados:
     - `just frontend-test-e2e` → marketing + app-media-mocked.
     - `just app-test-e2e-media-mocked` → app, suite `media-mocked`.
     - `just app-test-e2e-media-real` → app, suite `media-real`.
     - `just playwright-install` → browsers.
     - `pnpm exec playwright test --grep @frontend` (HAR replay solo).
     - `UPDATE_HAR=true pnpm exec playwright test --grep @integration` (record).
  4. Patrón HAR (routeFromHAR, replay vs record) extraído de `apps/web/app/e2e/playwright.config.ts:24-40` y `e2e/README.md`.
  5. Tag convention real: `@frontend`, `@integration`, `@smoke`, `@fast` (no inventar nuevos).
  6. Gap acknowledgment: `apps/web/admin/e2e/` está vacío; la skill lo declara y cita el ticket / Linear / nota (placeholder en texto si no hay ticket público todavía).
  7. Pointer a `playwright-best-practices/SKILL.md` para patrones genéricos (POM, expect locators, fixtures).
- **Límite de la rewrite**: NO se toca `playwright-best-practices/SKILL.md` (esa es knowledge genérico y sus ejemplos Next.js siguen siendo válidos como inspiración — el rework de ejemplos Next.js vive en P1-B).
- **Layout flat**: el folder final es `.agents/skills/playwright/` (top-level), no `.agents/skills/testing/playwright/`. El renaming lo consume AD-1.

### AD-5: UI precedence chain (P0-E — REQ-UIP-001..006)

Añadir al inicio de `.agents/DESIGN.md` (después del frontmatter YAML, antes de cualquier sección de tokens) la siguiente sección verbatim. Esta es la única sección canónica; el resto de DESIGN.md mantiene su estructura de tokens existente.

```markdown
## UI precedence chain

The following skills cooperate and have explicit precedence. When
multiple skills would otherwise contradict, this document wins.

1. `impeccable` (process and quality). Owns UX review, hierarchy,
   responsive behavior, accessibility, polish, workflow, visual QA.
2. `nothing-design` (visual language). Owns tokens, typography, motion,
   monochrome palette, dark/light first-class, spacing philosophy.
3. `frontend-design` (generic inspiration only). Use only when neither
   impeccable nor nothing-design applies. Existing product design
   systems always override its generic recommendations.

When a token or pattern in any of the above skills contradicts
DESIGN.md, DESIGN.md wins. CI enforces this via the skill doctor
(P1-D).
```

- **No se reordena** el resto de DESIGN.md. La sección va como PRIMERA sección del body (después del frontmatter YAML existente), para que cualquier lector la encuentre al abrir.
- **Enforcement**: el CI gate (P1-D) lee los tres skills (`impeccable`, `nothing-design`, `frontend-design`) y `DESIGN.md`, y emite severidad `block` cuando una contradicción es declarada y no referenciada a DESIGN.md.
- **No se modifica** ningún frontmatter ni el bloque `colors:`/`typography:`/`spacing:` de DESIGN.md.

### AD-6: Version policy (P1-A — REQ-VP-001..006)

Tabla de sustitución **exacta** (estos son los SHELL strings que `tasks.md` aplicará literales; nada de sustitución genérica):

| Encontrar (literal) | Reemplazar por |
|---|---|
| `Spring Boot 3.5+` | ``the Spring Boot version configured in `gradle/libs.versions.toml` `` |
| `Spring Boot 3.5.x` | ``the Spring Boot version (see `gradle/libs.versions.toml`) `` |
| `Kotlin 2.x` | ``the Kotlin version configured in `gradle/libs.versions.toml` `` |
| `Playwright 1.58.2` | ``the Playwright version (see `apps/web/{app,marketing,admin}/package.json`) `` |
| `Vitest 3.x` | ``the Vitest version (see `package.json`) `` |
| `pnpm 10.x` | ``the pnpm version (see `package.json`) `` |
| `Pinia v3.0.4` | ``the Pinia version (see `apps/web/app/package.json`) `` |
| Frontmatter `updated: 2026-01-28` o similar | Fecha real de la última verificación (capturar en commit message) |

- **Excepción explícita y conservada**: `Vue 3` es identidad, no versión → no tocar.
- **Verificación**: `rg -n 'Spring Boot 3\.5|Kotlin 2\.x|Playwright 1\.|Vitest 3\.x|pnpm 10\.x|Pinia v3\.0\.4' .agents/skills/ .agents/AGENTS.md .agents/DESIGN.md` debe retornar 0 hits.
- **No se introduce mecanismo runtime** (no plugin nuevo, no hook npm, no resolución dinámica). La política es **estática**: humanos editando skills verifican con `rg` y Skill Doctor (P1-D).

### AD-7: Modern best practices (P1-B — REQ-MBP-001..007)

- **Borrado:** `.agents/skills/shadcn-vue/SKILL.md` se elimina completo. El proyecto usa `@profiletailors/ui` propio; "shadcn-vue" no aplica.
- **Rewrite `playwright-best-practices/SKILL.md`**: cuando un ejemplo use Next.js pero la lección aplique al E2E del monorepo, sustituir el código Next.js por Vue (apps/web/app) o Astro (apps/web/marketing). Conserva lecciones genéricas (POM, fixtures, retries, accessibility).
- **`modern-web-guidance/SKILL.md`**: añadir nota en el cuerpo (no en description) — la description mantiene el `MANDATORY:` actual; el cuerpo aclara:
  > Applies only to HTML, CSS, and client-side JS not covered by local skills (`vue`, `astrolicious-astro`, `pinia`, `accessibility`, `core-web-vitals`, `performance`, `seo`, `frontend-architecture`). If a local skill covers the topic, the local skill wins.
- **Rules `ALWAYS` / `NEVER` / `REQUIRED`**: cada uno en cualquier skill debe justificarse con razón técnica adyacente (1-2 frases) o se reemplaza por texto más débil: `Prefer X. Justify deviation in PR description.` No se prohíbe la regla; se prohíbe la regla dogmática sin base.
- **Hard dependency** (REQ-KB-UMBRELLA-003, REQ-SD-008, REQ-MBP-006): este bloque **no puede cerrar** hasta que `automated-skill-doctor` (P1-D) esté operativo y validando SKILLs en CI. `tasks.md` lo refleja explícitamente con `depends-on`.

### AD-8: Comment cleanup pipeline (P1-C — REQ-CC-001..007)

- **Wording exacto a sustituir en `.agents/AGENTS.md`** (este es el SHELL canónico):
  - **Reemplaza** el párrafo:
    > Never leave comments in the repo. The standard is zero comments: no explanatory comments or docblocks, TODO/FIXME notes, lint/type suppression directives, or commented-out code. Express intent through names, structure, and tests; put rationale in commit messages or PR descriptions. Interpreter shebangs are executable directives, not comments.
  - **Por**:
    > Prefer self-documenting code. The agent MUST NOT generate explanatory comments or docblocks by default; intent should be expressed through names, types, structure, and tests. Comment policy is enforced during final cleanup, not as an architectural invariant.
    >
    > Allowed comments: SPDX file-header licenses in `License*.kt` / `LICENSE-*.md` files, interpreter shebangs (e.g. `#!/usr/bin/env node`), and generated markers emitted by an approved generator (the script records the generator name in the file header). Nothing else. TODO/FIXME/HACK notes, `@Suppress`, biome-ignore, eslint-disable, `@ts-ignore`, `@ts-expect-error`, `@ts-nocheck`, `//nolint`, compiler-warning suppressions, formatter exclusions, and commented-out code remain strictly prohibited. The full enforcement list is codified in `.agents/scripts/skill-comment-scan.mjs` and its allowlist.
- **Scanner determinístico `.agents/scripts/skill-comment-scan.mjs`** — contrato:
  - **Runtime**: Node ≥ 20, zero dependencies (solo `node:fs`, `node:path`, `node:readline`).
  - **CLI**: `node .agents/scripts/skill-comment-scan.mjs --paths <dir> [--allowlist <json>] [--json]`. Exit code `1` si hay violations; `0` si clean.
  - **Patterns detectados** (regex literal):
    - `^\s*//` en archivos `.kt` y `.kts`.
    - `^\s*\*` en bloques KDoc (líneas que abren con `/**` o continuación del bloque abierto).
    - `^\s*//` en `.ts`, `.tsx`, `.vue`, `.astro` y `.js`/`.mjs`.
    - `<!--` en `.md` y `.astro` (excluye HTML comments legítimos en islands Astro con `--astro-allow-html` if available; por simplicidad inicial, todos se marcan).
    - `^\s*#[^!]` en `.py` y `.sh` (excluye shebangs `#!/...`).
    - Tokens explícitos en cualquier archivo: `\bTODO\b`, `\bFIXME\b`, `\bHACK\b`, `@Suppress`, `@file:Suppress`, `biome-ignore`, `eslint-disable`, `@ts-ignore`, `@ts-expect-error`, `@ts-nocheck`, `//nolint`, `detekt-disable`, `ktlint-disable`, `spotless:off`.
  - **Allowlist**: paths explícitos pasados por `--allowlist <json>` (default: archivo canonical `.agents/scripts/skill-comment-allowlist.json` con entries `{path, reason}` para `LICENSE-*.md`, `shared/common/src/main/kotlin/License.kt`, `.agents/scripts/**`, `generated_*.kt` marker, `*.gen.ts`).
  - **Output JSON** (`--json`): `{violations: [{path, line, col, patternId, snippet, allowed: false}]}`.
  - **No corrige; solo reporta.** El sub-agente `comment-cleanup` (siguiente punto) hace el rewrite.
- **Sub-agente `comment-cleanup`** en `.agents/agents/comment-cleanup.md` con `model: haiku`. Caso de uso: dado un archivo con violations complejas (KDoc multi-línea, comentarios justificados por dominio), evalúa y decide:
  - Si el comentario es required (header license, generated marker, shebang) → confirmarlo y proponer entry para allowlist.
  - Si no es required → proponer rewrite names/types más explícitos que sustituyan la intención.
- **Test**: `.agents/scripts/__tests__/skill-comment-scan.test.mjs` cubre:
  - Detecta los 7 pattern groups con al menos 1 fixture cada uno.
  - Respeta allowlist por path.
  - Exit code correcto (0/1).
  - No false-positives en shebangs y headers SPDX.
  - Runner: Vitest si ya está como devDep en el repo, en caso contrario `node --test` nativo.

### AD-9: Automated skill doctor (P1-D — REQ-SD-001..009)

- **Script determinístico `.agents/scripts/skill-doctor.mjs`** — contrato:
  - **Runtime**: Node ≥ 20, zero dependencies.
  - **CLI**: `node .agents/scripts/skill-doctor.mjs --skills-dir <dir> [--fail-on contamination|missing-frontmatter|missing-metadata|broken-paths] [--json]`. Exit `1` en violation; `0` clean.
  - **Validaciones por cada `<skill>/SKILL.md`:**
    1. **`frontmatter.present`**: archivo comienza con `---` y termina el bloque con `---` antes del cuerpo.
    2. **`frontmatter.name`**: presente y `name === folder_name` (carpeta parent del `SKILL.md`).
    3. **`metadata.category`**: presente y en enum conocido (`backend-platform`, `frontend-platform`, `testing`, `governance`, `languages-typing`, `design`, `devops`, `design-pattern`, `knowledge-format`, `none`).
    4. **`metadata.family`**: presente y en enum conocido (`spring-boot`, `vue`, `playwright`, `vitest`, `typescript`, `kotlin`, `css`, `markdown`, `none`).
    5. **`metadata.version`**: presente y match `\d{4}-\d{2}-\d{2}`.
    6. **`paths.broken`**: cada path referenciado (markdown `[text](path)` y `[text](#anchor)`) existe en worktree. Rutas absolutas `/...` se interpretan desde repo root.
    7. **`contamination.present`**: 0 hits para tokens `CVIX`, `profiletailors.resume`, `apps/portfolio`, `apps/blog`, `packages/testing-e2e`. Por archivo y total.
    8. **`subagent`**: presencia opcional de `.agents/agents/<same-name>.md` para skills que declaran sub-agente dedicado (warning, no block).
  - **Salida `--json`**: `{skills: [{path, checks: [{id, severity: pass|warn|block, message}]}]}`.
- **Sub-agente `skill-doctor`** en `.agents/agents/skill-doctor.md` con `model: sonnet` (no haiku — las contradicciones cross-skill requieren juicio). Caso de uso: dado el diff de un PR que toca `.agents/skills/**` o `docs/architecture/adr/**`, lee los archivos y emite severidad:
  - `pass`: sin contradicción.
  - `warn`: contradicción leve (e.g. versión hardcoded en skill secundario).
  - `block`: contradicción material con una fuente canónica (DESIGN.md, ADR-0002 amended, AGENTS.md amended) o contamination residual.
- **Workflow `.github/workflows/skill-doctor.yml`:**
  - **Trigger**:
    ```yaml
    on:
      pull_request:
        paths:
          - '.agents/**'
          - 'docs/architecture/adr/**'
    ```
  - **Permissions**: `contents: read`, `pull-requests: read`.
  - **Jobs**:
    1. **`deterministic-scan`** (Node 20): clona, ejecuta `node .agents/scripts/skill-doctor.mjs --skills-dir .agents/skills --json`. Si `exit != 0` → `fail`.
    2. **`comment-cleanup-scan`** (Node 20): ejecuta `node .agents/scripts/skill-comment-scan.mjs --paths .agents/skills --paths docs/architecture/adr . --json`. Si `exit != 0` → `fail` (incluye para P1-C; activable gradualmente).
    3. **`llm-audit`**: invoca sub-agente `skill-doctor` contra diff del PR. Output severity `pass | warn | block`. Si `block` → `fail`.
  - **Required check**: tras primer merge a main, marcar el workflow como required en branch protection de GitHub (operación manual registrada en PR description; no automatizable desde `sdd-apply`).
- **Listo por fase:** P1-A y P1-C pueden correr independientemente; P1-B y la rama restante de P0 necesitan P1-D operativo para verificar verdaderas contradicciones.

## Sequencing & dependencies

Texto narrativo antes del ASCII porque el diagrama solo muestra orden; las dependencias por REQ-KB-UMBRELLA-003 son contractuales.

El apply se ejecuta en dos oleadas para minimizar blast radius:

- **Oleada 1 (P0)** — bloqueante; sin ella, P1 no puede verificar.
- **Oleada 2 (P1)** — cierra la cobertura CI y las piezas dogmáticas restantes.

Orden por commit (cada bloque cierra un set de tasks con commit propio o por archivo cuando aplique):

```text
Oleada P0
═══════════════════════════════════════════════════════════════════════════
[1/9] P0-C external-contamination          (AD-3)     ─┐
                                                       │  paralelos · no
[2/9] P0-A flat skill taxonomy              (AD-1)    ─┤  comparten código
                                                       │  durante apply
[3/9] P0-B backend doctrine + Spring scrub  (AD-2)     ─┤
                                                       │
[4/9] P0-D Playwright rewrite               (AD-4)    ─┤
                                                       │
[5/9] P0-E UI precedence chain              (AD-5)    ─┘
                                                       │
                                                       ▼
                                         umbrella-knowledge-bundle
                                         (REQ-KB-UMBRELLA-001..007)
                                                       │
                                                       ▼
Oleada P1
═══════════════════════════════════════════════════════════════════════════
[6/9] P1-A version policy                   (AD-6)
[7/9] P1-C comment cleanup pipeline         (AD-8)
[8/9] P1-D automated skill doctor           (AD-9)  ◀── CI gate operacional
[9/9] P1-B modern best practices           (AD-7)  ◀── depende de [8/9] cerrado
```

**Dependencias contractuales (REQs explícitos):**

- **REQ-KB-UMBRELLA-003** (`capability-modern-best-practices` → `capability-automated-skill-doctor`): P1-B **no puede verificar COMPLIANT** mientras `skill-doctor.mjs` no esté operativo. `sdd-verify` reflejará esto en la matriz.
- **REQ-SD-008** (skill-doctor cubre contradiction detection): el campo `block` severity en `llm-audit` solo existe cuando el sub-agente `skill-doctor` está definido.
- **REQ-CC-005** (CI emite violations JSON): la cadena `deterministic-scan → --json → `llm-audit`` requiere el allowlist y el scanner; por eso **P1-C debe cerrar antes o junto con P1-D**, no después.
- **UMBRELLA-002** (umbrella coordina todos los bloques): antes de archivar, el `umbrella-knowledge-bundle/spec.md` debe verificarse con todo P0 y P1 cerrado.

**Concurrency note** (carry-over de proposal): `publication-calendar-sse` debe archivar antes de comenzar P0-A porque P0-A renombra folders de skills y el archiving de cualquier otro cambio tocando `.agents/skills/**` puede colisionar.

## Verification contract

Mapa REQ → verifiable script/escenario. Cada capability tiene su `verify-report.md` populated por `sdd-verify`. Esta tabla es la entrada canónica del verificador.

| Block / Cap | REQs clave | Verificación ejecutable |
|---|---|---|
| P0-A · `skill-discovery-identity` | REQ-SD-001..009 | `node .agents/scripts/skill-doctor.mjs --skills-dir .agents/skills --fail-on missing-frontmatter --fail-on missing-metadata` exit 0. |
| P0-B · `backend-semantics` | REQ-BS-001..013 | (a) `rg 'inward-facing' .agents/AGENTS.md` exit 1 (0 hits). (b) `rg '^name: \|@SpringBootTest\|@WebMvcTest\|@SpringBootApplication\|@MockBean\|JpaRepository\|@RequiredArgsConstructor\|MockitoExtension' .agents/skills/spring-boot/references/` solo permitido con template WebTestClient/StepVerifier/R2DBC adyacente (manual spot-check). (c) `cat shared/common/src/main/kotlin/com/profiletailors/common/domain/Service.kt | head -25` muestra marker real citado en spring-boot/SKILL.md. |
| P0-C · `external-contamination` | REQ-EC-001..006 | `rg -n 'CVIX\|profiletailors\.resume' .agents/skills/vue/SKILL.md .agents/skills/spring-boot/references/swagger-standard.md .agents/skills/spring-boot/references/error-handling.md .agents/skills/spring-boot/references/request-response-dtos.md .agents/skills/kotlin/SKILL.md` retorna 0 hits. |
| P0-D · `playwright-rebuild` | REQ-PW-001..008 | (a) `cat .agents/skills/playwright/SKILL.md` secciones obligatorias presentes. (b) `rg 'apps/portfolio\|apps/blog\|packages/testing-e2e' .agents/skills/playwright/` 0 hits. (c) `just frontend-test-e2e` corre en seco con `--list` (Playwright list tests, sin ejecutar). |
| P0-E · `ui-governance-precedence` | REQ-UIP-001..006 | `rg -n 'UI precedence chain' .agents/DESIGN.md` 1 hit; bloque verbatim coincide con AD-5. |
| P1-A · `version-policy` | REQ-VP-001..006 | `rg -n 'Spring Boot 3\.5\|Kotlin 2\.x\|Playwright 1\.\|Vitest 3\.x\|pnpm 10\.x\|Pinia v3\.0\.4' .agents/ .agents/skills/` 0 hits. |
| P1-B · `modern-best-practices` | REQ-MBP-001..007 | (a) `[ ! -f .agents/skills/shadcn-vue/SKILL.md ]` true. (b) Compliance depende de P1-D cerrado. `sdd-verify` marca REQ-MBP-* como `COMPLIANT` solo cuando CI pasa `skill-doctor`. |
| P1-C · `comment-cleanup` | REQ-CC-001..007 | (a) `rg -n 'Never leave comments in the repo\. The standard is zero comments' .agents/AGENTS.md` 0 hits. (b) `node .agents/scripts/skill-comment-scan.mjs --paths .agents --paths docs/architecture/adr` exit 0 con un allowlist mínimo (`LICENSE-*`, shebangs, generated_*). (c) `node .agents/scripts/__tests__/skill-comment-scan.test.mjs` todos los tests verdes. |
| P1-D · `automated-skill-doctor` | REQ-SD-001..009 | (a) `node .agents/scripts/skill-doctor.mjs --skills-dir .agents/skills --json | jq '.skills | length'` ≥ 67. (b) `.github/workflows/skill-doctor.yml` parseable. (c) Sub-agente `.agents/agents/skill-doctor.md` model `sonnet`. |
| UMBRELLA · `umbrella-knowledge-bundle` | REQ-KB-UMBRELLA-001..007 | (a) Todos los P0 y P1 bloques COMPLIANT. (b) `.agents/skill-registry.md` regenerado (git diff muestra cambios solo de inventory + dates). (c) ADR-NNN creado y enlazado desde `docs/architecture/adr/README.md`. |

**Notas de verificabilidad:**
- Comandos `just` se invocan con `just --list` antes para confirmar recetas; si una receta no existe, se usa el comando package-level documentado en AGENTS.md.
- `sdd-verify` no infiere; solo emite COMPLIANT cuando su script de verificación retorna 0.
- Local evidence ≠ CI evidence: las dos se reportan separadas en `verify-report.md`.

## Open questions

Ninguno al cierre de esta fase. Las preguntas del explore (marker real, scrub reactivo de Spring, rewrite de Playwright, ADR-NNN, comment policy reframe, version sourcing, UI precedence) están todas cerradas en G-1..G-4 y en las decisiones de usuario del proposal. ADR-NNN se redacta como parte de P0-A (no en design); su numeración exacta se asigna al primer numbering libre — al cierre de design.md el siguiente ID libre es **`0025`**, y el task de P0-A fija ese ID como literal.

## Risks (carry-over con propuesta)

- **R5 (publication-calendar-sse overlap)**: arranca apply de P0-A solo después de archivar `publication-calendar-sse` (no overlap material). `tasks.md` debe explicitar gate "wait-for: publication-calendar-sse archive".
- **R6 (concurrent changes on shared knowledge)**: cualquier PR tercero tocando `.agents/skills/**` o `AGENTS.md` mientras corra el skill-doctor CI puede generar flicker. El order se decide por timestamp de merge en `main`; el último merge gana reescritura.
- **R3 (Spring scrub coverage)**: el inventario reactivo inicial puede dejar 1-2 references/*.md sin reescribir si los tokens incompatibles no aparecen explícitamente. Mitigación: la verificación de P0-B usa también `rg` por plantillas **positivas** (debe haber al menos 1 mención de `WebTestClient`, `StepVerifier`, `R2dbcRepository` en cada `references/*.md` reactivo).
- **R1 (design vs reality drift)**: el marker `com.profiletailors.common.domain.Service` se re-verifica en tiempo de apply contra el archivo `shared/common/.../Service.kt` antes de cualquier edit en `spring-boot/SKILL.md`. Si el archivo cambia de path o se renombra, el task de P0-B aborta y se re-explora.

## Out of scope (recordatorio para tasks.md)

- No reescritura de código de producto (server/smp, apps/web/**).
- Cambio único en CI de producto: nuevo `.github/workflows/skill-doctor.yml`. Ningún otro workflow se toca.
- `openspec/specs/` permanece inalterado por este design (la fase spec ya cerró sus 10 capabilities).
- `tmp/plans/2026-06-20-hexagonal-cleanup.md` housekeeping queda para change aparte.
- ADRs históricos con cvix-main (`0011-reusable-lead-capture-waitlist.md` y referencias) **no se tocan** en este change.
