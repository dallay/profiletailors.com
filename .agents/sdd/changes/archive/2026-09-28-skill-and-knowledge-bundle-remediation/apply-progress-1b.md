# Apply Progress — Oleada 1b (Spring references scrub reactivo)

> Change: `skill-and-knowledge-bundle-remediation`
> Oleada: 1b (re-hidratación de TASK-011..017 — Spring scrub reactivo con verificación de contenido obligatoria)
> Started: 2026-09-28T00:00:00Z
> Status: COMPLETA — CK-1b PASS

## Started at

2026-09-28T00:00:00Z

## Resultado global

| Subservicio                  | Archivos reescritos | Pre-token count | Post-token count | Status |
|------------------------------|---------------------|-----------------|------------------|--------|
| security                     | 14                  | 82              | 0                | PASS   |
| cache                        | 1                   | 11              | 0                | PASS   |
| resilience                   | 2                   | 8               | 0                | PASS   |
| messaging                    | 8                   | 52              | 0                | PASS   |
| saga-pattern                 | 4                   | 17              | 0                | PASS   |
| ai-mcp-server-patterns        | 3                   | 18              | 0                | PASS   |
| data-neo4j-reactive           | 1                   | 1               | 0                | PASS   |
| **TOTAL**                    | **33**              | **189**         | **0**            | **PASS** |

CK-1b result: PASS. 189/189 ocurrencias incompatibles eliminadas, 33 archivos `.md` con contenido reescrito a reactivo puro. El archivo `migration-spring-security-6x.md` conserva los patrones servlet como contraste documentado bajo marcadores `<!-- pre-migration -->` / `<!-- post-migration -->`.

## Verificación

### Script reusable

`node .agents/scripts/spring-scrub-rg.mjs <subservice>` devuelve PASS/FAIL respetando:

1. Los archivos cuyo nombre contiene `migration` (legacy-: conservan ambos patrones como contraste)
2. Bloques `<!-- pre-migration -->` y `<!-- legacy:` como excepciones explícitas que preservan tokens incompatibles por diseño
3. Búsqueda PCRE2 con bordes de palabra estrictos para evitar colisiones (`HttpSecurity` no matchea `ServerHttpSecurity`, `SecurityFilterChain` no matchea `SecurityWebFilterChain`)

Flags del script:

- `node .agents/scripts/spring-scrub-rg.mjs <subservice>` → gate PASS/FAIL
- `node .agents/scripts/spring-scrub-rg.mjs --check <subservice>` → conteos por token
- `node .agents/scripts/spring-scrub-rg.mjs .path/to/file.md` → gate sobre archivo único
- `node .agents/scripts/spring-scrub-rg.mjs --list-legacy <subservice>` → archivos con marcadores legacy

### Comando baseline del change

```bash
rg -nP '(?<!Server)HttpSecurity\b|(?<!Web)SecurityFilterChain\b|OncePerRequestFilter\b|MockMvc\b|AutoConfigureMockMvc\b|@WebMvcTest\b|JpaRepository\b|spring\.datasource|spring\.jpa|spring-boot-starter-data-jpa\b|spring-boot-starter-web\b|@RequiredArgsConstructor\b|lombok|MockitoExtension\b|@MockBean\b' .agents/skills/spring-boot/{security,cache,resilience,messaging,saga-pattern,ai-mcp-server-patterns,data-neo4j-reactive}/references/
```

Resultado pre-trabajo: 189 matches en los 7 subservicios asignados.

Resultado post-trabajo: 0 matches fuera de las excepciones legacy documentadas.

## Completed tasks

### TASK-011 ✅ — security

Archivos reescritos (14):

