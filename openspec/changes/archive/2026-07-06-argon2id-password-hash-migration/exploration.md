## Exploration: Argon2id password hash migration

### Current State
Local email/password authentication still uses `BCryptPasswordHasher` as the active `PasswordHasher` through `IdentityBootstrapConfiguration.passwordHasher()`. New registrations hash passwords with BCrypt and login verifies against the stored `password_hash` from `local_password_credentials`.

The `PasswordHasher` interface already includes `algorithm: String`, and the archived `auth-security-hardening` SDD change explicitly deferred Argon2id implementation while documenting rehash-on-login as the intended migration strategy.

Today the credential persistence model stores only `password_hash`; there is no persisted `password_algorithm` column. The R2DBC gateway inserts `principal_id` and `password_hash` only, and login reads only the hash. BCrypt malformed hashes already fail closed because `BCryptPasswordHasher.matches()` catches `IllegalArgumentException` and returns `false`.

### Affected Areas
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/BCryptPasswordHasher.kt` — current active password hasher and malformed-hash behavior baseline.
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/PasswordHasher.kt` — existing algorithm-aware interface to preserve or extend.
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/IdentityBootstrapConfiguration.kt` — active bean wiring must switch new hashing to Argon2id.
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt` — registration hashes new passwords; login is the right place for opportunistic rehash on successful BCrypt authentication.
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalPasswordCredentialGateway.kt` — current gateway contract lacks update/rehash capability and any algorithm persistence.
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/R2dbcLocalPasswordCredentialGateway.kt` — DB reads/writes currently only use `password_hash`; may need update path and optional schema support.
- `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt` — existing auth tests already cover registration/login flows and bcrypt algorithm baseline; best place for regression-first TDD.
- `openspec/changes/archive/2026-06-20-auth-security-hardening/{proposal.md,design.md,specs/credentials/spec.md}` — prior SDD artifacts already define Argon2id as deferred work and describe rehash-on-login as the intended migration direction.

### Approaches
1. **Infer algorithm from hash format** — keep schema unchanged, detect BCrypt vs Argon2id from the stored hash prefix/structure, and rehash to Argon2id after successful BCrypt login.
   - Pros: Minimal DB impact, fastest rollout, no data migration required, rollback is simpler.
   - Cons: Algorithm detection becomes format-coupled, future multi-algorithm support is less explicit, gateway still needs update capability.
   - Effort: Medium

2. **Persist explicit password algorithm** — add an algorithm column (or equivalent metadata), store `argon2id` for new hashes, preserve `bcrypt` for legacy rows, and update both hash and algorithm on successful login.
   - Pros: Explicit model, safer long-term multi-algorithm support, easier analytics/operational visibility.
   - Cons: Requires schema migration/backfill strategy, more moving parts, rollback needs DB compatibility planning.
   - Effort: Medium/High

### Recommendation
Prefer **Approach 2: persist explicit password algorithm**, while still supporting safe hash-format inference as a defensive fallback for legacy or malformed data.

Why: the codebase already introduced `PasswordHasher.algorithm` specifically for multi-algorithm evolution, and this is a security-sensitive path where explicitness beats clever parsing. Storing the active algorithm makes rehash intent auditable, reduces ambiguity if encoder defaults ever change, and gives us a clean contract for future migrations. We should still fail closed and optionally infer from the hash string when metadata is absent, so rollback and partial deployments remain safe.

### Risks
- Schema drift risk if application code expects `password_algorithm` before the database change is deployed everywhere.
- Login regression risk if rehash-on-login is wired before preserving BCrypt compatibility.
- Silent malformed-hash behavior could regress into 500s if Argon2 verification exceptions are not handled symmetrically.
- Rollback complexity increases if new writes become Argon2id while an older app version only understands BCrypt.

### Ready for Proposal
Yes — the codebase has enough evidence to write a proposal. The proposal should lock the migration strategy, rollout/rollback behavior, and whether explicit algorithm persistence is mandatory or optional with inference fallback.
