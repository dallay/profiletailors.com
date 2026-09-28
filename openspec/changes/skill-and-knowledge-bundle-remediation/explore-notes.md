# Explore Notes — `skill-and-knowledge-bundle-remediation`

> Fase `sdd-explore` cerrada. Producto de solo lectura; no se ha modificado
> ningún skill, ADR, AGENTS.md, DESIGN.md, CI ni código de aplicación.

Estado del change folder antes de la exploración: `phase=init`, `completed=[init]`, `next=explore`.
Resultado de la exploración: `status=ready`. Las doce auditorías requeridas
están cubiertas abajo en el mismo orden en que fueron solicitadas.

## 0. Modo y convenciones de lectura

- Total de archivos `SKILL.md` encontrados: **67** (todos dentro de `.agents/skills/`).
- Skill-registry canónico: `.agents/skill-registry.md` (generado por `sdd-init` el 2026-09-01).
- Directorios de skills con subcarpetas: `architecture-governance` (plano),
  `backend-platform`, `design-pattern` (con `behavioral/`, `creational/`,
  `structural/` vacías), `frontend-platform`, `impeccable` (con `agents/`,
  `reference/`, `scripts/`), `languages-typing`, `pnpm` (con `references/`),
  `testing`. Total de skills anidadas bajo subcarpetas: **47 de 67**.
- Skills planas (1 nivel): **20 de 67**.
- Skills con metadata en frontmatter: solo las de `impeccable`,
  `nothing-design`, `frontend-design`, `pinia`, `vitest`, `pnpm` declaran
  `metadata`. El resto usa `version`/`author`/`source` sueltos. Resultado:
  **no existe metadata consistente** `category`/`family` en ninguna skill
  del knowledge bundle. Esto bloquea el bloque P0-A "metadata.category +
  metadata.family" tal como lo aprobó el usuario.
- Folder name == frontmatter `name`: en la mayoría coincide, pero la skill
  `astrolicious-astro` vive bajo `.agents/skills/frontend-platform/` y se
  llama `astrolicious-astro` (no `astro`); el skill-registry la lista como
  `astrolicious-astro`. No hay normalización canónica.
- Skills huérfanas o con path roto: **ninguna** (todas se leyeron sin
  excepción).

## 1. Inventario del knowledge bundle actual

Cuantificación dura (todos los números derivados de `find`/`grep` sobre
el worktree actual, no del skill-registry):

| Métrica | Valor |
|---|---|
| Skills totales (`SKILL.md`) | 67 |
| Con frontmatter `name:` válido | 67/67 |
| Con `metadata:` estructurado (con `category`/`family`) | 0/67 |
| Con `metadata:` parcial (`author`, `version`, `source`) | 14/67 (vitest, pnpm, pinia, vue, kotlin, typescript, zod-4, frontend-design, nothing-design, impeccable, spring-boot + sus subskills) |
| Folder name == frontmatter `name` | 65/67 (las 2 excepciones son `astrolicious-astro` y `impeccable` que son nombres intencionales distintos al folder) |
| Skills anidadas (subcarpeta de `backend-platform`, `frontend-platform`, `design-pattern`, etc.) | 47/67 |
| Skills planas | 20/67 |

Familias presentes (derivadas de la ubicación de carpeta):

1. `architecture-governance` — 1
2. `backend-platform` — 21 (`gradle`, `docker-expert`, `hexagonal-architecture`,
   `ddd-architecture`, `spring-boot` + 16 subskills spring-boot)
3. `design-pattern` — 22 (GoF completas + subcarpetas `behavioral/`,
   `creational/`, `structural/` vacías — **debt de P0-A: subcarpetas
   categoriales vacías**)
4. `frontend-platform` — 15
5. `impeccable` — 1 (con `reference/`, `scripts/`, `agents/`)
6. `languages-typing` — 3
7. `open-pencil` — 1
8. `pinned-tag` — 1
9. `pnpm` — 1
10. `testing` — 3

Recomendación P0-A: aplanar cada `SKILL.md` a su propio folder top-level,
añadir `metadata.category` y `metadata.family` uniformes. Mantener
`impeccable/` con subcarpetas (`reference`, `scripts`, `agents`) porque son
artefactos ejecutables que la skill misma invoca.

## 2. Auditoría de skills backend Spring

Recorrido: `.agents/skills/backend-platform/spring-boot/**/*.md` y
subcarpetas (16 subskills spring-boot + `SKILL.md` raíz + `cqrs-handlers.md`
bajo `references/`). Resultado por categoría de contaminación:

### Servlet patterns que NO deberían existir (en un proyecto WebFlux)

