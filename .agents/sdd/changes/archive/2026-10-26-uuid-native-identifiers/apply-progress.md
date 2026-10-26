# Apply progress: uuid-native-identifiers

## Cierre posterior a la aprobación de WAITLIST — 2026-10-26

- El usuario aprobó el ámbito público `WAITLIST` separado del tenant. Se actualizaron propuesta,
  diseño, tasks y el contrato `specs/consent-scopes/spec.md`; ADR-0030 registra la decisión,
  sin afirmar revisión legal ni preparación productiva.
- SQL final incluye `notifications` opcional, sus IDs históricos/payloads y auditoría, y los ámbitos
  de consentimiento con referencias reales excluyentes. Históricos ausentes, ambiguos o incompatibles
  abortan con rollback. Las referencias de eventos no pueden cruzar ámbitos mediante NULL.
- El orquestador repitió el harness completo: `LIQUIBASE_TEST_CLASSPATH="$(cat <temp>/uuid-sql-cp)"
  UUID_SQL_TEST_LOGS=<temp>/uuid-sql-orchestrator-final bash
  server/smp/src/main/resources/db/changelog/uuid-native-identifiers/tests/run.sh`, exit 0.
  `<temp>` es el directorio `opencode` aprobado. Log `uuid-sql-orchestrator-final-console.log`:
  **34 rechazos con rollback**, concurrencia idempotente por ámbito, aislamiento, retirada,
  historial y equivalencia de esquema fresh/poblado; **93 FK** y **405 índices** válidos.
- Lens y Mirror revisaron independientemente el SQL final y sus hashes: ambos **CLEAN**.
  No confundir esta aprobación SQL con los gates runtime Kotlin/BDD, todavía en integración.
- Web: se restauraron las seis traducciones corrompidas, se alineó identidad refresh/perfil y
  se corrigió el workspace del cleanup Unsplash. Regresiones RED de cinco fallos, GREEN de once;
  ambos jueces aprobaron estos arreglos. No se ejecutó Unsplash real.
- Ocho specs Chromium adicionales inicialmente mostraron siete desajustes previos a UUID.
  Se corrigieron los fixtures manteniendo identidad, prefill, deduplicación, asociación,
  ausencia de refresh tras error y payload DSAR. La nueva comprobación responsive reveló
  márgenes negativos heredados en un diálogo `p-0`; el único cambio productivo añade
  `mx-0 mb-0` al footer local, sin modificar el componente compartido.
- Resultado final de esos ocho specs: **43 aprobadas / 0 fallos / 0 omisiones**. Geometría EN/ES
  en 360/1280: footer y botones contenidos, sin tolerancia extra ni eliminación de assertions.
  Ambos jueces revisaron el diff y los logs: **CLEAN**.
- Dashboard: **2005 pruebas**, lint, tipos y build aprobados; componente de ideas **20/20**.
  El orquestador repitió directamente la suite de fixtures y el componente: **31/31**,
  con `pnpm --filter app test:run src/uuid-fixtures.test.ts
  src/modules/ideas/presentation/components/IdeaComposerModal.test.ts --maxWorkers=2`.
- Scheduler final: **93 aprobadas / 1 omisión preexistente** con origen HAR `5173`.
  Un intento con puerto `5174` falló por el origen del HAR y está registrado por separado;
  no se presenta como prueba aprobada. Media mocked **37/4**; admin **15/0**; marketing **540/0**.
- `shared/web`: **74/74** con `pnpm --filter @profiletailors/shared-web test:run`.
  No cambió el receipt del navegador ni su versión. Validación del inventario de datos:
  `node tools/compliance/check-data-inventory.ts`, exit 0.
- La suite app inicial tuvo un fallo temporal de timer en `usePublicationEventReconnect.test.ts`;
  repetición focalizada y suite completa con dos workers pasaron sin cambiar código, assertions,
  configuración ni omitir pruebas. Las advertencias existentes de bundle/jsdom quedan visibles.
- No hubo SQL productivo, deploy, commit ni push. Falta terminar integración runtime/BDD,
  gate backend completo y la verificación/QA SDD final antes de declarar completa la migración.

## Verificación de cierre en curso — 2026-10-26

- Dashboard: tipos, 176 archivos / 2000 pruebas unitarias y build aprobados. Admin: tipos, 22 archivos / 148 pruebas y build aprobados. Biome detectó formato pendiente en 29 archivos ya implicados; se aplicó el formatter solo a esos archivos y ambos lint pasan.
- Scheduler E2E: la primera ejecución falló porque el fixture convirtió la clave semántica `linkedin-calendar` en UUID. `BulkTemplate.defaultTemplates()` mantiene esa clave sin persistencia de entidad; se restauró en las tres respuestas del fixture. Repetición completa: **93 aprobadas / 1 omitida**, exit 0. Media mocked: **37 aprobadas / 4 omitidas**, exit 0. Logs: `uuid-scheduler-completion-green.log` y `uuid-media-completion.log`.
- Marketing: `astro check` rechazó TypeScript 7.0.2, también presente en `HEAD`. Se verificó la incompatibilidad en la documentación de Astro. La dependencia de desarrollo ahora usa `~6.0.3`, igual que dashboard/admin; `pnpm` regeneró el lockfile sin desactivar el checker ni introducir un reemplazo experimental. Verificación nueva completa: lint, check de 84 archivos sin errores/advertencias/hints, 18 archivos / 163 pruebas unitarias, build y **540 E2E aprobadas**, sin omisiones. Log: `uuid-marketing-final-gates.log`.
- Las cinco clases PostgreSQL inicialmente bloqueadas pasan según la última ejecución del implementador: identity 5/5, invitaciones 18/18, retry 1/1, worker 10/10 y principal lookup 6/6. El worker ahora usa el esquema UUID convertido.
- El gate completo `just backend-check` detectó 305 fallos en 492 pruebas PostgreSQL. La tarea estándar repite esos fallos; no son 610 defectos diferentes. Las correcciones se distribuyen por contexto sin escrituras compartidas ni ejecuciones Gradle simultáneas. El gate todavía no pasa.
- Admin E2E: ejecución local nueva con 14/15 pruebas aprobadas; el fallo comparaba un UUID fijo con un ID generado por `makeEntry()`. La aserción ahora usa el ID del fixture asociado a `cid@example.com`, sin relajar las comprobaciones del resultado ni de los dos envíos. Repetición completa: **15/15 aprobadas**, sin omisiones.
- Comandos de admin: `pnpm --filter @profiletailors/admin exec biome format --write e2e/specs/waitlist-bulk-invite.spec.ts` y `pnpm --filter @profiletailors/admin test:e2e`. Evidencia local en `uuid-admin-e2e-completion.log` y `uuid-admin-e2e-completion-green.log`, dentro del directorio temporal aprobado.
- No se ejecutó SQL de producción, no hubo despliegue, commit ni push. El inventario, los gates completos y la verificación independiente siguen pendientes; no se declara terminada la migración.

