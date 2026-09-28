# Proposal — Skill and Knowledge Bundle Remediation

## Why

El knowledge bundle canónico (`.agents/AGENTS.md`, `.agents/skills/**`, ADRs,
`DESIGN.md`, PRODUCT.md, CI) se contradice con el código real del monorepo:
~150 ocurrencias de contamination servlet/JPA/Lombok/Mockito en
`spring-boot/**/references/`, marker fantasma `@ApplicationService` que no
existe, una skill Playwright construida sobre `apps/portfolio`/`apps/blog` que
nunca existieron, y la "decisión #1" del usuario (Domain owns repository ports)
escrita al revés en la constitución. El drift es acumulativo y silencioso;
sin CI que lo detecte, vuelve a aparecer al primer PR. Este change reconcilia
el bundle con el contrato vivo del proyecto y deja un skill-doctor en CI que
impide la regresión.

## What changes

Bloques P0 — críticos, sin orden de dependencia entre ellos salvo donde se indica:

- **P0-A · skill-discovery-identity** — Aplanar la jerarquía
  (`backend-platform/`, `frontend-platform/`, `testing/`, subcarpetas de
  `design-pattern/` vacías) a una skill por folder top-level. Cada SKILL.md
  gana `metadata.category` y `metadata.family` en frontmatter.
  Folder name == frontmatter `name` == skill id (excepto `impeccable` y
  `astrolicious-astro` que son nombres intencionales). Las 67 skills pasan a
  layout plano. `impeccable/` conserva sus subcarpetas ejecutables
  (`reference/`, `scripts/`, `agents/`). Criterio de aceptación: explore-notes
  §1 → "no existe metadata consistente" invertido; las 3 subcarpetas
  categoriales vacías de `design-pattern/` desaparecen; el skill-registry se
  regenera vía `sdd-init`.

- **P0-B · backend-semantics** — (a) Enmendar ADR-0002 añadiendo párrafo
  explícito "Domain owns the repository/gateway ports/interfaces. Application
  depends on those ports. Infrastructure depends on Application and implements
  those ports." Status: "Accepted" → "Accepted (amended)". (b) Sustituir en
  AGENTS.md (líneas 384 y 394) "inward-facing ports"/"inward-facing side" por
  "domain ports" o "domain-defined ports". (c) Borrar la sección "Local
  Architectural Markers" que define `@ApplicationService` en
  `com.profiletailors.common.application` (no existe) y sustituirla por
  referencia limpia al marker real
  `com.profiletailors.common.domain.Service` en
  `shared/common/src/main/kotlin/com/profiletailors/common/domain/Service.kt:18`.
  (d) Reescritura línea por línea de los archivos bajo
  `spring-boot/{security,cache,resilience,messaging,saga-pattern,
  ai-mcp-server-patterns,data-neo4j-reactive}/references/` sustituyendo
  patrones servlet por reactivos puros: `HttpSecurity` → `ServerHttpSecurity`,
  `SecurityFilterChain` → `SecurityWebFilterChain`, `OncePerRequestFilter`
  fuera (solo queda en guía de migración marcada `legacy-`), `MockMvc` →
  `WebTestClient`, `@MockBean` → `@MockkBean`, `JpaRepository` → R2DBC,
  `spring.datasource.url` → connection string R2DBC, `spring.jpa.*` borrado,
  `spring-boot-starter-data-jpa` → R2DBC, `spring-boot-starter-web` →
  `spring-boot-starter-webflux`, `lombok.RequiredArgsConstructor` borrado,
  `@RequiredArgsConstructor` borrado, `MockitoExtension` → Kotest/MockK. NO
  archive, NO delete — reescritura línea por línea. El `SKILL.md` principal
  de spring-boot queda prácticamente intacto (ya declara la doctrina).
  Criterio de aceptación: explore-notes §2 → todas las métricas de
  contamination a cero (salvo guías `legacy-*` marcadas explícitamente).

