# Design: Argon2id Password Hash Migration

## Technical Approach

Implement a dual-algorithm local password verification path that makes Argon2id the default for all
new registrations while keeping BCrypt readable for legacy rows. The implementation will persist
explicit password algorithm metadata in `local_password_credentials`, use that metadata to select
verification behavior, and opportunistically upgrade successful BCrypt logins to Argon2id.

The design follows the existing local auth pattern: `RegisterUserHandler` hashes before persistence,
`LoginUserHandler` verifies through `PasswordHasher`, and persistence flows through
`LocalPasswordCredentialGateway` + `R2dbcLocalPasswordCredentialGateway`. Liquibase YAML changelogs,
not raw SQL files, are the schema mechanism in this codebase.

**Deployment context**: This is the first production deployment of local authentication.
No deployed version exists, no production data exists — the migration is a clean cutover.
All null-safe metadata handling is kept as defensive production-grade practice, not as
a rollout necessity.

## Environment Assumption

**Zero environments have ever applied changelog `003-create-local-password-credentials.yaml`.**  
No production, staging, CI, or development database has run this Liquibase changeset. This means:
- Modifying the changelog in place is safe — no checksum validation failures can occur.
- The schema change is a clean cutover, not a migration on existing data.
- Dev seed data files can be updated without backward-compatibility constraints.

All null-safe metadata handling and defensive code in this design is production-grade practice,
not a rollout necessity.

## Architecture Decisions

### Decision: Persist explicit password algorithm metadata

**Choice**: Add a `password_algorithm` column to `local_password_credentials` and surface it in the
credential gateway read model.

**Alternatives considered**:

- Infer algorithm from hash format only
- Introduce separate credential rows per algorithm version

**Rationale**: The codebase already introduced `PasswordHasher.algorithm` as a seam for
multi-algorithm support. Persisting metadata makes verification explicit, simplifies future
migrations, and gives operational clarity. Format inference remains as a defensive fallback, not the
primary contract.

### Decision: Opportunistic rehash on successful login

**Choice**: Upgrade legacy BCrypt credentials to Argon2id during the successful login flow.

**Alternatives considered**:

- Offline bulk migration
- Force password reset for all legacy users
- Leave legacy BCrypt hashes indefinitely

**Rationale**: Plaintext passwords are only available at login time. Rehash-on-login upgrades active
users without user friction and avoids insecure or impossible bulk conversion attempts.

### Decision: Keep verification fail-closed across both algorithms

**Choice**: Normalize malformed or unsupported hash verification to invalid credentials.

**Alternatives considered**:

- Surface different errors for malformed hashes
- Let encoder exceptions bubble to global exception handling

**Rationale**: `BCryptPasswordHasher` already fails closed by returning `false` on malformed hashes.
Argon2id verification must preserve the same security and UX contract: no credential detail leaks,
no 500s.

### Decision: Explicit dual-bean wiring for algorithm-aware verification

**Choice**: Define two separate `@Bean` methods in `IdentityBootstrapConfiguration`:
one for the default `PasswordHasher` (Argon2id) and one specifically typed as
`BCryptPasswordHasher` for legacy verification. `LoginUserHandler` injects both
but by different types to avoid Spring autowiring ambiguity.

**Alternatives considered**:

- Single `PasswordHasher` bean that delegates internally (Strategy pattern)
- Make `BCryptPasswordHasher` a `@Component` with `@Primary` on Argon2id
- Inject `BCryptPasswordHasher` with `@Qualifier`

**Rationale**:

- `BCryptPasswordHasher` is a concrete class with no Spring annotation; after
  switching the default bean to `Argon2idPasswordHasher`, no bean of type
  `BCryptPasswordHasher` exists for injection.
- Adding a `@Bean fun bcryptPasswordHasher(): BCryptPasswordHasher` alongside
  the primary `@Bean fun passwordHasher(): PasswordHasher` means:
  - `RegisterUserHandler` injects `PasswordHasher` (ambiguity-free — only one
    `PasswordHasher`-typed bean exists: the primary Argon2id one)
  - `LoginUserHandler` injects `passwordHasher: PasswordHasher` (Argon2id) and
    `bcryptPasswordHasher: BCryptPasswordHasher` (by concrete type, no ambiguity)
  - No qualifiers, no `@Primary` annotation needed — Spring resolves by type
    since `BCryptPasswordHasher` and `PasswordHasher` are distinct types.