## New verification evidence

### Exclusive Kotlin integration — 2026-10-26 16:49 CET

Status: **blocked by the public-consent scope contract**, not by unavailable Docker or another
unclassified fixture. All four implementation handoffs were integrated. Public waitlist failures
cannot be fixed safely by inventing a workspace UUID.

#### Implementation and static verification

- Read the four temporary handoffs: `publishing-uuid-fix-report.json`,
  `uuid-identity-admin-fix-report-20261026.json`,
  `uuid-media-governance-scope-handoff-20261026.json`, and
  `uuid-bdd-harness-static-report.json`. Kotlin/tests had one exclusive writer and sequential
  Gradle invocations. The SQL writer could run disposable Docker checks, but not Gradle.
- Formatter target inventory: `uuid-integrator-kotlin-files-20261026.txt`, containing 202 modified
  Kotlin/build files plus the new transaction fixture. Command:
  `node scripts/gradle-run.mjs spotlessApply --no-daemon --console=plain "-PspotlessIdeHook=$files"`,
  with `files` set to the comma-separated absolute paths in that inventory. Logs:
  `uuid-integrator-format-01.log` through `uuid-integrator-format-05.log`. No unchanged files or
  analysis configuration were formatted or altered.
- Reworked long SQL expressions in `PlatformAdminBddSteps` and the exact fixture literals named
  by Detekt. Reused the existing publishing isolation account fixture without removing any of
  its four isolation assertions.
- Resolved `LargeClass` by separating database setup and publication/job fixture construction
  into `PublicationTransactionDatabaseFixture`, extending the existing
  `PostgresDatabaseTestBase`. The handler suite manually constructs its handlers and retains
  real PostgreSQL transactions. Removed duplicated Spring bootstrap, migrations, and cleanup;
  no source was moved to an excluded path. All 14 scenarios remain present and pass below.
- Early quality command:
  `node scripts/gradle-run.mjs :server:smp:compileTestKotlin :server:smp:spotlessCheck :server:smp:detekt --no-daemon --console=plain`.
  The first run found 38 findings; the second left only `LargeClass`; subsequent runs passed.
  Final `uuid-integrator-early-quality-04.log`: exit 0, `BUILD SUCCESSFUL in 26s`.

#### Full gate and preserved evidence

- Ran `just backend-check` after early quality checks.
  `uuid-integrator-check-01.log`: exit 1, `BUILD FAILED in 11m 34s`.
- Fresh XML, read only after the command finished:
  - `postgresIntegrationTest`: 494 tests, 4 failures, 0 errors, 0 skips.
  - `test`: 3298 tests, 4 failures, 0 errors, 2 skips.
  - Compilation and Detekt passed. These test tasks overlap; this is not eight distinct defects.
- XML snapshots: `uuid-integrator-check-01-test/` and
  `uuid-integrator-check-01-postgresIntegrationTest/`. Full suite/test/stack inventory:
  `uuid-integrator-check-01-results.json`.
- SHA-256 resource inventory: `uuid-integrator-check-01-resources-before.json`.
  Comparing all main/test resource files after the gate found **no changes during the full gate**.

#### Queue correction and focused confirmation

- The queue failure was JDBC connection refusal at `localhost:33714` in
  `@DynamicPropertySource` immediately after default container startup. Applied the already
  verified shared container factory to `PublishingQueuePostgresIntegrationTest`, including
  listening-port readiness. No sleeps or retry loops were added.
- Focused command:
  `node scripts/gradle-run.mjs :server:smp:postgresIntegrationTest --no-daemon --console=plain --tests '*PublishingQueuePostgresIntegrationTest' --tests '*PublishingHandlersTransactionPostgresIntegrationTest' --tests '*WaitlistRateLimitIntegrationTest'`.
  `uuid-integrator-focused-02.log`: 23 tests, 3 failures. Fresh XML confirms queue **5/5**,
  handler transactions **14/14**, and waitlist rate-limit **1/4**. Snapshots:
  `uuid-integrator-focused-02-xml/` and `uuid-integrator-focused-02-results.json`.
- The SQL writer added `tests/notifications-fixture.sql` and `tests/notifications-assertions.sql`
  under the UUID changelog between the full-gate resource snapshot and the end of the focused
  run. No immediately-before-focused manifest exists, so the exact change interval is unknown.
  Focused evidence is provisional until SQL resources stabilize and the gate is repeated.
  The full gate was not rerun after the queue fix; do not report it as having three failures.

#### Blocking contract and required decision

- Three public waitlist rate-limit scenarios expect initial joins to return HTTP 202 but receive
  HTTP 400. Captured response: `Invalid UUID string: waitlist:profile-tailors-launch`.