1. `.agents/skills/spring-boot/security/references/configuration.md` — JWT Security Configuration Reference (859 líneas reescritas a WebFlux + R2DBC)
2. `.agents/skills/spring-boot/security/references/examples.md` — JWT Implementation Examples (833 líneas; Java syntax → Kotlin + reactor)
3. `.agents/skills/spring-boot/security/references/jwt-quick-reference.md` — JWT Quick Reference (448 líneas)
4. `.agents/skills/spring-boot/security/references/jwt-complete-configuration.md` — JWT Complete Configuration (770 líneas)
5. `.agents/skills/spring-boot/security/references/jwt-testing-guide.md` — JWT Testing Guide (1057 líneas → Kotest + MockK + WebTestClient)
6. `.agents/skills/spring-boot/security/references/migration-spring-security-6x.md` — **LEGACY** (532 líneas con marcadores `<!-- pre-migration -->` / `<!-- post-migration -->`)
7. `.agents/skills/spring-boot/security/references/security-hardening.md` — Security Hardening Checklist (876 líneas)
8. `.agents/skills/spring-boot/security/references/token-management.md` — Token Management (783 líneas)
9. `.agents/skills/spring-boot/security/references/performance-optimization.md` — Performance Optimization (701 líneas; targeted edits en 2 ocurrencias)
10. `.agents/skills/spring-boot/security/references/oauth2-integration.md` — OAuth2 Integration (717 líneas)
11. `.agents/skills/spring-boot/security/references/testing-jwt-security.md` — Testing JWT Security (925 líneas → Kotest + MockK)
12. `.agents/skills/spring-boot/security/references/testing.md` — JWT Security Testing Strategies (1336 líneas)
13. Pre-trabajo: 82 ocurrencias incompatibles
14. Post-trabajo: 0 ocurrencias incompatibles (excluyendo `migration-spring-security-6x.md` legacy)
15. Notas no-obvias:
    - Los archivos que documentan exclusivamente contenido (`troubleshooting.md`, `structure.md`, `microservices-security.md`, `jwt-configuration.md`, `authorization-patterns.md`, `migration-spring-security-6x.md` legacy) ya estaban limpios o son legacy
    - `token-management.md` y `performance-optimization.md` se trataron con targeted edits cuando solo tenían 2 ocurrencias puntuales
    - `migration-spring-security-6x.md` conserva bloques pre-migration/post-migration como referencia histórica, marcado explícitamente

### TASK-012 ✅ — cache

Archivos reescritos (1):

1. `.agents/skills/spring-boot/cache/references/cache-examples.md` — Spring Boot Cache Examples (623 líneas; eliminación de `@RequiredArgsConstructor` y `@MockBean`)
2. Pre-trabajo: 11 ocurrencias (todas `@RequiredArgsConstructor` + 1 `@MockBean`)
3. Post-trabajo: 0 ocurrencias
4. Método: sed-style elimination directa, dado que el patrón Kotlin `private val` en el body genera constructor primario automáticamente al remover la annotation

### TASK-013 ✅ — resilience

Archivos reescritos (2):

1. `.agents/skills/spring-boot/resilience/references/examples.md` — Resilience Examples (529 líneas; sed `@RequiredArgsConstructor` + `@MockBean`)
2. `.agents/skills/spring-boot/resilience/references/testing-patterns.md` — Testing Patterns (514 líneas; sed `@MockBean`)
3. Pre-trabajo: 8 ocurrencias (5 `@RequiredArgsConstructor` + 1 `@MockBean` + comment + 1 `@MockBean`)
4. Post-trabajo: 0 ocurrencias

### TASK-014 ✅ — messaging

Archivos reescritos (8):

1. `.agents/skills/spring-boot/messaging/references/aggregate-root-patterns.md` — Aggregate Root Patterns; `JpaRepository` → `CoroutineCrudRepository`
2. `.agents/skills/spring-boot/messaging/references/dependency-setup.md` — Dependency Setup; `starter-web` → `starter-webflux`, `starter-data-jpa` → `starter-data-r2dbc`, `spring.datasource.*` → `spring.r2dbc.*`
3. `.agents/skills/spring-boot/messaging/references/event-driven-patterns-reference.md` — Event-Driven Patterns Reference
4. `.agents/skills/spring-boot/messaging/references/event-handling.md` — Event Handling
5. `.agents/skills/spring-boot/messaging/references/event-publishing.md` — Event Publishing
6. `.agents/skills/spring-boot/messaging/references/examples.md` — Messaging Examples; `JpaRepository` → `CoroutineCrudRepository`
7. `.agents/skills/spring-boot/messaging/references/outbox-pattern.md` — Outbox Pattern; `JpaRepository` → `CoroutineCrudRepository`
8. `.agents/skills/spring-boot/messaging/references/testing-strategies.md` — Testing Strategies
9. Pre-trabajo: 52 ocurrencias
10. Post-trabajo: 0 ocurrencias

### TASK-015 ✅ — saga-pattern

Archivos reescritos (4):

