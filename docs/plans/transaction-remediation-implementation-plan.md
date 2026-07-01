# Transaction Remediation Implementation Plan

> **For agentic workers:** Implement this plan task-by-task. Tasks 1-2 (unit tests) are independent of each other. Tasks 3-4 (handler fixes) are independent of each other. Tasks 5-6 (integration tests) can run in parallel after tasks 3-4 complete.

**Goal:** Wrap multi-write operations in `VerifyEmailHandler`, `ResendVerificationHandler`, and `CompleteLinkedInConnectionHandler` with `AtomicTransactionRunner.runAtomically {}`.

**Architecture:** Inject `AtomicTransactionRunner` into each handler, wrap multi-write operations in `runAtomically {}`, keep `eventPublisher.publish()` outside the transaction.

**Tech Stack:** Kotlin, Spring WebFlux, R2DBC, `TransactionalOperator`, `AtomicTransactionRunner`, Kotest/JUnit, Testcontainers.

---

## File Overview

| File | Change |
|------|--------|
| `server/smp/src/main/kotlin/.../identity/application/LocalAuthHandlers.kt` | Inject `AtomicTransactionRunner`, wrap multi-write in `VerifyEmailHandler` and `ResendVerificationHandler` |
| `server/smp/src/main/kotlin/.../publishing/application/PublishingHandlers.kt` | Inject `AtomicTransactionRunner`, wrap multi-write in `CompleteLinkedInConnectionHandler` |
| `server/smp/src/test/kotlin/.../identity/application/LocalAuthHandlersTest.kt` | Add 4 unit tests for transaction order and rollback |
| `server/smp/src/test/kotlin/.../publishing/application/PublishingHandlersTest.kt` | Add 2 unit tests for transaction order and rollback |
| `server/smp/src/test/kotlin/.../identity/integration/LocalAuthTransactionPostgresIntegrationTest.kt` | New file: 2 integration tests for rollback |
| `server/smp/src/test/kotlin/.../publishing/integration/PublishingHandlersTransactionPostgresIntegrationTest.kt` | Extend with 2 rollback tests (file already exists) |

---

## Task 1: Add VerifyEmailHandler unit tests

**Files:**
- Modify: `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt`

- [ ] **Step 1: Add `RecordingAtomicTransactionRunner` instance to `VerifyEmailHandler` tests**

Find the existing `RecordingAtomicTransactionRunner` class at line ~490 and `NoopAtomicTransactionRunner` at line ~486. These are used in `RegisterUserHandler` tests.

- [ ] **Step 2: Write test — verify email wraps writes in transaction (happy path)**

Add this test after the existing `ResendVerificationHandler` tests (around line 388):

```kotlin
@Test
fun `verifyEmail wraps markTokenUsed and updateEmailStatus in transaction`() = runTest {
    val order = mutableListOf<String>()
    val identityGateway = FakeIdentityRegistrationGateway(order)
    val principalLookup = FakePrincipalIdentityLookup(
        principalFacts = PrincipalIdentityFacts(
            principalId = "user-1",
            principalType = PrincipalType.USER,
            subject = "local:yuniel@example.com",
            provider = null,
            displayIdentity = "yuniel",
            email = "yuniel@example.com",
            username = "yuniel",
            emailStatus = EmailStatus.PENDING,
        ),
    )
    val transactionRunner = RecordingAtomicTransactionRunner(order)
    val handler = VerifyEmailHandler(
        identityRegistrationGateway = identityGateway,
        principalIdentityLookup = principalLookup,
        localJwtIssuer = FakeLocalJwtIssuer(),
        refreshSessionLifecycleService = fakeRefreshLifecycleService(),
        clock = fixedClock,
        transactionRunner = transactionRunner,
    )

    // Set up a valid token
    identityGateway.addValidToken("test-token-hash", "yuniel@example.com")

    handler.handle(VerifyEmailCommand("raw-token"))

    assertEquals(
        listOf(
            "tx:start",
            "token:verify",
            "markTokenUsed",
            "updateEmailStatus",
            "tx:commit",
            "identity:findByEmail",
            "jwt:issue",
            "refresh:create",
        ),
        order,
    )
}
```

- [ ] **Step 3: Write test — verify email rollback when updateEmailStatus fails**

Add this test after the previous one:

