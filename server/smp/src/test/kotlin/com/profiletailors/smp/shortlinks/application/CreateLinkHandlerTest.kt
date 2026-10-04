package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.shortlinks.domain.AliasAlreadyExistsException
import com.profiletailors.smp.shortlinks.domain.IdempotencyKeyConflictException
import com.profiletailors.smp.shortlinks.domain.IdempotencyPort
import com.profiletailors.smp.shortlinks.domain.IdempotencyRecord
import com.profiletailors.smp.shortlinks.domain.IdempotencyRequestInProgressException
import com.profiletailors.smp.shortlinks.domain.InvalidDestinationUrlException
import com.profiletailors.smp.shortlinks.domain.LinkRepository
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.shortlinks.domain.ReservedAliasException
import com.profiletailors.smp.shortlinks.domain.ShortCode
import com.profiletailors.smp.shortlinks.domain.ShortCodeCollisionException
import com.profiletailors.smp.shortlinks.domain.ShortCodeCollisionExhaustedException
import com.profiletailors.smp.shortlinks.domain.ShortCodeGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal class CreateLinkHandlerTest {
    private val workspaceId = "0199b1ca-0000-7000-8000-000000000001"
    private val ownerId = OwnerId.from(workspaceId)
    private val contextProvider = mockk<ResourceContextProvider> {
        every { require() } returns ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
    }
    private val linkRepository = mockk<LinkRepository>()
    private val idempotencyPort = mockk<IdempotencyPort>()
    private val linkCache = mockk<com.profiletailors.smp.shortlinks.domain.LinkCachePort>(relaxed = true)
    private val transactionRunner = object : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }
    private val generator = ShortCodeGenerator { ShortCode("generated") }
    private val properties = mockk<ShortLinksConfigProperties> {
        every { publicHost } returns "short.example"
        every { shortUrlBase } returns "https://short.example"
    }
    private val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `replays stored result for matching idempotency payload`() = runTest {
        val command = CreateLinkCommand("https://example.com/replay", null, null, "replay-key")
        val resultId = "0199b1ca-0000-7000-0000-000000000002"
        val storedResult = listOf(
            resultId,
            "generated",
            "https://short.example/generated",
            "https://example.com/replay",
            "ACTIVE",
            "2026-01-01T00:00:00Z",
            "",
            "1",
        ).joinToString("|")
        coEvery { idempotencyPort.findStoredResult("replay-key", ownerId) } returns
            IdempotencyRecord(payloadHash(command), storedResult)

        val result = handler().handle(command)

        assertEquals(resultId, result.id.toString())
        coVerify(exactly = 0) { linkRepository.save(any()) }
    }

    @Test
    fun `replays matching idempotency payload and rejects a different payload`() = runTest {
        val originalCommand = CreateLinkCommand("https://example.com/replay", null, null, "replay-key")
        val differentCommand = originalCommand.copy(destinationUrl = "https://example.com/different")
        coEvery { idempotencyPort.findStoredResult("replay-key", ownerId) } returns
            IdempotencyRecord(payloadHash(originalCommand), "stored-result")

        shouldThrow<IdempotencyKeyConflictException> {
            handler().handle(differentCommand)
        }
        coVerify(exactly = 0) { linkRepository.save(any()) }
    }

    @Test
    fun `rejects incomplete idempotency claim instead of reporting malformed result`() = runTest {
        val command = CreateLinkCommand("https://example.com/replay", null, null, "replay-key")
        coEvery { idempotencyPort.findStoredResult("replay-key", ownerId) } returns
            IdempotencyRecord(payloadHash(command), "")

        shouldThrow<IdempotencyRequestInProgressException> {
            handler().handle(command)
        }
        coVerify(exactly = 0) { linkRepository.save(any()) }
    }

    @Test
    fun `stores created link and its idempotency result in the same transaction`() = runTest {
        coEvery { idempotencyPort.findStoredResult("retry-key", ownerId) } returns null
        coEvery { idempotencyPort.claim("retry-key", ownerId, any()) } returns true
        coEvery { linkRepository.save(any()) } coAnswers { firstArg() }
        coEvery { idempotencyPort.store("retry-key", ownerId, any(), any()) } coAnswers { }
        val handler = handler()

        handler.handle(CreateLinkCommand("https://destination.example", null, null, "retry-key"))

        coVerify(exactly = 1) { idempotencyPort.claim("retry-key", ownerId, any()) }
        coVerify(exactly = 1) { linkRepository.save(any()) }
        coVerify(exactly = 1) { idempotencyPort.store("retry-key", ownerId, any(), any()) }
    }

    @Test
    fun `replays concurrent duplicate key result instead of creating a second link`() = runTest {
        val storedResult =
            "0199b1ca-0000-7000-8000-000000000002|existing|https://short.example/existing|" +
                "https://destination.example|ACTIVE|2026-01-01T00:00:00Z||1"
        val handler = handler()
        val command = CreateLinkCommand("https://destination.example", null, null, "retry-key")
        coEvery { idempotencyPort.findStoredResult("retry-key", ownerId) } returnsMany listOf(
            null,
            IdempotencyRecord(payloadHash(command), storedResult),
        )
        coEvery { idempotencyPort.claim("retry-key", ownerId, any()) } returns false

        val replay = handler.handle(command)

        assertEquals("existing", replay.shortCode)
        coVerify(exactly = 0) { linkRepository.save(any()) }
    }

    @Test
    fun `rejects reuse of an idempotency key with a different request payload`() = runTest {
        val handler = handler()
        coEvery { idempotencyPort.findStoredResult("retry-key", ownerId) } returns IdempotencyRecord(
            "a".repeat(64),
            "0199b1ca-0000-7000-8000-000000000002|existing|https://short.example/existing|" +
                "https://other.example|ACTIVE|2026-01-01T00:00:00Z||1",
        )

        shouldThrow<IdempotencyKeyConflictException> {
            handler.handle(CreateLinkCommand("https://destination.example", null, null, "retry-key"))
        }
        coVerify(exactly = 0) { idempotencyPort.claim(any(), any(), any()) }
        coVerify(exactly = 0) { linkRepository.save(any()) }
    }

    @Test
    fun `rejects conflicting in-flight request without creating another link`() = runTest {
        val handler = handler()
        coEvery { idempotencyPort.findStoredResult("retry-key", ownerId) } returnsMany listOf(
            null,
            IdempotencyRecord(
                payloadHash(CreateLinkCommand("https://destination.example", null, null, "retry-key")),
                "",
            ),
        )
        coEvery { idempotencyPort.claim("retry-key", ownerId, any()) } returns false

        shouldThrow<IdempotencyRequestInProgressException> {
            handler.handle(CreateLinkCommand("https://destination.example", null, null, "retry-key"))
        }
        coVerify(exactly = 0) { linkRepository.save(any()) }
    }

    @Test
    fun `rejects invalid destination without saving`() = runTest {
        coEvery { idempotencyPort.findStoredResult("bad-key", ownerId) } returns null
        coEvery { idempotencyPort.claim("bad-key", ownerId, any()) } returns true

        shouldThrow<InvalidDestinationUrlException> {
            handler().handle(CreateLinkCommand("not a url", null, null, "bad-key"))
        }
        coVerify(exactly = 0) { linkRepository.save(any()) }
    }

    @Test
    fun `rejects reserved alias`() = runTest {
        shouldThrow<ReservedAliasException> {
            handler().handle(CreateLinkCommand("https://destination.example", "admin", null, null))
        }
        coVerify(exactly = 0) { linkRepository.save(any()) }
    }

    @Test
    fun `maps custom alias collision to alias conflict without retry`() = runTest {
        coEvery { idempotencyPort.findStoredResult(any(), ownerId) } returns null
        coEvery { linkRepository.save(any()) } throws ShortCodeCollisionException(RuntimeException("taken"))

        shouldThrow<AliasAlreadyExistsException> {
            handler().handle(CreateLinkCommand("https://destination.example", "Taken42", null, null))
        }
        coVerify(exactly = 1) { linkRepository.save(any()) }
    }

    @Test
    fun `retries generated code on collision`() = runTest {
        var calls = 0
        coEvery { idempotencyPort.findStoredResult(any(), ownerId) } returns null
        coEvery { linkRepository.save(any()) } coAnswers {
            calls += 1
            if (calls == 1) throw ShortCodeCollisionException(RuntimeException("collision"))
            firstArg()
        }

        val result = handler().handle(CreateLinkCommand("https://destination.example", null, null, null))

        assertEquals("generated", result.shortCode)
        coVerify(exactly = 2) { linkRepository.save(any()) }
    }

    @Test
    fun `gives up after repeated collisions`() = runTest {
        coEvery { idempotencyPort.findStoredResult(any(), ownerId) } returns null
        coEvery { linkRepository.save(any()) } throws ShortCodeCollisionException(RuntimeException("taken"))

        shouldThrow<ShortCodeCollisionExhaustedException> {
            handler().handle(CreateLinkCommand("https://destination.example", null, null, null))
        }
        coVerify(exactly = 5) { linkRepository.save(any()) }
    }

    @Test
    fun `rejects malformed stored result`() = runTest {
        val command = CreateLinkCommand("https://example.com/replay", null, null, "replay-key")
        coEvery { idempotencyPort.findStoredResult("replay-key", ownerId) } returns
            IdempotencyRecord(payloadHash(command), "not-a-valid-result")

        shouldThrow<IllegalArgumentException> {
            handler().handle(command)
        }
        coVerify(exactly = 0) { linkRepository.save(any()) }
    }

    @Test
    fun `evicts cache after creating`() = runTest {
        coEvery { idempotencyPort.findStoredResult(any(), ownerId) } returns null
        coEvery { linkRepository.save(any()) } coAnswers { firstArg() }

        handler().handle(CreateLinkCommand("https://destination.example", null, null, null))

        coVerify(exactly = 1) { linkCache.evict("short.example", "generated") }
    }

    @Test
    fun `does not evict on idempotent replay`() = runTest {
        val command = CreateLinkCommand("https://example.com/replay", null, null, "replay-key")
        val storedResult = listOf(
            "0199b1ca-0000-7000-0000-000000000002",
            "generated",
            "https://short.example/generated",
            "https://example.com/replay",
            "ACTIVE",
            "2026-01-01T00:00:00Z",
            "",
            "1",
        ).joinToString("|")
        coEvery { idempotencyPort.findStoredResult("replay-key", ownerId) } returns
            IdempotencyRecord(payloadHash(command), storedResult)

        handler().handle(command)

        coVerify(exactly = 0) { linkRepository.save(any()) }
        coVerify(exactly = 0) { linkCache.evict(any(), any()) }
    }

    private fun payloadHash(command: CreateLinkCommand): String {
        val normalized = listOf(
            command.destinationUrl,
            command.customAlias.orEmpty(),
            command.expiresAt?.toString().orEmpty(),
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun handler() = CreateLinkHandler(
        contextProvider,
        linkRepository,
        generator,
        transactionRunner,
        clock,
        properties,
        idempotencyPort,
        linkCache,
    )
}