- `GovernanceWaitlistConsentRecorder.kt` constructs
  `workspaceId = "waitlist:${request.waitlistKey.value}"` for anonymous public consent. Its direct
  unit test explicitly expects `waitlist:profile-tailors-launch`: this is an existing semantic
  scope contract, not a malformed test entity UUID.
- `ConsentRecord.workspaceId` and `RecordConsentCommand.workspaceId` remain required strings,
  while `R2dbcConsentRepository` binds the inventoried workspace column as UUID. Public waitlist
  consent has no representable scope in that native schema.
- The migration independently rejects historical instances with
  `Synthetic waitlist consent scope requires an explicit scope contract`. Changing a fixture
  alone would conceal the production incompatibility.
- An owner must approve how anonymous waitlist consent is scoped separately from tenant workspace
  consent, including uniqueness/idempotency, historical `waitlist:<key>` records, consent events,
  and withdrawal/history lookup. The SQL writer must implement the same representation. The
  integrator did not invent a workspace UUID, erase the waitlist distinction, disable consent,
  widen UUID columns back to text, or alter the SQL guard.
- BDD fast/PostgreSQL were **not run in this continuation** because backend-check did not pass.
  Run them after the scope decision, implementation, and a passing stable-resource backend gate.
- SQL and runbooks remain owned by the active SQL writer and were not edited. No `.gitignore`,
  production SQL, deployment, commit, or push action occurred. Migration completion is not claimed.

Temporary filenames above are relative to
`/private/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/`.

### Backend transaction repair and full gate — 2026-10-26 16:01 CET

This section supersedes the earlier identity/bootstrap and focused worker results. The five
requested PostgreSQL classes are now green, including in both test tasks of the final full gate.
The migration is **not complete**: the full gate still has 305 failing PostgreSQL tests.
Docker is available. There is no demonstrated external Docker blocker.

#### Confirmed repairs

- Reproduced identity with the current source: bootstrap succeeded on that run and three invitation
  scenarios failed with PostgreSQL `42804` for `workspace_id` / `issued_by` fixture bindings.
  Earlier logs show connection refusal on the mapped localhost port immediately after startup.
  Testcontainers PostgreSQL 1.21.4 source confirms that its `waitUntilContainerStarted()` delegates
  only to the configured wait strategy, not the JDBC readiness loop in its base class. The default
  PostgreSQL strategy checks readiness log messages. Identity now uses the existing shared factory,
  which also checks the listening port. Static `@Container`, `PER_CLASS`, and startup-before-property
  registration remain unchanged. No sleeps, retry loops, or speculative lifecycle changes were added.
- Fixed UUID bindings in identity invitation fixtures, local password credentials, workspace
  provisioning, consent records/events, and refresh-session creation/rotation/revocation. Subject
  values, token hashes, lookup keys, and provider/worker values remain text. Added direct credential
  update/read checks and native-schema consent recording/withdrawal/history coverage that preserves
  an opaque anonymous subject.
- The worker class was not validating native UUID persistence: it used PostgreSQL 16, automatic
  baseline Liquibase, and non-UUID fixture IDs. It now uses the shared PostgreSQL 18 factory and
  applies the explicit UUID context and approval GUC in the same JDBC session. Its ten scenarios
  retain their rollback, retry, claim, recovery, and notification assertions. Fixture publication
  keys map deterministically to UUIDs only inside the test. Provider account references and
  `worker-*` lease tokens remain unchanged.
- The native-schema worker run exposed `42883` in job operation-key recovery and `42804` in
  notification insertion. Fixed the UUID binding pattern throughout the touched publication,
  publication-asset, job, delivery-attempt, and notification repositories, including nullable
  publication IDs and ID collections. No SQL casts or relaxed database constraints were added.
- The full gate exposed further stale fixtures in the directly affected consent and local-auth
  endpoint classes. Both complete classes now pass, including anonymous-subject preservation,
  workspace scoping, invitation registration, refresh, logout, verification, and rejected requests.
  Shared integration seed helpers now bind all confirmed entity IDs as UUID values.
- Native token and MCP idempotency tests no longer alter already-migrated UUID columns. They test
  the actual Liquibase schema. Fixed the MCP workspace/principal UUID bindings after its concrete
  PostgreSQL failure. Updated the workspace-filter fixture and the cleanup-order test to the current
  UUID contracts without removing assertions.
- Applied configured Spotless formatting to explicit absolute file lists only. Removed the reported
  whitespace in `IntegrationTestBase` and `PostgresTestContainerSupport`. Formatting-only changes
  also cover the migration files named by Detekt/Spotless findings. No analysis configuration,
  suppression, baseline, ignore path, or test tag changed.

#### Commands and local results

All logs below are in
`/private/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/`.
Gradle invocations ran sequentially. Individual Test tasks can still execute their own configured
workers; no second Gradle command was started while another command was running.

- RED identity reproduction:
  `node scripts/gradle-run.mjs :server:smp:postgresIntegrationTest --no-daemon --console=plain --tests 'com.profiletailors.smp.identity.integration.LocalAuthHandlersTransactionPostgresIntegrationTest'`.
  `uuid-identity-repro-20261026.log`: 5 tests, 3 failures. Subsequent `uuid-identity-fixed1` through
  `uuid-identity-fixed4` logs record the local-password, workspace, consent, and refresh failures.
  `uuid-identity-fixed5-20261026.log`: identity 5/5 and refresh gateway 5/5 passed.
- Direct RED credential regression:
  `node scripts/gradle-run.mjs :server:smp:postgresIntegrationTest --no-daemon --console=plain --tests 'com.profiletailors.smp.identity.infrastructure.R2dbcLocalPasswordCredentialGatewayTest'`.
  `uuid-password-red-20261026.log`: 3 tests, 1 failure before the adapter fix.