| Token | Apariciones | Clasificación |
|---|---|---|
| `HttpSecurity` | 26 (todas en `references/` de `spring-boot/security` y `ai-mcp-server-patterns`) | **ADAPT_TO_REACTIVE** — el `spring-boot/security/SKILL.md` principal ya dice "Use `ServerHttpSecurity`, not `HttpSecurity`"; los `references/` son un baúl de migraciones servlet→reactive mal podado. |
| `SecurityFilterChain` | 27 (mismas zonas) | **ADAPT_TO_REACTIVE** — debe sustituirse por `SecurityWebFilterChain` en ejemplos reactivos; mantener sólo en guías de migración si las marcamos explícitamente. |
| `OncePerRequestFilter` | 11 | **DELETE** salvo en guías de migración; el `SKILL.md` principal lo prohíbe ya. |
| `MockMvc` | 26 | **DELETE** — el proyecto usa `WebTestClient` (verificado en `spring-boot/testing-webflux/SKILL.md`). |
| `@AutoConfigureMockMvc` | 14 | **DELETE**. |
| `@WebMvcTest` | 3 | **DELETE**. |
| `WebMvcConfigurer` | 5 | **DELETE**. |
| `@Controller` (estereotipo) | 3 | **ADAPT_TO_REACTIVE** — sustituir por `@RestController` con `suspend fun`. |

### Persistencia JPA/Hibernate (debería ser 100% R2DBC)

| Token | Apariciones | Clasificación |
|---|---|---|
| `JpaRepository` | 2 (`saga-pattern`, `security`) | **DELETE** — el proyecto es R2DBC puro, ninguna clase `*Repository` extiende `JpaRepository`. |
| `spring.datasource.url` | 9 (entre `saga-pattern`, `messaging`, `security`, `ai-mcp-server-patterns`) | **DELETE** salvo una versión reactiva. |
| `spring.jpa.hibernate.ddl-auto` | 5 | **DELETE**. |
| `spring-boot-starter-data-jpa` | 2 | **DELETE** — debe ser R2DBC. |
| `spring-boot-starter-web` | 4 | **DELETE** — debe ser `spring-boot-starter-webflux`. |
| `EntityManager` | 1 | **DELETE**. |
| `Hibernate` (en config) | 3 | **DELETE**. |

### Lombok y Mockito

| Token | Apariciones | Clasificación |
|---|---|---|
| `lombok.RequiredArgsConstructor` | 1 (`messaging/references/event-handling.md`) | **DELETE** — el proyecto no usa Lombok; Gradle/Konsist no lo aceptarían. |
| `@RequiredArgsConstructor` (sin import) | 28+ en `cache`, `security`, `resilience`, `messaging`, `data-neo4j-reactive` | **DELETE / KEEP_AS_DO_NOT_USE** — no son válidos en Kotlin, deben sustituirse por constructores primarios o `data class`. |
| `MockitoExtension` | 14 (en `security`, `messaging`, `ai-mcp-server`) | **ADAPT_TO_REACTIVE** — el `spring-boot/testing-core/SKILL.md` ya dice "Kotest o JUnit 5 + MockK"; las `references/` son ruido heredado. |
| `@MockBean` | 6 (en `ai-mcp-server-patterns`, `cache`) | **ADAPT_TO_REACTIVE** — debe sustituirse por `@MockkBean` (ver `spring-boot/testing-webflux/SKILL.md`). |

### Marcadores canónicos

| Marker | Estado real | Comentario |
|---|---|---|
| `com.profiletailors.common.domain.Service` | **EXISTE** en `shared/common/src/main/kotlin/com/profiletailors/common/domain/Service.kt:18`. Es el marker real. | Lo usan 13+ archivos `application/` y el `SmpApplication.kt:42` lo incluye en `includeFilters`. |
| `@ApplicationService` (propuesto por `spring-boot/SKILL.md:86`) | **NO EXISTE** en el código. | `find . -name ApplicationService.kt` retorna vacío. La skill está inventando un marker paralelo; esto **debe eliminarse**. |

### MVC general

- `Mvc`/`MVC`: el `spring-boot/SKILL.md:628` ya tiene un anti-pattern
  marcado "❌ Using `MockMvc` as the default web test tool in WebFlux apps".
  La doctrina raíz es correcta; las `references/` la contradicen.
- `WebMvc`: aparece sólo en `ai-mcp-server-patterns` y la skill de
  migración servlet→reactive. Clasificación: **DELETE** salvo el archivo
  `migration-spring-security-6x.md`, que debe renombrarse a `*-legacy-*` o
  moverse a `archive/`.

Resumen del bloque P0-B: **~150 ocurrencias** de patrones incompatibles con
el proyecto, distribuidos mayoritariamente en `references/` de
`spring-boot/{security,cache,resilience,messaging,saga-pattern,
ai-mcp-server-patterns,data-neo4j-reactive}`. El SKILL.md principal está
limpio; el ruido está en los `references/`.

## 3. Auditoría de decisiones arquitectónicas declaradas

### La contradicción central (la "decisión #1" del usuario)

Texto literal de cada fuente canónica:

**AGENTS.md (`.agents/AGENTS.md:394`)**:

