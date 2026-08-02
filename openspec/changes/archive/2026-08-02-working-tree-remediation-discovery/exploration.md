# Exploration: Working Tree Remediation — 97 Uncommitted Paths Across 10+ Work Units

## Executive Summary

The repository working tree contains **97 modified/untracked/deleted paths** (`git status --short`:
78 modified, 1 staged+unstaged `MM`, 1 staged rename `R`, 1 deleted `D`, 16 untracked) spanning
**11 distinct work units** (the 10 previously identified plus a backend quality-gate cleanup unit
for the detekt baseline deletion). No commits exist yet for any of this work, and the index is in a
**mixed state**: 4 paths are staged (consent banner refactor, consent test, cookie-settings link,
consent e2e relocation) while everything else is unstaged — a single mega-commit would be unsafe.

The changes fall into three tiers by risk:

- **Tier 1 — Security-hardening, contract-changing (must land first, atomically):** password
  minimum 8→12 (ASVS L2 V2.1.1, SEC-009), removal of `/api/media/proxy` from the public
  permit-list (SEC-001), and the OAuth state-signing placeholder-secret guard (SEC-002). These
  change behavior and touch both backend and frontend; splitting them breaks tests.
- **Tier 2 — Feature/supporting work:** marketing consent/a11y/SEO, authorization BDD coverage,
  ideas formatting, LinkedIn publishing support, distributed waitlist rate-limit E2E, gradle
  license enforcement.
- **Tier 3 — Documentation and repo config:** compliance/retention/security docs, dev docs,
  package.json tooling, openspec config. Docs should land only after the implementation they
  reference is committed (several docs claim implementation that must exist by then).

