# Design: Working Tree Remediation — 97 Paths, 11 Work Units

## MVP Decision Update — F7.1 Deferred

Redis and distributed rate-limit storage are explicitly out of scope for the MVP. The accepted
implementation remains a bounded, per-JVM Caffeine cache in
`shared/shield/ratelimit/.../Bucket4jRateLimiter.kt`: the adapter creates and consumes Bucket4j
buckets from the local Caffeine cache, so bucket state is not shared between replicas.

SMP production configuration confirms the safe default in
`server/smp/src/main/resources/application.yaml:35-39`: waitlist limiting is
`SMP_WAITLIST_RATE_LIMIT_ENABLED:false`, with DALLAY-512 and DALLAY-513 documented as blockers
to safe multi-replica enablement. Operators can opt in explicitly, but that opt-in is still
per-instance. F7.1 is cancelled/deferred rather than failed, and the untracked distributed E2E
test is removed because it tests a capability the MVP deliberately does not provide.

## Technical Approach

Land the 97 uncommitted paths as 11 unit-scoped commits (A1, A2, B–I), each verified by in-tree tests before the next. Backend honors hex dependency (domain ← application ← infrastructure) and CQRS handler patterns; frontend uses strict TS with existing Zod/i18n conventions. All code exists in the tree — this design validates it against the code, closes confirmed gaps (stale ES copy, `registerSchema` parity), and resolves the SEC-002 test-secret conflict. Specs: `specs/` (8 deltas) + proposal + exploration.

## Architecture Decisions