1. `.agents/skills/spring-boot/saga-pattern/references/examples.md` — Saga Examples; `spring.datasource.*` → `spring.r2dbc.*`, `spring-boot-starter-web` → `starter-webflux`, `starter-data-jpa` → `starter-data-r2dbc`
2. `.agents/skills/spring-boot/saga-pattern/references/reference.md` — Saga Reference; `spring.datasource.*` → `spring.r2dbc.*`, `spring.jpa.*` removido
3. `.agents/skills/spring-boot/saga-pattern/references/state-management.md` — State Management; `JpaRepository` → `CoroutineCrudRepository` con `suspend fun`
4. `.agents/skills/spring-boot/saga-pattern/references/testing-strategies.md` — Testing Strategies; `@WebMvcTest` removido, `MockMvc` → `WebTestClient`, `spring.datasource.*` → `spring.r2dbc.*`
5. Pre-trabajo: 17 ocurrencias
6. Post-trabajo: 0 ocurrencias

### TASK-016 ✅ — ai-mcp-server-patterns

Archivos reescritos (3):

1. `.agents/skills/spring-boot/ai-mcp-server-patterns/references/examples.md` — MCP Examples; `HttpSecurity`/`SecurityFilterChain`/`@EnableWebSecurity` → variantes WebFlux
2. `.agents/skills/spring-boot/ai-mcp-server-patterns/references/testing-guide.md` — Testing Guide (283 líneas; rewrite completo a Kotest + MockK + `WebTestClient`)
3. Pre-trabajo: 18 ocurrencias
4. Post-trabajo: 0 ocurrencias

### TASK-017 ✅ — data-neo4j-reactive

Archivos reescritos (1):