- Direct RED provisioning regression:
  `node scripts/gradle-run.mjs :server:smp:test --no-daemon --console=plain --tests 'com.profiletailors.smp.tenancy.infrastructure.R2dbcWorkspaceProvisioningServiceTest'`.
  `uuid-provision-red-20261026.log`: 3 tests, 2 failures before the adapter fix.
- Direct RED consent regression:
  `node scripts/gradle-run.mjs :server:smp:postgresIntegrationTest --no-daemon --console=plain --tests '*R2dbcConsentRepositoryTest.native UUID*'`.
  `uuid-consent-red-20261026.log`: 1 test, 1 failure before the adapter fix.
- Direct RED refresh regression:
  `node scripts/gradle-run.mjs :server:smp:postgresIntegrationTest --no-daemon --console=plain --tests '*R2dbcRefreshSessionGatewayTest'`.
  `uuid-refresh-red-20261026.log`: 5 tests, 4 failures before the adapter fix.
- Worker native-schema RED:
  `node scripts/gradle-run.mjs :server:smp:postgresIntegrationTest --no-daemon --console=plain --tests '*PublishingWorkerTransactionPostgresIntegrationTest'`.
  `uuid-worker-native-red-20261026.log`: 10 tests, 10 failures. After the binding fixes,
  `uuid-worker-native-green2-20261026.log`: 10/10 passed.
- Ran the following command separately, in this exact class order:
  `node scripts/gradle-run.mjs :server:smp:postgresIntegrationTest --no-daemon --console=plain --tests '<fully-qualified-class>'`.
  Classes and results:
  `com.profiletailors.smp.identity.integration.LocalAuthHandlersTransactionPostgresIntegrationTest` 5/5;
  `com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcInvitationRepositoryTest` 18/18;
  `com.profiletailors.smp.publishing.integration.PublishingRetryEndpointPostgresIntegrationTest` 1/1;
  `com.profiletailors.smp.publishing.integration.PublishingWorkerTransactionPostgresIntegrationTest` 10/10;
  `com.profiletailors.smp.identity.infrastructure.R2dbcPrincipalIdentityLookupTest` 6/6.
  Each command exited 0. Fresh logs are `uuid-final-<simple-class-name>-20261026.log`; each has a
  neighboring `.log.xml` snapshot to prevent later Gradle invocations from overwriting its evidence.
  Total: 40 tests, no failures/errors/skips.
- Additional complete adapter regression command:
  `node scripts/gradle-run.mjs :server:smp:postgresIntegrationTest --no-daemon --console=plain --tests '*R2dbcConsentRepositoryTest' --tests '*R2dbcLocalPasswordCredentialGatewayTest' --tests '*R2dbcRefreshSessionGatewayTest' --tests '*LocalAuthEndpointIntegrationTest' --tests '*NativeUuidTokenPostgresTest' --tests '*NativeUuidIdempotencyPostgresTest'`.
  `uuid-adapter-regressions-20261026.log`: exit 0; 39 tests, no failures/errors/skips.
- Support regression command:
  `node scripts/gradle-run.mjs :server:smp:test --no-daemon --console=plain --tests '*R2dbcWorkspaceProvisioningServiceTest' --tests '*PostgresTestContainerSupportTest' --tests '*WorkspaceContextWebFilterTest'`.
  `uuid-support-regressions-20261026.log`: exit 0; 9 tests, no failures/errors/skips.
- Scoped formatter command:
  `node scripts/gradle-run.mjs :server:smp:spotlessApply --no-daemon --console=plain '-PspotlessIdeHook=<comma-separated-absolute-file-paths>'`.
  The initial regex-based attempt was invalid for this Spotless version and did not format files;
  it is not passing formatter evidence. Later absolute-path invocations formatted only files
  implicated by the task or exact migration-related findings. Final full static gate:
  `node scripts/gradle-run.mjs :server:smp:spotlessCheck :server:smp:detekt --no-daemon --console=plain`.
  `uuid-static-final-20261026.log`: exit 0, `BUILD SUCCESSFUL`.
- First `just backend-check`: exit 1, `uuid-backend-check-20261026.log`; 492 PostgreSQL tests with
  323 failures, 3294 standard tests with 325 failures / 2 skips, and one Detekt duplication finding.
  The directly affected fixtures and Detekt finding were then repaired as described above.
- Final `just backend-check`: exit 1, `uuid-backend-check-final-20261026.log`, `BUILD FAILED in 10m`.
  `postgresIntegrationTest`: 492 tests, 305 failures, no errors/skips.
  `test`: 3294 tests, 305 failures, no errors, 2 skips.
  Detekt and `verifySecurityVersions` passed. The five requested classes and all six additional
  adapter classes above passed again in both test tasks. These tasks overlap; their failure counts
  are not 610 distinct defects.
- `uuid-backend-final-results-20261026.json` preserves full suite totals, timestamps, test names,
  messages, and nested causes from both final test tasks. Dominant remaining causes in each task:
  90 invalid `principal-1` UUID fixtures, 69 UUID-column/varchar-ID inserts, 17 invalid `user-1`
  UUID parses, 15 invalid `workspace-1` parses, 15 SQL `user-1` UUID literals, 12 `entry-test-1`
  literals, and 9 old `profile-tailors-launch` literals. These are diagnostic groups, not a complete
  owner-approved fix inventory. Other failures include expected HTTP statuses becoming 500.
- `git diff --check`: passed. No production SQL, deployment, commit, push, `.gitignore` edit,
  dependency change, or rule weakening was performed.
- Progress/RPI documentation: the root Markdownlint invocation inherited repository-wide globs
  and reported unrelated existing findings. A separate temporary invocation used the same rule
  settings and exactly the two edited document paths, including the normally ignored SDD document.
  `uuid-progress-docs-lint-exact-20261026.log`: 2 files, 0 issues. The RPI progress reference resolves.

#### Remaining work and contract impact