```kotlin
@Test
fun `verifyEmail rolls back when updateEmailStatus fails`() = runTest {
    val order = mutableListOf<String>()
    val identityGateway = object : FakeIdentityRegistrationGateway(order) {
        override suspend fun updateEmailStatus(email: String, status: EmailStatus) {
            order.add("updateEmailStatus")
            throw IllegalStateException("DB error")
        }
    }
    val principalLookup = FakePrincipalIdentityLookup(
        principalFacts = PrincipalIdentityFacts(
            principalId = "user-1",
            principalType = PrincipalType.USER,
            subject = "local:yuniel@example.com",
            provider = null,
            displayIdentity = "yuniel",
            email = "yuniel@example.com",
            username = "yuniel",
            emailStatus = EmailStatus.PENDING,
        ),
    )
    val transactionRunner = RecordingAtomicTransactionRunner(order)
    val handler = VerifyEmailHandler(
        identityRegistrationGateway = identityGateway,
        principalIdentityLookup = principalLookup,
        localJwtIssuer = FakeLocalJwtIssuer(),
        refreshSessionLifecycleService = fakeRefreshLifecycleService(),
        clock = fixedClock,
        transactionRunner = transactionRunner,
    )

    identityGateway.addValidToken("test-token-hash", "yuniel@example.com")

    try {
        handler.handle(VerifyEmailCommand("raw-token"))
        throw AssertionError("Expected exception")
    } catch (e: IllegalStateException) {
        assertEquals("DB error", e.message)
    }

    // Verify markTokenUsed was NOT called because transaction rolled back
    assertFalse(order.contains("markTokenUsed"))
    assertFalse(order.contains("tx:commit"))
}
```

- [ ] **Step 4: Run tests to verify they fail (TDD — handler not modified yet)**

Run: `just backend-test-fast --tests "LocalAuthHandlersTest"`

Expected: Compilation error — `VerifyEmailHandler` constructor does not have `transactionRunner` parameter yet.

- [ ] **Step 5: Commit**

```bash
git add server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt
git commit -m "test: add VerifyEmailHandler transaction unit tests"
```

---

## Task 2: Add ResendVerificationHandler unit tests

**Files:**
- Modify: `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt`

- [ ] **Step 1: Write test — resend verification wraps writes in transaction (happy path)**

Add after the existing `resend verification returns accepted for unknown email` test (around line 388):

```kotlin
@Test
fun `resendVerification wraps invalidateEmailTokens and createEmailVerificationToken in transaction`() = runTest {
    val order = mutableListOf<String>()
    val identityGateway = FakeIdentityRegistrationGateway(order)
    val principalLookup = FakePrincipalIdentityLookup(
        principalFacts = PrincipalIdentityFacts(
            principalId = "user-1",
            principalType = PrincipalType.USER,
            subject = "local:yuniel@example.com",
            provider = null,
            displayIdentity = "yuniel",
            email = "yuniel@example.com",
            username = "yuniel",
            emailStatus = EmailStatus.PENDING,
        ),
    )
    val eventPublisher = RecordingEventPublisher(order)
    val transactionRunner = RecordingAtomicTransactionRunner(order)
    val handler = ResendVerificationHandler(
        identityRegistrationGateway = identityGateway,
        eventPublisher = eventPublisher,
        principalIdentityLookup = principalLookup,
        transactionRunner = transactionRunner,
    )

    handler.handle(ResendVerificationCommand("yuniel@example.com"))

    assertEquals(
        listOf(
            "identity:findByEmail",
            "tx:start",
            "invalidateEmailTokens",
            "token:create",
            "tx:commit",
            "event:publish",
        ),
        order,
    )
}
```

- [ ] **Step 2: Write test — resend verification rollback when createEmailVerificationToken fails**

