# Apply Progress — working-tree-remediation-discovery

**Updated**: 2026-08-02
**Status**: Original slices remain landed: Tier 1 (A1, B, C, D), Tier 2 (A2, E, G1, G2), H, and I. Post-verification remediation is complete for the SEC-001 production boundary, the remaining B password contract paths, and the planned Actuator test support. The approved pragmatic MVP decision defers distributed waitlist rate limiting: F7.1 is cancelled/deferred, not failed; the untracked distributed E2E test is removed; no Redis dependency or distributed implementation is added. Final verification completed with PASS WITH WARNINGS; remaining non-zero gates are classified baseline or harness warnings, and archive is eligible because no current MVP blocker remains.

## Slice F — MVP Decision Applied (2026-08-02)

| Scope | Result | Evidence |
|-------|--------|----------|
| Distributed waitlist rate limiting | **Deferred out of MVP** | `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/infrastructure/adapter/Bucket4jRateLimiter.kt:59-72` constructs a bounded Caffeine cache with `maximumSize(properties.cache.maxSize)` and `expireAfterAccess(Duration.ofMinutes(properties.cache.ttlMinutes))`; this state is per JVM and is not shared between replicas. |
| SMP waitlist default | **Accepted behavior retained** | `server/smp/src/main/resources/application.yaml:35-39` documents DALLAY-512/DALLAY-513 as blockers and binds `enabled` to `${SMP_WAITLIST_RATE_LIMIT_ENABLED:false}`. |
| Redis/distributed dependency | **Not added** | `shared/shield/ratelimit` has no distributed store configuration or Redis/bucket4j-redis dependency; the existing implementation remains Caffeine-only. |
| F7.1 test | **Removed** | Deleted only the untracked `server/smp/src/test/kotlin/com/profiletailors/smp/leadcapture/integration/WaitlistDistributedRateLimitE2ETest.kt`, which exercised the deliberately deferred capability. |

The future two-replica burst/reset scenarios remain preserved in the waitlist delta spec and design
under clearly marked follow-up sections. Multi-replica enablement must wait for DALLAY-512 and
DALLAY-513. The unrelated app WIP paths were not changed or staged.

## Post-verification Remediation — Apply (2026-08-02)

| Scope | Commit | Paths | Verification |
|-------|--------|-------|--------------|
| SEC-001 production boundary | `e3b78d16` | `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt` | Existing five SEC-001 BDD scenarios were used. Restoring the public permit entry produced 5 failures (the SEC-001 media-proxy scenario plus the 4 known unrelated failures); removing it restored the 4-failure baseline. |
| B password contract | `272b1fdc` | `apps/web/app/e2e/specs/registration.spec.ts`, `apps/web/app/src/shared/i18n/locales/en/auth.ts`, `apps/web/app/src/shared/i18n/locales/en/passwordRecovery.ts` | All three paths now state 12. Password-policy and schema tests passed inside the app run; the full app unit command remained at its known 1,225/1,240 result with 15 unrelated failures. |
| Actuator supporting test configuration | `3511aacd` | `server/smp/src/test/kotlin/com/profiletailors/smp/integration/ActuatorEndpointsIntegrationTest.kt` | Focused `ActuatorEndpointsIntegrationTest` PostgreSQL integration run passed. The properties disable unavailable Redis health and expose health components required by the existing assertions. |

### Remediation verification outcomes

- `just backend-bdd-fast`: 151 passed, 4 known unrelated failures (trending hashtags, saved hashtag deletion, scheduled publication, and quick-created scheduled publication). All five SEC-001 scenarios passed with `e3b78d16`.
- `pnpm --filter app test:run`: 1,225 passed, 15 failed across 1,240 tests. The failures are the existing 14 `Lightbulb` mock failures in `App.test.ts`/`AppShell.test.ts` and the existing AI comparison assertion in `CreatePostModal.test.ts`; the password-policy and validation tests passed.
- Registration-only Playwright execution was attempted with the available app config and failed all 15 browser projects/tests during page/locator startup or detachment, before providing a password-contract assertion. This is recorded as environment/test-harness evidence, not attributed to the three committed contract paths.
- `./gradlew :server:smp:postgresIntegrationTest --tests 'com.profiletailors.smp.integration.ActuatorEndpointsIntegrationTest' --no-daemon`: `BUILD SUCCESSFUL`.
- Unrelated application WIP, the removed Slice F test, and other OpenSpec artifacts were not staged in the remediation commits.