| # | Slice | Decision | Rationale |
|---|-------|----------|-----------|
| B1 | B | `MIN_PASSWORD_LENGTH = 12` stays `private const val` in both companion objects (`LocalAuthHandlers.kt:239`, `ResetPasswordHandler.kt:100`); no shared `PasswordPolicy` extraction | Matches the existing two-constant pattern; extraction is out of diff scope |
| B2 | B | `RegisterUserRequest.password`: `@Size(min=12,max=128)` + `@Schema(minLength=12)`; `ResetPasswordRequest.newPassword`: OpenAPI `minLength=12` only, no `@Size` (verified) | Register validated by bean validation; reset by handler `validatePassword` → `PasswordRecoveryPasswordException` (400). No behavior change beyond diff intent |
| B3 | B | **`registerSchema` gets `min(12, 'passwordTooShort')` via `.extend()`; `authCredentialsSchema` stays `min(1)`** | Gap #2 (inline parity, SEC-009). Login must not enforce policy — legacy creds still sign in. Consequence: 4 existing `schemas.test.ts` register cases use 11-char `'password123'` and WILL fail — update fixtures to 12+ chars, add 11-char rejection mirror |
| B4 | B | ES copy: **test assertion, not shared constant** — new `password-policy.test.ts` imports `messages_es`/`messages_en` from `shared/i18n/index.ts`, asserts `auth.passwordPlaceholder`, `passwordRecovery.passwordPolicy`/`passwordTooShort` contain `"12"` and not `"8"` | Locale files are flat `as const` objects; a shared constant would refactor all locales + consumers. Assertion is cheap, matches `i18n-keys.test.ts` precedent |
| C | C | Default-deny: `/api/media/proxy` removed from GET `permitAll()` (verified absent; remaining GET list: health/** + prometheus + capabilities/public + media assets preview/content). `.anyExchange().authenticated()` unchanged | SEC-001. `security-endpoint-authorization.feature` (`@security @smoke @fast`, 5 scenarios) proves it: 401 on proxy/assets/workspaces; 200 only on health + capabilities/public |
| D1 | D | Guard stays **global**: `init` `require`s non-blank + no case-insensitive `startsWith` of `[CHANGE_ME, change_me, changeme, placeholder, test-]` → `IllegalArgumentException` → startup aborts | Deterministic, env-independent; no way to ship a guessable secret |
| D2 | D | **Conflict resolved — keep `test-` in the guard.** BDD secrets already use `bdd-`/`smp-` prefixes (verified): `bdd-test-oauth-state-signing-secret-32b` (BddTestProperties:13), `smp-integration-test-…` (`application.properties:21`), `smp-e2e-test-…` (waitlist E2E:122). Only `test-` value is `LINKEDIN_CLIENT_ID=test-client-id`, which the guard never checks | Prod-scoping the guard weakens the control and makes tests non-deterministic; test-secret renaming is zero-risk and already done |
| D3 | D | Add 2 unit tests to `HmacOAuthStateSignerTest`: `test-` rejected; `bdd-`/`smp-` accepted | Current 11 tests miss `test-` reject and BDD-secret accept — lock the contract explicitly, not via suite boot |
| D4 | D | Keep `@ConditionalOnProperty(name=["publishing.linkedin.client-id"], matchIfMissing=false)` on `oauthStateSigner` (verified) | Signer active only when LinkedIn configured; BDD fast sets all four LinkedIn props; Postgres BDD inherits secret from test `application.properties:21` |
| A1 | A | Banner: fixed light-on-dark hex `#1a1a1a`/`#333`/`#fff`/`#a3a3a3` (5.0:1)/`#0ea5e9` (6.5:1) with contrast comments; buttons `var(--color-surface,#333)` + fallbacks | `--color-background` doesn't exist in marketing theme; banner stays dark in both themes |
| A2 | A | Focus mgmt: `tabindex="-1"` on `<div id="main-content">` in all 6 pages (verified) | Skip-link/programmatic-focus target without extra tab stop |
| A3 | A | robots/sitemap as Astro endpoint routes: `robots.txt.ts` (text/plain + Sitemap line), `sitemap.xml.ts` (application/xml, 6 routes × en/es) | Static SSG; `Layout.astro` emits `index,follow,…` vs `noindex,…` via `noindex` prop |
| A4 | A | `test.use({ contextOptions: { reducedMotion: 'reduce' } })` in `accessibility.spec.ts` (verified + commented) | Direct `test.use({ reducedMotion })` is silently dropped by Playwright 1.62; contextOptions reliable + real WCAG 2.3.3 scenario |
| E1 | E | Normalize ideas via Spotless (format-only diff + `requireConnectedAccountId` rename); verify `just backend-lint` green | Formatting churn is the presumed enabler for baseline cleanup |
| E2 | E | **Baseline discovery:** `config/detekt/baseline.xml` (stub) is ALREADY deleted; the ACTIVE baseline is root `server/smp/detekt-baseline.xml` (33KB, ~227 suppressions, auto-discovered — no `.kts` references it). Run `backend-lint` without the stub; DO NOT delete the real baseline here | Exploration misidentified the file; dropping 227 live suppressions is high-risk, out of scope |
| F | F | **Deferred outside MVP:** do not add Redis or distributed bucket wiring. Retain bounded per-JVM Caffeine buckets and keep SMP waitlist limiting default OFF; multi-replica enablement waits for DALLAY-512/DALLAY-513 | The two-replica test assumed a missing capability and is removed. A future change may add shared-window coverage after the blockers are resolved |
| G1 | G | **Split G:** G1 license enforcement, G2 dependency bumps | Policy vs version churn (awsS3 major 2.20.15→2.50.1) must not share a commit; G2 gated by `backend-build` |
| G2 | G | jk1 `gradle-license-report` 2.9 via `LicenceReportPlugin` (id `com.profiletailors.legal.licence-report`): JSON+Text renderers, `LicenseBundleNormalizer`, runtime+compile classpaths, `doLast` fail on `BLOCKED_LICENCES` (GPL-2.0 variants) | AGPL gate; `just licence-check` = pnpm scan + `:server:smp:generateLicenseReport` (justfile:339) |
| G3 | G | G2 bumps (verified in-tree): `awsS3=2.50.1`, `springdoc=3.0.3`, `jackson-module-kotlin 2.22.1` (smp build:110), `kotlin-bom` in `KotlinLibraryPlugin` | Compile-gated; mixed Jackson 2/3 is pre-existing, not introduced here |
| H/I | H/I | Docs land only after referenced implementation commits; validation checklist below | ADR-0012 `licence-check` in `ci-local` verified (justfile:360); audit report claims SEC-001/SEC-002 → after C/D |

## Data Flow — Slice F (Deferred Follow-up)

```
clientA ── POST /api/waitlists/*/entries ──> replicaA ──> local Caffeine bucket
clientB ── POST /api/waitlists/*/entries ──> replicaB ──> local Caffeine bucket

The buckets above are intentionally independent in the MVP. A future DALLAY-512 follow-up may
replace them with a shared store; DALLAY-513 must also establish trusted client identity before
multi-replica waitlist limiting is enabled.
```

## H/I — Docs Validation Checklist

**Before H:** verify `POST /api/governance/retention/rules` + `retention_periods` table exist (or soften docs); audit report lands after C+D; `seedPublishedPublication` — confirm analytics BDD green, else mark dead seed.
**Before I:** README Astro 7 / Kotlin 2.3 match `package.json` + `libs.versions.toml:2` (2.3.21 verified); every documented `just` recipe exists; PR template + `.vscode/settings.json` are new — no claims; `openspec/config.yaml` strict-TDD already in-tree.

## File Changes (per slice)

| Slice | Files | Action |
|-------|-------|--------|
| B | `LocalAuthHandlers.kt`, `ResetPasswordHandler.kt`, `LocalAuthController.kt` | Modified (12-char, in tree) |
| B | `schemas.ts` (register min 12), `schemas.test.ts` (fixtures + mirror), `ResetPasswordView.vue` (minlength=12), `en/es` `auth.ts` + `passwordRecovery.ts` | Modified (ES fix + register parity are the new bits) |
| B | `password-policy.test.ts` | Create |
| C | `IdentitySecurityConfiguration.kt`, `security-endpoint-authorization.feature`, `SecurityAuthorizationBddSteps.kt`, `AuthorizationBddSteps.kt`, `BddDatabaseSupport.kt` | Modified/Create (in tree) |
| D | `HmacOAuthStateSigner.kt`, `LinkedInPublishingAdapters.kt`, `HmacOAuthStateSignerTest.kt` (+2), `BddTestProperties.kt`, `CucumberSpringConfiguration.kt`, test `application.properties` | Modified (in tree) |
| A | `ConsentBanner.astro`, `Layout.astro`, `global.css`, 6 legal pages, `accessibility.spec.ts`, relocated `consent.spec.ts`, `robots.txt.ts`, `sitemap.xml.ts` | Modified/Create (in tree) |
| E | 5 ideas files (Spotless + rename); `config/detekt/baseline.xml` | Modified; Delete (stub, already gone) |
| F | `WaitlistDistributedRateLimitE2ETest.kt` | **Removed as deferred:** no Redis/distributed implementation is added in MVP |
| G | `build-logic/build.gradle.kts`, `LicenceReportPlugin.kt`, `libs.versions.toml`, `server/smp/build.gradle.kts`, `KotlinLibraryPlugin.kt` | Modified/Create (in tree) |
| H/I | `docs/**`, `README.md`, `package.json`, PR template, `.vscode/settings.json`, `openspec/config.yaml` | Modified/Create (in tree) |

## Testing Strategy

| Slice | What | Runner |
|-------|------|--------|
| B | `ResetPasswordHandlerTest` 11-char reject; `registration.feature` 11/12; `LocalAuthEndpointIntegrationTest`; `schemas.test.ts` 11/12 + mirror; `password-policy.test.ts` | `backend-test-fast`, `backend-bdd-fast`, `backend-test-postgres`, `pnpm --filter app test:run` |
| C | security-endpoint-authorization.feature (5 scenarios) | `backend-bdd-fast` |
| D | SignerTest: +`test-` reject, +`bdd-`/`smp-` accept; suite boot | `backend-test-fast`, `backend-bdd-fast` |
| A | `ConsentBanner.test.ts`, `consent.spec.ts`, `accessibility.spec.ts` | `frontend-test`, `frontend-test-e2e` |
| E | `backend-lint` green without stub | `just backend-lint` |
| F | MVP posture: bounded per-JVM limiter with SMP waitlist default OFF; distributed two-replica behavior is follow-up only | Existing configuration/unit evidence; no distributed E2E test in MVP |
| G | G1: `licence-check` fails on GPL-2.0; G2: `backend-build` | `just licence-check`, `just backend-build` |
| H/I | Final gate | `just ci-local` |

## Commit Boundary Mapping

A1 staged consent → A2 marketing a11y/SEO → **B** password 8→12 → **C** authz BDD → **D** signer guard → **E** ideas fmt + stub baseline → **F** waitlist decision/defer → **G1** license enforcement → **G2** dependency bumps → **H** compliance docs → **I** repo config. (B, C, D = Tier 1 security, first.)

## Migration / Rollout

No migrations. Atomic slices; revert via `git revert`/reset; stage A1 separately (index is half-staged — `ConsentBanner.astro` is `MM`). **Pre-D gate:** confirm real `SMP_LINKEDIN_STATE_SIGNING_SECRET` doesn't start with a blocked prefix.

## Risks

| Risk | Mitigation |
|------|-----------|
| awsS3 2.50.1 major bump breaks compile (G2) | `backend-build` gate before G2 commit |
| SEC-002 guard rejects a real dev `.env` secret | Pre-D verification; document `bdd-`/`smp-` test-secret convention |
| registerSchema min(12) breaks 4 existing tests | Update fixtures to 12+ chars in the same commit (TDD) |
| Retention docs overstate governance API (H) | Validate API existence before docs land |
| Waitlist multi-replica behavior is not globally enforced (F) | Accepted MVP deferral; keep limiter default OFF and resolve DALLAY-512/DALLAY-513 before enablement |
| Licence gate string-matches JSON (false positive risk) | Inspect `dependency-licence.txt` on failure |

## Open Questions

- [ ] Delete the REAL baseline (`server/smp/detekt-baseline.xml`, ~227 suppressions) in a follow-up? Spec intent says "no baseline", but the diff only deletes the stub.
- [ ] Does the retention governance API exist? Blocks H.
- [ ] `seedPublishedPublication` — analytics BDD green, or dead seed code?
- [ ] Future follow-up: implement and verify a shared waitlist bucket after DALLAY-512/DALLAY-513; not an MVP gate.
