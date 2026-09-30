# Capability — Backend Semantics (P0-B)

## Purpose

Reconciliar la doctrina backend del knowledge bundle con el código real
del monorepo: enmendar ADR-0002 para declarar explícitamente que
**Domain owns the repository/gateway ports/interfaces**, corregir
AGENTS.md donde dice "inward-facing ports" (léxico invertido),
borrar la referencia al marker fantasma `@ApplicationService` y
sustituirla por el marker real `com.profiletailors.common.domain.Service`,
y reescribir línea por línea los archivos `references/` de las
subskills de Spring que enseñan patrones servlet, JPA, Lombok o
Mockito cuando el proyecto es WebFlux + R2DBC + coroutines + MockK
puro.

## Authority

- Governance decision **G-1** (ADR-0002 enmendado).
- Trazabilidad: bloque **P0-B** del `proposal.md`.
- ADR a modificar: ADR-0002 (enmienda, status pasa a "Accepted
  (amended)").

## Scope

- Archivos modificados:
  - `.agents/AGENTS.md` líneas 384 y 394 (sustitución léxica).
  - `docs/architecture/adr/0002-adhere-to-hexagonal-architecture.md`
    (adición del párrafo canónico).
  - `.agents/skills/spring-boot/SKILL.md` sección
    "Local Architectural Markers" (borrado de `@ApplicationService`).
  - `.agents/skills/hexagonal-architecture/SKILL.md`
    (verificar que ya dice "domain owns ports"; anotar la
    coincidencia).
  - `.agents/skills/spring-boot-{security,cache,
    resilience,messaging,saga-pattern,ai-mcp-server-patterns,
    data-neo4j-reactive}/references/**.md` (rewrite línea por línea).
- Reescritura línea por línea: NO archive, NO delete en
  `references/`. Las guías con prefijo `legacy-*` o que documenten
  explícitamente una migración servlet→reactive conservan su patrón
  servlet marcado como tal.

## Requirements

| REQ-ID               | Statement                                                                                                                                       |
|----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-BS-001           | ADR-0002 SHALL contener en su sección "Decision" el párrafo canónico: "Domain owns the repository/gateway ports/interfaces. Application depends on those ports. Infrastructure depends on Application and implements those ports." |
| REQ-BS-002           | El status de ADR-0002 SHALL pasar de "Accepted" a "Accepted (amended)" y SHALL incluir `Updated: <fecha>` en la sección de metadatos.            |
| REQ-BS-003           | `.agents/AGENTS.md` línea 384 SHALL sustituir "inward-facing ports" por "domain-defined ports" (o equivalente acordado en design).                |
| REQ-BS-004           | `.agents/AGENTS.md` línea 394 SHALL sustituir "inward-facing side" por "domain side" (o equivalente acordado en design).                         |
| REQ-BS-005           | `.agents/skills/spring-boot/SKILL.md` SHALL borrar toda mención a `@ApplicationService` en `com.profiletailors.common.application`. |
| REQ-BS-006           | `.agents/skills/spring-boot/SKILL.md` SHALL referenciar el marker real `com.profiletailors.common.domain.Service` definido en `shared/common/src/main/kotlin/com/profiletailors/common/domain/Service.kt:18`. |
| REQ-BS-007           | Cada `references/` reescrito SHALL sustituir `HttpSecurity` por `ServerHttpSecurity` salvo en guías marcadas explícitamente como `legacy-*`.  |
| REQ-BS-008           | Cada `references/` reescrito SHALL sustituir `SecurityFilterChain` por `SecurityWebFilterChain` salvo en guías `legacy-*`.                      |
| REQ-BS-009           | Cada `references/` reescrito SHALL sustituir `MockMvc` por `WebTestClient`, `@MockBean` por `@MockkBean`, y `MockitoExtension` por Kotest o JUnit5 + MockK. |
| REQ-BS-010           | Cada `references/` reescrito SHALL sustituir `JpaRepository` por R2DBC (`suspend fun`, `Reactive*` tipos), borrar Lombok y sustituir `@RequiredArgsConstructor` por constructor primario o `data class`. |
| REQ-BS-011           | Cada `references/` reescrito SHALL sustituir `spring-boot-starter-web` y `spring-boot-starter-data-jpa` por `spring-boot-starter-webflux` y la dependencia R2DBC correspondiente. |
| REQ-BS-012           | `OncePerRequestFilter` SHALL aparecer solo en guías marcadas `legacy-*` o de migración servlet→reactive; SHALL NO aparecer en ningún otro `references/`. |

## Scenarios

### Scenario: ADR-0002 enmendado

**REQ-BS-001, REQ-BS-002**

- GIVEN el estado actual de ADR-0002 no contiene el párrafo canónico
- WHEN se aplica esta capability
- THEN SHALL existir en `docs/architecture/adr/0002-adhere-to-hexagonal-architecture.md`
  una sección "Decision" actualizada con la frase "Domain owns the
  repository/gateway ports/interfaces", SHALL aparecer "Accepted
  (amended)" como status, y SHALL existir una línea de fecha de
  actualización.

### Scenario: AGENTS.md corregido

**REQ-BS-003, REQ-BS-004**

- GIVEN `.agents/AGENTS.md` líneas 384 y 394 contienen
  "inward-facing ports" y "inward-facing side"
- WHEN se aplica esta capability
- THEN SHALL aparecer en su lugar "domain-defined ports" y
  "domain side" (o equivalente registrado en `design.md`).
- AND la sección "Operating Contract" SHALL permanecer textualmente
  idéntica al snapshot de `explore-notes.md`.

### Scenario: Marker real apuntado, marker fantasma borrado

**REQ-BS-005, REQ-BS-006**

- GIVEN `spring-boot/SKILL.md` define `@ApplicationService` en
  `com.profiletailors.common.application`
- WHEN se aplica esta capability
- THEN SHALL eliminarse toda mención a `@ApplicationService` en esa
  skill, SHALL NO existir ningún archivo `ApplicationService.kt`
  referenciado por la skill, y SHALL existir la referencia al marker
  real `com.profiletailors.common.domain.Service` con un path
  exacto (`Service.kt:18` o el que el apply determine tras
  re-inspección).

### Scenario: Spring scrub sustituido por patrones reactivos

**REQ-BS-007, REQ-BS-008, REQ-BS-009, REQ-BS-010, REQ-BS-011, REQ-BS-012**

- GIVEN `references/` en `spring-boot-{security,cache,resilience,
  messaging,saga-pattern,ai-mcp-server-patterns,
  data-neo4j-reactive}/` contiene tokens incompatibles
  (HttpSecurity, SecurityFilterChain, MockMvc, JpaRepository, Lombok,
  @MockBean, MockitoExtension)
- WHEN se aplica esta capability
- THEN SHALL ejecutarse una reescritura línea por línea (NO archive,
  NO delete) de esos `references/`, SHALL haber 0 hits de los tokens
  incompatibles fuera de guías `legacy-*` marcadas explícitamente,
  y SHALL haber presencia reactiva (ServerHttpSecurity,
  SecurityWebFilterChain, WebTestClient, @MockkBean, StepVerifier,
  R2DBC, coroutines, MockK).

### Scenario: skill hexagonal coherente

- GIVEN `.agents/skills/hexagonal-architecture/SKILL.md`
  ya declara "Ports | Interfaces defined by domain, implemented by
  infrastructure" según `explore-notes.md` §3
- WHEN finaliza el apply de esta capability
- THEN SHALL NO contradecir ADR-0002 enmendado; SHALL existir una
  referencia al ADR desde la skill (vía link markdown estándar).

### Scenario: Review catches reintroduced backend drift

**REQ-BS-007**

- GIVEN a PR reintroduces `HttpSecurity` in
  `spring-boot-security/references/` outside an explicitly marked legacy guide
- WHEN authors and reviewers check backend guidance under ADR-0026
- THEN they SHALL require correction to `ServerHttpSecurity` before approval.
- AND enforcement SHALL use code review; the retired
  `.github/workflows/skill-doctor.yml` is not an automated merge gate.