**Confirmed gaps to fix during implementation:** Spanish i18n is stale (`es/auth.ts` placeholder
still says "Al menos 8 caracteres"; `es/passwordRecovery.ts` `passwordTooShort` still says "al
menos 8 caracteres"), and the frontend `registerSchema` has no minimum-length rule (only `min(1)`),
so the register form relies on the backend 422 instead of inline validation.

---

## Full Path → Work Unit Mapping (all 97 paths)

### Unit 1 — Marketing consent, accessibility, and SEO (16 paths)

| Path | Change |
|---|---|
| `apps/web/marketing/README.md` | Astro 6→7, legal-baseline section |
| `apps/web/marketing/src/components/consent/ConsentBanner.astro` | **MM** — always-dark banner, WCAG-contrast fixed colors (staged + unstaged) |
| `apps/web/marketing/src/components/consent/ConsentBanner.test.ts` | **staged** — test updates for banner changes |
| `apps/web/marketing/src/components/consent/CookieSettingsLink.astro` | **staged** — link fix |
| `apps/web/marketing/e2e/consent.spec.ts` → `apps/web/marketing/tests/e2e/consent.spec.ts` | **staged rename** — e2e relocation + updates |
| `apps/web/marketing/src/layouts/Layout.astro` | Robots meta content, og-image `.svg`, theme script refactor |
| `apps/web/marketing/src/pages/_AcceptableUsePage.astro` | `tabindex="-1"` on main-content (a11y focus mgmt) |
| `apps/web/marketing/src/pages/_AccessibilityPage.astro` | `tabindex="-1"` (a11y) |
| `apps/web/marketing/src/pages/_CookiePolicyPage.astro` | `tabindex="-1"` (a11y) |
| `apps/web/marketing/src/pages/_HomePage.astro` | `tabindex="-1"` (a11y) |
| `apps/web/marketing/src/pages/_PrivacyPolicy.astro` | `tabindex="-1"` (a11y) |
| `apps/web/marketing/src/pages/_TermsPage.astro` | `tabindex="-1"` (a11y) |
| `apps/web/marketing/src/styles/global.css` | `body` background token |
| `apps/web/marketing/tests/e2e/accessibility.spec.ts` | `reducedMotion` context fix for axe false negatives |
| `apps/web/marketing/src/pages/robots.txt.ts` | **new** — robots.txt route |
| `apps/web/marketing/src/pages/sitemap.xml.ts` | **new** — sitemap.xml route |

### Unit 2 — App authentication and password recovery (9 paths)

| Path | Change |
|---|---|
| `apps/web/app/e2e/README.md` | E2E docs update |
| `apps/web/app/e2e/specs/registration.spec.ts` | Password 8→12 assertion |
| `apps/web/app/src/modules/auth/presentation/ResetPasswordView.vue` | `minlength=12` |
| `apps/web/app/src/modules/module-relocation.spec.ts` | Timeout bump 20s (flaky guard) |
| `apps/web/app/src/shared/i18n/locales/en/auth.ts` | Placeholder "At least 12 characters" |
| `apps/web/app/src/shared/i18n/locales/en/passwordRecovery.ts` | Policy/error text 8→12 |
| `apps/web/app/src/shared/i18n/locales/es/passwordRecovery.ts` | **PARTIAL** — policy updated, `passwordTooShort` still "8" |
| `apps/web/app/src/shared/lib/validation/schemas.test.ts` | Schema test updates |
| `apps/web/app/src/shared/lib/validation/schemas.ts` | `resetPasswordSchema` min 8→12 (register schema untouched) |

### Unit 3 — Backend identity, password recovery, and security (8 paths)

| Path | Change |
|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt` | `MIN_PASSWORD_LENGTH` 8→12 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/ResetPasswordHandler.kt` | `MIN_PASSWORD_LENGTH` 8→12 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/LocalAuthController.kt` | `@Size(min=12)` + OpenAPI minLength |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt` | **SEC-001** — removed `/api/media/proxy` from `permitAll()` |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/ResetPasswordHandlerTest.kt` | 11-char rejection test |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/LocalAuthEndpointIntegrationTest.kt` | Test passwords → 12+ chars |
| `server/smp/src/test/resources/features/auth/registration.feature` | 11 rejected / 12 accepted scenarios |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/ActuatorEndpointsIntegrationTest.kt` | Redis health disabled + `show-components` (health hardening) |

### Unit 4 — Backend authorization BDD coverage (4 paths)

| Path | Change |
|---|---|
| `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/AuthorizationBddSteps.kt` | New step: register with explicit email+password |
| `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/SecurityAuthorizationBddSteps.kt` | **new** — unauthenticated-GET steps |
| `server/smp/src/test/resources/features/security-endpoint-authorization.feature` | **new** — SEC-001 endpoint auth scenarios |
| `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/BddDatabaseSupport.kt` | `seedPublishedPublication` + `hashtag_saved_sets` cleanup |

### Unit 5 — Ideas feature (5 paths — mostly formatting/refactor)

| Path | Change |
|---|---|
| `server/smp/.../ideas/application/IdeasApi.kt` | Formatting only |
| `server/smp/.../ideas/application/IdeasCommandHandlers.kt` | Formatting + `requireConnectedSocialAccountId` → `requireConnectedAccountId` rename |
| `server/smp/.../ideas/domain/IdeaModels.kt` | Formatting only |
| `server/smp/.../ideas/infrastructure/http/IdeasController.kt` | Formatting only |
| `server/smp/.../ideas/infrastructure/persistence/R2dbcIdeaRepositories.kt` | `BIND_WORKSPACE_ID` const + formatting |

### Unit 6 — LinkedIn OAuth and publishing (9 paths)

| Path | Change |
|---|---|
| `server/smp/.../publishing/infrastructure/linkedin/HmacOAuthStateSigner.kt` | **SEC-002** — placeholder-secret fail-fast guard |
| `server/smp/.../publishing/infrastructure/linkedin/LinkedInPublishingAdapters.kt` | `@ConditionalOnProperty` on `oauthStateSigner` bean |
| `server/smp/src/test/kotlin/.../linkedin/HmacOAuthStateSignerTest.kt` | Placeholder-prefix rejection tests |
| `apps/web/app/src/shared/i18n/locales/en/composer.ts` | **new strings** — hashtags + AI assistant (i18n only, no UI in diff) |
| `apps/web/app/src/shared/i18n/locales/es/composer.ts` | **new strings** — hashtags + AI assistant (i18n only) |
| `apps/web/app/src/modules/auth/infrastructure/auth-api.ts` | `resetAt`/`upgradeOptions` on `ApiError` (consumed by `CreatePostModal.vue` AI quota) |
| `server/smp/src/test/kotlin/.../bdd/CucumberSpringConfiguration.kt` | Adds `LINKEDIN_STATE_SIGNING_SECRET` test property |
| `server/smp/src/test/kotlin/.../bdd/glue/BddTestProperties.kt` | Adds state-signing-secret constant |
| `server/smp/src/test/resources/application.properties` | Test state-signing secret (passes the guard) |

### Unit 7 — Distributed waitlist rate-limit E2E coverage (1 path)

| Path | Change |
|---|---|
| `server/smp/src/test/kotlin/com/profiletailors/smp/leadcapture/integration/WaitlistDistributedRateLimitE2ETest.kt` | **new** — two-replica Testcontainers test sharing a bucket4j window |

### Unit 8 — Gradle dependency-license enforcement (5 paths)

| Path | Change |
|---|---|
| `gradle/build-logic/build.gradle.kts` | Registers `com.profiletailors.legal.licence-report` plugin |
| `gradle/build-logic/src/main/kotlin/com/profiletailors/buildlogic/legal/LicenceReportPlugin.kt` | **new** — jk1 license-report + blocked-license fail (GPL-2.0) |
| `gradle/libs.versions.toml` | `licenceReport=2.9`, `springdoc 3.0.2→3.0.3`, `awsS3 2.20.15→2.50.1` |
| `server/smp/build.gradle.kts` | Applies licence-report plugin, `jackson-module-kotlin 2.21.2→2.22.1` |
| `gradle/build-logic/.../library/KotlinLibraryPlugin.kt` | Adds `kotlin-bom` platform to library modules |

### Unit 9 — Compliance, retention, and security documentation (17 paths)

| Path | Change |
|---|---|
| `docs/compliance/README.md` | Links status taxonomy + legal baseline |
| `docs/compliance/customer-dpa-template.md` | Status wording |
| `docs/compliance/data-inventory.md` | Status wording (internal control artifact) |
| `docs/compliance/legal-document-register.md` | v1.1 — operator-hosted legal publication approved |
| `docs/compliance/legal-publication-gate.md` | Gate reconciliation |
| `docs/compliance/retention-and-erasure-control-plan.md` | Status wording |
| `docs/compliance/subprocessor-register.md` | Register updates |
| `docs/compliance/agpl-source-offer.md` | **new** — source-offer runbook |
| `docs/compliance/contributor-copyright-map.md` | **new** — contributor/copyright map |
| `docs/compliance/marketing-legal-baseline.md` | **new** — legal-artifact baseline |
| `docs/compliance/status-taxonomy.md` | **new** — status conventions |
| `docs/retention-framework-acceptance-criteria.md` | **new** — claims governance API implementation |
| `docs/retention-framework-operations.md` | **new** |
| `docs/retention-framework-quick-reference.md` | **new** |
| `docs/security/audit-report.md` | **new** — OWASP/ASVS audit; claims "Fixed in this audit" |
| `docs/consent-management.md` | Consent contract doc updates |
| `docs/architecture/adr/0012-agpl-commercial-strategy.md` | Status Proposed→Accepted, acceptance criteria, proprietary boundary |

### Unit 10 — Repository configuration and developer documentation (20 paths)

| Path | Change |
|---|---|
| `README.md` | Monorepo overview, Astro 6→7, command table |
| `docs/README.md` | Index |
| `docs/getting-started.md` | Setup steps |
| `docs/portless-setup.md` | Setup port 7638 |
| `docs/gradle-build-system.md` | Kotlin 2.3, excluded `build` projects, H2→fast BDD wording |
| `docs/release-verification.md` | Manual smoke prerequisites |
| `docs/testing/test-tags-and-env.md` | Tag/env doc updates |
| `docs/api-versioning.md` | API versioning doc |
| `docs/api-versioning-implementation-summary.md` | Summary |
| `docs/architecture/README.md` | Architecture index |
| `docs/architecture/adr-discovery/evidence-ledger.md` | Astro 6→7 |
| `docs/architecture/c4/01-system-context.md` | Astro 6→7 |
| `docs/architecture/c4/02-container.md` | Container diagrams |
| `docs/architecture/c4/README.md` | Index |
| `docs/architecture/c4/SUMMARY.md` | Index |
| `server/smp/README.md` | Backend docs |
| `package.json` | Scripts → `node scripts/*.mjs` tooling |
| `.github/pull_request_template.md` | **new** — PR template |
| `.vscode/settings.json` | **new** — `java.compile.nullAnalysis.mode: automatic` |
| `openspec/config.yaml` | Context refresh + strict-TDD test-runner config |

### Unit 11 — Backend quality-gate cleanup (1 path)

| Path | Change |
|---|---|
| `server/smp/config/detekt/baseline.xml` | **deleted** — baseline suppression removed |

### Cross-cutting (openspec spec-sync for password policy — 2 paths)

| Path | Change |
|---|---|
| `openspec/specs/e2e/login-flow.md` | Password minimum 8→12 |
| `openspec/specs/e2e/register-flow.md` | Password minimum 8→12 |

---

## Per-Unit Inferred Intent

1. **Marketing consent/a11y/SEO** — Ship a WCAG-AA-compliant, always-dark consent banner (theme-independent), focus-management for legal/home pages, reduced-motion-correct axe scans, and new robots/sitemap routes. E2E directory relocated `e2e/` → `tests/e2e/`.
2. **App auth/password recovery** — Sync frontend to the ASVS L2 12-char minimum: reset schema, view `minlength`, EN i18n, e2e spec, plus a flaky-test timeout guard.
3. **Backend identity/security** — Enforce the 12-char minimum at the domain/handler/API layers (SEC-009), close the unauthenticated `/api/media/proxy` hole (SEC-001), harden actuator health test config.
4. **Authorization BDD** — Prove every endpoint is authenticated by default; assert 401 on previously-exposed paths and 200 only on the explicit public allowlist.
5. **Ideas** — Mostly Spotless/Kotlin formatting normalization plus a method rename; likely the cleanup that enabled deleting the detekt baseline.
6. **LinkedIn OAuth/publishing** — Fail fast on guessable state-signing secrets (SEC-002), gate the signer bean on `client-id`, provide BDD/test secrets that pass the guard, and add composer i18n strings (hashtags + AI) plus `ApiError` quota fields for the composer UI.
7. **Waitlist rate-limit E2E** — Prove the distributed bucket4j window is shared across two replicas against real Postgres.
8. **Gradle license enforcement** — Reject AGPL-incompatible licenses (GPL-2.0) at build time via jk1 license-report; includes incidental dependency bumps (springdoc, awsS3, jackson, kotlin-bom).
9. **Compliance/retention/security docs** — Close ADR-0012 acceptance criteria: AGPL source-offer runbook, contributor map, legal baseline/taxonomy, retention framework ops/acceptance, and the security audit report recording the fixes in this working tree.
10. **Repo config/dev docs** — Modernize command tooling (node scripts), PR template, VSCode Java null-analysis config, and refresh docs to the Astro 7 / Kotlin 2.3 reality.
11. **Backend quality cleanup** — Remove the detekt baseline once all findings are resolved in-tree.

---

## Dependency Order / Sequencing Recommendation

```
1. [Tier 1] Commit B — Password 8→12 (Units 2+3+spec-sync)          ← API contract change, atomic
2. [Tier 1] Commit C — Authorization BDD (Unit 4)                    ← asserts Commit B's security change
3. [Tier 1] Commit D — LinkedIn signer guard (Unit 6)                ← touches shared BDD config with C
4. [Tier 2] Commit A — Consent/a11y/SEO (Unit 1, staged slice first)
5. [Tier 2] Commit E — Ideas formatting + detekt baseline (Units 5+11)
6. [Tier 2] Commit F — Waitlist rate-limit E2E (Unit 7)
7. [Tier 2] Commit G — Gradle license enforcement (Unit 8)
8. [Tier 3] Commit H — Compliance/retention/security docs (Unit 9)
9. [Tier 3] Commit I — Repo config + dev docs + openspec config (Unit 10)
```

**Rationale:**
- Units 2 and 3 **must be one commit** — the backend now rejects <12-char passwords while the frontend
  register/reset flows and the e2e/specs reference the old minimum; any split leaves the tree broken
  (frontend register currently submits 8-char passwords that the backend 422s).
- Unit 4's new feature file asserts the SEC-001 change (Unit 3) — commit it immediately after B, or
  fold into the same branch as a follow-up commit.
- Unit 6 and Unit 4 both edit BDD config files (`BddTestProperties.kt`, `CucumberSpringConfiguration.kt`,
  `application.properties`) — sequence them to avoid same-file conflicts; D before C is fine (C only
  reads the state-signing secret via existing property).
- Unit 11 depends on Unit 5's formatting normalization (the likely reason the baseline can be
  deleted) — commit together and verify `just backend-lint` passes without the baseline.
- Units 9/10 docs reference implementation that must exist: ADR-0012 claims `just licence-check` in
  `ci-local` (verified present in justfile), the audit report claims the SEC-001/SEC-002 fixes (in
  Units 3/6), retention docs claim the governance API. Land docs after their commits.
- The already-staged consent slice (4 paths in the index) is behavior-changing — commit it as its own
  first commit before touching other marketing files to avoid mixing staged/unstaged intent.

---

## Suspicious or Incomplete Changes

1. **Spanish i18n stale (CONFIRMED, must fix):**
   - `apps/web/app/src/shared/i18n/locales/es/auth.ts:28` — `passwordPlaceholder: 'Al menos 8 caracteres'` (file NOT in the modified list at all).
   - `apps/web/app/src/shared/i18n/locales/es/passwordRecovery.ts:18` — `passwordTooShort: 'La contraseña debe tener al menos 8 caracteres.'` (file partially updated: `passwordPolicy` is 12, the error string is still 8).
   - English equivalents were updated; the Spanish side was missed.
2. **`registerSchema` has no min-length rule** — `schemas.ts:18-27` only enforces `min(1)` on register password while `resetPasswordSchema` got `min(12)`. The register form (`RegisterForm.vue`) will submit short passwords and surface a backend 422 instead of inline validation. Decide: bump register schema to 12 for parity.
3. **Index is half-staged** — `ConsentBanner.astro` is `MM` (staged + unstaged); `ConsentBanner.test.ts`, `CookieSettingsLink.astro`, and the `consent.spec.ts` rename are staged-only. Any `git commit -am`-style command would silently mix unrelated units. Handle the staged slice explicitly.
4. **Composer i18n without UI** — `en/composer.ts` / `es/composer.ts` add ~75 hashtags/AI strings each, but no consuming component is in this diff. `CreatePostModal.vue` (which consumes `resetAt`/`upgradeOptions`) is already committed. This looks like i18n-ahead-of-UI; safe to commit as groundwork but flag as WIP if the UI commit is missing.
5. **Mixed concerns in Unit 8** — `libs.versions.toml` carries license-report addition AND unrelated dependency bumps (awsS3 2.20.15→2.50.1 — a major jump; jackson 2.22.1; springdoc 3.0.3). Consider splitting "license enforcement" from "dependency upgrades" into two commits, and verify the awsS3 bump against `backend-build`.
6. **`HmacOAuthStateSigner` fail-fast broadness** — the guard rejects any secret starting with `CHANGE_ME`/`changeme`/`placeholder`/`test-` (case-insensitive). Any dev/prod `.env` currently holding `SMP_LINKEDIN_STATE_SIGNING_SECRET` starting with `test-` will break app startup. Confirm real secrets are strong before this lands.
7. **`seedPublishedPublication` usage** — added to `BddDatabaseSupport` and consumed by `AnalyticsBddSteps.kt` (existing, not in this diff). Verify the analytics BDD features actually run green with it (or it is dead seed code).
8. **Docs claiming implementation that must exist** — `docs/retention-framework-acceptance-criteria.md` claims a governance retention-rules API (`POST /api/governance/retention/rules`, `retention_periods` table). If that API is not in this working tree or already committed, the doc overstates reality; validate before publishing.
9. **`WaitlistDistributedRateLimitE2ETest` is heavy** — spins two full Spring Boot replicas + Testcontainers Postgres (`@Tag("postgres")`). Slow and Docker-dependent; confirm it passes via `just backend-test-postgres` (requires `SMP_DB_TEST_PASSWORD`, Docker).
10. **`KotlinLibraryPlugin` kotlin-bom** — adds `kotlin-bom` platform to every library module; verify shared libs still compile (`just backend-build`).
11. **Detekt baseline deletion** — if any suppressed finding resurfaced, `just backend-lint` fails; the ideas formatting churn (Unit 5) is the presumed fix — commit both together.

---

## TDD Regression-Test Requirements

**Already written in the working tree (validate, don't duplicate):**

| Change | Test | Where | Runner |
|---|---|---|---|
| Password 11 rejected / 12 accepted | Unit: `ResetPasswordHandlerTest` 11-char test; BDD: `registration.feature` 2 scenarios; Integration: `LocalAuthEndpointIntegrationTest` passwords; App: `schemas.test.ts`, `registration.spec.ts` | server + app | `just backend-test-fast`, `just backend-bdd-fast`, `just backend-test-postgres`, `pnpm --filter app test:run` |
| SEC-001 endpoint auth | `security-endpoint-authorization.feature` (5 scenarios, `@security @smoke @fast`) | server | `just backend-bdd-fast` |
| SEC-002 placeholder secret guard | `HmacOAuthStateSignerTest` 5 new tests (4 reject + 1 accept) | server | `just backend-test-fast` |
| Consent banner contrast/theme | `ConsentBanner.test.ts`, relocated `consent.spec.ts` | marketing | `just frontend-test`, `just frontend-test-e2e` |
| A11y (reduced-motion axe) | `accessibility.spec.ts` | marketing | `just frontend-test-e2e` |
| Distributed waitlist rate limit | `WaitlistDistributedRateLimitE2ETest` | server | `just backend-test-postgres` |
| Registration module guard flake | `module-relocation.spec.ts` (20s timeout) | app | `pnpm --filter app test:run` |

**Gaps to write during implementation (TDD):**
- A regression test asserting the ES password messages say "12" (or better: a single source-of-truth for the minimum that both locales reference) — currently untested and wrong.
- Decide + test register-form parity: if `registerSchema` is bumped to `min(12)`, add a schema test mirroring the reset-schema one.
- A test (or manual verification note) that `HmacOAuthStateSigner` still accepts the BDD/test secrets (`bdd-…`, `smp-…`) — currently only covered implicitly by the suite booting.

---

## Verification Commands (just / per project — verified to exist)

**Backend (Units 3, 4, 5, 6, 7, 8, 11):**
- `just backend-test-fast` — unit + handler tests
- `just backend-bdd-fast` — fast BDD suite (security feature, registration feature)
- `just backend-test-postgres` — Postgres integration (waitlist E2E, auth integration)
- `just backend-bdd-postgres` — Postgres BDD (needs `just infra-up` first)
- `just backend-lint` — detekt (must pass WITHOUT the deleted baseline)
- `just backend-check` — detekt + tests, excludes BDD suites
- `just backend-build` — compile/package (validates awsS3/jackson/kotlin-bom bumps)
- `just licence-check` — dependency license gate (in `ci-local`; ADR-0012 claim)

**Frontend (Unit 1 marketing, Unit 2 app):**
- `just frontend-test` — marketing Vitest
- `just frontend-test-e2e` — marketing Playwright (consent + accessibility specs)
- `just frontend-check` — Astro type/content checks
- `just frontend-lint` — Biome (marketing + app via `just`)
- `pnpm --filter app test:run` — app Vitest (schemas, module-relocation)
- `pnpm --filter app type-check` — vue-tsc
- `pnpm --filter app test:e2e:scheduler` — app registration e2e

**Full gates:**
- `just ci-local` — fast CI (no E2E, no Postgres BDD); includes `just licence-check`
- `just ci` / `just ci-full` — full CI (needs `just infra-up` for Postgres BDD)

---

## Recommended Commit Boundaries

| # | Commit | Units | Conventional message (suggested) |
|---|---|---|---|
| A1 | Staged consent slice (4 index paths) | 1 | `fix(marketing): always-dark consent banner with WCAG-AA contrast` |
| A2 | Marketing a11y/SEO (remaining 12) | 1 | `feat(marketing): a11y focus management, robots/sitemap, reduced-motion a11y test` |
| B | Password 8→12 (backend + app + specs, incl. ES i18n fix) | 2+3+specs | `feat(identity): enforce ASVS L2 12-char password minimum (SEC-009)` |
| C | Authorization BDD (SEC-001 coverage) | 4 | `test(smp): endpoint authorization BDD coverage (SEC-001)` |
| D | LinkedIn signer guard + BDD config support | 6 | `fix(publishing): fail fast on placeholder OAuth state secrets (SEC-002)` |
| E | Ideas formatting + detekt baseline removal | 5+11 | `refactor(ideas): formatting normalization; drop detekt baseline` |
| F | Waitlist distributed rate-limit E2E | 7 | `test(smp): distributed waitlist rate-limit across replicas` |
| G | Gradle license enforcement (deps separate if preferred) | 8 | `build: enforce AGPL-compatible dependency licenses; bump deps` |
| H | Compliance/retention/security docs | 9 | `docs(compliance): retention framework, legal baseline, audit report` |
| I | Repo config + dev docs + openspec config | 10 | `chore: repo tooling, PR template, docs refresh (Astro 7, Kotlin 2.3)` |

Each commit must pass its unit-level verification commands before the next; `just ci-local` as the
final gate after I.
