# publishing.infrastructure.credentials — test coverage design

## Goal

Raise instruction coverage from **46%** → **≥ 80%** on
`com.profiletailors.smp.publishing.infrastructure.credentials`.

## Current coverage per class

| Class | Instruction Cov. | Status |
|---|---|---|
| `PublishingCredentialsProperties` | **100%** | ✅ already covered |
| `CredentialEncryptionService` | **88%** | ✅ minor gap (key init edge cases) |
| `R2dbcLinkedInCredentialGateway` | **8%** | 🔴 main target |
| `LinkedInCredentials` | **66%** | 🟡 data class, acceptable |
| `EncryptedCredential` | **0%** | 🟡 data class, acceptable |

**Total missed: 233 / 435 = 46%.** Main gap is `R2dbcLinkedInCredentialGateway`.

## Strategy

Use `DatabaseUnitTestBase` (H2 in-memory) for integration tests — same pattern as B2.
`R2dbcLinkedInCredentialGateway` uses real `DatabaseClient` + real `CredentialEncryptionService`.
`LinkedInCredentials` and `EncryptedCredential` are data classes with no branching logic — acceptable at low coverage.

### Target: R2dbcLinkedInCredentialGateway (8% → ≥ 90%)

**Methods:**
- `storeForOwner(ownerType, ownerId, credentials): UUID` — INSERT then return UUID
- `resolveCredential(id): LinkedInCredentials` — SELECT, decrypt, deserialize

**Tests (5 scenarios):**
1. `storeForOwner inserts encrypted credential and returns UUID` — verify ID is returned, row exists in DB
2. `storeForOwner stores different owner types correctly` — verify row has correct owner_type
3. `resolveCredential decrypts and deserializes to LinkedInCredentials` — roundtrip store then resolve, verify all fields match
4. `resolveCredential throws when credential not found` — awaitSingle throws
5. `resolveCredential decrypts credential with all fields including null refreshToken` — test nullable handling

### CredentialEncryptionService — remaining gap (88%)

Already has one test for happy path. Gap is the key initialization:
- `init` block: invalid key size throws `IllegalArgumentException`

**Tests (1 scenario):**
1. `init throws IllegalArgumentException for key of invalid size` — 8-byte key (64 bits), should reject

## File layout

```
src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/
  LinkedInCredentialGatewayTest.kt       ← 6 new tests
```

## Coverage target

- `R2dbcLinkedInCredentialGateway`: 8% → **≥ 90%**
- `CredentialEncryptionService`: 88% → **≥ 95%**
- Overall package: 46% → **≥ 80%**