**Wiring contract**:

```kotlin
// IdentityBootstrapConfiguration
@Bean
fun passwordHasher(): PasswordHasher = Argon2idPasswordHasher()

@Bean
fun bcryptPasswordHasher(): BCryptPasswordHasher = BCryptPasswordHasher()

// LoginUserHandler
internal class LoginUserHandler(
    private val localPasswordCredentialGateway: LocalPasswordCredentialGateway,
    private val passwordHasher: PasswordHasher,                 // Argon2id (primary, unambiguous)
    private val bcryptPasswordHasher: BCryptPasswordHasher,    // BCrypt (by concrete type)
    private val principalIdentityLookup: PrincipalIdentityLookup,
    private val localJwtIssuer: LocalJwtIssuer,
    private val refreshSessionLifecycleService: RefreshSessionLifecycleService,
    private val clock: Clock,
)
```

### Decision: First-deploy clean-cutover schema with defensive metadata handling

**Choice**: Add `password_algorithm` as a nullable column (or NOT NULL with conservative
default `'bcrypt'`) and handle null/missing metadata via format-inference fallback.

**Alternatives considered**:

- Make `password_algorithm` NOT NULL without default, requiring explicit values everywhere
- Skip metadata persistence entirely and rely only on format inference

**Rationale**: Although no production data exists (first deploy), keeping metadata null-safe
is a production-grade practice that:
- Simplifies dev seed data updates and test fixture migration (no need to rewrite every INSERT)
- Prevents surprises from direct DB inserts, corrupted rows, or future schema drift
- Costs virtually nothing and is consistent with the `shouldUpgradeToArgon2id` helper design
- Does NOT represent a rollout necessity — the column could be NOT NULL with default,
  but nullable + inference is strictly more robust for the same implementation cost

## Data Flow

### Registration Flow

```
Client ──POST /api/auth/register──→ LocalAuthController
                                          │
                                          ▼
                              RegisterUserHandler.handle()
                                          │
                                          ▼
                          PasswordHasher.hash(rawPassword)
                                          │
                                          ▼
                 LocalPasswordCredentialGateway.create(principalId, hash, algorithm)
                                          │
                                          ▼
                      Persist `password_hash` + `password_algorithm=argon2id`
```

### Login Flow with Legacy BCrypt Upgrade

```
Client ──POST /api/auth/login──→ LocalAuthController
                                       │
                                       ▼
                            LoginUserHandler.handle()
                                       │
                                       ▼
               LocalPasswordCredentialGateway.findByEmail(email)
                                       │
                                       ▼
            Resolve algorithm metadata (or infer from hash if metadata missing)
                         │                          │
                         │                          └── malformed/unsupported → invalid credentials
                         ▼
              Verify raw password against selected algorithm
                         │
             ┌───────────┴────────────┐
             │                        │
             ▼                        ▼
        verification false       verification true
             │                        │
             ▼                        ▼
   InvalidEmailPasswordException   if legacy bcrypt → rehash to argon2id → persist update
                                              │
                                              ▼
                                      issueAuthSession()
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/Argon2idPasswordHasher.kt` | Create | New Argon2id-backed `PasswordHasher` implementation with fail-closed verify semantics |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/BCryptPasswordHasher.kt` | Modify | Keep legacy verifier path and ensure algorithm naming remains stable |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/PasswordHasher.kt` | Modify | Clarify algorithm contract if needed for algorithm-aware verification |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalPasswordCredentialGateway.kt` | Modify | Extend create/find contracts and add update capability for rehash-on-login |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/R2dbcLocalPasswordCredentialGateway.kt` | Modify | Persist/read `password_algorithm` and update credential on successful BCrypt login |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt` | Modify | Registration writes Argon2id credentials; login upgrades BCrypt credentials opportunistically |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/IdentityBootstrapConfiguration.kt` | Modify | Wire Argon2id as default `PasswordHasher` bean + add `bcryptPasswordHasher` bean for legacy verification |
| `server/smp/src/main/resources/db/changelog/identity/003-create-local-password-credentials.yaml` | Modify | Add `password_algorithm` column to existing `createTable`. Safe: changelog has never been applied in production |
| `server/smp/src/main/resources/db/changelog/dev/001-seed-test-data.yaml` | Modify | Keep dev seed imports aligned with the new credential schema |
| `server/smp/src/main/resources/db/changelog/data/dev/local_password_credentials_dev.csv` | Modify | Add password algorithm seed values for dev data |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt` | Modify | Add RED-first tests for Argon2id registration, BCrypt compatibility, rehash, and malformed hashes |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/LocalAuthEndpointIntegrationTest.kt` | Modify | Assert persisted algorithm metadata and successful legacy upgrade behavior against PostgreSQL |