```kotlin
@Test
fun `resendVerification rolls back when createEmailVerificationToken fails`() = runTest {
    val order = mutableListOf<String>()
    val identityGateway = object : FakeIdentityRegistrationGateway(order) {
        override suspend fun createEmailVerificationToken(email: String, tokenHash: String, expiresAt: Instant) {
            order.add("token:create")
            throw IllegalStateException("DB error")
        }
    }
    val principalLookup = FakePrincipalIdentityLookup(
        principalFacts = PrincipalIdentityFacts(
            principalId = "user-1",
            principalType = PrincipalType.USER,
            subject = "local:yuniel@example.com",
            provider = null,
            displayIdentity = "yuniel",
            email = "yuniel@example.com",
            username = "yuniel",
            emailStatus = EmailStatus.PENDING,
        ),
    )
    val eventPublisher = RecordingEventPublisher(order)
    val transactionRunner = RecordingAtomicTransactionRunner(order)
    val handler = ResendVerificationHandler(
        identityRegistrationGateway = identityGateway,
        eventPublisher = eventPublisher,
        principalIdentityLookup = principalLookup,
        transactionRunner = transactionRunner,
    )

    try {
        handler.handle(ResendVerificationCommand("yuniel@example.com"))
        throw AssertionError("Expected exception")
    } catch (e: IllegalStateException) {
        assertEquals("DB error", e.message)
    }

    // Verify invalidateEmailTokens was NOT committed because transaction rolled back
    assertTrue(order.contains("invalidateEmailTokens"))
    assertFalse(order.contains("tx:commit"))
    assertFalse(order.contains("event:publish"))
}
```

- [ ] **Step 3: Run tests to verify they fail (TDD — handler not modified yet)**

Run: `just backend-test-fast --tests "LocalAuthHandlersTest"`

Expected: Compilation error — `ResendVerificationHandler` constructor does not have `transactionRunner` parameter yet.

- [ ] **Step 4: Commit**

```bash
git add server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt
git commit -m "test: add ResendVerificationHandler transaction unit tests"
```

---

## Task 3: Fix VerifyEmailHandler and ResendVerificationHandler

**Files:**
- Modify: `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt`

- [ ] **Step 1: Add import for AtomicTransactionRunner**

Verify import already exists at line 7:
```kotlin
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
```

- [ ] **Step 2: Modify VerifyEmailHandler constructor to inject AtomicTransactionRunner**

Find `VerifyEmailHandler` class at line 278. Add `transactionRunner` parameter:

```kotlin
@Service
internal class VerifyEmailHandler(
    private val identityRegistrationGateway: IdentityRegistrationGateway,
    private val principalIdentityLookup: PrincipalIdentityLookup,
    private val localJwtIssuer: LocalJwtIssuer,
    private val refreshSessionLifecycleService: RefreshSessionLifecycleService,
    private val clock: Clock,
    private val transactionRunner: AtomicTransactionRunner,
) : CommandWithResultHandler<VerifyEmailCommand, LocalAuthSessionResult> {
```

- [ ] **Step 3: Wrap multi-write operations in runAtomically block**

Replace lines 298-299:
```kotlin
identityRegistrationGateway.markTokenUsed(tokenHash, now)
identityRegistrationGateway.updateEmailStatus(storedToken.email, EmailStatus.VERIFIED)
```

With:
```kotlin
transactionRunner.runAtomically {
    identityRegistrationGateway.markTokenUsed(tokenHash, now)
    identityRegistrationGateway.updateEmailStatus(storedToken.email, EmailStatus.VERIFIED)
}
```

- [ ] **Step 4: Modify ResendVerificationHandler constructor to inject AtomicTransactionRunner**

Find `ResendVerificationHandler` class at line 320. Add `transactionRunner` parameter:

```kotlin
@Service
internal class ResendVerificationHandler(
    private val identityRegistrationGateway: IdentityRegistrationGateway,
    private val eventPublisher: EventPublisher<DomainEvent>,
    private val principalIdentityLookup: PrincipalIdentityLookup,
    private val transactionRunner: AtomicTransactionRunner,
) : CommandWithResultHandler<ResendVerificationCommand, ResendVerificationResult> {
```

- [ ] **Step 5: Wrap multi-write operations in runAtomically block**

Replace lines 337-345:
```kotlin
// Invalidate old tokens
identityRegistrationGateway.invalidateEmailTokens(normalizedEmail)

// Generate new token
val generated = EmailVerificationTokenHasher.generate()
identityRegistrationGateway.createEmailVerificationToken(
    email = normalizedEmail,
    tokenHash = generated.tokenHash,
    expiresAt = generated.expiresAt,
)
```