## Slice I — Repo Config + Dev Docs + OpenSpec Config (landed, no push)

| Slice | Commit | Message | Gate result |
|-------|--------|---------|-------------|
| I | `ad800dd0` | `chore: repo tooling, PR template, docs refresh (Astro 7, Kotlin 2.3)` | Slice validation PASS; `just frontend-check` PASS; `just ci-local` blocked by 15 pre-existing app test failures |

### I10.1–I10.3 validation

- Staged and committed exactly the 20 requested Slice I paths. No Slice F path or unrelated modified path was staged.
- Docs now match the manifests: marketing declares Astro `^7.1.0`, Gradle version catalog declares Kotlin `2.3.21`, and the stale Astro 6 evidence citation was corrected.
- Every root `package.json` script reference resolves to an existing `scripts/*.mjs` file. Every documented `just` recipe in the Slice I docs exists in `Justfile`.
- `openspec/config.yaml` retains strict TDD and repository test runners. `just frontend-check` passed with zero errors and zero warnings (14 hints).
- `just ci-local` ran after staging. It stopped in the pre-existing app unit suite: 15 failures in `App.test.ts`, `AppShell.test.ts`, and `CreatePostModal.test.ts`; failures are unrelated to Slice I. The commit hook checks (backend Detekt, Spotless, shared Detekt, gitleaks) passed.

## Tier 1 commits (landed, no pushes)

| Slice | Commit | Message | Gate result |
|-------|--------|---------|-------------|
| Infra | `f33384a1` | `fix(tooling): scope spotless pre-commit restage to staged files` | hooks green |
| A1 | `95cf6c13` | `fix(marketing): always-dark consent banner with WCAG-AA contrast` | `just frontend-test` 85 ok; `frontend-test-e2e` 190 ok |
| B | `3a8d82f4` | `feat(identity): enforce ASVS L2 12-char password minimum (SEC-009)` | backend-detekt/spotless, frontend-biome-app, gitleaks, shared-detekt green |
| C | `850668ca` | `test(smp): endpoint authorization BDD coverage (SEC-001)` | `just backend-bdd-fast` 151/155 pass (4 pre-existing fails) |
| D | `eed89343` | `fix(publishing): fail fast on placeholder OAuth state secrets (SEC-002)` | `backend-test-fast` 1480/1482 (2 pre-existing); BDD 151/155 (4 pre-existing) |

## Tier 2 commits (landed, no pushes)

| Slice | Commit | Message | Gate result |
|-------|--------|---------|-------------|
| A2 | `a40fd83b` | `feat(marketing): a11y focus management, robots/sitemap, reduced-motion a11y test` | `just frontend-test` 85 ok; marketing E2E 190 ok (1.7m); app media-mocked E2E 36 pass / 4 skip (known defects ML-CAS-007, ML-COMPOSE-006) |
| E | `15f9a1d4` | `refactor(ideas): formatting normalization; drop detekt stub baseline` | detekt + spotlessCheck PASS; `backend-test-fast` 1482 tests, 2 failures (both pre-existing/out-of-E-scope, see below) |
| G1 | `2babe18f` | `build: enforce AGPL-compatible dependency licenses` | `just licence-check` PASS (frontend scan + generateLicenseReport; only GPL matches carry Classpath Exception — not blocked); config-cache warning from jk1 plugin (known limitation, non-fatal) |
| G2 | `83a00eff` | `build(deps): bump springdoc, awsS3, jackson, add kotlin-bom platform` | `backend-test-fast` 1482 tests, 2 failures (both pre-existing); compileKotlin/compileTestKotlin PASS |

## Slice H — Compliance/Retention/Security Docs (landed, no push)