- **P0-C · external-contamination** — DELETE puro en 6 archivos: footer
  `© 2024 CVIX` en `frontend-platform/vue/SKILL.md`, las 3 menciones de
  `profiletailors.resume`/`Resume` en
  `backend-platform/spring-boot/references/{swagger-standard,error-handling,
  request-response-dtos}.md`, `profiletailors.resume` en
  `languages-typing/kotlin/SKILL.md`. La limpieza de la skill Playwright vive
  en P0-D (rewrite, no delete puntual). Criterio de aceptación:
  explore-notes §5 → 0 hits para `CVIX`/`profiletailors.resume`/
  `Resume`-bounded-context en skills (los ADRs históricos que mencionan
  `cvix-main` NO se tocan; los `tmp/plans/*.md` con menciones legacy se
  abordan como housekeeping aparte, fuera de este change).

- **P0-D · playwright-rebuild** — Rewrite desde cero de
  `.agents/skills/testing/playwright/SKILL.md` anclado a la realidad del
  monorepo: `apps/web/app/e2e/` (config + specs), `apps/web/marketing/
  tests/e2e/` (config + specs), `apps/web/admin` (gap real, sin E2E hoy),
  `shared/web` (consent contract), `scripts/run-playwright.mjs` (verificado
  que existe). Doctrine sobre HAR-based API mocking, `testDir: ./specs`
  (app) vs `testDir: ./tests/e2e` (marketing), y el comando canónico
  `just frontend-test-e2e` / `just app-test-e2e-media-mocked`. Sustituir
  cualquier referencia a `apps/portfolio`, `apps/blog`,
  `packages/testing-e2e`, `seed-portfolio.spec.ts`, `seed-blog.spec.ts`.
  Criterio de aceptación: explore-notes §6 → 0 referencias a paths
  inexistentes; cada comando de la skill es ejecutable en el worktree.

- **P0-E · ui-governance-precedence** — Añadir sección "UI precedence chain"
  en `.agents/DESIGN.md` (primeras ~100 líneas) declarando:
  `impeccable` → `nothing-design` → `frontend-design`, con `DESIGN.md` como
  tie-breaker cuando haya conflicto entre skills. La justificación (que
  el explore-notes §7 ya articuló) se preserva en el cuerpo de la sección:
  `impeccable` es el "cómo", `nothing-design` es el "qué" (lenguaje visual
  vigente monocromo Doto/Space Grotesk/Space Mono, dark+light first-class),
  `frontend-design` cubre el "anti-slop" cuando las dos anteriores no aplican.
  DESIGN.md gana siempre que un token concreto difiera de la recomendación de
  la skill. Criterio de aceptación: explore-notes §7 → DESIGN.md menciona
  las tres skills por nombre y declara explícitamente la cadena.

Bloques P1 — gobernanza y sostenibilidad:

- **P1-A · version-policy** — Borrar versiones literales hardcoded en 6+
  skills (`spring-boot/cache/SKILL.md` con "3.5+", `spring-boot/security/
  references/{jwt-quick-reference,jwt-complete-configuration}.md` con "3.5.x",
  `hexagonal-architecture/references/kotlin-clean-architecture.md` con
  "2.x", `testing/playwright/SKILL.md` con "1.58.2"/"4.11.1",
  `testing/vitest/SKILL.md` con "3.x", `pnpm/SKILL.md` con "10.x",
  `frontend-platform/pinia/SKILL.md` con "v3.0.4", y los metadatos de
  `kotlin`/`typescript`/`zod-4` datados 2026-01-28). Sustituir por texto
  neutro del estilo "the current Spring Boot version (see
  `gradle/libs.versions.toml`)" o "see `package.json`". El skill-registry se
  regenera; no se edita a mano. Criterio de aceptación: explore-notes §8 →
  0 versiones literales en skills y metadatos (excepto las descriptivas tipo
  "Vue 3" que son identidad, no versión stale).