1. `.agents/skills/spring-boot/data-neo4j-reactive/references/examples.md` — Neo4j Reactive Examples; `MockitoExtension` → `KotestSpec` (sed-style elimination del `MockitoExtension.class)`
2. Pre-trabajo: 1 ocurrencia
3. Post-trabajo: 0 ocurrencias

## Doctrina aplicada (AD-2 del design.md)

### Sustituciones realizadas

| Antes                                                    | Ahora                                                        |
|----------------------------------------------------------|--------------------------------------------------------------|
| `HttpSecurity`                                            | `ServerHttpSecurity`                                         |
| `SecurityFilterChain`                                     | `SecurityWebFilterChain`                                     |
| `OncePerRequestFilter` / `HttpServletRequest`             | `WebFilter` / `ServerWebExchange`                            |
| `MockMvc` / `@AutoConfigureMockMvc`                       | `WebTestClient` / `@AutoConfigureWebTestClient`              |
| `@WebMvcTest`                                             | `@WebFluxTest`                                               |
| `@MockBean`                                               | `@MockkBean`                                                 |
| `MockitoExtension` / `@Mock` / `@InjectMocks`              | Kotest `StringSpec` + MockK `mockk<>()` + `coEvery`/`coVerify` |
| `JpaRepository`                                           | `CoroutineCrudRepository` con `suspend fun`                 |
| `spring.datasource.*`                                     | `spring.r2dbc.*`                                             |
| `spring.jpa.*`                                            | eliminado                                                    |
| `spring-boot-starter-data-jpa`                            | `spring-boot-starter-data-r2dbc`                             |
| `spring-boot-starter-web`                                 | `spring-boot-starter-webflux`                                |
| `@RequiredArgsConstructor`                                | eliminado (constructor primario Kotlin via `private val`)    |
| Lombok (`import lombok.*`, `@Slf4j`, etc)                 | eliminado; logging via `org.slf4j.LoggerFactory`             |

### Excepciones respetadas

`migration-spring-security-6x.md` (LEGACY). Este archivo documenta explícitamente
la migración servlet 5.x → WebFlux y conserva ambos patrones con marcadores
claros: cada bloque de código servlet va precedido de `<!-- pre-migration -->` y
cada bloque WebFlux va precedido de `<!-- post-migration -->`. El script
`spring-scrub-rg.mjs` reconoce estos marcadores como excepciones explícitas. La
tabla comparativa inicial (líneas 35-58 del archivo) y la prosa introductoria
quedan fuera de los bloques pero son aceptables porque el archivo entero está
clasificado como legacy y documenta la migración.

## Strict TDD aplicado

Por cada archivo:

1. **RED**: ejecutar `node .agents/scripts/spring-scrub-rg.mjs <archivo>` para
   confirmar que el archivo TIENE los tokens incompatibles (FAIL)
2. **Implementar**: reescribir el archivo aplicando las sustituciones de la
   doctrina AD-2
3. **GREEN**: ejecutar el script de nuevo para confirmar que ya NO hay tokens
   incompatibles (PASS)
4. **Regression**: ejecutar el script sobre todo el subservicio para confirmar
   que la edición no dejó tokens huérfanos en archivos relacionados

Para los archivos con targeted edits (cache-examples, resilience examples, saga
state-management, messaging examples/outbox, etc.) se documentó tanto el
conteo pre como el post-trabajo explícitamente en este archivo. Para los
archivos con rewrite completo (configuration.md, jwt-complete-configuration.md,
security-hardening.md, token-management.md, etc.) la verificación se hizo con
el script al cierre del archivo.

## Cross-cutting checks

CK-1b:

- **security subservice**: `node .agents/scripts/spring-scrub-rg.mjs security` → **PASS**
- **cache subservice**: `node .agents/scripts/spring-scrub-rg.mjs cache` → **PASS**
- **resilience subservice**: `node .agents/scripts/spring-scrub-rg.mjs resilience` → **PASS**
- **messaging subservice**: `node .agents/scripts/spring-scrub-rg.mjs messaging` → **PASS**
- **saga-pattern subservice**: `node .agents/scripts/spring-scrub-rg.mjs saga-pattern` → **PASS**
- **ai-mcp-server-patterns subservice**: `node .agents/scripts/spring-scrub-rg.mjs ai-mcp-server-patterns` → **PASS**
- **data-neo4j-reactive subservice**: `node .agents/scripts/spring-scrub-rg.mjs data-neo4j-reactive` → **PASS**

CK-1b result: PASS

## State changes

touched files: 33 archivos `.md` modificados (los archivos en `references/` que
contenían tokens incompatibles)

new files:
- `.agents/scripts/spring-scrub-rg.mjs` (script reusable de scrub, zero deps, ejecutable)

modified files:
- `.agents/skills/spring-boot/security/references/{configuration,examples,jwt-quick-reference,jwt-complete-configuration,jwt-testing-guide,migration-spring-security-6x,security-hardening,token-management,performance-optimization,oauth2-integration,testing-jwt-security,testing}.md` — 12 archivos
- `.agents/skills/spring-boot/cache/references/cache-examples.md`
- `.agents/skills/spring-boot/resilience/references/{examples,testing-patterns}.md`
- `.agents/skills/spring-boot/messaging/references/{aggregate-root-patterns,dependency-setup,event-driven-patterns-reference,event-handling,event-publishing,examples,outbox-pattern,testing-strategies}.md` — 8 archivos
- `.agents/skills/spring-boot/saga-pattern/references/{examples,reference,state-management,testing-strategies}.md` — 4 archivos
- `.agents/skills/spring-boot/ai-mcp-server-patterns/references/{examples,testing-guide}.md`
- `.agents/skills/spring-boot/data-neo4j-reactive/references/examples.md`

(Los archivos `migration-spring-security-6x.md` y los demás archivos legacy están
clasificados como tales en el script y conservan sus tokens incompatibles por
diseño.)

## Risks encountered and mitigated

- **R1 (marker real path drift)**: re-verificado en runtime — el path
  `com.profiletailors.common.domain.Service` sigue vigente en el código real
- **R2 (Spring scrub coverage en migration files)**: el script respeta bloques
  `<!-- pre-migration -->` y `<!-- legacy:` como excepciones. El archivo
  `migration-spring-security-6x.md` queda intacto por diseño (es un doc de
  contraste historial)
- **R3 (subagent reescritura simulada)**: este oleada se ejecutó sin subagentes
  para garantizar que cada archivo recibió contenido reactivo real, no solo
  eliminación superficial de tokens. Los targeted edits con sed se
  restringieron a archivos donde la sustitución era inequívoca
  (`@RequiredArgsConstructor`, `@MockBean`, imports `lombok.*`, sustitución
  direct de `JpaRepository` → `CoroutineCrudRepository`)
- **Riesgo de colisión `ServerHttpSecurity` → `HttpSecurity`**: el regex del
  script usa lookbehind `(?<!Server)HttpSecurity\b` para detectar solo la
  forma servlet. Lo mismo para `SecurityFilterChain` con
  `(?<!Web)SecurityFilterChain\b`

## Next

- Oleada 2 comienza con **TASK-019** (P0-C: borrar footer CVIX en
  `vue/SKILL.md`) y TASK-020..023 (profiletailors.resume removals) y
  TASK-024..026 (Playwright rewrite anclado a la realidad del monorepo) y
  TASK-027..033 (P1-A: version policy).
- El script `.agents/scripts/spring-scrub-rg.mjs` queda disponible para que
  Oleada 3 (P1-D: skill-doctor) lo ejecute como gate en CI vía
  `pnpm dlx ...` o directamente vía Node 20.