| Slice | Commit | Message | Gate result |
|-------|--------|---------|-------------|
| H | `dd90582f` | `docs(compliance): retention framework, legal baseline, audit report` | Pre-commit `backend-detekt`, `backend-spotless`, `shared-detekt`, and `gitleaks` PASS |

### H9.1 retention governance validation

- **API:** No `POST /api/governance/retention/rules` route exists. `ComplianceController.kt:25-28`
  is mapped to `/api/governance/compliance`; its only mappings are `POST /evaluations` at lines
  68-70, `GET /release-gate` at lines 91-98, and `GET /ping` at lines 108-110. The targeted backend
  Kotlin search found no retention controller or retention-rule mapping.
- **Liquibase:** `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml:51-63`
  includes governance changelogs `001` through `007`. The targeted changelog search found no
  `retention_periods` table, retention governance migration, or `V100__retention_governance.xml`.
- **Documentation action:** The three retention framework documents now label the rule API and
  retention table as planned/not implemented and distinguish the existing compliance control and
  fixed scheduled jobs from a future rule engine. No implementation was invented.

### H9.2 security audit validation

- **SEC-001:** `git show 850668ca --name-status` showed only BDD/support paths; it did not contain
  `IdentitySecurityConfiguration.kt`. The omitted production deletion of `/api/media/proxy` from
  the public `permitAll()` list is now landed in focused commit `e3b78d16`; `850668ca` remains the
  BDD/support coverage commit.
- **SEC-002:** `eed89343` contains the `HmacOAuthStateSigner` placeholder-prefix guard,
  `ConditionalOnProperty` wiring, regression tests, and test-secret configuration. The audit
  document retains the fixed claim and cites `eed89343`.

### H9.3/H9.4 compliance and ADR validation

- Staged exactly the Slice H documentation set: modified/new `docs/compliance/*`, the three
  retention documents, `docs/security/audit-report.md`, `docs/consent-management.md`, and
  `docs/architecture/adr/0012-agpl-commercial-strategy.md` (17 paths).
- `Justfile:339-344` defines `licence-check`; `Justfile:351-360` defines `ci-local` and invokes
  `just licence-check`. Commit `2babe18f` adds the licence-report build plugin and SMP wiring but
  does not modify `Justfile`; the recipe predates it (`d395d8c3`). The AGPL source-offer runbook
  exists at `docs/compliance/agpl-source-offer.md`. ADR-0012's acceptance claim is therefore
  supported by current repository evidence.
- Commit `dd90582f` was created without pushing and contains documentation only. No code, Slice F,
  or Slice I paths were staged.

## Tier 1 key implementation details

- **B**: `MIN_PASSWORD_LENGTH = 12` private const in LocalAuthHandlers.kt:239 + ResetPasswordHandler.kt:100; `@Size(min=12,max=128)` + `@Schema(minLength=12)`; `registerSchema` `.extend()` `min(12,'passwordTooShort')`; `authCredentialsSchema` stays min(1); ES copy assertions (en/es "12" not "8") in new `password-policy.test.ts`; the remaining English copy and registration E2E contract are landed in `272b1fdc`; BDD literals → `bdd-RegisteredP@ssw0rd1`; integration payloads → `TEST_PASSWORD_S3cr3tP@ssw0rd*123`; openspec login/register-flow.md 8→12 synced.
- **C**: `security-endpoint-authorization.feature` (5 scenarios @security @smoke @fast); `SecurityAuthorizationBddSteps.kt` (new, actuator paths routed to `local.management.port` client with no API media-type header — management runs on fixed port 8080 while app uses RANDOM_PORT); `BddDatabaseSupport.kt` reset now deletes `ideas` + `idea_board_configs` BEFORE publications/workspaces (fixes FK cascade `fk_idea_board_workspace` — BDD fast went 58 fails → 4); `AuthorizationBddSteps.kt` `whenClientRegistersWithEmailAndPassword` step.
- **D**: `HmacOAuthStateSigner` init guard: `INSECURE_PLACEHOLDER_PREFIXES = [CHANGE_ME, change_me, changeme, placeholder, test-]` rejected case-insensitively; `@ConditionalOnProperty(client-id, matchIfMissing=false)` on `oauthStateSigner` bean; signer test secret `test-secret-...` → `unit-secret-...`; +2 TDD tests (`test-` rejected; `bdd-`/`smp-` accepted) — RED proven by temporarily disabling guard (5 reject tests fail) then GREEN (14/14); BDD wiring: `bdd-test-oauth-state-signing-secret-32b` (BddTestProperties) + `smp-integration-test-oauth-state-signing-secret` (test application.properties); `.gitleaks.toml` allowlist for HmacOAuthStateSignerTest.kt fixture; stale allowlist header comment corrected.