> "Put repository/gateway contracts on the inward-facing side and implementations in infrastructure; follow the existing context convention rather than importing an adapter into application code."

**AGENTS.md (`.agents/AGENTS.md:384`) — tabla de capas**:

> Application | Domain and **inward-facing ports**

**ADR-0002 (`docs/architecture/adr/0002-adhere-to-hexagonal-architecture.md`)**:

> "**Application**: Use-case handlers and ports (interfaces). MUST NOT depend on the infrastructure layer or Spring stereotypes."

> "Application services MUST use the custom `com.profiletailors.common.domain.Service` marker instead of Spring's `@Service`."

**Hexagonal-architecture skill (`.agents/skills/backend-platform/hexagonal-architecture/SKILL.md:34`)**:

> "Ports | Interfaces defined by **domain**, implemented by infrastructure"

**Hexagonal-architecture skill (`.agents/skills/backend-platform/hexagonal-architecture/SKILL.md:120-145`)** — el ejemplo concreto:

```kotlin
// domain/WorkspaceRepository.kt
interface WorkspaceRepository {
    suspend fun save(workspace: Workspace): Workspace
    ...
}
```

Y la `Skill` Mapping table (mismo archivo):

| Layer | Typical Elements |
|---|---|
| Domain | Entities, Value Objects, Domain Events, **Repository Ports** |
| Application | Commands, Queries, Handlers, Application Services |

**Spring-boot skill** — no menciona dónde vive `WorkspaceRepository`;
solo da el ejemplo en `application/create/WorkspaceCreator.kt` que recibe
un `WorkspaceRepository` por constructor (lo cual es consistente con que
ese interface venga del `domain/`).

**DDD skill (`.agents/skills/backend-platform/ddd-architecture/SKILL.md`)**:

> "Marked aggregate roots and domain entities communicate across bounded contexts by identity (`Id`, `Ids`, or `Identifier` names), not by direct aggregate object references."

No habla de dónde viven los puertos; asume el patrón domain-owns-ports.

**Arquitectura real verificada (no escrita)**:

```text
server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/domain/WorkspaceMutationRepository.kt:3:
  interface WorkspaceMutationRepository
```

**100% de los repositorios viven en `domain/`**, no en `application/`.
Confirmado con grep (`grep -rn Repository` sobre `domain/` retorna hits; sobre
`application/` retorna sólo referencias a tipos, no definiciones).

### Diagnóstico

AGENTS.md **contradice** tanto a la skill `hexagonal-architecture` como a
ADR-0002 y al código real cuando dice "inward-facing side" para puertos.
"Inward-facing" es el lado **application**, no el **domain**. El propio
AGENTS.md, en su tabla de capas, repite el mismo error: "Application |
Domain and inward-facing ports".

Las tres fuentes verdaderas (código, skill hexagonal, ADR-0002) coinciden:
**Domain owns the ports**. La frase canónica correcta es:

> "Domain owns the repository/gateway ports/interfaces. Application
> depends on those ports. Infrastructure depends on Application and
> implements those ports."

Esto es exactamente la "decisión #1" del usuario.

### Propuesta de resolución

- ADR-0002 debe enmendarse: añadir un párrafo en `## Decision` que
  declare explícitamente que los repositorios viven en `domain/`, no en
  `application/`. Status: "Accepted" → "Accepted (amended)".
- AGENTS.md debe sustituir "inward-facing ports" por "domain ports" o
  "domain-defined ports", en línea 384 y línea 394.
- El usuario aprobó governance_decisions.G-1 con esta misma forma: "modify
  ADR-0002; canonical decision becomes 'Domain owns repository
  ports/interfaces'". El bloque está bien encaminado, solo falta que la
  propuesta concrete el wording literal.

## 4. Marker `Service` real — verificado

- Path real: `shared/common/src/main/kotlin/com/profiletailors/common/domain/Service.kt`
- Declaración: `annotation class Service` (línea 18, paquete `com.profiletailors.common.domain`).
- Annotation target: `CLASS`, retention `RUNTIME`, `@MustBeDocumented`.
- Documentación interna (líneas 3-14): explica que Spring descubre los
  handlers anotados con este marker a través de `includeFilters` en
  `SmpApplication.kt` (verificado, líneas 32-44).
- Uso real: 13 archivos en `server/smp/src/main/kotlin/.../application/`
  lo importan (ej. `PublishingProviderCatalogHandlers.kt:3`); 1 archivo
  en `infrastructure/credentials/` (`CredentialEncryptionService.kt:3`,
  probable excepción del boundary que AGENTS.md permite).
- Contradicción en skill: `spring-boot/SKILL.md:86` define
  `annotation class ApplicationService` en el paquete
  `com.profiletailors.common.application` — **no existe**. Es un marker
  fantasma que la skill inventa. Debe borrarse.