- The full backend gate remains a concrete repository blocker, not an external Docker blocker.
  Remaining failures span publishing persistence/handlers/queues, media, tenancy, authorization,
  platform-admin/waitlists, password reset, analytics, ideas, audit, compliance, privacy, and
  social-content persistence. They cannot be represented as one further identity mismatch or as
  a passing migration. Do not mark backend task 3.1, native-schema task 3.2, or gate task 5.2 done.
- The conditional follow-up full PostgreSQL/BDD gates were not run after the failed backend gate.
  The backend gate itself executed the full PostgreSQL task twice in this continuation. Fast BDD,
  PostgreSQL BDD, remote CI, builds, and deployed verification are not claimed by this section.
- These repairs change persistence boundaries and test fixtures, not product scope, API media type,
  endpoint schemas, dependencies, architecture direction, migration SQL, environment contracts, or
  rollout policy. Therefore no new product/ADR/C4/OpenAPI/runbook changes are required for this
  repair slice. The broader inventory, historical-data transformation, operational approval, and
  source-spec reconciliation requirements remain open under the existing change.

### Completion work in progress — 2026-10-26

- Web implementer reports dashboard lint/type-check/unit tests/build passed (176 files, 1,996 tests); admin lint/type-check/unit tests/build passed (22 files, 148 tests). Scheduler E2E reported 93 passed / 1 skipped; mocked media reported 37 passed / 4 skipped, including the Unsplash UUID-to-publication reference regression.
- Admin E2E reported 2 passed / 13 failed at an ambiguous `Waitlist` heading selector before UUID checks. A scoped follow-up is correcting the harness and auditing the remaining frontend fixtures and affected marketing gates. Do not claim admin E2E or generic Chromium suite passed.
- Added web fixture regressions for matching canonical principal IDs and imported Unsplash assets; opaque tokens, external IDs and semantic keys are preserved. Results above are implementer evidence, pending independent final verification.
- Added `docs/runbooks/uuid-native-identifiers.md`, linked from `docs/README.md`, aligned observability with raw entity UUIDs, and added the schema-compatibility warning to production rollback. Scoped Markdown lint and 103 offline links passed. Runbook still needs reconciliation with the final SQL; no production backup/restore evidence is claimed.
- Independent contract review identified broad guards rejecting existing embedded/polymorphic/historical data. A dedicated SQL slice is implementing only typed/contextual transformations and retaining explicit diagnostics for unknown contracts; backend consumer reconciliation continues separately. These open items block completion despite successful simple cutover and negative-path tests.

### Liquibase conversion against populated disposable PostgreSQL

- Applied the baseline changelog to an isolated PostgreSQL 18 database, inserted representative legacy principal, workspace, ownership, role, and permission rows, then ran the actual UUID-context Liquibase changesets with the explicit `profiletailors.uuid_cutover_approved=on` connection setting.
- Verified prefixed principal/workspace IDs became raw UUIDs and the ownership row references remained aligned; `role-owner` and `perm-001` used their fixed UUID mappings while semantic `role_key` and `permission_key` values remained unchanged. The explicit compliance mapping `ctrldim-access-001` → `fde15161-205c-523e-b09b-d2418ce12309` was present.
- Liquibase reported 430 rows affected. The resulting schema had UUID types for converted columns, 89 validated foreign keys, and valid indexes. The isolated container and database were removed after verification.
- Collision, orphan/unmapped-reference, and missing-approval rejection were tested through Liquibase on separate disposable PostgreSQL 18 databases. Each failed with the expected guard/error and left schema/data unmodified; disposable resources were removed. These results do not authorize production SQL or deployment.

### Remaining prefixed internal ID generators

- Replaced prefixes for risk acceptance (`ra-`), compliance control (`ctrl-`), consent (`cs-`), compliance evidence (`ev-`), consent event fallback (`ce-`), audit event (`audit-`), ideas (`idea-`), notification events (`nevt-`), workspace membership (`wm-`), refresh sessions (`refresh-session-`), and saved hashtag sets (`htset-`) with raw UUID generation.
- Added focused UUID assertions and observed RED/GREEN for the risk acceptance, compliance-control, consent, evidence, consent repository, audit, idea, notification-handler, and workspace-membership slices. The notification repository fallback and refresh-session/hashtag slices have passing focused tests, but complete pre-change RED evidence was not captured for those slices.
- A combined focused test run produced XML reports for 11 suites with 62 tests, zero failures/errors/skips. The PostgreSQL notification repository test was not included in that XML set and requires separate confirmation.
- Scoped `spotlessCheck` passed according to the implementing agent. `detekt` did not pass because it found a max-line-length issue in the pre-existing `LocalAuthEndpointIntegrationTest.kt`; that file was not changed in this generator slice.
- `worker-${UUID.randomUUID()}` remains intentionally prefixed: source/schema review confirms it is an opaque lease-owner token stored only in `publication_jobs.claimed_by_worker` (`varchar(128)`, no FK), cleared when claims end, and excluded from the UUID conversion inventory. It is not an internal entity ID.
- Fixture audit found a large remaining set of legacy-looking values in Kotlin tests and BDD data. Many are ID-like values; token values, semantic keys, unrelated synthetic inputs, and positional arguments still need owner-by-owner classification. Do not mass-replace by prefix alone.

## Verified local slices


### Notification identifiers

- `NotificationId.generate()` now returns an unprefixed canonical UUID. Construction rejects prefixed, abbreviated, uppercase, whitespace-padded, and invalid values.
- Changed only `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/Notification.kt` and its direct `NotificationTest.kt` in this slice.
- Implementer reports RED with 19 cases and 9 failures before the production change, then GREEN with 76 module tests and no failures/errors/skips. Current XML reports independently inspected by the orchestrator confirm 76 tests and no failures/errors/skips.
- Implementer ran `./gradlew :shared:notifications:test :shared:notifications:spotlessCheck :shared:notifications:detekt --console=plain` successfully. Orchestrator inspected the exact diff and ran `git diff --check -- shared/notifications` successfully.
- Existing warnings in `InvitationEmailTest` and `WelcomeEmailTest` and Gradle deprecation notices were reported separately; no suppressions were introduced by this slice.
- This verifies the notification value-object slice only, not persistence, backend integration, or the full migration.