## Tier 2 key implementation details

- **A2**: `Layout.astro` `#main-content` focus management + robots/og-image meta; 6 legal/home pages `tabindex="-1"`; `global.css` body token; NEW `src/pages/robots.txt.ts` (APIRoute, `User-agent: * Allow: /` + Sitemap from `site ?? https://profiletailors.com`) and `src/pages/sitemap.xml.ts` (ROUTES `['/', '/privacy/', '/terms/', '/cookies/', '/acceptable-use/', '/accessibility/']`, en+es variants, weekly changefreq, priority 1.0/0.9/0.7/0.6); `accessibility.spec.ts` reducedMotion via `contextOptions`; marketing/README.md Astro 6→7 + legal baseline. Stale `astro preview` on :4321 killed before E2E.
- **E**: Spotless-format of 5 ideas files (IdeasApi, IdeasCommandHandlers, IdeaModels, IdeasController, R2dbcIdeaRepositories); rename `requireConnectedSocialAccountId`→`requireConnectedAccountId`; deleted `server/smp/config/detekt/baseline.xml` stub (109-byte `<baseline><missing>baseline to be generated</missing></baseline>`, unreferenced — no build file references `config/detekt`). Active `server/smp/detekt-baseline.xml` untouched.
- **G1**: NEW `gradle/build-logic/src/main/kotlin/com/profiletailors/buildlogic/legal/LicenceReportPlugin.kt` — `BLOCKED_LICENCES` (GPL-2.0-only, GPL-2.0, GNU GPL v2/v2.0-only), applies `com.github.jk1:gradle-license-report` v2.9, output `$buildDir/reports/dependency-licence/`, Json + Text renderers, enforcement wired on `generateLicenseReport`; registration in build-logic/build.gradle.kts; applied in server/smp/build.gradle.kts; `libs.versions.toml` `licenceReport = "2.9"` + `gradle-licence-report` entry. Toml + smp build.gradle.kts staged surgically per-file so G1/G2 each hold only their own lines.
- **G2**: `libs.versions.toml` springdoc 3.0.2→3.0.3, awsS3 2.20.15→2.50.1; smp `jackson-module-kotlin` 2.21.2→2.22.1; `KotlinLibraryPlugin.kt` adds kotlin-bom platform for implementation + testImplementation.

## SLICE F — DEFERRED OUT OF MVP (test removed; no implementation added)

`WaitlistDistributedRateLimitE2ETest.kt` (previously untracked in `server/smp/src/test/kotlin/com/profiletailors/smp/leadcapture/integration/`) was removed because it tested a capability deliberately deferred from the MVP. Evidence for the decision:

