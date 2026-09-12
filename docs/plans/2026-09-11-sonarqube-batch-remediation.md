# SonarQube Batch Remediation Implementation Plan

> **For agentic workers:** Implement this plan task-by-task using the `dispatching-parallel-agents`
> skill for independent tasks, or execute inline with review checkpoints.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Llevar `dallay_profiletailors.com` de Quality Gate ERROR a OK por lotes reversibles sin romper hexagonal ni accesibilidad.

**Architecture:** Atacar primero reliability (bugs + complejidad 40/43), luego accesibilidad frontend, luego diseño Kotlin, luego higiene TS, dejando coverage 0% como track separado porque exige pipeline no refactor. Cada lote TDD + gate estrecho + verificación MCP.

**Tech Stack:** Kotlin Spring WebFlux R2DBC, Vue 3 Pinia, Astro marketing, SonarQube MCP, Just, Detekt, Biome, Vitest, JaCoCo.

---

## Inventario verificado MCP 2026-09-11

- Gate ERROR: `new_reliability_rating` 3 vs 1, `new_coverage` 0.0 vs 80. Security, maintainability, duplicación OK.
- Medidas: bugs 3, code_smells 95, vulnerabilidades 0, ncloc 68441.
- Total OPEN+CONFIRMED: 73 MEDIUM+ y 27 LOW/INFO.
- Clusters: S3776 x2 CRITICAL, Web:S6819 x~12, S107 x~9, S6517 x~10, S7721 x~6, S5976 x~10, S3358 x2, S6532 x3, S5906 x~10, resto S1481 S1144 S6615 S6508 S6564 S8786 S4624 S6840.

## Reglas globales por lote

- TDD obligatorio: test failing primero, mínimo para pasar, refactor seguro.
- Hexagonal: domain puro, application orquesta por puertos, infrastructure adapta. No llamar repos desde controllers/handlers.
- Cero comentarios, cero suppressions, cero baseline nuevo, cero `any` nuevo.
- Verificación MCP por lote: `search_sonar_issues_in_projects` + `get_project_quality_gate_status`.
- Commits convencionales pequeños, sin Co-Authored-By.

---

### Lote 1: Reliability CRITICAL publishing

**Files:**

- Modify: `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/BulkPublishingHandlers.kt:66`
- Modify: `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/BulkValidationPipeline.kt:41`
- Test: `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/BulkPublishingHandlersTest.kt`
- Test: `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/domain/BulkValidationPipelineTest.kt`
- Test: `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/domain/BulkValidationPipelineCoverageTest.kt`

- [ ] **Step 1: Reproducir complejidad actual**

Run: `just backend-lint`
Expected: Detekt + Sonar S3776 40 y 43 reportados

- [ ] **Step 2: Escribir test de regresión para validate por particiones**

```kotlin
@Test
fun `validate rechaza header invalido sin procesar filas`() = runTest {
  val result = pipeline.validate("ws-1", "bad,header\nbody,2026-09-12T10:00:00Z,")
  assertEquals(1, result.rows.size)
}
```

- [ ] **Step 3: Extraer validadores cohesivos en BulkValidationPipeline**

Extraer métodos privados puros: `validateHeader`, `validateRow`, `validateSchedule`, `validateMedia`, `detectConflicts`. Sin lógica en infrastructure, sin cambiar firmas públicas.

- [ ] **Step 4: Extraer handlers en BulkPublishingHandlers**

Separar orquestación por caso de uso, delegar a pipeline y puertos existentes.

- [ ] **Step 5: Verificar**

Run: `just backend-check`
Run: `just backend-bdd-fast`
Expected: PASS, complejidad <=15 por método

- [ ] **Step 6: Verificar Sonar MCP**

Run MCP: `search_sonar_issues_in_projects` proyectos `dallay_profiletailors.com` severities HIGH BLOCKER
Expected: 0 CRITICAL S3776

- [ ] **Step 7: Commit**

```bash
git add server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/BulkPublishingHandlers.kt server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/BulkValidationPipeline.kt server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/BulkPublishingHandlersTest.kt server/smp/src/test/kotlin/com/profiletailors/smp/publishing/domain/BulkValidationPipelineTest.kt
git commit -m "fix(publishing): reduce cognitive complexity bulk pipeline"
```

### Lote 2: Accesibilidad frontend

**Files:**