Recomendación P0-B: la skill `spring-boot` debe corregir su sección
"Local Architectural Markers" para apuntar al marker real
`com.profiletailors.common.domain.Service` (no proponer un paralelo).

## 5. Auditoría de contaminación externa

Hits literales en `.agents/skills/**` (sin contar docs/, openspec/, scripts/):

| Token | Archivo | Línea | Clasificación |
|---|---|---|---|
| `CVIX` | `frontend-platform/vue/SKILL.md` | 316 | **DELETE** — `<p>© 2024 CVIX</p>` |
| `CVIX` | `backend-platform/spring-boot/references/swagger-standard.md` | 3 | **DELETE** — "mandatory standard for documenting all REST controllers in the CVIX" |
| `profiletailors.resume` | `backend-platform/spring-boot/references/error-handling.md` | 12, 148 | **DELETE** — `@RestControllerAdvice("com.profiletailors.resume")` no existe en este monorepo |
| `profiletailors.resume` | `languages-typing/kotlin/SKILL.md` | 120 | **DELETE** — comentario en ejemplo que apunta a un paquete inexistente |
| `Resume` (en ejemplos Spring) | `backend-platform/spring-boot/references/request-response-dtos.md` | 164-215 | **DELETE / ADAPT_TO_PT** — `CreateResumeRequest`, `ResumeRequestMapper`, `Resume` no sonbounded contexts del monorepo actual |
| `Resume` (en ejemplos Spring) | `backend-platform/spring-boot/references/error-handling.md` | 11-192 | **DELETE** — todo el archivo usa `ResumeExceptionHandler`, `InvalidResumeDataException`, etc. |
| `cvix-main` (mención histórica) | `docs/architecture/adr/0011-reusable-lead-capture-waitlist.md` | 103, 106 | **KEEP** — ADR histórica, no modificar. |
| `cvix-main` (planes legacy) | `tmp/plans/2026-06-20-hexagonal-cleanup.md` | 57, 615, 617 | **DELETE** — directorio `tmp/` no es contrato; revisar si `tmp/plans/` debe archivarse o eliminarse en un cambio de housekeeping aparte. |
| `portfolio`/`blog` (contexto ajeno) | `testing/playwright/SKILL.md` | 21, 32-43, 53, 62-66, 149-321 | **DELETE / REWRITE** — el monorepo real tiene `apps/web/{app,admin,marketing}` y `shared/web`. La skill describe una arquitectura de `apps/portfolio/` y `apps/blog/` que no existe. |
| `profiletailors.resume` (más amplio) | grep extendido | — | **DELETE** — no existe un bounded context `resume` en `server/smp/`. |

Resumen: **contaminación externa presente en 6 archivos de skills** y un
histórico legítimo en un ADR. Las skills afectadas son `vue`,
`spring-boot/references/swagger-standard.md`, `spring-boot/references/
error-handling.md`, `spring-boot/references/request-response-dtos.md`,
`languages-typing/kotlin/SKILL.md`, y `testing/playwright/SKILL.md` (la más
dañina: la skill entera está construida sobre apps inexistentes).

## 6. Auditoría de skill Playwright

Skill: `.agents/skills/testing/playwright/SKILL.md`.

Lo que dice la skill vs. lo que existe:

| Claim de la skill | Realidad | Notas |
|---|---|---|
| `packages/testing-e2e/` (carpeta raíz del paquete compartido) | NO existe | Las specs viven en `apps/web/app/e2e/specs/` y `apps/web/marketing/tests/e2e/` |
| `apps/portfolio/playwright.config.ts` | NO existe | Solo `apps/web/app/e2e/playwright.config.ts` y `apps/web/marketing/playwright.config.ts` |
| `apps/blog/playwright.config.ts` | NO existe | idem |
| `seed-portfolio.spec.ts`, `seed-blog.spec.ts` | NO existen | |
| `pnpm --filter=portfolio test:e2e` | Falla — no existe | |
| `Playwright version: 1.58.2` | Sin verificar en `package.json` directamente (no inspeccionado), pero irrelevante: la skill describe apps inexistentes | |
| HAR-based API mocking (apps/web/app) | Existe (`apps/web/app/e2e/playwright.config.ts`, líneas 8-25) | Coincidencia parcial; el patrón HAR es real, pero la skill lo enmarca dentro de `apps/portfolio/` |
| `testDir: ./specs` (apps/web/app) | Existe | |
| `testDir: ./tests/e2e` (marketing) | Existe | |
| `apps/web/admin/e2e/` | NO existe | admin SPA sin E2E (gap real, no es problema de la skill) |

Veredicto P0-D: la skill Playwright debe reescribirse desde cero. Es el
caso más grave del bloque P0-D porque afecta toda la doctrina de testing
E2E del monorepo. La skill adyacente `playwright-best-practices/SKILL.md`
está menos contaminada pero también usa ejemplos genéricos tipo Next.js
que no aplican.