1. **No distributed bucket store exists.** `shared/shield/ratelimit` `Bucket4jRateLimiter.kt` constructs a bounded per-JVM Caffeine cache with `maximumSize(properties.cache.maxSize)` and `expireAfterAccess(Duration.ofMinutes(properties.cache.ttlMinutes))`. `RateLimitProperties.kt` has no `store` field; no bucket4j-redis / lettuce / spring-data-redis dependency is present (`shared/shield/ratelimit/build.gradle.kts` only has `libs.bucket4j.core`; the version catalog has bucket4j `8.10.1`). The removed test's `application.rate-limit.store.*` keys bound to nothing.
2. **Cross-replica expectation cannot hold**: two independent per-JVM Caffeine buckets (capacity 3 each) → replica A + replica B combined allow ~6 requests/min before 429, so the 4th cross-replica request would get 202, not the expected shared-window 429.
3. **Immediate failure at Liquibase init (historical test evidence):** `BeanCreationException`/`PSQLException: Connection to localhost:5432 refused` at line 125 — `SpringApplicationBuilder.properties(map)` sets default-properties (lowest precedence), overridden by classpath `application.yaml` `spring.liquibase.url: jdbc:postgresql://localhost:5432/profiletailors_smp` (container's mapped port ignored). Even fixing precedence, assertion 2 still fails.
4. `application.yaml:35-39` documents DALLAY-512/DALLAY-513 as blockers and the production-safe default as `${SMP_WAITLIST_RATE_LIMIT_ENABLED:false}` — the prior design assumed a capability that does not exist.

**Future follow-up only** (not required to unblock MVP): implement a Redis/bucket4j-proxy distributed store in the shared library (new deps + config binding + wiring), resolve DALLAY-513 trusted client identity, and add replacement two-replica coverage. Do not reintroduce the removed test until that capability exists. F7.1 is marked `[x]` as cancelled/deferred in `tasks.md`.

## Pre-existing failures (NOT introduced by Tier 1/Tier 2 — evidence-based)

- `backend-test-fast` (both Tier 1 and Tier 2 runs, 1482 tests / 2 failed): `ModularStructureTest` (ideas→publishing domain violation via `ConvertIdeaHandler` importing `publishing.domain.SocialConnectionStatus` + `ScheduleMode`; allowed targets are publishing :: application — verified imports exist at HEAD, rename in E6.1 neither caused nor fixed it) and the historical `WaitlistDistributedRateLimitE2ETest` initialization. The latter is no longer a current failure after the test was removed; it documented the deferred capability.
- `backend-build` without env injection fails 38 `@Tag("postgres")` tests with `SMP_DB_TEST_PASSWORD must be set` — expected; `backend-test-fast` injects via `scripts/with-db-password-gradle.mjs`. Not a code regression.
- `backend-bdd-fast` (4): hashtags trending (0 results — relies on stale data the broken reset used to leave), delete saved hashtag set (400 — data issue), create/quick-create scheduled publication (500 server error). **Isolation experiment**: disabling the C reset fix returns 58 FK failures; the 4 persist with or without it → pre-existing latent test pollution in hashtags/publishing features (their slices should fix).
- `apps/web/app`: App.test.ts lucide-mock gap, AppShell.test.ts (scheduler-sidebar), CreatePostModal — pre-existing, files clean.

## Gate evidence notes

- Property-precedence probe (Tier 1): with `LINKEDIN_STATE_SIGNING_SECRET` removed from fast BDD `@SpringBootTest`, suite still boots (155 tests) → test `application.properties` value overrides test-profile yaml default → postgres BDD config safe, no extra wiring needed.
- Slice-F property-precedence probe (Tier 2): `SpringApplicationBuilder.properties(map)` is default-properties (lowest precedence) → classpath `application.yaml` `spring.liquibase.url` wins over the container-mapped URL → `localhost:5432` refused. This is exactly the class of bug the F test needs a higher-precedence mechanism for (env var / `--spring.liquibase.url` arg / `@DynamicPropertySource`), and is moot while the distributed store is absent.

## Final verification — 2026-08-02

- `I11.1` is complete. The full landed sequence, including `bf895af1`, `aa7dc26d`, and the
  artifact-only OpenSpec commit `7599d36a`, was inspected for path mixing and boundary drift.
- The application/backend working tree is committed. After the verification artifact update,
  only intentional OpenSpec files are expected to be modified.
- `just frontend-test`, `just frontend-check`, `just licence-check`, and `just backend-lint` pass.
  Backend fast/BDD, full app tests, and `ci-local` retain known unrelated baseline or harness
  failures documented in `verification.md`.
- Distributed waitlist two-replica scenarios remain follow-up work after DALLAY-512/DALLAY-513;
  they are not current MVP failures.