### Privacy request identifiers

- `DataSubjectRequestId` now generates and validates canonical unprefixed UUIDs; nine direct privacy test files update validation regressions and fixtures.
- Implementer observed RED with four failures in twelve cases, then a successful run of 101 privacy tests using `node scripts/gradle-run.mjs :server:smp:test --no-daemon --tests 'com.profiletailors.smp.privacy.*'`.
- Two later attempts ended with `java.io.EOFException`, and compilation-cache conflicts were reported during concurrent Gradle execution. Stable repeat verification remains pending; the orchestrator found no current privacy XML reports to independently confirm the earlier success.
- Implementer reports SMP Detekt passed. Spotless was blocked by formatting in the already modified `tenancy/application/TenancyApplicationApi.kt`; this remains an integration defect to fix without weakening formatting rules.
- Orchestrator ran the privacy-scoped `git diff --check` successfully and confirmed ten changed files. Run Gradle verification sequentially after concurrent implementation finishes.

### Workspace and membership provisioning

- Workspace/membership generators now emit raw UUID strings instead of `ws-`/`wm-`; a direct provisioning test checks the four inserts and reference consistency.
- Implementer observed RED with two failures among three provisioning tests, then GREEN with provisioning 3/3 and workspace resolver 4/4. XML reports were subsequently removed by other shared-worktree test executions; repeat centrally before final verification.
- Scoped Spotless and production SMP Detekt passed according to the implementer. `detektTest` still fails with 515 findings reported outside this slice; an initial finding in the new test was corrected. Classify the remaining findings against the baseline during integration rather than assume all are pre-existing.
- Orchestrator inspected the production diff and ran the two-file `git diff --check` successfully.
- Persistence is NOT yet PostgreSQL-native UUID: binds remain strings for current varchar columns. Schema integration must coordinate UUID binds for `workspaces.id`, `workspace_ownerships.workspace_id`, `workspace_memberships.id/workspace_id`, and `membership_roles.membership_id`, plus principal references. `role-owner` remains unchanged pending classification of role identifiers.

### Platform principals, registration, and tenancy binding

- `PlatformPrincipalIds` emits canonical raw UUID strings. It accepts legacy `user-<uuid>` inputs and canonicalizes through `UUID`; malformed values fail. Direct tests cover raw output, legacy input, and invalid identifiers.
- Registration now emits raw UUID principal IDs. Invitation acceptance no longer requires the legacy `user-` prefix, and its focused test expects the raw UUID.
- Default workspace provisioning binds the mapped owner role UUID `6ce35f6c-2f1e-5702-9b59-d7a0e9402006`; its direct test was updated.
- `IdempotencyRecord.id` is `UUID?`, matching the identified `idempotency_records.id` database PK. A focused model test confirms the UUID key is retained. This remains a model-only type update; the current R2DBC repository omits generated ID retrieval. Email-verification token ID model correction is not included because this slice found no direct ID-bearing model.
- Focused verification run `node scripts/gradle-run.mjs :server:smp:test --no-daemon --console=plain --tests 'com.profiletailors.smp.platformadmin.application.PlatformPrincipalIdsTest' --tests 'com.profiletailors.smp.identity.application.LocalAuthHandlersTest' --tests 'com.profiletailors.smp.platformadmin.application.InvitationActivationCoordinatorTest' --tests 'com.profiletailors.smp.tenancy.infrastructure.R2dbcWorkspaceProvisioningServiceTest' --tests 'com.profiletailors.smp.mcp.domain.IdempotencyRecordTest'` completed successfully. XML confirms 59 tests across five suites, with 0 failures, 0 errors, and 0 skipped.
- `node scripts/gradle-run.mjs :server:smp:postgresIntegrationTest --no-daemon --console=plain --tests 'com.profiletailors.smp.integration.LocalAuthEndpointIntegrationTest'` completed successfully. Current XML reports 22 tests, 0 failures, 0 errors, and 0 skipped.
- The focused evidence does not establish a verified TDD RED→GREEN sequence. No UUID Liquibase migration was executed against PostgreSQL in this continuation; no production SQL was executed. The Liquibase changesets remain gated by `@uuid-native-identifiers`, while application defaults are `prod` and `dev`; `002-convert.sql` also requires `profiletailors.uuid_cutover_approved = 'on'`. End-to-end execution of this context and approval path remains unverified. Email verification token IDs are generated by PostgreSQL and have no application token ID model.

### Publishing generators

- Twenty UUID-backed internal generators across nine publishing production files now emit unprefixed UUID strings: publications, jobs, recurring schedules, publication groups, delivery attempts, assets, bulk jobs/rows, internal connections, and social accounts. Eight direct test files add canonical UUID assertions.
- Implementer observed RED with nine failing scenarios among 935 executed tests, then reported GREEN for publishing and production SMP Detekt. The orchestrator independently inspected 91 current publishing XML reports containing 919 tests with no failures, errors, or skips. These counts describe different runs and must not be conflated.
- Orchestrator ran publishing-scoped `git diff --check` successfully and confirmed seventeen changed files.
- `just backend-check` was attempted by the implementer and failed at `verifySecurityVersions`: expected Jackson 2.22.3/3.2.3, resolved 2.22.4/3.2.4. No dependency changes or gate weakening were made. Investigate provenance separately; do not label it pre-existing without baseline evidence.
- Provider IDs, tokens, nonces, and idempotency keys are unchanged. Operational `worker-` identifiers and the separate `nevt-` event generator remain unchanged and require explicit classification. Native UUID persistence and historical data compatibility remain unverified.

