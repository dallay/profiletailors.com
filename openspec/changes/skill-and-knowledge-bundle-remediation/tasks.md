# Tasks — Skill and Knowledge Bundle Remediation

## Tracking
- Source: `proposal.md`
- Specs: `specs/**` (10 capabilities, 78 REQs)
- Design: `design.md` (9 ADs)
- State: `state.yaml` (`current_phase: tasks` post-update)
- Hard dep: `P1-B ⇸ P1-D` (REQ-KB-UMBRELLA-003) — P1-B `verify` queda bloqueado hasta que el skill-doctor corra verde en CI
- Concurrency gate: `publication-calendar-sse` debe archivarse antes de empezar Oleada 1 (riesgo R5)

## Orden de aplicación (oleadas)

### Oleada 1 — Governance foundations (P0-A + P0-B + P0-E)
Tasks necesarias para tener gobernanza antes de tocar contenido de skills.
Output de esta oleada: taxonomía canónica plana, ADR-0002 enmendado, AGENTS.md wording canónico, marker real documentado, scrub reactivo de spring-boot/*, precedence chain UI declarada.

### Oleada 2 — Content remediation + version policy (P0-C + P0-D + P1-A)
Tasks de borrado directo y rewrites ancladas a la realidad.
Nota: P1-B (modern-best-practices) se aplica aquí estructuralmente, pero `verify` queda en estado "applied, blocked-verify" porque depende de P1-D.

### Oleada 3 — Anti-drift gates (P1-C + P1-D)
El doctor (P1-D) se implementa y opera antes de poder cerrar P1-B. Una vez verde CI para el doctor, P1-B pasa a verify.

### Cierre — Cross-cutting
CK-1..CK-5 corren al final del apply completo.

## Tasks

### Oleada 1 — Governance foundations

- id: TASK-001
  block: P0-A
  name: Inventariar y aplanar la jerarquía de `.agents/skills/`
  description: Mover cada skill a top-level con `git mv`. Subir cada subskill de `backend-platform/`, `frontend-platform/`, `testing/`, `languages-typing/` a `.agents/skills/<id>/`. Conservar las excepciones documentadas (`impeccable/`, `astrolicious-astro/`).
  references:
    - design.md#AD-1
    - specs/capability-skill-discovery-identity.md#REQ-SD-001..009
  depends_on: []
  deliverable: Inventario exhaustivo en `.agents/skills/` (cada entry debe tener `SKILL.md`); subcarpetas categoriales (`backend-platform/`, `frontend-platform/`, `testing/`, `languages-typing/`) eliminadas; `git mv` history preservado.
  verify: `git status` muestra solo renames; `find .agents/skills -mindepth 2 -maxdepth 2 -type d -name 'backend-platform' -o -name 'frontend-platform' -o -name 'testing' -o -name 'languages-typing'` retorna 0 hits.
  done_criteria: 67 skills en layout plano bajo `.agents/skills/<id>/SKILL.md`; sin carpetas disciplinares intermedias; `impeccable/` y `astrolicious-astro/` siguen como top-level con sus subcarpetas ejecutables.
  estimated_effort: L

- id: TASK-002
  block: P0-A
  name: Añadir frontmatter `metadata.category` y `metadata.family` por skill
  description: Para cada `SKILL.md` top-level tras TASK-001, poblar los campos `metadata.category` y `metadata.family` según el enum de AD-1. Mantener fechas reales de última edición en `metadata.version`.
  references:
    - design.md#AD-1
    - specs/capability-skill-discovery-identity.md#REQ-SD-005..007
  depends_on: [TASK-001]
  deliverable: 67 archivos `SKILL.md` con frontmatter `metadata:{category,family,source,version}` completo.
  verify: `node .agents/scripts/skill-doctor.mjs --skills-dir .agents/skills --fail-on missing-frontmatter --fail-on missing-metadata` retorna exit 0 (este script se crea en TASK-042; en apply, primero se commitea el skill-doctor.mjs y luego se corre — orden Oleada 3 puede reordenarse si se necesita bootstrap). Antes de Oleada 3, verificar manualmente: `head -10 .agents/skills/<id>/SKILL.md | grep -E 'category:|family:'` por cada skill.
  done_criteria: Cada SKILL.md declara `metadata.category` y `metadata.family`; ninguna metadata category fuera del enum conocido; ninguna family fuera del enum conocido.
  estimated_effort: M

- id: TASK-003
  block: P0-A
  name: Validar `name == folder_name` y renombrar las que no cumplan
  description: Regla: el campo `name` del frontmatter debe ser exactamente igual al nombre del folder que contiene el `SKILL.md`. Renombrar frontmatter `name` o carpeta cuando difieran. Excepciones: `impeccable`, `astrolicious-astro` (sí match por convención; no requieren excepción).
  references:
    - design.md#AD-1
    - specs/capability-skill-discovery-identity.md#REQ-SD-003
  depends_on: [TASK-001, TASK-002]
  deliverable: 67 carpetas con `folder_name` y `frontmatter.name` idénticos.
  verify: Loop por cada skill: `name=$(rg '^name:' .agents/skills/$id/SKILL.md -N --no-filename | head -1 | awk '{print $2}'); [ "$name" = "$id" ] || echo "MISMATCH $id vs $name"`. Cero mismatches.
  done_criteria: Sin output "MISMATCH"; grep `name:` coincide 1:1 con la carpeta padre.
  estimated_effort: S

- id: TASK-004
  block: P0-A
  name: Borrar subcarpetas categoriales vacías en `design-pattern/`
  description: Las subcarpetas `behavioral/`, `creational/`, `structural/` de `design-pattern/` están vacías (solo `catalog.json` y `README.md` referencian agrupadores). Tras flatten, estas categorías no existen en la taxonomía canónica. Borrar las carpetas vacías manteniendo los SKILL.md de skills individuales (`abstract-factory`, `builder`, etc.) que ya viven top-level en `design-pattern/`.
  references:
    - design.md#AD-1
    - specs/capability-skill-discovery-identity.md#REQ-SD-002
  depends_on: [TASK-001]
  deliverable: Subcarpetas `behavioral/`, `creational/`, `structural/` eliminadas. Skills individuales conservadas.
  verify: `find .agents/skills/design-pattern/behavioral .agents/skills/design-pattern/creational .agents/skills/design-pattern/structural -type f` retorna 0 hits; `find .agents/skills/design-pattern -mindepth 2 -type d` retorna solo carpetas estructurales permitidas.
  done_criteria: Carpetas eliminadas; pattern SKILLs intactos; `catalog.json` actualizado si lista paths inexistentes.
  estimated_effort: XS

- id: TASK-005
  block: P0-A
  name: Regenerar `.agents/skill-registry.md`
  description: Tras los renames y los metadatos, regenerar el skill-registry automáticamente. Marcar el archivo como Auto-generated y commitear diff solo de inventory + dates.
  references:
    - design.md#AD-1
    - specs/capability-skill-discovery-identity.md#REQ-SD-008
  depends_on: [TASK-002, TASK-003]
  deliverable: `.agents/skill-registry.md` regenerado, contiene 67 entries y cabecera "Auto-generated".
  verify: `rg '^Auto-generated' .agents/skill-registry.md` 1 hit; `rg -c '^## ' .agents/skill-registry.md` >= 67.
  done_criteria: Cabecera Auto-generated presente; las 67 skills listadas con su `metadata.category` y `metadata.family`; diff manual tras commit no incluye edits manuales al registry.
  estimated_effort: S

- id: TASK-006
  block: P0-B
  name: Enmendar ADR-0002 con la decisión canónica de ports
  description: Patch a `docs/architecture/adr/0002-adhere-to-hexagonal-architecture.md`. (1) Frontmatter: `Status: Accepted` → `Status: Accepted (amended)` y añadir línea `Updated: 2026-09-27 (amended by skill-and-knowledge-bundle-remediation)`. (2) Sección "Decision": añadir al final el párrafo literal de AD-2 sobre `Ports location (added 2026-09-27)`.
  references:
    - design.md#AD-2
    - specs/capability-backend-semantics.md#REQ-BS-001..003
  depends_on: []
  deliverable: `docs/architecture/adr/0002-adhere-to-hexagonal-architecture.md` enmendado con status y párrafo nuevo.
  verify: `rg -n 'Accepted \(amended\)' docs/architecture/adr/0002-adhere-to-hexagonal-architecture.md` 1 hit; `rg -n 'Ports location \(added 2026-09-27\)' docs/architecture/adr/0002-adhere-to-hexagonal-architecture.md` 1 hit; `rg -n 'Updated: 2026-09-27 \(amended by skill-and-knowledge-bundle-remediation\)' docs/architecture/adr/0002-adhere-to-hexagonal-architecture.md` 1 hit.
  done_criteria: Tres hits verificados; el bloque "Ports location" queda en la sección `## Decision`, después del bloque de tres capas existente.
  estimated_effort: S

- id: TASK-007
  block: P0-B
  name: Sustituir "inward-facing ports" en `.agents/AGENTS.md` línea 384
  description: Edit literal línea 384, columna "May depend on", fila "Application" de la tabla de capas: `Domain and inward-facing ports` → `Domain, including domain-defined ports`. Wording exacto desde AD-2. Sin tocar el resto de la fila.
  references:
    - design.md#AD-2
    - specs/capability-backend-semantics.md#REQ-BS-004
  depends_on: []
  deliverable: `.agents/AGENTS.md` línea 384 actualizada.
  verify: `sed -n '384p' .agents/AGENTS.md | grep -F 'Domain, including domain-defined ports'` exit 0; `rg -n 'inward-facing' .agents/AGENTS.md` retorna 0 hits en la línea 384.
  done_criteria: Línea 384 contiene el wording canónico; el resto de la fila "Application" intacto.
  estimated_effort: XS

- id: TASK-008
  block: P0-B
  name: Sustituir "inward-facing side" en `.agents/AGENTS.md` línea 394
  description: Edit literal línea 394: `Put repository/gateway contracts on the inward-facing side and implementations in infrastructure;` → `Place repository/gateway ports/interfaces in domain; implement them in infrastructure; inject them into application services through composition.` Wording exacto desde AD-2.
  references:
    - design.md#AD-2
    - specs/capability-backend-semantics.md#REQ-BS-004
  depends_on: []
  deliverable: `.agents/AGENTS.md` línea 394 actualizada; continuar el bullet con la frase final `Follow the existing context convention rather than importing an adapter into application code.`
  verify: `sed -n '394p' .agents/AGENTS.md | grep -F 'Place repository/gateway ports/interfaces in domain; implement them in infrastructure; inject them into application services through composition.'` exit 0; `rg -n 'inward-facing side' .agents/AGENTS.md` retorna 0 hits.
  done_criteria: Wording canónico presente; bullet final "Follow the existing context convention..." preservado.
  estimated_effort: XS

- id: TASK-009
  block: P0-B
  name: Reescribir "Local Architectural Markers" en `spring-boot/SKILL.md` con el marker real
  description: Borrar la sección que inventa `com.profiletailors.common.application.ApplicationService` (no existe). Sustituirla por referencia al marker real `com.profiletailors.common.domain.Service` apuntando a `shared/common/src/main/kotlin/com/profiletailors/common/domain/Service.kt:18`. Documentar `includeFilters` de `SmpApplication.kt` como mecanismo de discovery. Prohibir `@Service`/`@Component`/`@Repository` Spring en code de application; permitir solo en `infrastructure/`.
  references:
    - design.md#AD-2
    - specs/capability-backend-semantics.md#REQ-BS-005..008
  depends_on: [TASK-001]
  deliverable: Sección "Local Architectural Markers" de `.agents/skills/spring-boot/SKILL.md` reescrita; referencia exacta al marker real.
  verify: `rg -n 'com\.profiletailors\.common\.application\.ApplicationService' .agents/skills/spring-boot/SKILL.md` 0 hits; `rg -n 'com\.profiletailors\.common\.domain\.Service' .agents/skills/spring-boot/SKILL.md` ≥ 1 hit; `find . -name 'ApplicationService.kt' 2>/dev/null` retorna 0 hits confirmando que el marker fantasma no existe.
  done_criteria: Marker fantasma borrado; marker real referenciado con path y línea.
  estimated_effort: S

- id: TASK-010
  block: P0-B
  name: Inventariar tokens incompatibles en `spring-boot/*/references/`
  description: Comando canónico para producir el listado exacto que cada task de scrub consumirá. Output: lista de archivos con hits por token. Se commitea como `.agents/skills/spring-boot/.scrub-inventory.txt` (autogenerated, .gitignore o comentario explícito de regenerable).
  references:
    - design.md#AD-2
    - specs/capability-backend-semantics.md#REQ-BS-009..011
  depends_on: [TASK-001]
  deliverable: `.agents/skills/spring-boot/.scrub-inventory.txt` con índice `archivo:token:count`.
  verify: `rg -n 'HttpSecurity|SecurityFilterChain|OncePerRequestFilter|MockMvc|AutoConfigureMockMvc|JpaRepository|spring\.datasource|spring\.jpa|starter-web(?!flux)|starter-data-jpa|RequiredArgsConstructor|MockitoExtension|@MockBean' .agents/skills/spring-boot/` produce inventario reproducible; el archivo `.scrub-inventory.txt` contiene exactamente esos paths.
  done_criteria: Inventario determinístico; alimenta TASK-011..TASK-017.
  estimated_effort: XS

- id: TASK-011
  block: P0-B
  name: Reescribir `spring-boot/security/references/*.md` línea por línea a reactivo
  description: Por cada archivo bajo `.agents/skills/spring-boot/security/references/` (5-7 archivos): sustituir `HttpSecurity` por `ServerHttpSecurity`, `SecurityFilterChain` por `SecurityWebFilterChain`, `OncePerRequestFilter` por explicación reactiva o marcador `legacy-`, `MockMvc`/`@WebMvcTest` por `WebTestClient`/`@WebFluxTest`. Plantilla reactiva desde `.agents/skills/spring-boot/testing-webflux/SKILL.md`. Conservar fragmentos marcados explícitamente `legacy-` solo si documentan el patrón antiguo como anti-patrón.
  references:
    - design.md#AD-2
    - specs/capability-backend-semantics.md#REQ-BS-011
  depends_on: [TASK-001, TASK-010]
  deliverable: Cada `*.md` reescrito; commits por archivo con mensaje `docs(skills): rewrite spring-boot/security/references/<file>.md to reactive`.
  verify: `rg -n 'HttpSecurity|SecurityFilterChain|MockMvc|@WebMvcTest' .agents/skills/spring-boot/security/references/` debe retornar solo líneas dentro de marcadores `legacy-` explícitos; `rg -n 'WebTestClient|StepVerifier|@WebFluxTest|ServerHttpSecurity|SecurityWebFilterChain' .agents/skills/spring-boot/security/references/` ≥ 1 hit por archivo reactivo.
  done_criteria: Zero contamination servlet en contextos reactivos; al menos 1 plantilla reactiva por archivo; archivos marcados `legacy-*` solo si documentan el patrón viejo como anti-patrón.
  estimated_effort: L

- id: TASK-012
  block: P0-B
  name: Reescribir `spring-boot/cache/references/*.md` línea por línea a reactivo
  description: Sustituir `spring.datasource.*` por `spring.r2dbc.*`, eliminar `spring.jpa.*`, sustituir `spring-boot-starter-data-jpa` por R2DBC. Mantener ejemplos `@Cacheable` reactivo y `ReactiveCacheManager`.
  references:
    - design.md#AD-2
    - specs/capability-backend-semantics.md#REQ-BS-012
  depends_on: [TASK-001, TASK-010]
  deliverable: `*.md` reescritos; commits por archivo.
  verify: `rg -n 'spring\.datasource\.|spring\.jpa\.|starter-data-jpa' .agents/skills/spring-boot/cache/references/` 0 hits fuera de `legacy-`; `rg -n 'r2dbc|ReactiveCacheManager' .agents/skills/spring-boot/cache/references/` ≥ 1 hit por archivo reactivo.
  done_criteria: Mismas reglas de TASK-011 aplicadas a cache.
  estimated_effort: L

- id: TASK-013
  block: P0-B
  name: Reescribir `spring-boot/resilience/references/*.md` línea por línea
  description: Sustituir `Resilience4j` configurado como servlet por variantes reactivas (`TimeLimiter`, `Bulkhead` con `Reactor`); eliminar `CompletableFuture` block y reemplazarlo por `Mono`/`Flux`/`runTest` patterns.
  references:
    - design.md#AD-2
    - specs/capability-backend-semantics.md#REQ-BS-011
  depends_on: [TASK-001, TASK-010]
  deliverable: `*.md` reescritos.
  verify: `rg -n 'MockMvc|@WebMvcTest|MockitoExtension' .agents/skills/spring-boot/resilience/references/` 0 hits fuera de legacy-; `rg -n 'Mono|Flux|StepVerifier' .agents/skills/spring-boot/resilience/references/` ≥ 1 hit por archivo reactivo.
  done_criteria: Patrones servlet eliminados; ejemplos reactivos introducidos.
  estimated_effort: L

- id: TASK-014
  block: P0-B
  name: Reescribir `spring-boot/messaging/references/*.md` línea por línea
  description: Sustituir listeners bloqueantes (`@KafkaListener` síncrono, `@RabbitListener` síncrono) por `@ReactiveKafkaConsumer` / proyectores `Flux<>`; eliminar `JmsTemplate` síncrono; mantener idempotencia y outbox con ejemplos R2DBC.
  references:
    - design.md#AD-2
    - specs/capability-backend-semantics.md#REQ-BS-012
  depends_on: [TASK-001, TASK-010]
  deliverable: `*.md` reescritos.
  verify: `rg -n 'synchronized|Blocking|jms\.|javax\.jms' .agents/skills/spring-boot/messaging/references/` 0 hits fuera de legacy-; `rg -n 'Reactive|suspend|Flux|Mono' .agents/skills/spring-boot/messaging/references/` ≥ 1 hit por archivo.
  done_criteria: Mensajería alineada con doctrina reactiva.
  estimated_effort: L

- id: TASK-015
  block: P0-B
  name: Reescribir `spring-boot/saga-pattern/references/*.md` línea por línea
  description: Sustituir ejemplos `BlockingSaga` por variantes reactivas (`Mono`-based compensation chains, `suspend fun` orchestrator); eliminar JDBC reference.
  references:
    - design.md#AD-2
    - specs/capability-backend-semantics.md#REQ-BS-013
  depends_on: [TASK-001, TASK-010]
  deliverable: `*.md` reescritos.
  verify: `rg -n 'JpaRepository|JdbcTemplate|@Transactional' .agents/skills/spring-boot/saga-pattern/references/` 0 hits fuera de legacy-; `rg -n 'R2dbc|CoroutinesR2dbcRepository|withTransaction' .agents/skills/spring-boot/saga-pattern/references/` ≥ 1 hit.
  done_criteria: Persistencia R2DBC-only; orchestrator reactivo.
  estimated_effort: L

- id: TASK-016
  block: P0-B
  name: Reescribir `spring-boot/ai-mcp-server-patterns/references/*.md` línea por línea
  description: Sustituir ejemplos blocking tool-call handlers por spring-ai `Reactor`/`Flux` patterns; eliminar referencias a JPA.
  references:
    - design.md#AD-2
    - specs/capability-backend-semantics.md#REQ-BS-013
  depends_on: [TASK-001, TASK-010]
  deliverable: `*.md` reescritos.
  verify: `rg -n '@MockBean|@WebMvcTest|MockitoExtension' .agents/skills/spring-boot/ai-mcp-server-patterns/references/` 0 hits fuera de legacy-; `rg -n 'WebTestClient|StepVerifier|@WebFluxTest' .agents/skills/spring-boot/ai-mcp-server-patterns/references/` ≥ 1 hit.
  done_criteria: Tool-calling alineado con reactivo.
  estimated_effort: L

- id: TASK-017
  block: P0-B
  name: Reescribir `spring-boot/data-neo4j-reactive/references/*.md` línea por línea
  description: Verificar y alinear con la realidad reactiva (R2DBC complement, no sustituir). Mantener Cypher; sustituir ejemplos blocking por `ReactiveNeo4jTemplate` y `Mono`/`Flux` returns.
  references:
    - design.md#AD-2
    - specs/capability-backend-semantics.md#REQ-BS-012
  depends_on: [TASK-001, TASK-010]
  deliverable: `*.md` reescritos.
  verify: `rg -n 'MockMvc|@WebMvcTest|HttpSecurity' .agents/skills/spring-boot/data-neo4j-reactive/references/` 0 hits fuera de legacy-; `rg -n 'ReactiveNeo4jTemplate|Mono|Flux' .agents/skills/spring-boot/data-neo4j-reactive/references/` ≥ 1 hit por archivo.
  done_criteria: Neo4j reactivo exclusivo.
  estimated_effort: M

- id: TASK-018
  block: P0-E
  name: Añadir sección "UI precedence chain" en `.agents/DESIGN.md`
  description: Insertar como PRIMERA sección del body (después del frontmatter YAML existente, antes de tokens) el bloque verbatim de AD-5. La sección declara la cadena `impeccable → nothing-design → frontend-design`, con `DESIGN.md` como tie-breaker y referencia al skill-doctor CI.
  references:
    - design.md#AD-5
    - specs/capability-ui-governance-precedence.md#REQ-UIP-001..006
  depends_on: []
  deliverable: `.agents/DESIGN.md` con sección "UI precedence chain" verbatim al inicio del body.
  verify: `rg -n '^## UI precedence chain' .agents/DESIGN.md` 1 hit; comparar el bloque capturado por `sed -n '/^## UI precedence chain/,/^[a-zA-Z0-9_-]*: *$/p'` con el bloque de AD-5; diff textual debe ser zero tras captura limpia.
  done_criteria: Verbatim match; sin reordenamiento del resto del documento.
  estimated_effort: S

### Oleada 2 — Content remediation + version policy

- id: TASK-019
  block: P0-C
  name: Borrar footer `© 2024 CVIX` en `vue/SKILL.md`
  description: Identificar la última sección del archivo que contiene el footer externo y eliminarla. La sección restante del SKILL.md queda coherente sin él.
  references:
    - design.md#AD-3
    - specs/capability-external-contamination.md#REQ-EC-001
  depends_on: [TASK-001]
  deliverable: `.agents/skills/vue/SKILL.md` sin footer externo.
  verify: `rg -n 'CVIX' .agents/skills/vue/SKILL.md` 0 hits; `wc -l .agents/skills/vue/SKILL.md` reducido en ≥ 1 línea.
  done_criteria: Confirmación de 0 hits; resto de secciones intacto.
  estimated_effort: XS

- id: TASK-020
  block: P0-C
  name: Borrar `profiletailors.resume` en `spring-boot/references/swagger-standard.md`
  description: Sustituir el path o la mención por un placeholder neutro (ej. "your application domain"). Conservar la lección sobre Swagger/OpenAPI standalone.
  references:
    - design.md#AD-3
    - specs/capability-external-contamination.md#REQ-EC-002
  depends_on: [TASK-001]
  deliverable: Archivo purgado.
  verify: `rg -n 'profiletailors\.resume' .agents/skills/spring-boot/references/swagger-standard.md` 0 hits.
  done_criteria: Verificación pasa; resto del contenido del archivo útil y conservador.
  estimated_effort: XS

- id: TASK-021
  block: P0-C
  name: Borrar `profiletailors.resume` en `spring-boot/references/error-handling.md`
  description: Sustituir por placeholder neutro.
  references:
    - design.md#AD-3
    - specs/capability-external-contamination.md#REQ-EC-003
  depends_on: [TASK-001]
  deliverable: Archivo purgado.
  verify: `rg -n 'profiletailors\.resume' .agents/skills/spring-boot/references/error-handling.md` 0 hits.
  done_criteria: Verificación pasa.
  estimated_effort: XS

- id: TASK-022
  block: P0-C
  name: Borrar `profiletailors.resume` en `spring-boot/references/request-response-dtos.md`
  description: Sustituir por placeholder neutro.
  references:
    - design.md#AD-3
    - specs/capability-external-contamination.md#REQ-EC-004
  depends_on: [TASK-001]
  deliverable: Archivo purgado.
  verify: `rg -n 'profiletailors\.resume' .agents/skills/spring-boot/references/request-response-dtos.md` 0 hits.
  done_criteria: Verificación pasa.
  estimated_effort: XS

- id: TASK-023
  block: P0-C
  name: Borrar `profiletailors.resume` en `kotlin/SKILL.md`
  description: Sustituir por placeholder neutro o generalizar la sección.
  references:
    - design.md#AD-3
    - specs/capability-external-contamination.md#REQ-EC-005
  depends_on: [TASK-001]
  deliverable: Archivo purgado.
  verify: `rg -n 'profiletailors\.resume' .agents/skills/kotlin/SKILL.md` 0 hits.
  done_criteria: Verificación pasa.
  estimated_effort: XS

- id: TASK-024
  block: P0-D
  name: Borrar Playwright skill actual y reescribir desde cero
  description: Borrar `.agents/skills/playwright/SKILL.md` y, si existe, `.agents/skills/playwright/references/`. Crear nuevo SKILL.md anclado a la realidad: secciones obligatorias (triggers, mapa de superficies E2E, comandos verificados, doctrina HAR, tag convention, gap acknowledgment de admin/e2e vacío, pointer a playwright-best-practices).
  references:
    - design.md#AD-4
    - specs/capability-playwright-rebuild.md#REQ-PW-001..008
  depends_on: [TASK-001]
  deliverable: `.agents/skills/playwright/SKILL.md` reescrito desde cero.
  verify: `rg -n 'apps/portfolio|apps/blog|packages/testing-e2e' .agents/skills/playwright/SKILL.md` 0 hits; `rg -n 'apps/web/(app|admin|marketing)|scripts/run-playwright\.mjs' .agents/skills/playwright/SKILL.md` ≥ 1 hit; el frontmatter declara `metadata.family: playwright`.
  done_criteria: 7 secciones obligatorias presentes; comandos `just frontend-test-e2e` y `just app-test-e2e-media-mocked` referenciados.
  estimated_effort: L

- id: TASK-025
  block: P0-D
  name: Verificar comandos y doctrina HAR en skill Playwright reescrita
  description: Confirmar que `just frontend-test-e2e`, `just app-test-e2e-media-mocked`, `just app-test-e2e-media-real`, `just playwright-install` y el par `pnpm exec playwright test --grep @frontend` + `UPDATE_HAR=true pnpm exec playwright test --grep @integration` están documentados con su semántica. Verificar doctrina HAR (routeFromHAR, replay vs record) coincidente con `apps/web/app/e2e/playwright.config.ts:24-40` y `e2e/README.md`.
  references:
    - design.md#AD-4
    - specs/capability-playwright-rebuild.md#REQ-PW-007
  depends_on: [TASK-024]
  deliverable: Skill Playwright con comandos verificables y doctrina HAR alineada al monorepo.
  verify: `rg -n '^| just frontend-test-e2e|^| just app-test-e2e-media-mocked|^| just app-test-e2e-media-real|^| just playwright-install' .agents/skills/playwright/SKILL.md` ≥ 4 hits; `rg -n 'routeFromHAR|UPDATE_HAR' .agents/skills/playwright/SKILL.md` ≥ 1 hit.
  done_criteria: Comandos y patrón HAR documentados; spot-check manual cruzado con `apps/web/app/e2e/playwright.config.ts`.
  estimated_effort: S

- id: TASK-026
  block: P0-D
  name: Documentar gap `apps/web/admin/e2e/` en skill Playwright
  description: Declarar en la skill que `apps/web/admin/e2e/` está vacío y referenciar la ruta `apps/web/admin/` como pendiente. Sin inventar tests ni specs. Pointer a `playwright-best-practices/SKILL.md` para patrones genéricos.
  references:
    - design.md#AD-4
    - specs/capability-playwright-rebuild.md#REQ-PW-008
  depends_on: [TASK-024]
  deliverable: Sección "Gap acknowledgment" en Playwright skill.
  verify: `rg -n 'apps/web/admin/e2e' .agents/skills/playwright/SKILL.md` ≥ 1 hit; texto adyacente declara "gap, no tests today".
  done_criteria: Gap explícito; sin tests falsos ni placeholders como specs reales.
  estimated_effort: XS

- id: TASK-027
  block: P1-A
  name: Sustituir versiones hardcoded en `spring-boot/cache/SKILL.md`
  description: Reemplazar `Spring Boot 3.5+` por `the Spring Boot version configured in gradle/libs.versions.toml`. Tabla literal de AD-6.
  references:
    - design.md#AD-6
    - specs/capability-version-policy.md#REQ-VP-001
  depends_on: [TASK-001]
  deliverable: Texto sustituido verbatim.
  verify: `rg -n 'Spring Boot 3\.5\+' .agents/skills/spring-boot/cache/SKILL.md` 0 hits; `rg -n 'libs\.versions\.toml' .agents/skills/spring-boot/cache/SKILL.md` ≥ 1 hit.
  done_criteria: Sin `3.5+` literal; manifest path referenciado.
  estimated_effort: XS

- id: TASK-028
  block: P1-A
  name: Sustituir versiones hardcoded en `spring-boot/security/references/{jwt-quick-reference,jwt-complete-configuration}.md`
  description: Reemplazar `Spring Boot 3.5.x` por `the Spring Boot version (see gradle/libs.versions.toml)`. Tabla literal de AD-6.
  references:
    - design.md#AD-6
    - specs/capability-version-policy.md#REQ-VP-002
  depends_on: [TASK-001]
  deliverable: Ambos archivos purgados.
  verify: `rg -n 'Spring Boot 3\.5\.x' .agents/skills/spring-boot/security/references/jwt-quick-reference.md .agents/skills/spring-boot/security/references/jwt-complete-configuration.md` 0 hits; ambos referencian `libs.versions.toml`.
  done_criteria: Verificación pasa.
  estimated_effort: XS

- id: TASK-029
  block: P1-A
  name: Sustituir versión hardcoded en `hexagonal-architecture/references/kotlin-clean-architecture.md`
  description: Reemplazar `Kotlin 2.x` por `the Kotlin version configured in gradle/libs.versions.toml`.
  references:
    - design.md#AD-6
    - specs/capability-version-policy.md#REQ-VP-003
  depends_on: [TASK-001]
  deliverable: Archivo purgado.
  verify: `rg -n 'Kotlin 2\.x' .agents/skills/hexagonal-architecture/references/kotlin-clean-architecture.md` 0 hits; `rg -n 'libs\.versions\.toml' .agents/skills/hexagonal-architecture/references/kotlin-clean-architecture.md` ≥ 1 hit.
  done_criteria: Verificación pasa.
  estimated_effort: XS

- id: TASK-030
  block: P1-A
  name: Sustituir versión hardcoded en `playwright/SKILL.md`
  description: Reemplazar `Playwright 1.58.2` por `the Playwright version (see apps/web/{app,marketing,admin}/package.json)`. Aplicar a la skill Playwright reescrita de TASK-024 si el renombre de AD-1 ocurrió antes.
  references:
    - design.md#AD-6
    - specs/capability-version-policy.md#REQ-VP-004
  depends_on: [TASK-024]
  deliverable: Texto sustituido.
  verify: `rg -n 'Playwright 1\.[0-9]' .agents/skills/playwright/SKILL.md` 0 hits; `rg -n 'apps/web/.*package\.json' .agents/skills/playwright/SKILL.md` ≥ 1 hit.
  done_criteria: Verificación pasa.
  estimated_effort: XS

- id: TASK-031
  block: P1-A
  name: Sustituir versiones hardcoded en `vitest/SKILL.md` y `pnpm/SKILL.md`
  description: Reemplazar `Vitest 3.x` por `the Vitest version (see package.json)`; reemplazar `pnpm 10.x` por `the pnpm version (see package.json)`.
  references:
    - design.md#AD-6
    - specs/capability-version-policy.md#REQ-VP-005
  depends_on: [TASK-001]
  deliverable: Ambos purgados.
  verify: `rg -n 'Vitest 3\.x|pnpm 10\.x' .agents/skills/vitest/SKILL.md .agents/skills/pnpm/SKILL.md` 0 hits; ambos referencian `package.json`.
  done_criteria: Verificación pasa.
  estimated_effort: XS

- id: TASK-032
  block: P1-A
  name: Sustituir versión hardcoded en `pinia/SKILL.md`
  description: Reemplazar `Pinia v3.0.4` por `the Pinia version (see apps/web/app/package.json)`.
  references:
    - design.md#AD-6
    - specs/capability-version-policy.md#REQ-VP-006
  depends_on: [TASK-001]
  deliverable: Texto sustituido.
  verify: `rg -n 'Pinia v3\.0\.4' .agents/skills/pinia/SKILL.md` 0 hits; `rg -n 'apps/web/app/package\.json' .agents/skills/pinia/SKILL.md` ≥ 1 hit.
  done_criteria: Verificación pasa.
  estimated_effort: XS

- id: TASK-033
  block: P1-A
  name: Actualizar frontmatter `updated:` de `kotlin`, `typescript`, `zod-4`
  description: Capturar la fecha real del último cambio verificado (commit date u opencode session date) y escribirla como `metadata.version` (formato `YYYY-MM-DD`) en cada SKILL.md. Sin inventar fechas.
  references:
    - design.md#AD-6
    - specs/capability-version-policy.md#REQ-VP-006
  depends_on: [TASK-002]
  deliverable: 3 archivos con `metadata.version` real.
  verify: `rg -n '^  version: 2026-01-28$' .agents/skills/kotlin/SKILL.md .agents/skills/typescript/SKILL.md .agents/skills/zod-4/SKILL.md` 0 hits; cada uno tiene `version:` con fecha post-2026-09-01.
  done_criteria: Sin fechas heredadas; las fechas reales aplicadas.
  estimated_effort: XS

- id: TASK-034
  block: P1-B
  name: Borrar skill `shadcn-vue/SKILL.md`
  description: Eliminar `.agents/skills/shadcn-vue/SKILL.md` (no consumido por el proyecto). Conservar el folder si contiene subcarpetas con assets legítimos; si no, eliminar también.
  references:
    - design.md#AD-7
    - specs/capability-modern-best-practices.md#REQ-MBP-001
  depends_on: [TASK-001]
  deliverable: Skill eliminada.
  verify: `[ ! -f .agents/skills/shadcn-vue/SKILL.md ]` true.
  done_criteria: Verdadero; ninguna referencia residual en otras skills (spot-check: `rg -n 'shadcn-vue' .agents/skills/` solo debe retornar menciones históricas en `archived/` o en notas, no referencias activas).
  estimated_effort: XS

- id: TASK-035
  block: P1-B
  name: Reescribir `playwright-best-practices/SKILL.md` sustituyendo ejemplos Next.js por Vue/Astro
  description: Conservar lecciones genéricas (POM, fixtures, retries, accessibility). Cuando un ejemplo use Next.js pero la lección aplique al E2E del monorepo, sustituir el código por Vue (apps/web/app) o Astro (apps/web/marketing). Etiquetar ejemplos genéricos sin rewrite como "Next.js example, lesson is platform-agnostic".
  references:
    - design.md#AD-7
    - specs/capability-modern-best-practices.md#REQ-MBP-002..004
  depends_on: [TASK-001]
  deliverable: Skill modificada con ejemplos anclados al monorepo.
  verify: `rg -n 'Next\.js' .agents/skills/playwright-best-practices/SKILL.md | wc -l` ≤ baseline + adyacencia justificada; al menos 3 ejemplos reescritos a Vue/Astro (spot-check manual).
  done_criteria: Spot-check confirma reescritura; patrón POM/fixtures preservado.
  estimated_effort: M

- id: TASK-036
  block: P1-B
  name: Acotar alcance de `modern-web-guidance/SKILL.md`
  description: Añadir nota en el cuerpo (no en description) aclarando que aplica solo a HTML/CSS/JS no cubierto por skills locales; cualquier skill local gana.
  references:
    - design.md#AD-7
    - specs/capability-modern-best-practices.md#REQ-MBP-005
  depends_on: []
  deliverable: Nota de cuerpo añadida.
  verify: `rg -n 'If a local skill covers the topic, the local skill wins' .agents/skills/modern-web-guidance/SKILL.md` 1 hit.
  done_criteria: Verificación pasa.
  estimated_effort: XS

- id: TASK-037
  block: P1-B
  name: Auditar `ALWAYS`/`NEVER`/`REQUIRED` en skills y justificar o suavizar
  description: Cada regla dogmática debe justificarse con razón técnica adyacente o sustituirse por `Prefer X. Justify deviation in PR description.`. Prohibido reglas dogmáticas sin base. Recorrido por las 67 skills; commit por skill o por bloque.
  references:
    - design.md#AD-7
    - specs/capability-modern-best-practices.md#REQ-MBP-007
  depends_on: [TASK-001]
  deliverable: Skills actualizados según auditoría.
  verify: `rg -n '\bALWAYS\b|\bNEVER\b|\bREQUIRED\b' .agents/skills/ -g '!SKILL.md'` 0 hits en archivos que no son SKILL.md; para cada hit en SKILL.md, inspeccionar adyacencia y verificar justificación ≥ 1 frase.
  done_criteria: Reglas dogmáticas sin justificación, sustituidas o robustecidas con razón técnica.
  estimated_effort: M

- id: TASK-038
  block: P1-B
  name: Marcar P1-B como `applied, blocked-verify` hasta P1-D operativo
  description: Tareas P1-B se aplican en Oleada 2 (TASK-034..TASK-037), pero la verificación COMPLIANT depende de P1-D. Documentar este estado en el `verify-report.md` y en `state.yaml.apply_summary` cuando el apply ejecute. Hard dep REQ-KB-UMBRELLA-003.
  references:
    - design.md#AD-7
    - specs/capability-modern-best-practices.md#REQ-MBP-006
    - specs/capability-umbrella-knowledge-bundle.md#REQ-KB-UMBRELLA-003
  depends_on: [TASK-034, TASK-035, TASK-036, TASK-037]
  deliverable: Marcador explícito en `state.yaml.apply_summary` y referencia a la dependencia REQ-KB-UMBRELLA-003.
  verify: `rg -n 'blocked-verify' openspec/changes/skill-and-knowledge-bundle-remediation/state.yaml` ≥ 1 hit; `rg -n 'REQ-KB-UMBRELLA-003' openspec/changes/skill-and-knowledge-bundle-remediation/state.yaml` ≥ 1 hit.
  done_criteria: Marcador presente; cierre de P1-B se desbloquea solo al cerrar TASK-042..TASK-044.
  estimated_effort: XS

### Oleada 3 — Anti-drift gates

- id: TASK-039
  block: P1-C
  name: Crear `.agents/scripts/skill-comment-scan.mjs` (Node 20+, zero deps)
  description: Script determinístico que detecta los 7 pattern groups de AD-8. CLI: `--paths <dir>` repeatible, `--allowlist <json>`, `--json`. Exit 1 si violation; 0 clean.
  references:
    - design.md#AD-8
    - specs/capability-comment-cleanup.md#REQ-CC-001..004
  depends_on: []
  deliverable: `.agents/scripts/skill-comment-scan.mjs` ejecutable.
  verify: `node .agents/scripts/skill-comment-scan.mjs --paths .agents/skills` corre y produce output no-error con allowlist mínimo (TASK-040).
  done_criteria: Script cumple contrato de AD-8; ejecutable sin dependencias externas.
  estimated_effort: M

- id: TASK-040
  block: P1-C
  name: Crear allowlist canónico `.agents/scripts/skill-comment-allowlist.json`
  description: Entries por defecto: `LICENSE-*.md`, `shared/common/src/main/kotlin/License.kt`, `.agents/scripts/**`, `generated_*.kt` con `*.gen.ts`. Estructura: `[{path, reason}]`. Documentar la lista como "non-exhaustive, extend in PR".
  references:
    - design.md#AD-8
    - specs/capability-comment-cleanup.md#REQ-CC-004
  depends_on: []
  deliverable: `.agents/scripts/skill-comment-allowlist.json` con entries iniciales.
  verify: `cat .agents/scripts/skill-comment-allowlist.json | jq 'length'` ≥ 4.
  done_criteria: Allowlist cargable por TASK-039.
  estimated_effort: XS

- id: TASK-041
  block: P1-C
  name: Crear sub-agente `comment-cleanup` (model: haiku)
  description: Caso de uso: dado un archivo con violations complejas (KDoc multi-línea, comentarios justificados por dominio), evalúa si el comentario es required (header license, generated marker, shebang) y propone entry al allowlist, o propone rewrite names/types más explícitos.
  references:
    - design.md#AD-8
    - specs/capability-comment-cleanup.md#REQ-CC-005..006
  depends_on: []
  deliverable: `.agents/agents/comment-cleanup.md` (o `.toml`) con frontmatter `model: haiku`.
  verify: `head -5 .agents/agents/comment-cleanup.md | grep -F 'model: haiku'` exit 0.
  done_criteria: Sub-agente discoverable por los runners de OpenCode/AgentSync.
  estimated_effort: S

- id: TASK-042
  block: P1-C
  name: Crear test determinístico del scanner
  description: `.agents/scripts/__tests__/skill-comment-scan.test.mjs`: cubre 7 pattern groups con ≥ 1 fixture cada uno, respeta allowlist por path, exit codes correctos, no false-positives en shebangs/headers SPDX. Runner: Vitest si ya devDep; en su defecto `node --test`.
  references:
    - design.md#AD-8
    - specs/capability-comment-cleanup.md#REQ-CC-007
  depends_on: [TASK-039, TASK-040]
  deliverable: Test ejecutable.
  verify: `node .agents/scripts/__tests__/skill-comment-scan.test.mjs` (o `pnpm exec vitest run .agents/scripts/__tests__/skill-comment-scan.test.mjs`) todos los tests verdes.
  done_criteria: Cobertura de los 7 pattern groups; ≥ 8 assertions (los 7 grupos + exit code).
  estimated_effort: M

- id: TASK-043
  block: P1-C
  name: Reframear AGENTS.md sección "Fix Simplicity and Zero-Comment Policy"
  description: Sustituir el párrafo actual por el wording verbatim de AD-8 (`Prefer self-documenting code...`). Eliminar la sección "Static Analysis..." que duplica el comment contract (mantener la parte de static-analysis intacta).
  references:
    - design.md#AD-8
    - specs/capability-comment-cleanup.md#REQ-CC-001
  depends_on: []
  deliverable: `.agents/AGENTS.md` reemplazado con wording canónico.
  verify: `rg -n 'Prefer self-documenting code\. The agent MUST NOT generate explanatory comments' .agents/AGENTS.md` 1 hit; `rg -n 'Never leave comments in the repo\. The standard is zero comments' .agents/AGENTS.md` 0 hits.
  done_criteria: Wording canónico presente; legacy wording eliminado.
  estimated_effort: S

- id: TASK-044
  block: P1-D
  name: Crear `.agents/scripts/skill-doctor.mjs` (Node, zero deps)
  description: Implementa las 8 validaciones de AD-9: `frontmatter.present`, `frontmatter.name`, `metadata.category`, `metadata.family`, `metadata.version`, `paths.broken`, `contamination.present`, `subagent` (warn). CLI: `--skills-dir <dir>`, `--fail-on <kind>` repeatible, `--json`. Exit 1 si hay violations coincidentes con `--fail-on`; 0 si clean.
  references:
    - design.md#AD-9
    - specs/capability-automated-skill-doctor.md#REQ-SD-001..007
  depends_on: []
  deliverable: `.agents/scripts/skill-doctor.mjs` ejecutable.
  verify: `node .agents/scripts/skill-doctor.mjs --skills-dir .agents/skills --json | jq '.skills | length'` ≥ 67 (post-Oleada 1); `node .agents/scripts/skill-doctor.mjs --skills-dir .agents/skills --fail-on missing-frontmatter` exit 0 sobre el worktree tras TASK-002/003/005.
  done_criteria: 8 checks implementadas; modo `--fail-on` selectivo funcional.
  estimated_effort: L

- id: TASK-045
  block: P1-D
  name: Crear sub-agente `skill-doctor` (model: sonnet)
  description: Caso de uso: dado el diff de un PR que toca `.agents/skills/**` o `docs/architecture/adr/**`, lee los archivos y emite severidad `pass | warn | block`. `block` cuando hay contradicción material con una fuente canónica (DESIGN.md, ADR-0002 amended, AGENTS.md amended) o contamination residual.
  references:
    - design.md#AD-9
    - specs/capability-automated-skill-doctor.md#REQ-SD-008
  depends_on: []
  deliverable: `.agents/agents/skill-doctor.md` (o `.toml`) con frontmatter `model: sonnet`.
  verify: `head -5 .agents/agents/skill-doctor.md | grep -F 'model: sonnet'` exit 0.
  done_criteria: Sub-agente discoverable por AgentSync.
  estimated_effort: S

- id: TASK-046
  block: P1-D
  name: Crear workflow `.github/workflows/skill-doctor.yml`
  description: Trigger `pull_request` con paths filter `.agents/**` y `docs/architecture/adr/**`. Jobs: `deterministic-scan` (Node 20, ejecuta TASK-044), `comment-cleanup-scan` (ejecuta TASK-039), `llm-audit` (invoca TASK-045). Permissions `contents: read`, `pull-requests: read`. Fail si cualquier job exit != 0 o severidad `block`.
  references:
    - design.md#AD-9
    - specs/capability-automated-skill-doctor.md#REQ-SD-001..009
  depends_on: [TASK-039, TASK-044, TASK-045]
  deliverable: `.github/workflows/skill-doctor.yml` parseable.
  verify: `python3 -c 'import yaml,sys; yaml.safe_load(open(".github/workflows/skill-doctor.yml"))'` exit 0; `rg -n 'pull_request' .github/workflows/skill-doctor.yml` ≥ 1 hit; `rg -n 'paths:' .github/workflows/skill-doctor.yml` ≥ 1 hit con `.agents/**` listado.
  done_criteria: Workflow parseable; sintaxis correcta; especificación de permissions correcta.
  estimated_effort: M

- id: TASK-047
  block: P1-D
  name: Activar P1-B verify al cerrar TASK-046
  description: Cuando el workflow TASK-046 queda mergeado a main, marcar como required check en branch protection (operación manual en GitHub UI, registrada en PR description). P1-B pasa de `applied, blocked-verify` a `COMPLIANT`.
  references:
    - design.md#AD-9
    - specs/capability-umbrella-knowledge-bundle.md#REQ-KB-UMBRELLA-003
  depends_on: [TASK-046]
  deliverable: Branch protection actualizada; nota en `verify-report.md`.
  verify: PR description incluye paso manual "marcar skill-doctor como required check". Verificación real: `gh api repos/{owner}/{repo}/branches/main/protection/required_status_checks` lista `skill-doctor` (verificación externa al sandbox).
  done_criteria: Workflow declarado required; P1-B listo para COMPLIANT.
  estimated_effort: XS

- id: TASK-048
  block: ADR-NNN
  name: Crear `docs/architecture/adr/0025-skill-and-knowledge-bundle-taxonomy.md`
  description: ADR-NNN (próximo ID libre = `0025`) que codifica la taxonomía canónica: folder name == frontmatter name == skill id; `metadata.category` y `metadata.family` requeridos; reglas del skill-doctor. Añadir entrada al `docs/architecture/adr/README.md`. Vincular con `umbrella-knowledge-bundle/spec.md`.
  references:
    - design.md#AD-1 (driver) + governance G-2 en `proposal.md`
    - specs/capability-umbrella-knowledge-bundle.md#REQ-KB-UMBRELLA-002
    - specs/capability-skill-discovery-identity.md#REQ-SD-008
  depends_on: [TASK-002, TASK-003]
  deliverable: `docs/architecture/adr/0025-skill-and-knowledge-bundle-taxonomy.md`; índice de ADRs actualizado.
  verify: `[ -f docs/architecture/adr/0025-skill-and-knowledge-bundle-taxonomy.md ]` exit 0; `rg -n '0025-skill-and-knowledge-bundle-taxonomy' docs/architecture/adr/README.md` ≥ 1 hit.
  done_criteria: ADR creado con secciones estándar; referenciado desde el README.
  estimated_effort: S

### Cierre — Cross-cutting verifications

- id: TASK-049
  block: cross-verify
  name: Ejecutar CK-1 (skill-doctor --fail-on contamination)
  description: Verificación cruzada del gate primario.
  references:
    - design.md#AD-9 (verification contract)
    - specs/capability-automated-skill-doctor.md#REQ-SD-005
  depends_on: [TASK-019, TASK-020, TASK-021, TASK-022, TASK-023, TASK-024, TASK-044]
  deliverable: Reporte Pass/Fail registrado en `verify-report.md`.
  verify: `node .agents/scripts/skill-doctor.mjs --skills-dir .agents/skills --fail-on contamination` exit 0.
  done_criteria: Exit 0; log sin violations de contamination.
  estimated_effort: XS

- id: TASK-050
  block: cross-verify
  name: Ejecutar CK-2 (comment-scan con allowlist)
  description: Verificación cruzada del post-processing de comments.
  references:
    - design.md#AD-8
    - specs/capability-comment-cleanup.md#REQ-CC-005
  depends_on: [TASK-039, TASK-040, TASK-043]
  deliverable: Reporte Pass/Fail con lista de allowlist aplicada.
  verify: `node .agents/scripts/skill-comment-scan.mjs --paths .agents --paths docs/architecture/adr` exit 0.
  done_criteria: Exit 0; allowlist aplicado; sin false-positives en shebangs/SPDX.
  estimated_effort: XS

- id: TASK-051
  block: cross-verify
  name: Ejecutar CK-3 (simulación PR que reintroduce drift)
  description: Crear branch local, añadir una línea de contamination simulada (`CVIX` en un SKILL.md), abrir PR contra main, verificar que el workflow `skill-doctor` falla.
  references:
    - design.md#AD-9
    - specs/capability-automated-skill-doctor.md#REQ-SD-009
  depends_on: [TASK-046]
  deliverable: Evidencia registrada en `verify-report.md` (logs del job fallido en GitHub Actions local run, o simulación Node reproduciendo el veredicto).
  verify: `git checkout -b verify/reintroduce-drift; echo CVIX >> .agents/skills/testing-core/SKILL.md; node .agents/scripts/skill-doctor.mjs --skills-dir .agents/skills --fail-on contamination` exit != 0; revertir con `git checkout main -- .agents/skills/`.
  done_criteria: Evidencia reproducible de que el gate bloquearía un PR drift.
  estimated_effort: S

- id: TASK-052
  block: cross-verify
  name: Ejecutar CK-4 (skill-registry regenerado)
  description: Regenerar y verificar que el diff solo muestra inventory + dates, sin edits manuales.
  references:
    - design.md#AD-1
    - specs/capability-skill-discovery-identity.md#REQ-SD-008
  depends_on: [TASK-005, TASK-044]
  deliverable: `.agents/skill-registry.md` regenerado.
  verify: `git diff .agents/skill-registry.md` solo muestra cambios de dates/inventory; `rg -c '^## ' .agents/skill-registry.md` ≥ 67.
  done_criteria: Diff mínimo; 67 entries; sin edits manuales.
  estimated_effort: XS

- id: TASK-053
  block: cross-verify
  name: Ejecutar CK-5 (ADR-0002 amended + ADR-NNN existe con taxonomía)
  description: Verificación cruzada de artefactos contractuales.
  references:
    - design.md#AD-2 (driver)
    - specs/capability-backend-semantics.md#REQ-BS-001
    - specs/capability-umbrella-knowledge-bundle.md#REQ-KB-UMBRELLA-002
  depends_on: [TASK-006, TASK-048]
  deliverable: Confirmación de ambos ADRs y su trazabilidad.
  verify: `rg -n 'Ports location \(added 2026-09-27\)' docs/architecture/adr/0002-adhere-to-hexagonal-architecture.md` 1 hit; `rg -n 'folder_name\|canonical taxonomy\|metadata\.category' docs/architecture/adr/0025-skill-and-knowledge-bundle-taxonomy.md` ≥ 1 hit; ambos indexados en `docs/architecture/adr/README.md`.
  done_criteria: Ambos ADRs presente en índice; trazabilidad visible.
  estimated_effort: XS

## Cross-cutting verifications (al final del apply)

- CK-1: `node .agents/scripts/skill-doctor.mjs --skills-dir .agents/skills --fail-on contamination` → exit 0 (TASK-049).
- CK-2: `node .agents/scripts/skill-comment-scan.mjs --paths .agents --paths docs/architecture/adr` → exit 0 con allowlist aplicado (TASK-050).
- CK-3: PR simulado reintroduciendo drift → el workflow `skill-doctor` falla (TASK-051).
- CK-4: `.agents/skill-registry.md` regenerado refleja 67 skills con metadata canónica (TASK-052).
- CK-5: ADR-0002 enmendado con frase canónica y `0025-skill-and-knowledge-bundle-taxonomy.md` existe y enlaza DESIGN.md (TASK-053).

## Sequencing rationale

El orden de las 3 oleadas minimiza blast radius y respeta las dependencias contractuales:

1. **Oleada 1 (Governance foundations)** no toca contenido de skills de producto; solo mueve carpetas (preservando `git mv`), anota metadatos y enmendar constituciones. Si una task de esta oleada falla, las 67 skills siguen encontrables pero con metadata mejorada y la constitución clara para Oleada 2.
2. **Oleada 2 (Content remediation + version policy)** aplica borrados puntuales y rewrites anclados; aquí ya puede haber contención si el doctor detecta contamination residual. Las tasks P0-C y P0-D son paralelizables entre sí.
3. **Oleada 3 (Anti-drift gates)** construye el CI gate después de que todo el contenido ha sido remediado. Esto garantiza que el primer commit del gate encuentra un estado limpio y solo falla ante regresión real.
4. **Hard dep P1-B ⇸ P1-D**: las tasks P1-B se aplican estructuralmente en Oleada 2 (TASK-034..TASK-037), pero el `verify` queda en estado `applied, blocked-verify` (TASK-038). El desbloqueo se materializa en TASK-047 cuando el workflow es marcado required. Si P1-D no cierra, el apply completo puede archivar — pero el `verify-report.md` y la `umbrella-knowledge-bundle/spec.md` deben reflejar `COMPLIANT` solo tras TASK-047.
5. **Concurrencia (R5)**: `publication-calendar-sse` debe archivarse antes de TASK-001; gate "wait-for" previo al apply.

## Open risks

| ID | Riesgo | Estado | Mitigación |
|---|---|---|---|
| R1 | Marker `com.profiletailors.common.domain.Service` cambia de path o se renombra | open | TASK-009 verifica el archivo `shared/common/.../Service.kt` antes de editar spring-boot/SKILL.md; si cambia, TASK-009 aborta y se re-explora. |
| R2 | Skills con referencias rotas no detectadas hasta P1-D | mitigated | AD-9 check 6 `paths.broken` cubre el universo; TASK-044 lo implementa. |
| R3 | Inventario reactivo deja 1-2 `references/*.md` sin reescribir (tokens no aparecen explícitamente) | mitigated | Verificación P0-B usa `rg` positivo por plantillas reactivas (WebTestClient, StepVerifier, R2dbcRepository) — TASK-049 cross-verify. |
| R4 | Editor de código reorganiza accidentalmente `.agents/skills/breakdown` durante flatten | mitigated | TASK-001 usa `git mv` y deja solo renames en `git status`. |
| R5 | `publication-calendar-sse` queda en `apply` mientras corra TASK-001 | open | Gate "wait-for: publication-calendar-sse archive" antes de Oleada 1; el state.yaml registra la dependencia. |
| R6 | PR tercero reintroduce drift mientras corre el skill-doctor CI | mitigated | El doctor corre en cada PR; failures bloquean merge. Documentado en `docs/architecture/adr/0025-skill-and-knowledge-bundle-taxonomy.md`. |
