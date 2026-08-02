# Tasks: Working Tree Remediation — 97 Paths, 11 Work Units

## Approved MVP Decision — Slice F

Distributed waitlist rate limiting is explicitly **deferred out of MVP**. Do not add Redis,
Bucket4j distributed storage, or another multi-replica rate-limit implementation in this change.
The accepted behavior is bounded per-JVM Caffeine bucket state, with
`application.rate-limit.waitlist.enabled` defaulting to `false` in SMP through
`SMP_WAITLIST_RATE_LIMIT_ENABLED:false`. Multi-replica enablement remains deferred until
DALLAY-512 (distributed bucket backend) and DALLAY-513 (trusted proxy/client identity) are
resolved. F7.1 is therefore **cancelled/deferred, not failed**.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~2,800–3,500 (97 paths; docs/tests/formatting-heavy) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | A1 → B → C → D → A2 → E → F → G1 → G2 → H → I |
| Delivery strategy | ask-on-risk |
| Chain strategy | feature-branch-chain (linear local commits, no pushes) |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Commit | Base |
|------|------|--------|------|
| 1 | Staged consent slice | A1 | branch head |
| 2 | Password 8→12 (SEC-009) | B | head |
| 3 | Authz BDD (SEC-001) | C | B |
| 4 | Signer guard (SEC-002) | D | C |
| 5 | Marketing a11y/SEO | A2 | D |
| 6 | Ideas fmt + stub baseline | E | A2 |
| 7 | Waitlist rate-limit E2E | F | E |
| 8 | License enforcement | G1 | F |
| 9 | Dependency bumps | G2 | G1 |
| 10 | Compliance docs | H | G2 |
| 11 | Repo config + docs | I | H |

## Phase 1: A1 — Staged Consent Commit

- [x] A1.1 Commit 4 staged index paths only (ConsentBanner.astro staged half, ConsentBanner.test.ts, CookieSettingsLink.astro, consent.spec.ts rename e2e/→tests/e2e/); leave unstaged MM half for A2. RED: in-tree ConsentBanner.test.ts + consent.spec.ts already assert WCAG-AA palette (`#1a1a1a`/`#0ea5e9`/`#a3a3a3`/`#fff`/`#333`) — no new code. Verify: `just frontend-test`, `just frontend-test-e2e`. Commit: `fix(marketing): always-dark consent banner with WCAG-AA contrast`.

## Phase 2: B — Password 8→12 (SEC-009)

- [x] B2.1 LocalAuthHandlers.kt:239 + ResetPasswordHandler.kt:100 `MIN_PASSWORD_LENGTH=12`; LocalAuthController.kt `@Size(min=12,max=128)`+`@Schema(minLength=12)`. RED: ResetPasswordHandlerTest 11-char reject (in-tree). Verify: `just backend-test-fast`.
- [x] B2.2 BDD registration.feature 11-rejected/12-accepted (in-tree). Verify: `just backend-bdd-fast`.
- [x] B2.3 ResetPasswordView.vue `minlength=12`; schemas.ts resetPasswordSchema min(12) (in-tree). Verify: `pnpm --filter app test:run`.
- [x] B2.4 registerSchema parity `.extend()` min(12,'passwordTooShort'); fix 4 schemas.test.ts fixtures `'password123'`→12+; add 11-char mirror. Verify: `pnpm --filter app test:run`.
- [x] B2.5 ES i18n: es/auth.ts:28 placeholder + es/passwordRecovery.ts:18 `passwordTooShort` → "12"; create password-policy.test.ts asserting en/es contain "12" not "8". Verify: `pnpm --filter app test:run`.
- [x] B2.6 Spec-sync: registration.spec.ts (in-tree); openspec/specs/e2e login-flow.md + register-flow.md 8→12. Verify: `just frontend-test`. Commit: `feat(identity): enforce ASVS L2 12-char password minimum (SEC-009)`.

## Phase 3: C — Authorization BDD (SEC-001)

- [x] C3.1 New security-endpoint-authorization.feature (@security @smoke @fast, 5 scenarios: 401 proxy/assets/workspaces, 200 health+capabilities/public), SecurityAuthorizationBddSteps.kt, AuthorizationBddSteps.kt register step, BddDatabaseSupport.kt seeding — in-tree, no new code unless red. Verify: `just backend-bdd-fast`. Commit: `test(smp): endpoint authorization BDD coverage (SEC-001)`.

## Phase 4: D — LinkedIn Signer Guard (SEC-002)

- [x] D4.1 RED first: +2 HmacOAuthStateSignerTest tests (`test-` rejected; `bdd-`/`smp-` accepted) → GREEN via in-tree guard (init require non-blank + case-insensitive reject CHANGE_ME/change_me/changeme/placeholder/test-). Verify: `just backend-test-fast`.
- [x] D4.2 BDD boot: BddTestProperties.kt `bdd-test-oauth-state-signing-secret-32b`, CucumberSpringConfiguration.kt property, application.properties:21 `smp-integration-test-…` (design D2 prefix-safe); keep `@ConditionalOnProperty` (D4). Verify: `just backend-bdd-fast`.
- [x] D4.3 Pre-D gate: real `SMP_LINKEDIN_STATE_SIGNING_SECRET` must not start with a blocked prefix. Commit: `fix(publishing): fail fast on placeholder OAuth state secrets (SEC-002)`.