Referencias a `scripts/run-playwright.mjs`: **existe** (`scripts/
run-playwright.mjs`) y debe ser citado por la skill reconstruida. Misma
cosa para `apps/web/app/e2e/playwright.config.ts` y
`apps/web/marketing/playwright.config.ts`.

## 7. Auditoría UI skills y DESIGN.md

### Estado actual de DESIGN.md (474 líneas, sin mención de precedence)

- Formato: tokens (colores, tipografía, spacing, components) en YAML-like.
- Diseño vigente: dark-first Nothing-inspired, monocromático.
- Cero menciones a `impeccable`, `nothing-design`, `frontend-design`,
  `precedence`, `cadena de autoridad` o equivalente.
- Conclusión: la governance de UI está totalmente implícita; cualquier
  agente que lea DESIGN.md no sabe qué hacer si `impeccable` y
  `nothing-design` recomiendan cosas distintas (lo cual ocurre:
  `impeccable` es genérico high-craft, `nothing-design` es específico del
  lenguaje visual vigente).

### Solapamientos y contradicciones entre las 3 skills UI

| Aspecto | `impeccable` | `nothing-design` | `frontend-design` |
|---|---|---|---|
| Audiencia | Premium? High-craft genérico | Nothing-style específico | "Anti-slop AI" genérico |
| Tipografía | Recomienda Doto/Space Grotesk/Space Mono en examples | **Esos mismos 3 fonts son los del sistema Nothing** | Recomienda "distinctive fonts, avoid Inter/Roboto/Arial" — coincide en espíritu con Nothing pero no nombra Doto |
| Dark mode | Lo trata como modo de trabajo | "Both modes are first-class" | No específico |
| Modos (Persuade/Operate/Read/Experience) | Sí, formal | No | No |
| Tokens | No emite tokens; delega a DESIGN.md | "tokens.md" como referencia | No |
| Comandos formales | `shape`, `polish`, `critique`, `audit`, `bolder`, etc. | Ninguno formal | Ninguno formal |

### Diagnóstico

El bloque P0-E es totalmente acertado. La cadena de precedence
`impeccable → nothing-design → frontend-design` (con DESIGN.md como
tie-breaker) tiene sentido si la justificamos así:

- `impeccable` es **cómo** se diseña (proceso, criterios de calidad,
  modos de superficie, comandos de trabajo).
- `nothing-design` es **qué** se diseña (el lenguaje visual vigente:
  monocromo, Doto/Space Grotesk/Space Mono, three-layer hierarchy,
  ambos modos dark/light con igual rigor).
- `frontend-design` es **cómo evitar lo genérico** (anti-slop guidance,
  aplica cuando impeccable/nothing-design no cubren un caso edge, p.ej.
  proyectos que no son Nothing).
- DESIGN.md es el **token registry canónico** y la verdad de implementación
  concreta del proyecto. Cuando una skill sugiere algo que DESIGN.md no
  tiene, gana DESIGN.md.

DESIGN.md debe declarar esta cadena explícitamente en una sección nueva,
probablemente "UI precedence chain" en el frontmatter o en las primeras
100 líneas.

## 8. Auditoría de versiones hardcoded

Hits literales:

| Versión hardcoded | Archivo | Línea | Clasificación |
|---|---|---|---|
| `Spring Boot 3.5+` | `backend-platform/spring-boot/cache/SKILL.md` | 13 | **DELETE** — el proyecto está en `springBoot = "4.0.8"` (gradle/libs.versions.toml). |
| `Spring Boot 3.5.x` | `backend-platform/spring-boot/security/references/jwt-quick-reference.md` | 3 | **DELETE**. |
| `Spring Boot 3.5.x` | `backend-platform/spring-boot/security/references/jwt-complete-configuration.md` | 3 | **DELETE**. |
| `Kotlin 2.x` | `backend-platform/hexagonal-architecture/references/kotlin-clean-architecture.md` | 3 | **DELETE / ADAPT** — el proyecto está en `kotlin = "2.4.10"`. |
| `Playwright 1.58.2` | `testing/playwright/SKILL.md` | 50 | **DELETE** — debe leerse de `package.json`. |
| `@axe-core/playwright 4.11.1` | `testing/playwright/SKILL.md` | 53 | **DELETE**. |
| `Vitest 3.x` | `testing/vitest/SKILL.md` | (header metadata) | **DELETE** — metadata version debe leerse del package.json del workspace. |
| `pnpm 10.x` | `pnpm/SKILL.md` | (header) | **DELETE**. |
| `Pinia v3.0.4` | `frontend-platform/pinia/SKILL.md` | (header) | **DELETE**. |
| `JDK = "25"` (real, en libs.versions.toml) | — | — | **KEEP** — es el source of truth del manifest, no la skill. |
| `Vue 3` (en descripciones de skills) | múltiples | — | **KEEP** — el proyecto es Vue 3; esto es descriptivo, no hardcoded stale. |
| `Kotlin 2.4.10` (en skill-registry.md) | `.agents/skill-registry.md` | 123 | **KEEP / DELETE** — el registry debe regenerarse por `sdd-init`, no editarse a mano. |