With:
```kotlin
// Invalidate old tokens and create new token atomically
transactionRunner.runAtomically {
    identityRegistrationGateway.invalidateEmailTokens(normalizedEmail)
    val generated = EmailVerificationTokenHasher.generate()
    identityRegistrationGateway.createEmailVerificationToken(
        email = normalizedEmail,
        tokenHash = generated.tokenHash,
        expiresAt = generated.expiresAt,
    )
    generated
}.let { generated ->
    // Publish event for email dispatch (outside transaction)
    eventPublisher.publish(
        UserRegistered(
            principalId = identityFacts.principalId,
            email = normalizedEmail,
            username = identityFacts.username,
            rawVerificationToken = generated.rawToken,
        ),
    )
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `just backend-test-fast --tests "LocalAuthHandlersTest"`

Expected: All `VerifyEmail` and `ResendVerification` tests pass.

- [ ] **Step 7: Commit**

```bash
git add server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt
git commit -m "feat: wrap VerifyEmailHandler and ResendVerificationHandler multi-write in transaction"
```

---

## Task 4: Fix CompleteLinkedInConnectionHandler

**Files:**
- Modify: `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt`

- [ ] **Step 1: Add import for AtomicTransactionRunner**

Add after existing imports:
```kotlin
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
```

- [ ] **Step 2: Modify CompleteLinkedInConnectionHandler constructor to inject AtomicTransactionRunner**

Find `CompleteLinkedInConnectionHandler` class at line 145. Add `transactionRunner` parameter:

```kotlin
@Service
internal class CompleteLinkedInConnectionHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val socialConnectionProvider: SocialConnectionProvider,
    private val oauthStateSigner: OAuthStateSigner,
    private val socialConnectionRepository: SocialConnectionRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val channelEventPublisher: ChannelEventPublisher,
    private val clock: Clock,
    private val transactionRunner: AtomicTransactionRunner,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy =
        permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<CompleteLinkedInConnectionCommand, SocialConnectionResult> {
```

- [ ] **Step 3: Wrap multi-write operations in runAtomically block**

Replace lines 179-203 (the upsert calls):
```kotlin
val connection = socialConnectionRepository.upsert(
    SocialConnection(
        id = "soconn-${UUID.randomUUID()}",
        workspaceId = workspaceId,
        provider = SocialProvider.LINKEDIN,
        providerConnectionRef = providerResult.providerConnectionRef,
        status = SocialConnectionStatus.ACTIVE,
        credentialReference = providerResult.credentialReference,
        connectedAt = clock.instant(),
    ),
)
val account = socialAccountRepository.upsert(
    SocialAccount(
        id = "soacc-${UUID.randomUUID()}",
        socialConnectionId = connection.id,
        workspaceId = workspaceId,
        provider = SocialProvider.LINKEDIN,
        providerAccountId = providerResult.account.providerAccountId,
        kind = providerResult.account.kind,
        displayName = providerResult.account.displayName,
        profileUrn = providerResult.account.profileUrn,
        avatarUrl = providerResult.account.avatarUrl,
        status = SocialConnectionStatus.ACTIVE,
    ),
)
```

With:
```kotlin
val (connection, account) = transactionRunner.runAtomically {
    val conn = socialConnectionRepository.upsert(
        SocialConnection(
            id = "soconn-${UUID.randomUUID()}",
            workspaceId = workspaceId,
            provider = SocialProvider.LINKEDIN,
            providerConnectionRef = providerResult.providerConnectionRef,
            status = SocialConnectionStatus.ACTIVE,
            credentialReference = providerResult.credentialReference,
            connectedAt = clock.instant(),
        ),
    )
    val acc = socialAccountRepository.upsert(
        SocialAccount(
            id = "soacc-${UUID.randomUUID()}",
            socialConnectionId = conn.id,
            workspaceId = workspaceId,
            provider = SocialProvider.LINKEDIN,
            providerAccountId = providerResult.account.providerAccountId,
            kind = providerResult.account.kind,
            displayName = providerResult.account.displayName,
            profileUrn = providerResult.account.profileUrn,
            avatarUrl = providerResult.account.avatarUrl,
            status = SocialConnectionStatus.ACTIVE,
        ),
    )
    conn to acc
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `just backend-test-fast --tests "PublishingHandlersTest"`

Expected: All `CompleteLinkedInConnectionHandler` tests pass.

- [ ] **Step 5: Commit**

```bash
git add server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt
git commit -m "feat: wrap CompleteLinkedInConnectionHandler multi-write in transaction"
```

---

## Task 5: Add CompleteLinkedInConnectionHandler unit tests

**Files:**
- Modify: `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlersTest.kt`

- [ ] **Step 1: Add unit test for happy path transaction order**

Find the existing `CompleteLinkedInConnectionHandler` tests and add:

```kotlin
@Test
fun `completeLinkedInConnection wraps upserts in transaction`() = runTest {
    val order = mutableListOf<String>()
    val connectionRepository = FakeSocialConnectionRepository(order)
    val accountRepository = FakeSocialAccountRepository(order)
    val transactionRunner = RecordingAtomicTransactionRunner(order)
    val channelPublisher = CapturingChannelEventPublisher()
    val handler = CompleteLinkedInConnectionHandler(
        principalContextProvider = fakePrincipalContextProvider(principalId = "user-1"),
        resourceContextProvider = fakeWorkspaceContextProvider(workspaceId = "ws-1"),
        socialConnectionProvider = FakeSocialConnectionProvider(
            providerConnectionRef = "linkedin-ref",
            account = ProviderAccountProfile(
                providerAccountId = "li-account-1",
                kind = "PERSONAL",
                displayName = "Yuniel Acosta",
                profileUrn = "urn:li:person:123",
                avatarUrl = null,
            ),
            credentialReference = "cred-ref",
        ),
        oauthStateSigner = fakeOauthStateSigner(
            workspaceId = "ws-1",
            principalId = "user-1",
        ),
        socialConnectionRepository = connectionRepository,
        socialAccountRepository = accountRepository,
        channelEventPublisher = channelPublisher,
        clock = fixedClock,
        transactionRunner = transactionRunner,
    )

    handler.handle(CompleteLinkedInConnectionCommand("auth-code", "redirect-uri", "valid-state"))

    assertEquals(
        listOf(
            "tx:start",
            "connection:upsert",
            "account:upsert",
            "tx:commit",
            "channel:publish",
        ),
        order,
    )
}
```

- [ ] **Step 2: Add unit test for rollback when second upsert fails**

```kotlin
@Test
fun `completeLinkedInConnection rolls back when account upsert fails`() = runTest {
    val order = mutableListOf<String>()
    val connectionRepository = FakeSocialConnectionRepository(order)
    val accountRepository = object : FakeSocialAccountRepository(order) {
        override suspend fun upsert(account: SocialAccount): SocialAccount {
            order.add("account:upsert")
            throw IllegalStateException("DB error")
        }
    }
    val transactionRunner = RecordingAtomicTransactionRunner(order)
    val channelPublisher = CapturingChannelEventPublisher()
    val handler = CompleteLinkedInConnectionHandler(
        principalContextProvider = fakePrincipalContextProvider(principalId = "user-1"),
        resourceContextProvider = fakeWorkspaceContextProvider(workspaceId = "ws-1"),
        socialConnectionProvider = FakeSocialConnectionProvider(
            providerConnectionRef = "linkedin-ref",
            account = ProviderAccountProfile(
                providerAccountId = "li-account-1",
                kind = "PERSONAL",
                displayName = "Yuniel Acosta",
                profileUrn = "urn:li:person:123",
                avatarUrl = null,
            ),
            credentialReference = "cred-ref",
        ),
        oauthStateSigner = fakeOauthStateSigner(
            workspaceId = "ws-1",
            principalId = "user-1",
        ),
        socialConnectionRepository = connectionRepository,
        socialAccountRepository = accountRepository,
        channelEventPublisher = channelPublisher,
        clock = fixedClock,
        transactionRunner = transactionRunner,
    )

    try {
        handler.handle(CompleteLinkedInConnectionCommand("auth-code", "redirect-uri", "valid-state"))
        throw AssertionError("Expected exception")
    } catch (e: IllegalStateException) {
        assertEquals("DB error", e.message)
    }

    // Verify connection upsert was NOT committed because transaction rolled back
    assertTrue(order.contains("connection:upsert"))
    assertFalse(order.contains("tx:commit"))
    assertFalse(order.contains("channel:publish"))
}
```

- [ ] **Step 3: Add helper classes if needed**

You may need to add these helper classes to the test file:
- `RecordingAtomicTransactionRunner` (same pattern as in `LocalAuthHandlersTest.kt`)
- `CapturingChannelEventPublisher` (for testing event publishing)

- [ ] **Step 4: Run tests to verify they pass**

Run: `just backend-test-fast --tests "PublishingHandlersTest"`

Expected: All new tests pass.

- [ ] **Step 5: Commit**

```bash
git add server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlersTest.kt
git commit -m "test: add CompleteLinkedInConnectionHandler transaction unit tests"
```

---

## Task 6: Add Postgres integration tests

**Files:**
- Create: `server/smp/src/test/kotlin/com/profiletailors/smp/identity/integration/LocalAuthTransactionPostgresIntegrationTest.kt`
- Modify: `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingHandlersTransactionPostgresIntegrationTest.kt`

### LocalAuthTransactionPostgresIntegrationTest.kt (new file)

- [ ] **Step 1: Create integration test file**

Create `server/smp/src/test/kotlin/com/profiletailors/smp/identity/integration/LocalAuthTransactionPostgresIntegrationTest.kt`

Follow the pattern of `PublishingWorkerTransactionPostgresIntegrationTest.kt`:

```kotlin
package com.profiletailors.smp.identity.integration

import com.profiletailors.smp.identity.application.VerifyEmailCommand
import com.profiletailors.smp.identity.application.ResendVerificationCommand
import com.profiletailors.smp.identity.domain.EmailStatus
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.connection.init.ConnectionFormula
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlinx.coroutines.runBlocking

@Testcontainers
class LocalAuthTransactionPostgresIntegrationTest {

    @Container
    val postgres = PostgreSQLContainer("postgres:16")

    lateinit var connectionFactory: ConnectionFactory

    @BeforeAll
    fun setup() {
        connectionFactory = ConnectionFactories.get(postgres.jdbcUrl.replace("jdbc:", "r2dbc:"))
        runBlocking {
            // Initialize schema using ConnectionFormula or manual scripts
        }
    }

    @Test
    fun `verifyEmail rolls back markTokenUsed when updateEmailStatus fails`() = runBlocking {
        // Setup: create user identity with PENDING email status
        // Setup: create email verification token
        // Action: run handler, make updateEmailStatus fail (e.g., via constraint)
        // Verify: token's used_at is still NULL (rolled back)
    }

    @Test
    fun `resendVerification rolls back invalidateEmailTokens when createEmailVerificationToken fails`() = runBlocking {
        // Setup: create user identity with PENDING email status
        // Setup: create existing token
        // Action: run handler, make createEmailVerificationToken fail
        // Verify: old token's used_at is still NULL (rolled back)
    }
}
```

### PublishingHandlersTransactionPostgresIntegrationTest.kt (extend existing)

- [ ] **Step 1: Add rollback test for CompleteLinkedInConnectionHandler**

Find the existing file and add:

```kotlin
@Test
fun `completeLinkedInConnection rolls back connection when account upsert fails`() = runBlocking {
    // Setup: create workspace, principal
    // Action: run handler, make socialAccountRepository.upsert fail
    // Verify: no connection record in DB (rolled back)
}
```

- [ ] **Step 2: Run integration tests**

Run: `just backend-bdd-postgres`

Expected: All integration tests pass.

- [ ] **Step 3: Commit**

```bash
git add server/smp/src/test/kotlin/com/profiletailors/smp/identity/integration/LocalAuthTransactionPostgresIntegrationTest.kt
git add server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingHandlersTransactionPostgresIntegrationTest.kt
git commit -m "test: add Postgres integration tests for transaction rollback"
```

---

## Task 7: Final verification

- [ ] **Step 1: Run full test suite**

Run: `just backend-check`

Expected: All tests pass (excluding pre-existing failures with `modularity` and `postgres` tags).

- [ ] **Step 2: Run CI locally**

Run: `just ci-local`

Expected: All CI checks pass.

- [ ] **Step 3: Final commit with summary**

```bash
git commit --allow-empty -m "chore: close #193 transaction remediation complete

- VerifyEmailHandler: wrap markTokenUsed + updateEmailStatus in transaction
- ResendVerificationHandler: wrap invalidateEmailTokens + createEmailVerificationToken in transaction
- CompleteLinkedInConnectionHandler: wrap upsert(connection) + upsert(account) in transaction

Closes #193"
```

---

## Dependencies

- Task 3 requires Task 1 (tests fail until handler is fixed)
- Task 4 requires Task 5 (tests exist for it)
- Task 6 requires Tasks 3 and 4 (handlers modified)
- Tasks 1, 2, 5 can be done in parallel (separate test files)

## Running Tests

```bash
# Fast unit tests (no Postgres)
just backend-test-fast

# With Postgres integration tests (requires infra-up)
just infra-up
just backend-bdd-postgres
just infra-down

# Full CI
just ci-local
```
