# DALLAY-544 — Épica mutflow (plan incremental y reversible)

Ruta: Delegated direct por fases (el usuario pidió un único prompt ejecutable, sin SDD formal).
Estado: Working
Actualizado: 2026-09-26

## Orden obligatorio

1. Fase 1 — Integración local y baseline (DALLAY-545)
2. Fase 2 — Piloto acotado de lógica crítica (DALLAY-546)
3. Fase 3 — Workflow CI advisory (DALLAY-547)
4. Fase 4 — Gate bloqueante SOLO con evidencia medida (DALLAY-548, no implementar ahora)

## Verdad del repositorio (verificada, no inventada)

- JDK 25 (Temurin 25.0.4, `AppConfiguration.JVM_25`, quality-gate `java-version: 25`). El pedido de
  spike en JDK 21 no aplica al árbol actual; se documenta como desviación.
- Kotlin 2.4.10 en catálogo, Gradle 9.7.1, Spring Boot 4.0.8, JUnit 6.1.3, coroutines 1.10.2,
  MockK 1.14.11, Kotest 6.2.5.
- mutflow 1.5.0 (Apache-2.0, Maven Central) construido contra Kotlin 2.4.20. Riesgo de skew
  compiler-plugin vs Kotlin 2.4.10 del repo; el wiring básico compila (`compileMutatedMainKotlin`
  OK en TestKit y en `:server:smp`).
- mutflow expone `mutflow { targets, enabled, maxMutationRuns, timeoutMs, verificationMode }`,
  `-Pmutflow.enabled`, `MUTFLOW_VERIFICATION_MODE` (STRICT/LENIENT/DISABLED), `@MutFlowTest`,
  `MutFlow.underTest`, resumen por consola. No se asume HTML/XML propio.

## Fase 1 — Hecho / por cerrar

- [x] RPI-001/002 Versión fijada en catálogo + classpath en build-logic
- [x] RPI-003 `MutationTestingPlugin` aislado (targets explícitos, LENIENT, `verifyMutationClean`)
- [x] RPI-004 Opt-in solo en `:server:smp`
- [x] RPI-005 `MutationTestingPluginTest` 3/3 en verde
- [x] RPI-006 Muestra `PublicationRetryMutationBaselineTest` (`:server:smp:test` OK 3m5s)
- [ ] RPI-007 `verifyMutationClean` ejecutado y registrado
- [ ] RPI-009b `detekt`, `licence-check`, confirmación de tareas normales intactas

## Fase 2 — Piloto (2 scopes, ≤20 clases)

- Scope A: `PublicationLifecyclePolicy` (transiciones de publicación, `requireRetryable`).
- Scope B: `BulkValidationPipeline` (validación bulk, rama blank + header inválido, incluye
  `runTest` para evaluar coroutines).
- Clasificación obligatoria por superviviente: aserción ausente/débil, frontera-error ausente,
  producción no cubierta, equivalente, artefacto irrelevante/generado, fallo tooling/aislamiento,
  constructo Kotlin no soportado.
- Solo reforzar tests conductuales en las 3 primeras categorías.
- Reejecutar desde estado limpio y comparar baseline vs final.

## Fase 3 — CI advisory (nuevo workflow, sin tocar CI principal)

Requisitos: `workflow_dispatch`, schedule, PR solo con cambios relevantes, concurrencia que
cancele obsoletos, caché Gradle, permisos `contents: read`, artefactos/logs con retención acotada,
scopes omitidos con motivo, reproducción local, timeout explícito, resultado advisory (sin
bloquear). Resumen con SHA, trigger, módulos-scopes, versiones, intentadas/eliminadas/
supervivientes, timeouts/fallos tooling, duración, rutas de artefactos, scopes omitidos.
Meta: 10 ejecuciones representativas antes de proponer gate. Si infra/timeouts/reruns > 5%,
bajar a manual/programado.

## Fase 4 — Gate (NO implementar)

Condiciones incumplidas hoy: sin piloto cerrado, sin 10 runs advisory, sin métricas de
estabilidad/accionabilidad/coste. Se deja interruptor a advisory y criterios de reversión
documentados. Cero umbrales bloqueantes en este cambio.

## Próximo paso

Fase 1 cerrada salvo `backend-check` completo (pesado; `backend-lint` verde, tests focalizados
verdes). Fase 2 piloto con 2 scopes medidos abajo. Fase 3 workflow creado, pendiente primera
ejecución en CI. Fase 4 diferida con criterios explícitos.

## Evidencia medida 2026-09-26

- Build-logic `MutationTestingPluginTest`: 3/3 verde (tras exclusión stdlib).
- `just backend-lint`: BUILD SUCCESSFUL (tras excluir `kotlin-stdlib` y
  `kotlin-gradle-plugin-api` transitivos de mutflow en build-logic).
- `just licence-check`: BUILD SUCCESSFUL, mutflow reportado como Apache-2.0.
- Baseline LENIENT (2 clases, filtros `--tests`): 5 intentadas, 5 eliminadas, 0 supervivientes,
  0 timeouts, 0 fallos tooling. Detalle en `docs/testing/mutation-testing.md`.
- `verifyMutationClean`: BUILD SUCCESSFUL.
- `docs-lint`: `docs/testing/mutation-testing.md` limpio; fallos restantes son de `tmp/plans`
  PWA preexistentes, fuera de este cambio.
- Tareas normales intactas (`test`, `postgresIntegrationTest`, `bddFastTest`, `bddPostgresTest`
  registradas con su descripción original) más `verifyMutationClean` nueva.
- `just backend-check` completo relanzado en background tras reinicio del servidor; pendiente
  resultado para cierre de Fase 1.
- Hallazgo coroutines: `underTest` exige lambda común; puente `runBlocking` dentro del bloque
  con `@Test` plano (ver docs).
- Hallazgo Liquibase: `mutatedMain` duplicaba el changelog en el classpath de tests. Fix en la
  convención (vaciar resources de `mutatedMain`), cubierto por TestKit 4/4. Verificación triple:
  falla con mutflow pre-fix, pasa con `-Pmutflow.enabled=false`, pasa con mutflow post-fix
  (`R2dbcConsentRepositoryTest`, BUILD SUCCESSFUL 4m21s).