## Phase 5: A2 — Marketing A11y/SEO

- [x] A5.1 Layout.astro robots meta+og-image, global.css body token, `tabindex="-1"` on `#main-content` in 6 pages (_AcceptableUse, _Accessibility, _CookiePolicy, _Home, _PrivacyPolicy, _Terms) — in-tree. Verify: `just frontend-check`.
- [x] A5.2 robots.txt.ts + sitemap.xml.ts routes (new, in-tree). Verify: `just frontend-test-e2e`.
- [x] A5.3 accessibility.spec.ts reducedMotion via `contextOptions` (in-tree fix). Verify: `just frontend-test-e2e`.
- [x] A5.4 marketing/README.md (Astro 6→7, legal baseline). Verify: `just frontend-test`. Commit: `feat(marketing): a11y focus management, robots/sitemap, reduced-motion test`.

## Phase 6: E — Ideas Formatting + Stub Baseline

- [x] E6.1 Spotless-format IdeasApi.kt, IdeasCommandHandlers.kt, IdeaModels.kt, IdeasController.kt, R2dbcIdeaRepositories.kt; rename `requireConnectedSocialAccountId`→`requireConnectedAccountId`. Verify: `just backend-lint`, `just backend-test-fast`.
- [x] E6.2 Confirm config/detekt/baseline.xml stub deleted; DO NOT touch active server/smp/detekt-baseline.xml. Verify: `just backend-lint`. Commit: `refactor(ideas): formatting normalization; drop detekt baseline`.

## Phase 7: F — Waitlist Distributed Rate-Limit E2E (Deferred)

- [x] F7.1 **Cancel/defer the distributed waitlist E2E outside MVP.** Do not add Redis or distributed rate-limit wiring. Remove the untracked `server/smp/src/test/kotlin/com/profiletailors/smp/leadcapture/integration/WaitlistDistributedRateLimitE2ETest.kt` because it tests the deliberately deferred capability. Evidence: `Bucket4jRateLimiter.kt` uses a bounded per-JVM Caffeine cache; `RateLimitProperties` has no distributed store field; the shared rate-limit module has no Redis/bucket4j-redis dependency; and `application.yaml:35-39` defaults the SMP waitlist limiter to `${SMP_WAITLIST_RATE_LIMIT_ENABLED:false}`. Follow-up coverage waits for DALLAY-512/DALLAY-513.

### Slice F Follow-up Scenarios — Preserved, Not Current MVP Requirements

After DALLAY-512 and DALLAY-513 are resolved, a future change MAY add failing-first coverage for:

- A burst consumed on replica A being rejected on replica B by one shared waitlist window.
- A shared waitlist window resetting for both replicas after the configured period.

These scenarios are intentionally not part of the current acceptance gate.

## Phase 8: G — License Enforcement (G1) + Dependency Bumps (G2)

- [x] G8.1 G1: LicenceReportPlugin.kt (new, fail BLOCKED_LICENCES GPL-2.0), build-logic/build.gradle.kts registration, server/smp/build.gradle.kts apply, libs.versions.toml licenceReport=2.9. Verify: `just licence-check`, `just backend-build`. Commit: `build: enforce AGPL-compatible dependency licenses`.
- [x] G8.2 G2 (separate commit): libs.versions.toml springdoc 3.0.3, awsS3 2.50.1; smp build.gradle.kts jackson 2.22.1; KotlinLibraryPlugin.kt kotlin-bom. Verify: `just backend-build`, `just backend-test-fast`. Commit: `build: bump springdoc, awsS3, jackson, kotlin-bom`.

## Phase 9: H — Compliance/Retention/Security Docs

- [x] H9.1 GATE: verify `POST /api/governance/retention/rules` + `retention_periods` table exist; else soften retention-framework-acceptance-criteria.md claims.
- [x] H9.2 Verify audit-report.md SEC-001/SEC-002 claims match landed fixes (after C/D).
- [x] H9.3 docs/compliance/* (12 incl. agpl-source-offer, contributor map, legal baseline, taxonomy), retention-framework-*.md, consent-management.md.
- [x] H9.4 ADR-0012 Accepted only when licence-check in ci-local (justfile:360) + agpl-source-offer.md exists. Docs only. Commit: `docs(compliance): retention framework, legal baseline, audit report`.

## Phase 10: I — Repo Config + Dev Docs + Openspec Config

- [x] I10.1 README.md, docs/** (12 paths incl. c4×4, gradle, test-tags), server/smp/README.md — verified every documented just recipe exists; Astro 7/Kotlin 2.3.21 match manifests.
- [x] I10.2 package.json → node scripts/*.mjs; .github/pull_request_template.md (new); .vscode/settings.json (new).
- [x] I10.3 openspec/config.yaml strict-TDD runner (in-tree). Validation: focused Slice I manifest/script/recipe checks PASS; `just frontend-check` PASS; `just ci-local` ran and is blocked by 15 pre-existing app test failures. Commit: `chore: repo tooling, PR template, docs refresh (Astro 7, Kotlin 2.3)`.

## Phase 11: Final Review

- [ ] I11.1 Review 11-commit sequence: `git log --oneline`; clean tree between commits; each commit green before next; NO pushes, NO `-am` mixes.