### Pre-existing gate drift: verifySecurityVersions

- Root cause confirmed on `main`, unrelated to this migration. Commit `da8f8dc2` (2026-10-23) raised the catalog to Jackson `2.22.4`/`3.2.4`, but the expected-version map in `server/smp/build.gradle.kts` still pinned `2.22.3`/`3.2.3` from 2026-10-04.
- Orchestrator reproduced the failure with `node scripts/gradle-run.mjs :server:smp:verifySecurityVersions --no-daemon --console=plain`: nine Jackson coordinates reported expected/resolved mismatches.
- Fix: the task now derives the expected Jackson versions from `libs.versions.jackson2` and `libs.versions.jackson` instead of duplicated literals. Exact-equality enforcement and the patched-line requirement are unchanged; no rule, threshold, or dependency was weakened. The values were captured as plain strings inside the task registration to keep the Gradle configuration cache serializable.
- Result: `BUILD SUCCESSFUL` for `:server:smp:verifySecurityVersions` after the fix. The build-logic plugin tests are self-contained with their own catalog and require no change.

## Delivery and safety

- Local current-worktree scope only. PR partition is deferred. No branch, commit, PR, publishing, production SQL execution, or deployment action was taken. The existing unrelated `.gitignore` modification was left unchanged.
- API media type `application/vnd.api.v1+json` is preserved. Internal UUIDs in the implemented workspace/shortlinks parser boundary use raw canonical UUIDs.
- The previous workspace/shortlinks slice was reviewed for tenant-boundary risk: parsing rejects malformed/prefixed workspace values before placing them in request context, and produces HTTP 400 through the typed error mapper. This slice does not replace authentication, authorization, membership, or isolation checks, and those HTTP-level properties are still unverified.
- No TDD RED/GREEN evidence is claimed. Earlier focused tests were written, but Gradle 9.8.2 distribution download timed out before the test runner started.
- Production UUID-only rollout remains blocked pending per-column/schema/consumer inventory, reviewed SQL and operational runbook, approved rollout approach, and user-executed production pre/postcondition validation.

### Backend PostgreSQL fixture failures — 2026-10-26 fresh focused run

- Fresh focused command: `node scripts/gradle-run.mjs :server:smp:test --no-daemon --console=plain --tests 'com.profiletailors.smp.identity.integration.LocalAuthHandlersTransactionPostgresIntegrationTest' --tests 'com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcInvitationRepositoryTest' --tests 'com.profiletailors.smp.publishing.integration.PublishingRetryEndpointPostgresIntegrationTest' --tests 'com.profiletailors.smp.publishing.integration.PublishingWorkerTransactionPostgresIntegrationTest'`; current console log is `/private/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/uuid-postgres-focused-20261026T133750.log`; exit 1. Kotlin test compilation succeeded. A later sequential retry-only run exposed the UUID type mismatch described below; a later identity-only run compiled but again failed during Testcontainers connection setup without behavior-level results.
- Current console evidence reports PublishingWorkerTransactionPostgresIntegrationTest 10/10 passed. Retry had a confirmed stale fixture (`owner-1` principal_id) and now binds a canonical UUID value into `user_identities.principal_id` and references that same UUID in membership. A sequential retry-class run exposed the next incompatible fixture operation: PostgreSQL `[42804] column "id" is of type uuid but expression is of type character varying`; the class still uses legacy workspace and membership IDs/helpers, so the retry test is not green and the demonstrated primary cause is broader than its principal field.
- R2dbcInvitationRepositoryTest reports 13 failures. The concurrent acceptance test timed out with `UncompletedCoroutinesError`; other failing tests report `BadSqlGrammarException` while inserting/using legacy fixture IDs. Root cause for the barrier timeout is not yet isolated. Do not claim invitation concurrency fixed.
- LocalAuthHandlersTransactionPostgresIntegrationTest failed at initialization with connection refusal during container bootstrap in the fresh run; native migration/bootstrap behavior is not established. Its `@DynamicPropertySource` disables automatic Liquibase and invokes its own JDBC migration, applying the UUID context and approval GUC, but that setup is after the failing container connection attempt. A follow-up class-only Gradle attempt again stopped at initialization with localhost connection refusal; this is environment/container setup failure and gives no migration or behavior evidence. The attempted principal UUID bind change was made to seedInvitation's principal insert, but remains unverified.
- XML files were regenerated by Gradle at 13:37 but their embedded suite timestamps remained at 12:xx. Treat XML summaries as stale for this run; the fresh console log is the current evidence. Earlier 13:09/13:32 report data must not be presented as the current result.
- Test-only migration setup and cleanup changes in `PostgresTestContainerSupport`/`IntegrationTestBase` are partial. Production data, SQL, and deployment were not touched.

## Cumulative task state

- [ ] 1.1 Complete cross-context internal ID/schema/client inventory. `implementation-inventory.md` now records confirmed candidate owners and discovery evidence, but the per-column and full consumer inventory remains incomplete.
- [ ] 1.2 Complete ID lifecycle traces through types, generators/parsers, persistence, APIs, events/queues/callbacks, clients and fixtures. Only the initial shortlinks-to-workspace trace is complete.
- [x] 1.3 Preserve `application/vnd.api.v1+json`; use unprefixed internal UUIDs in v1.
- [x] 2.1 ADR-0029 supersedes ADR-0005; index and architecture overview updated without rewriting historical decision text.
- [ ] 2.2 Prepare reviewed, unexecuted SQL after the table/column/relation inventory is complete. No SQL artifact created; automatic global conversion would be unsafe at this point.
- [ ] 2.3 Document backup, execution order, checks, rollback/forward-fix and user validation.
- [ ] 2.4 Select rollout based on inventory and operations evidence; no downtime or rolling compatibility assumption made.
- [ ] 3.1 Broadly migrate inventoried backend IDs, generators, parsers, persistence and contracts.
- [ ] 3.2 Convert only confirmed internal ID columns/relations to PostgreSQL UUID through the approved plan.
- [x] 3.3 Validate raw canonical UUID workspace header input and reject invalid input with HTTP 400. Unit regression tests exist but are not run; HTTP auth/workspace-isolation integration tests remain.
- [ ] 3.4 Replace prefix diagnostics while preserving excluded opaque/provider values.
- [ ] 4.1 Migrate confirmed dashboard/admin clients, DTOs, mocks, fixtures and contracts.
- [ ] 4.2 Complete shortlinks HTTP/auth/isolation regression coverage and run tests.
- [ ] 4.3 PostgreSQL-test conversion, relations, constraints, indexes, collisions and orphans.
- [ ] 5.1 Reconcile API/OpenAPI/docs/runbooks/examples/ADR/C4/specs for confirmed scope.
- [ ] 5.2 Run applicable backend/frontend/BDD/PostgreSQL/type/lint/build/E2E gates.
- [ ] 5.3 Keep deployment blocked until the user executes reviewed SQL and validates production pre/postconditions.