- Modify: `apps/web/app/src/modules/settings/presentation/AccountPrivacyView.vue:131-137`
- Modify: `apps/web/app/src/modules/dashboard/presentation/views/AnalyticsView.vue:276-281`
- Modify: `apps/web/app/src/modules/ideas/presentation/views/IdeasView.vue:283-310`
- Modify: `apps/web/app/src/modules/publishing/presentation/components/BulkImportModal.vue:76`
- Modify: `apps/web/app/src/modules/publishing/presentation/components/BulkPreviewTable.vue:34-50`
- Modify: `apps/web/app/src/modules/auth/presentation/RegisterForm.vue:84-85`
- Modify: `apps/web/app/src/modules/auth/presentation/ForgotPasswordView.vue:42`
- Modify: `apps/web/app/src/modules/auth/presentation/ResetPasswordView.vue:79`
- Modify: `apps/web/app/src/modules/invitation/presentation/AcceptInvitationView.vue:86-102`
- Modify: `apps/web/app/src/modules/ideas/presentation/components/IdeaComposerModal.vue:230`

- [ ] **Step 1: Test failing accesibilidad para dialog nativo**

```ts
it("usa dialog nativo accesible", () => {
  const wrapper = mount(BulkImportModal)
  expect(wrapper.find("dialog").exists()).toBe(true)
})
```

- [ ] **Step 2: Migrar role dialog a elemento dialog, role img a img/svg, role status a output, listbox a select/datalist**

Un archivo por commit, preservar estilos y foco por teclado.

- [ ] **Step 3: Asociar labels con id y corregir autocomplete**

Agregar `id` + `label for`, autocomplete válido en RegisterForm.

- [ ] **Step 4: Verificar**

Run: `pnpm --filter app lint`
Run: `pnpm --filter app test:run`
Run: `pnpm --filter app type-check`
Expected: PASS sin nuevos Biome warnings

- [ ] **Step 5: Commit por vista**

```bash
git commit -m "fix(a11y): use native dialog and labelled inputs"
```

### Lote 3: Diseño Kotlin S107 S6517 S6532 S108 S1144

**Files:**

- Modify: `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/tools/PublicationTools.kt:142-434`
- Modify: `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/PlatformAdminBootstrapConfiguration.kt:54-112`
- Modify: `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublicationCreationService.kt:51`
- Modify: `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/infrastructure/McpErrorMapper.kt:128-143`
- Modify: `server/smp/src/main/kotlin/com/profiletailors/smp/observability/infrastructure/Slf4jOperationalEventSink.kt:26-27`

- [ ] **Step 1: Introducir param objects por contexto sin cambiar comportamiento**

```kotlin
data class PublicationQuery(
  val workspaceId: String,
  val limit: Int = 20
)
```

- [ ] **Step 2: Convertir interfaces de un método a fun interface o function type**

Solo donde ADR-0015/0016 lo permite, mantener puertos inward-facing.

- [ ] **Step 3: Reemplazar if por require con mensajes existentes**

- [ ] **Step 4: Eliminar métodos privados no usados y bloques vacíos**

- [ ] **Step 5: Verificar**

Run: `just backend-check`
Expected: PASS sin nuevo Detekt

### Lote 4: Mantenibilidad TypeScript

**Files:**

- Modify: `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts:992`
- Modify: `apps/web/app/src/modules/ideas/application/useIdeaDragAndDrop.ts:83`
- Modify: `apps/web/marketing/src/i18n/utils.ts:52`
- Modify: `apps/web/app/src/modules/auth/presentation/ForgotPasswordView.vue:33-35`
- Modify: `apps/web/app/src/modules/publishing/application/markdown.ts:13`

- [ ] **Step 1: Mover funciones anidadas a outer scope con test de equivalencia**

- [ ] **Step 2: Desanidar ternarios en sentencias independientes**

- [ ] **Step 3: Simplificar regex super-lineal con casos de prueba**

- [ ] **Step 4: Verificar**

Run: `pnpm --filter app lint`
Run: `just frontend-lint`
Run: `just frontend-test`
Expected: PASS

### Lote 5: Higiene tests y MINOR

**Files:**

- Modify specs S5976 y S5906 solo donde no oculte intención

- [ ] **Step 1: Parametrizar tests duplicados con it.each manteniendo cobertura**

- [ ] **Step 2: Reemplazar assertions genéricas por toHaveLength**

- [ ] **Step 3: Verificar suites afectadas**

### Lote 6 separado: Coverage 0% new code

No es refactor. Requiere revisar `docs/sonarqube-coverage.md`, JaCoCo, Codecov, y por qué Sonar ve 0.0. Proponer pipeline fix aparte, no mezclar con lotes 1-5.

## Orden de ejecución

1, 2, 3, 4, 5, 6. Cada lote cierra con MCP gate check y resumen qué cambió, por qué, cómo validar.