## Interfaces / Contracts

### Modified: Local password credential gateway

```kotlin
interface LocalPasswordCredentialGateway {
    suspend fun create(principalId: String, passwordHash: String, passwordAlgorithm: String)
    suspend fun findByEmail(email: String): LocalPasswordCredentialRecord?
    suspend fun updatePassword(principalId: String, passwordHash: String, passwordAlgorithm: String)
}

data class LocalPasswordCredentialRecord(
    val principalId: String,
    val email: String,
    val username: String?,
    val passwordHash: String,
    val passwordAlgorithm: String?,
)
```

### New: Argon2id password hasher

```kotlin
class Argon2idPasswordHasher : PasswordHasher {
    override val algorithm: String = "argon2id"

    override fun hash(rawPassword: String): String = /* Argon2id encoder output */

    override fun matches(rawPassword: String, passwordHash: String): Boolean = try {
        /* verify using Argon2id */
    } catch (_: IllegalArgumentException) {
        false
    }
}
```

### Login upgrade decision helper (conceptual)

```kotlin
private fun shouldUpgradeToArgon2id(record: LocalPasswordCredentialRecord): Boolean
```

This helper can centralize the compatibility logic:
- explicit `bcrypt` metadata → upgrade after successful login
- missing metadata + BCrypt-looking hash → upgrade after successful login
- explicit `argon2id` metadata → no upgrade
- malformed/unknown format → fail closed

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | `Argon2idPasswordHasher` hashes, verifies, and fails closed on malformed hashes | Focused tests against the new hasher |
| Unit | `LoginUserHandler` accepts legacy BCrypt and triggers upgrade | Add regression-first handler tests using fake gateway state + update assertions |
| Unit | `LoginUserHandler` rejects malformed/unsupported hashes | Assert invalid-credentials outcome, not exception leakage |
| Unit | `RegisterUserHandler` persists Argon2id metadata | Extend existing registration tests to assert algorithm and hash semantics |
| Integration | Liquibase/dev seed compatibility with `password_algorithm` | Verify seeded login still works and schema loads correctly |
| Integration | Real DB login upgrades BCrypt row to Argon2id | Extend `LocalAuthEndpointIntegrationTest` with seeded BCrypt row and post-login assertions |

## Migration / Rollout

1. **Schema**: Add `password_algorithm` column directly to the existing
   `003-create-local-password-credentials.yaml` changelog. Safe: this changelog has
   never been applied in any production database, so there is no checksum divergence risk.
2. **Dev seed data**: Update `local_password_credentials_dev.csv` to include the
   `password_algorithm` column.
3. **Code**: Implement `Argon2idPasswordHasher`, wire it as the default bean, add a
   separate `bcryptPasswordHasher` bean for legacy verification, and update gateway
   contracts to persist/read/update algorithm metadata.
4. **Test fixtures**: Update integration test database seeds to include explicit algorithm
   metadata where applicable; keep BCrypt-format seeds to test legacy compatibility.
5. **Merge and deploy**: Single deployment — schema, code, and seed data land together.

Rollback (first-deploy specific):
- **Pre-deploy**: Revert the entire PR. No compatibility concerns.
- **Post-deploy (no data)**: Revert code, drop column if desired, truncate any dev rows.
  Single coordinated revert, no split-brain risk.
- **Defensive code retained**: Null-safe metadata handling and format-inference fallback
  remain in the codebase as production-grade practice, not as rollout scaffolding.

## Open Questions

- [ ] Do we want a dedicated password-algorithm resolver abstraction, or is local handler/gateway logic sufficient for this migration scope?
