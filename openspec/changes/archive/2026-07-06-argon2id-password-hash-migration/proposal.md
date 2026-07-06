# Proposal: Argon2id Password Hash Migration

## Intent

Migrate local email/password credential hashing from BCrypt to Argon2id for stronger default password
storage while preserving login compatibility for existing users. This change closes the gap left by
`auth-security-hardening`, which prepared the `PasswordHasher.algorithm` seam but explicitly deferred
Argon2id implementation, migration behavior, and rollback guidance.

## Scope

### In Scope

- Add an Argon2id-backed `PasswordHasher` implementation for local user passwords
- Switch new local password registrations to Argon2id
- Persist password hash algorithm metadata for local password credentials
- Preserve BCrypt login compatibility for existing rows
- Rehash BCrypt credentials to Argon2id after successful login
- Fail closed for malformed or unsupported password hashes without user-visible 500s
- Add focused auth tests for new registration hashing, legacy BCrypt login compatibility, rehash on
  login, and malformed-hash handling
- Document rollout and rollback behavior in SDD artifacts

### Out of Scope

- OAuth/social login credential handling
- Refresh token hashing migration
- API key verifier migration
- Bulk offline password rehashing without plaintext passwords
- Frontend behavior changes

## Approach

Introduce an Argon2id `PasswordHasher` implementation using Spring Security-supported primitives and
make it the default bean for new local password hashing. Extend the local password credential
persistence model to store both `password_hash` and `password_algorithm` so the backend can make
explicit verification decisions instead of relying solely on hash parsing.

During login, the handler will load the stored credential metadata, verify against the appropriate
algorithm, and, when a legacy BCrypt hash succeeds, opportunistically replace the stored hash with a
new Argon2id hash in the same request flow. Defensive inference from the stored hash format will
remain available as a fallback for legacy rows or rollback scenarios where metadata may be absent or
stale. All verification failures, including malformed hashes, MUST fail closed and surface as normal
invalid-credentials outcomes rather than server errors.

## Deployment Context

This change targets the **first production deployment** of the local authentication
system. No version of this code has been deployed to any environment — there are no
production BCrypt hashes, no existing credential rows, and no previous application
binary running in production.

This means the migration is a **clean cutover**: the first deploy will simultaneously
introduce the Argon2id default, the `password_algorithm` metadata column, and the
BCrypt verification fallback. There is no backward-compatible rollout window because
there is no running system to maintain compatibility with.

All backward-compatible design choices (nullable metadata, format-inference fallback,
defensive fail-closed behavior) are kept as production-grade robustness measures, not
as rollout necessities.

## Affected Areas

| Area                                                                                              | Impact   | Description                                                                 |
|---------------------------------------------------------------------------------------------------|----------|-----------------------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/BCryptPasswordHasher.kt` | Modified | Preserve legacy verification behavior and align algorithm semantics         |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/Argon2idPasswordHasher.kt` | New      | Add Argon2id hashing implementation for local passwords                    |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/PasswordHasher.kt`       | Modified | Preserve/clarify algorithm contract for multi-hash support                 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalPasswordCredentialGateway.kt` | Modified | Add algorithm-aware read model and update/rehash capability                |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/R2dbcLocalPasswordCredentialGateway.kt` | Modified | Persist/read `password_algorithm` and support successful-login rehash      |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt`    | Modified | Registration writes Argon2id; login triggers BCrypt-to-Argon2id rehash     |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/IdentityBootstrapConfiguration.kt` | Modified | Wire Argon2id hasher as the active password hasher bean                    |
| `server/smp/src/main/resources/**`                                                                | Modified | Add/adjust database migration for `password_algorithm` metadata            |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt` | Modified | Add regression-first auth flow coverage for migration scenarios            |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/**`                   | Modified | Add integration coverage for persistence/rollback compatibility if needed  |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Deployment pipeline split (schema applies but code deploy is delayed or fails) | Low | Schema column is additive (`ALTER TABLE ADD COLUMN`), harmless to running code; application tolerates both null and present metadata |
| Partial deployment leaves rows without `password_algorithm` metadata | Low | No production data at first deploy; code still supports format-inference fallback for robustness |
| Rehash-on-login introduces login regressions | Medium | Add regression-first tests for BCrypt compatibility before implementation and keep update path narrow |
| Argon2 verification exceptions become 500s | Low | Mirror BCrypt fail-closed behavior and normalize malformed/unsupported hashes to invalid credentials |
| Future binary-only rollback to BCrypt-only code (post-data) | Low | Retain BCrypt verifier path as long as legacy BCrypt rows may exist; document that rollback must be coordinated (code + schema awareness) |

## Rollback Plan

**Context**: This is the first production deployment of local authentication. There is
no previous production binary and no production data. Rollback scenarios are therefore
simple and low-risk.

### Pre-deploy rollback (during development or review)

- Revert the entire changeset as a single commit. No data migration, no compatibility
  concerns — the feature simply does not reach production.

### Post-deploy rollback (after deploy to any environment)

- **Coordinated revert**: Revert the application commit. The old code (without Argon2id)
  simply lacks local authentication entirely — there is no partial-feature state to
  worry about.
- **Schema**: The additive `password_algorithm` column is harmless and can remain.
  If a full schema revert is desired, drop the column via manual SQL — zero data risk
  since no production data exists at first deploy.
- **Data**: If the environment accumulated test/dev rows during a brief deployment
  window, those can be truncated. No production data is at risk.

### Defensive code posture

Despite the clean-cutover context, the implementation keeps BCrypt verification alive
and handles null/missing `password_algorithm` metadata via format inference. These are
production-grade robustness measures (not rollout necessities) that:
- Prevent surprises from direct DB inserts, corrupted rows, or future schema drift
- Ensure fail-closed behavior on malformed/unsupported hashes regardless of context
- Cost nothing to maintain and simplify future algorithm migrations

### What we do NOT need

- No staged rollback with partial binary deployments
- No backward-compatible Argon2id verification in BCrypt-only releases (none exist)
- No hash reverse-migration; Argon2id rows written during a brief deployment are
  trivially replaceable via truncation or coordinated schema revert

## Dependencies

- Spring Security password encoding support for Argon2id in the current backend stack
- Existing local auth flow in `LocalAuthHandlers` and credential persistence gateway
- Database migration support for `local_password_credentials`
- Archived SDD change `2026-06-20-auth-security-hardening` for the original migration seam and intent

## Success Criteria

- [ ] New local password registrations persist Argon2id hashes
- [ ] Local password credentials persist explicit algorithm metadata or equivalent backward-compatible metadata behavior
- [ ] Existing BCrypt users continue to log in successfully
- [ ] Successful BCrypt login rehashes and persists the credential as Argon2id
- [ ] Malformed or unsupported password hashes fail as invalid credentials, not 500s
- [ ] Focused auth tests cover algorithm detection, registration hashing, legacy BCrypt login, and rehash-on-login
- [ ] Focused backend verification passes for the changed auth area
- [ ] SDD artifacts document rollout, rollback, and verification evidence