## Verification evidence

- `git status --short --branch`: inspected before changes; worktree already contained `.gitignore`, ADR/documentation, resolver/API/error-handler/test modifications, and the new shortlinks test/change artifact. Preserved those edits.
- `just -l`: completed; command hub lists `backend-check`, `backend-test`, and related recipes.
- Local static source searches: found 116 production UUID generation/parser occurrences, 71 prefixed-ID-shaped occurrences, 107 Liquibase changelog files, and 530 ID/type-like changelog matches. Counts are discovery signals, not classified migration inventory.
- `git diff --check`: run after documentation and task edits; no whitespace errors reported.
- Gradle bootstrap: `./gradlew --version` completed successfully with Gradle 9.8.2 and Java 25.0.1 on 2026-10-25. The prior distribution-download blocker is resolved. Backend tests and TDD remain unverified until their runners complete.
- Frontend, PostgreSQL, BDD, architecture, production SQL, and deployed verification: not run.

## Latest diagnostic pass — 2026-10-26 15:10 CET

- Focused retry Postgres test is now passing 1/1. The initial UUID/string repository fixes were not the only cause: error capture exposed an `owner-token` fixture carrying `owner-1`, which caused `UUID.fromString` to fail in authentication. Added a canonical-UUID `retry-owner-token` fixture without changing unrelated owner token consumers. Subsequent fresh runs traced remaining `uuid = character varying` errors to the identity lookup bind, one publication asset lookup, delivery-attempt deletion and publication job deletion, plus the test count/status queries and the shared `countPublicationJobs` helper. Production persistence binds were converted to UUID; focused test queries/helpers now bind native UUID as well. Green evidence: `/private/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/retry-fresh6.log`; XML timestamp 15:04:57 CET, 1 test, 0 failures/errors/skips. The principal-lookup suite previously ran with legacy fixture UUID failures (4 failures of 6); rerun after canonicalizing those fixtures remains needed.
- Invitation-only run at 13:22 CET reported 18 tests / 12 failures. Eleven SQL failures showed UUID columns compared with or written from `character varying`; invitation repository bindings were changed to UUID at the DB boundary. Rerun at 13:43 CET passed 17/18: concurrency progressed from timeout to assertion, but expected one membership and got zero. Root diagnostic from assertion instrumentation was a separate UUID/string binding in `R2dbcWorkspaceMembershipRepository` query. Those workspace/principal bindings and UUID insert ID were changed. Latest 13:48 CET rerun still reports 17/18 and the only failure is membership count zero (line 333); the 60-second hang and SQL UUID errors are gone. Barrier code was not changed. Need trace why membership wasn't persisted and confirm the two acceptance outcomes.
- Focused invitation Postgres class now passes 18/18 at 15:08 CET (`/private/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/invitation-fresh3.log`). No production concurrency barrier changed. The remaining zero-membership assertion was querying native UUID columns using string literals, so the test now binds canonical workspace and accepted-principal UUIDs. The concurrent transaction results and persisted accepted invitation condition also pass. Earlier UUID bindings in `R2dbcWorkspaceMembershipRepository` remain in place.
- Identity transaction integration class was rerun independently at 15:09 CET. Testcontainers logged container startup and its JDBC URL as `localhost:33252`; the first `DriverManager.getConnection` inside `@DynamicPropertySource` immediately failed with `Connection refused` before Spring context initialization. The method manually starts the same static `@Container` instance when `!isRunning`, then constructs both R2DBC and JDBC properties and opens JDBC directly. This confirms the failure is during that callback after container startup, but does not establish why the host port is not accepting connections. No speculative changes to lifecycle/configuration were made. Container stdout/stderr and port readiness need inspection. The previous seed fixture parse failure (`UUID string too large`) was addressed.
- `R2dbcPrincipalIdentityLookupTest` now passes 6/6 in its focused PostgreSQL run at 15:09 CET (`/private/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/principal-lookup-fresh.log`). The four legacy fixture IDs were changed from non-UUID strings to canonical UUIDs; the new native-UUID lookup regression also passes.
- Current logs: retry green `/private/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/retry-fresh6.log`; invitation `/private/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/invitation-final.log`; identity `/private/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/identity-followup.log`; focused lookup `/private/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/principal-lookup-red.log`. Gradle overwrites XML across invocations; use each console log and timestamps for evidence.
- No tasks are marked complete by this diagnostic pass. Current worktree contains changes from other contributors; these edits were limited to the migration's publication/invitation adapters and their focused fixtures. No production SQL, deployment, commit, or push was performed.

## Remaining risk

A full native-UUID migration cannot safely be marked complete based on search counts or a parser-only change. PostgreSQL column classification and FK/index/constraint/seed effects, all internal/external ID distinctions, frontend and asynchronous consumers, and rollout/rollback procedures remain open.