- **P1-B · modern-best-practices** — Re-revisar Kotlin, TypeScript, Vue,
  Pinia, Vitest, pnpm guidance contra las reglas dogmáticas declaradas en
  AGENTS.md y eliminar contradicciones internas. Acciones concretas: borrar
  `frontend-platform/shadcn-vue/SKILL.md` (el proyecto no consume shadcn,
  usa `@profiletailors/ui` propio), reescribir ejemplos de
  `playwright-best-practices` para usar Vue/Astro en vez de Next.js,
  documentar que `modern-web-guidance` es MANDATORY solo cuando aplique HTML/
  CSS/JS no cubierto por skills locales. Criterio de aceptación:
  explore-notes §9 → skills de testing y pnpm coherentes con manifests;
  shadcn-vue fuera del bundle.

- **P1-C · comment-cleanup** — (a) Reframear AGENTS.md (sección "Fix
  Simplicity and Zero-Comment Policy") para que el wording sea:
  "prefer self-documenting code; do not generate explanatory comments by
  default; cleanup enforced in post-processing". La política sigue siendo
  zero-comments, pero el cleanup es pipeline explícito. (b) Crear
  `.agents/scripts/skill-comment-scan.mjs` (Node puro, sin deps externos)
  que falle el build si encuentra `^\s*//`, `^\s*\*`, `#` (en archivos de
  código), `<!--` (en `.md`/Astro). (c) Crear sub-agente barato
  `comment-cleanup` (en `.agents/agents/`) con `model: haiku` para casos
  context-dependent (sabe distinguir comentario de licencia en cabecera,
  shebang ejecutable ya permitido en AGENTS.md, comentario dentro de string
  en ejemplo). (d) Test que verifique el determinístico (no quedan
  comentarios en paths específicos). Criterio de aceptación: explore-notes
  §12 → script + sub-agente + test funcionales; AGENTS.md con el wording
  reframeado.

- **P1-D · automated-skill-doctor** — Crear `.agents/scripts/skill-doctor.mjs`
  (Node) que verifique por cada SKILL.md: frontmatter `name` presente,
  `name` coincide con nombre de carpeta, `metadata.category` y
  `metadata.family` presentes, paths internos referenciados existen en el
  worktree, ninguna string de contamination (`CVIX`, `profiletailors.resume`,
  `apps/portfolio`, `apps/blog`, `packages/testing-e2e`). Crear sub-agente
  `skill-doctor` (en `.agents/agents/`) para contradicciones cross-skill con
  contexto LLM. Crear workflow `.github/workflows/skill-doctor.yml` que
  ejecute el script + invoque el sub-agente en PR que toquen `.agents/` y
  bloquee merge si falla cualquiera de las dos capas. Criterio de
  aceptación: explore-notes §10 → el gate existe y rechaza un PR de prueba
  con drift reintroducido.

## Decisions / authority

Governance (cerradas en `state.yaml`, no se renegocia):

- **G-1**: ADR-0002 se enmienda con la frase canónica "Domain owns
  repository/gateway ports/interfaces". Owner del ADR mantiene su rol.
- **G-2**: Crear ADR-NNN para taxonomía canónica de skills + CI
  anti-contradiction gates (folder == frontmatter name == skill id;
  `metadata.category`/`family` obligatorios; reglas del skill-doctor
  codificadas).
- **G-3**: Zero-comments se preserva pero se reframea en AGENTS.md como
  "self-documenting-first; cleanup enforced in post-processing". Pipeline
  determinístico + sub-agente barato es parte del cambio.
- **G-4**: DESIGN.md declara explícitamente la cadena
  `impeccable → nothing-design → frontend-design` (DESIGN.md como
  tie-breaker).

Decisiones del usuario en esta fase (también cerradas):

1. **Marker Service**: usar el marker real `com.profiletailors.common.domain.Service`
   (en `shared/common/src/main/kotlin/com/profiletailors/common/domain/Service.kt:18`).
   No `@ApplicationService`, no marker paralelo.
2. **Spring scrub**: reescritura línea por línea de los `references/`
   contaminados. NO archive, NO delete. Sustituir por patrones reactivos
   puros (`ServerHttpSecurity`, `SecurityWebFilterChain`, `StepVerifier`,
   coroutines, R2DBC).
3. **Playwright**: rewrite desde cero anclado a `apps/web/{app,admin,
   marketing}` + `shared/web` + `scripts/run-playwright.mjs`.
4. **Taxonomía**: flatten + metadata `category`/`family`. Una skill por
   folder top-level. Sin jerarquía `backend-platform/`/`frontend-platform/`/
   `testing/` como subcarpeta de skills. Categorizar vía frontmatter.

**Authority**: este proposal autoriza implementación una vez que las fases
`sdd-spec`, `sdd-design` y `sdd-tasks` estén listas. Ningún bloque se
implementa hasta cerrar las specs.

## Scope (in / out)

**In**:

- P0-A, P0-B, P0-C, P0-D, P0-E, P1-A, P1-B, P1-C, P1-D (9 bloques).
- Cambios en `.agents/AGENTS.md`, `.agents/DESIGN.md`, ADR-0002,
  ADR-NNN (nuevo), `.agents/skills/**/SKILL.md` y sus `references/`
  cuando aplique, `.agents/skill-registry.md` (regenerado), workflow
  nuevo en `.github/workflows/`, scripts determinísticos en
  `.agents/scripts/`, sub-agentes nuevos en `.agents/agents/`, test
  nuevo del comment-cleanup pipeline.
- Housekeeping de `tmp/plans/2026-06-20-hexagonal-cleanup.md` (con
  menciones legacy `cvix-main`) queda fuera — se aborda en change aparte.

**Out**:

- No reescritura de código de producto (backend, frontend, shared).
- No cambio en CI de producto (`.github/workflows/ci.yml`,
  `quality-gate.yml`, security lanes) — solo se añade `skill-doctor.yml`.
- No cambio en `publication-calendar-sse` (queda en standby; se archivará
  antes de empezar el apply de este change para evitar pisar
  `.agents/`/ADRs concurrentes).
- No cambios en `openspec/specs/` — eso es fase `sdd-spec`.
- No eliminación de skills enteras salvo `shadcn-vue` (P1-B, decisión
  justificada en explore-notes §9).
- No renombrar las 2 skills intencionalmente distintas al folder
  (`impeccable`, `astrolicious-astro`).
- No tocar ADRs históricos que mencionan `cvix-main` (ej. ADR-0011).

## Dependencies

- **Concurrente (orden recomendado)**: `publication-calendar-sse` está en
  `apply` y no solapa con skills/AGENTS.md/ADRs (verificado en
  explore-notes §11). **Recomendación**: publicar y archivar
  `publication-calendar-sse` antes de iniciar el apply de este change.
  Si `publication-calendar-sse` se archivara primero, este change puede
  correr en paralelo a cambios que solo toquen backend/frontend features.
- **Archivado (sin impacto)**: `reactive-calendar-browser-sync` ya archivado;
  sus specs viven en
  `openspec/specs/{visual-calendar,publishing,privacy-compliance}/spec.md`
  y NO se reescriben desde este change. Verificar al final del apply que
  ninguna spec archivada referencia skills por path inexistente.
- **Sin dependencias de manifest**: P1-A hace explícito que las versiones
  se leen de `gradle/libs.versions.toml` y `package.json`. Ningún bump de
  versión ocurre aquí.

## Risks & mitigations

- **R1 (alto) — Spring scrub línea por línea es costoso**. ~150 ocurrencias
  en ~6 subskills `references/`. Riesgo: regresión de doctrina si el rewrite
  introduce patrones reactivos no canónicos. Mitigación: el rewrite sigue
  las plantillas ya presentes en `spring-boot/testing-webflux/SKILL.md` y
  `spring-boot/SKILL.md` (las partes declarativas están limpias); revisión
  cruzada skill por skill; P1-D detecta drift reintroducido en CI.

- **R2 (alto) — Playwright rewrite elimina doctrine heredada**. Riesgo: si
  algún workflow interno o agente consumía los paths falsos
  (`apps/portfolio`, `packages/testing-e2e`), se rompe. Mitigación:
  verificación previa con grep sobre `.agents/agents/**`,
  `.github/workflows/**`, `.agents/commands/**` (resultado del explore:
  no existe tal dependencia).

- **R3 (medio) — Concurrencia con `publication-calendar-sse`**. Aunque hoy
  no solapan archivos, si `publication-calendar-sse` se archivara tocando
  markers `@Service` o `application/` mientras este change reescribe la
  doctrina del marker en `spring-boot/SKILL.md`, hay ventana de conflicto.
  Mitigación: archivar `publication-calendar-sse` antes del apply; revisar
  diff final de `.agents/` contra su propio diff antes de mergear.

- **R4 (medio) — P1-A borrar versiones puede dejar huecos**. Riesgo: una
  skill ya no dice "qué API llamar" sin referencia alternativa. Mitigación:
  sustituir por patrón "see `gradle/libs.versions.toml`" o "see
  `package.json`"; el explore-notes §8 lista las sustituciones literales.

- **R5 (bajo) — AGENTS.md cambia la constitución**. Líneas 384 y 394. El
  wording exacto se fija en `sdd-spec`. Mitigación: la frase canónica
  ("Domain owns the repository/gateway ports/interfaces") viene de las
  tres fuentes verdaderas (código, skill hexagonal, ADR-0002); no es
  invención.

- **R6 (bajo) — `tmp/plans/2026-06-20-hexagonal-cleanup.md` queda con
  contamination legacy `cvix-main`**. Riesgo: si se ignora, debt visible.
  Mitigación: documentado como out-of-scope; housekeeping aparte.

## Verification preview

Criterios que `sdd-verify` validará al final del apply:

1. **Estructura**: 67 skills en layout plano (1 nivel). 0 subcarpetas
   categoriales vacías bajo `design-pattern/`. `impeccable/` conserva sus
   subcarpetas ejecutables.
2. **Frontmatter**: cada SKILL.md tiene `metadata.category` y
   `metadata.family` válidos. Folder name == frontmatter `name` (excepto
   las 2 intencionales).
3. **Backend doctrine**: AGENTS.md líneas 384 y 394 dicen "domain ports"
   (o equivalente acordado en spec); ADR-0002 amended con la frase
   canónica; ADR-NNN nuevo presente con taxonomía + gates; cero hits de
   `HttpSecurity`/`SecurityFilterChain`/`OncePerRequestFilter`/`MockMvc`/
   `@MockBean`/`JpaRepository`/`lombok`/`RequiredArgsConstructor`/
   `MockitoExtension` en skills (excepto guías marcadas `legacy-*`).
4. **Markers**: `@ApplicationService` borrado de `spring-boot/SKILL.md`;
   referencia a `com.profiletailors.common.domain.Service` presente y
   correcta.
5. **Contamination**: 0 hits para `CVIX`, `profiletailors.resume`,
   `Resume`-bounded-context, `apps/portfolio`, `apps/blog`,
   `packages/testing-e2e` en `.agents/skills/**`.
6. **Playwright**: skill reescrita. Cada comando citado existe y es
   ejecutable en el worktree (`just frontend-test-e2e`,
   `just app-test-e2e-media-mocked`, etc.).
7. **UI precedence**: DESIGN.md declara la cadena
   `impeccable → nothing-design → frontend-design` (DESIGN.md tie-breaker).
8. **Versiones**: 0 versiones literales hardcoded en frontmatter o cuerpo
   de skills (excepto las descriptivas tipo "Vue 3").
9. **Comment pipeline**: `.agents/scripts/skill-comment-scan.mjs` corre y
   falla con drift reintroducido; sub-agente `comment-cleanup` ejecutable;
   test del determinístico pasa; AGENTS.md usa el wording reframeado.
10. **CI gate**: `.github/workflows/skill-doctor.yml` existe, corre en PR
    que tocan `.agents/`, y bloquea merge ante drift.
11. **Concurrencia**: ninguna spec archivada
    (`openspec/specs/{visual-calendar,publishing,privacy-compliance}/
    spec.md`) referencia skills por path inexistente tras el apply.
12. **Calidad**: lint + type-check + tests de las skills afectadas pasan;
    AGENTS.md link-safe; skill-registry regenerado por `sdd-init`.
