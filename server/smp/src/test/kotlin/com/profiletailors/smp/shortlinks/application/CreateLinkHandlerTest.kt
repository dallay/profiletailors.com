package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.shortlinks.domain.IdempotencyKeyConflictException
import com.profiletailors.smp.shortlinks.domain.IdempotencyPort
import com.profiletailors.smp.shortlinks.domain.IdempotencyRecord
import com.profiletailors.smp.shortlinks.domain.IdempotencyRequestInProgressException
import com.profiletailors.smp.shortlinks.domain.LinkRepository
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.shortlinks.domain.ShortCode
import com.profiletailors.smp.shortlinks.domain.ShortCodeGenerator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
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
    fun `replays stored result for matching idempotency payload`() = runBlocking {
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
    fun `replays matching idempotency payload and rejects a different payload`() = runBlocking {
        val originalCommand = CreateLinkCommand("https://example.com/replay", null, null, "replay-key")
        val differentCommand = originalCommand.copy(destinationUrl = "https://example.com/different")
        coEvery { idempotencyPort.findStoredResult("replay-key", ownerId) } returns
            IdempotencyRecord(payloadHash(originalCommand), "stored-result")

        try {
            handler().handle(differentCommand)
            throw AssertionError("Expected idempotency payload conflict")
        } catch (_: IdempotencyKeyConflictException) {
            coVerify(exactly = 0) { linkRepository.save(any()) }
        }
    }

    @Test
    fun `rejects incomplete idempotency claim instead of reporting malformed result`() = runBlocking {
        val command = CreateLinkCommand("https://example.com/replay", null, null, "replay-key")
        coEvery { idempotencyPort.findStoredResult("replay-key", ownerId) } returns
            IdempotencyRecord(payloadHash(command), "")

        try {
            handler().handle(command)
            throw AssertionError("Expected idempotency request in progress")
        } catch (_: IdempotencyRequestInProgressException) {
            coVerify(exactly = 0) { linkRepository.save(any()) }
        }
    }

    @Test
    fun `stores created link and its idempotency result in the same transaction`() = runBlocking {
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
    fun `replays concurrent duplicate key result instead of creating a second link`() = runBlocking {
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
    fun `rejects reuse of an idempotency key with a different request payload`() = runBlocking {
        val handler = handler()
        coEvery { idempotencyPort.findStoredResult("retry-key", ownerId) } returns IdempotencyRecord(
            "a".repeat(64),
            "0199b1ca-0000-7000-8000-000000000002|existing|https://short.example/existing|" +
                "https://other.example|ACTIVE|2026-01-01T00:00:00Z||1",
        )

        try {
            handler.handle(CreateLinkCommand("https://destination.example", null, null, "retry-key"))
            throw AssertionError("Expected idempotency key conflict")
        } catch (_: IdempotencyKeyConflictException) {
            coVerify(exactly = 0) { idempotencyPort.claim(any(), any(), any()) }
            coVerify(exactly = 0) { linkRepository.save(any()) }
        }
    }

    @Test
    fun `rejects conflicting in-flight request without creating another link`() = runBlocking {
        val handler = handler()
        coEvery { idempotencyPort.findStoredResult("retry-key", ownerId) } returnsMany listOf(
            null,
            IdempotencyRecord(
                payloadHash(CreateLinkCommand("https://destination.example", null, null, "retry-key")),
                "",
            ),
        )
        coEvery { idempotencyPort.claim("retry-key", ownerId, any()) } returns false

        try {
            handler.handle(CreateLinkCommand("https://destination.example", null, null, "retry-key"))
            throw AssertionError("Expected in-progress request")
        } catch (_: IdempotencyRequestInProgressException) {
            coVerify(exactly = 0) { linkRepository.save(any()) }
        }
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