Recomendación P1-A: borrar todas las versiones literales de los frontmatters
y de los cuerpos de las skills. Cuando se necesite una versión, leerla del
manifest (gradle/libs.versions.toml, package.json). Para agentes sin
acceso al manifest, mejor un texto neutro ("current Spring Boot", "el
proyecto usa Spring Boot según gradle/libs.versions.toml") que una
versión stale.

Skills más afectadas:
1. `spring-boot/security/references/` (varias menciones 3.5.x)
2. `spring-boot/cache/SKILL.md` (3.5+)
3. `testing/playwright/SKILL.md` (1.58.2, 4.11.1)
4. `languages-typing/{kotlin,typescript,zod-4}/SKILL.md` (versiones
   declaradas en metadata; las tres son de Anthony Fu y datadas
   2026-01-28).
5. `frontend-platform/{vue,pinia}/SKILL.md` (vue sí; pinia hardcodea v3.0.4).
6. `pnpm/SKILL.md` (pnpm 10.x).

## 9. Auditoría de skills de testing y pnpm

### Reglas dogmáticas vs. realidad

| Skill | Regla dogmática | Realidad del proyecto | Veredicto |
|---|---|---|---|
| `vitest/SKILL.md` | Describe Vitest como framework; v3.x hardcoded | El proyecto usa Vitest vía `vitest.config.ts` por app. Versión real: leer de package.json. | OK en espíritu; **DELETE versión hardcoded**. |
| `playwright/SKILL.md` | Apps `portfolio`/`blog`, `packages/testing-e2e/` | Real: `apps/web/{app,admin,marketing}`, sin paquete compartido | **REWRITE** (bloque P0-D). |
| `playwright-best-practices/SKILL.md` | Frameworks Next.js, ejemplos genéricos | Proyecto no usa Next.js | **REWRITE** para usar ejemplos reales de Vue/Astro. |
| `pnpm/SKILL.md` | pnpm 10.x hardcoded | pnpm 11.20.0 (skill-registry.md) | **DELETE versión**, mantener doctrine. |
| `languages-typing/kotlin/SKILL.md` | Reglas puras Kotlin (`!!` ban, nullability, immutability) | Coherente con el código | OK; **DELETE** el comentario `profiletailors.resume` que es contaminación. |
| `languages-typing/typescript/SKILL.md` | strict types, no `any` | Coherente | OK. |
| `frontend-platform/vue/SKILL.md` | Vue 3 + Pinia + Vee-Validate + Zod | Coherente; **DELETE** la línea `© 2024 CVIX`. |
| `frontend-platform/pinia/SKILL.md` | Pinia v3.0.4 hardcoded | OK en espíritu; **DELETE versión**. |
| `frontend-platform/shadcn-vue/SKILL.md` | (no inspeccionada a fondo) | El proyecto no usa shadcn-vue; usa `@profiletailors/ui` propio | **KEEP_AS_DO_NOT_USE / DELETE** — la skill existe pero el proyecto no consume shadcn. |

Reglas legacy incompatibles identificadas:

- `shadcn-vue` no es consumida por el proyecto.
- `chrome-extensions` no aplica (no hay extensión de Chrome en el repo).
- `cloudflare` (no está en `.agents/skills/`, pero verificable: el deploy
  real es Cloudflare Pages según `.agents/AGENTS.md`).
- `agents-sdk`, `ai-elements`, `ai-gateway`, `ai-sdk`, `chat-sdk`,
  `mcp-server-patterns`, `brandkit`, `impeccable` y ~30 skills más en el
  catálogo disponible (`<available_skills>` en el system prompt) **no
  pertenecen a este monorepo**. No están en `.agents/skills/`, así que
  no son contamination; pero el orquestador debe cuidarse de no
  invocarlas por error.

`modern-web-guidance/SKILL.md` está marcada como MANDATORY para HTML/CSS/
JS. Esto colisiona con el principio de usar skills locales; pero como
no se invoca explícitamente desde el worktree, no es un bloqueador. Solo
documentar.

## 10. Auditoría de CI para skill doctor

Workflows presentes en `.github/workflows/`:

- `ci.yml` — gate principal; cero hits de "skill" o "knowledge".
- `quality-gate.yml` — coverage y SonarQube; cero hits.
- `security-deep.yml`, `security-pr.yml` — security lanes; cero hits.
- `cla.yml`, `labeler.yml`, `link-checker.yml`, `semantic-pull-request.yml`,
  `stale.yml`, `sync-labels.yml`, `cleanup-cache.yml`,
  `release-please.yml`, `release-image.yml` — workflows ortogonales.

`.agents/agents/` contiene 21 sub-agentes registrados
(`adr-consistency-auditor`, `documentation-maintainer`,
`openspec-reconciliation`, `suppression-auditor`, etc.). **Ninguno
existente es un "skill doctor"**. Hay un `dependency-maintenance.md`
pero no audita el knowledge bundle.

`.agents/commands/` solo tiene los tres comandos de Playwright
(`playwright-test-generator.md`, `playwright-test-healer.md`,
`playwright-test-planner.md`).

**No existe gate de CI que valide el knowledge bundle** (folder == frontmatter, metadata válida, paths referenciados existen, contaminación externa, contradicciones internas).

Recomendación P1-D: crear un step en `ci.yml` (o un workflow aparte
`skill-doctor.yml`) que ejecute:

1. Script determinístico (Node.js o Bash) que verifique:
   - cada `SKILL.md` tiene `name` en frontmatter;
   - `name` coincide con el nombre de carpeta;
   - metadata `category`/`family` presentes;
   - las referencias a paths internos del monorepo existen.
2. Sub-agente barata (sub-agent "skill-doctor") que revise contradicciones
   cross-skill con LLM context.
3. Bloquea merge si falla cualquiera de las dos capas.

## 11. Concurrencia confirmada

Cambios activos en `openspec/changes/`:

- `publication-calendar-sse`: phase=apply, completed=[explore, propose, spec,
  design, tasks]; next=verify. No toca `.agents/skills/`, `.agents/AGENTS.md`,
  ni `docs/architecture/adr/`. Trabaja sobre `server/smp/src/main/kotlin/
  com/profiletailors/smp/publishing/` (dominio de publishing) y el SPA
  `apps/web/app/`. Tasks `revised-pending-approval` per su state.yaml. No
  hay solapamiento de archivos con este change.
- `reactive-calendar-browser-sync`: phase=archive, completed full cycle.
  Archivado; sus specs viven en `openspec/specs/{visual-calendar,
  publishing, privacy-compliance}/spec.md`. NO se reescriben desde este
  change; sí debemos verificar que no referencian skills por path
  inexistente.
- `skill-and-knowledge-bundle-remediation`: este.

Recomendación: publicar `publication-calendar-sse` antes de empezar
`apply` de este change, para que la remediación no compita con cambios
en publishing. Si `publication-calendar-sse` se archivara primero, este
change puede proceder en paralelo a otros cambios que toquen solo
features/backend.

## 12. Palancas para P1-C (comment cleanup)

### Estado actual de herramientas

- `rg` (ripgrep): disponible (verificado en todos los greps de esta
  exploración).
- `biome` (con `biome.json` por web app): puede eliminar `//` comments,
  pero el proyecto usa Biome como linter/formateador; no como
  comment-stripper agresivo. Su política es formatear, no podar.
- `detekt` (Kotlin): tiene reglas para detectar comentarios; el `AGENTS.md`
  dice que el estándar es zero comments. Pero Detekt no remueve, sólo
  reporta.
- No existe script canónico de cleanup de comentarios en `.agents/agents/`
  ni en `scripts/`.

### Cómo invocar la sub-agente barata

- El agente `sdd-onboard` (sub-agent type disponible) hace walkthrough
  SDD; no es exactamente "cleanup", pero su wrapper puede adaptarse.
- Alternativa: command custom `.agents/commands/cleanup-comments.md`
  (mecanismo ya presente para los comandos Playwright) que invoque un
  script determinístico primero (rg patterns para `//`, `/*`, `#`,
  `<!--`) y luego delegue a un sub-agent barato con `model: haiku`
  (precio/tiempo) para los casos context-dependent (comentarios dentro
  de strings, en ejemplos de skills, en bloques `markdown`).
- No hay tests que validen "no quedan comentarios". Esto debe crearse
  como parte del bloque P1-C: un archivo de tests bajo
  `server/smp/src/test/.../NoCommentsTest.kt` y/o un Vitest test en
  `shared/web` que verifique cero `//`/`/*` en archivos `.kt` y `.ts`
  dentro de paths específicos.

Recomendación P1-C (orden):
1. Reframear `AGENTS.md` (sección "Fix Simplicity and Zero-Comment
   Policy") para que sea explícitamente self-documenting-first y el
   cleanup sea enforcement post-procesado.
2. Crear script determinístico `.agents/scripts/skill-comment-scan.mjs`
   (Node, sin deps externos) que falle el build si encuentra `^\\s*//`,
   `^\\s*\\*`, `#` (en archivos de código), `<!--` (en archivos
   `.md`/`Astro`).
3. Crear sub-agente barato `comment-cleanup` que itere sobre findings
   con contexto (sabe distinguir comentario de licencia en cabecera de
   archivo; comentario de shebang ejecutable — ya permitido en
   AGENTS.md).
4. Test que verifica el determinístico.

## Resumen ejecutivo de hallazgos (lo que necesita saber quien viene detrás)

1. **Decisión #1 del usuario es correcta y el código la sigue.** El
   repositorio de práctica es `server/smp/src/main/kotlin/.../tenancy/
   domain/WorkspaceMutationRepository.kt`. La contradicción está
   textual en `.agents/AGENTS.md` (líneas 384 y 394) y debe corregirse
   como parte de P0-B. ADR-0002 no menciona explícitamente dónde vive
   el puerto — la corrección debe añadir el párrafo que la decisión
   exige.
2. **El bloque P0-B es el más costoso.** Aproximadamente 150 ocurrencias
   de patrones servlet/JPA/Lombok/Mockito分布在 6 subskills de
   `spring-boot/**/references/` y un puñado de SKILL.md principales.
   El ruido está mayoritariamente en `references/`, lo cual permite
   una estrategia quirúrgica: podar `references/` masivamente, dejar
   los SKILL.md casi intactos, mantener solo las guías que el
   `spring-boot/SKILL.md` principal ya declara como anti-patterns.
3. **`@ApplicationService` es un marker fantasma.** Solo aparece en
   `.agents/skills/backend-platform/spring-boot/SKILL.md:86`. No existe
   en el código. Borrar y sustituir por la referencia a
   `com.profiletailors.common.domain.Service`.
4. **Playwright skill está construida sobre apps que no existen.** El
   bloque P0-D requiere rewrite desde cero, anclado a `apps/web/app/e2e/`,
   `apps/web/marketing/tests/e2e/`, `apps/web/admin` (gap), y
   `scripts/run-playwright.mjs` (verificado que existe).
5. **Contaminación externa dispersa.** 6 archivos de skills contienen
   referencias a `CVIX`, `profiletailors.resume`, `apps/portfolio`,
   `apps/blog`. La corrección es DELETE puro, no reescritura.
6. **UI precedence no está declarada.** DESIGN.md no menciona
   `impeccable`, `nothing-design`, `frontend-design`, ni una cadena de
   autoridad. La corrección del bloque P0-E debe añadir una sección
   al inicio de DESIGN.md.
7. **Versiones hardcoded en 6+ skills.** Todas en metadatos o cuerpo.
   Sustituir por referencias neutras a los manifests.
8. **No existe gate de CI para knowledge bundle.** El bloque P1-D debe
   crear tanto el script determinístico como el workflow y el sub-agente.
9. **Concurrencia limpia.** `publication-calendar-sse` está en apply
   sin tocar skills/AGENTS.md. `reactive-calendar-browser-sync` está
   archivado sin specs que apunten a skills. No hay bloqueo mutuo.
10. **Zero-comments reframe es viable.** Las herramientas ya están
    (rg, biome, detekt); falta el script determinístico y el sub-agente
    barato. Se propone crear un workflow `cleanup-comments.yml` que
    combine ambos, con tests que verifiquen el estado cero.

## Riesgos identificados (a reportar al usuario antes de `sdd-propose`)

- **Riesgo alto (P0-B):** el cambio de AGENTS.md "inward-facing ports" →
  "domain-defined ports" altera la lectura semántica de la constitución.
  Confirmar wording exacto con el usuario antes de proponer.
- **Riesgo alto (P0-D):** el rewrite de Playwright skill elimina
  doctrine heredada. Si algún workflow interno dependía de los paths
  `packages/testing-e2e/`, se rompe. (Verificado: no existe tal
  dependencia.)
- **Riesgo medio (P1-A):** borrar versiones hardcoded puede dejar
  huecos donde una skill ya no dice "qué API llamar" sin una referencia
  alternativa. Se debe sustituir por patrones "consulta
  `gradle/libs.versions.toml`" / "consulta `package.json`".
- **Riesgo medio (P0-C):** `tmp/plans/2026-06-20-hexagonal-cleanup.md`
  contiene menciones a `cvix-main`; está en `tmp/`, no es contrato, pero
  si se ignora puede crear deuda. Recomendar borrarlo en el mismo change
  o en uno de housekeeping aparte.
- **Riesgo bajo (P0-E):** DESIGN.md cambia para declarar precedence;
  verificar que ningún consumer de DESIGN.md (scripts/impeccable,
  CI, etc.) asume que el archivo es solo tokens.
- **Riesgo bajo (concurrencia):** si `publication-calendar-sse` aplica
  cambios en publishing que toquen `@Service` markers, podemos
  encontrar archivos sobre los que ambos cambios escriban. Hoy no es
  el caso; revisar al momento de merge.

## Estado final de la fase

- `state.yaml` se actualizará con `exploration_summary` (sección
  inmediatamente debajo).
- Próxima fase recomendada: `sdd-propose`, con la lista de 9 bloques
  ya ordenados por el usuario (P0-A → P0-E, P1-A → P1-D) y los
  findings del P0-B (marcador real vs. fantasma) integrados al texto
  de la propuesta.