package com.profiletailors.smp.mcp.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.profiletailors.smp.mcp.domain.IdempotencyRecord
import com.profiletailors.smp.mcp.infrastructure.JacksonMcpJsonSerializer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class IdempotencyGuardTest {

    private val repository: IdempotencyRecordRepository = mockk()
    private val objectMapper = jacksonObjectMapper()
    private val serializer = JacksonMcpJsonSerializer(objectMapper)
    private val guard = IdempotencyGuard(repository, serializer)

    @Test
    fun `null idempotency key bypasses the guard and runs execute once`() {
        runBlocking {
            var called = 0
            val result = guard.guard(
                workspaceId = "ws-1",
                principalId = "user-1",
                toolName = "create_publication",
                idempotencyKey = null,
                type = Result::class.java,
                execute = {
                    called += 1
                    Result(publicationId = "pub-X", value = 42)
                },
            )

            assertThat(called).isEqualTo(1)
            assertThat(result.publicationId).isEqualTo("pub-X")
            coVerify(exactly = 0) { repository.find(any(), any(), any(), any()) }
            coVerify(exactly = 0) { repository.save(any()) }
        }
    }

    @Test
    fun `replay with the same idempotency key returns the cached result and skips execute`() {
        runBlocking {
            val cachedJson = objectMapper.writeValueAsString(Result(publicationId = "pub-X", value = 42))
            coEvery { repository.find("ws-1", "user-1", "create_publication", any()) } returns cachedJson

            var called = 0
            val result = guard.guard(
                workspaceId = "ws-1",
                principalId = "user-1",
                toolName = "create_publication",
                idempotencyKey = "agent-retry-1",
                type = Result::class.java,
                execute = {
                    called += 1
                    Result(publicationId = "should-not-run", value = 99)
                },
            )

            assertThat(result.publicationId).isEqualTo("pub-X")
            assertThat(result.value).isEqualTo(42)
            assertThat(called).isEqualTo(0)
        }
    }

    @Test
    fun `first call executes and saves the response under the hashed key`() {
        runBlocking {
            coEvery { repository.find(any(), any(), any(), any()) } returns null
            val saved = slot<IdempotencyRecord>()
            coEvery { repository.save(capture(saved)) } returnsArgument 0

            val result = guard.guard(
                workspaceId = "ws-1",
                principalId = "user-1",
                toolName = "create_publication",
                idempotencyKey = "agent-retry-1",
                type = Result::class.java,
                execute = { Result(publicationId = "pub-X", value = 42) },
            )

            assertThat(result.publicationId).isEqualTo("pub-X")
            val savedRecord = saved.captured
            assertThat(savedRecord.workspaceId).isEqualTo("ws-1")
            assertThat(savedRecord.principalId).isEqualTo("user-1")
            assertThat(savedRecord.toolName).isEqualTo("create_publication")
            assertThat(savedRecord.keyHash).isEqualTo(IdempotencyKeyHasher.hash("agent-retry-1"))
            assertThat(savedRecord.responseJson).contains("pub-X")
            assertThat(savedRecord.responseJson).doesNotContain("agent-retry-1")
        }
    }

    @Test
    fun `plaintext idempotency key is never persisted`() {
        runBlocking {
            coEvery { repository.find(any(), any(), any(), any()) } returns null
            val saved = slot<IdempotencyRecord>()
            coEvery { repository.save(capture(saved)) } returnsArgument 0

            guard.guard(
                workspaceId = "ws-1",
                principalId = "user-1",
                toolName = "create_publication",
                idempotencyKey = "super-secret-plaintext-key",
                type = Result::class.java,
                execute = { Result(publicationId = "pub-X", value = 1) },
            )

            val record = saved.captured
            assertThat(record.keyHash).doesNotContain("super-secret-plaintext-key")
            assertThat(record.keyHash).hasSize(64)
            assertThat(record.responseJson).doesNotContain("super-secret-plaintext-key")
        }
    }

    @Test
    fun `workspace isolation — different workspace runs the execute path`() {
        runBlocking {
            coEvery { repository.find("ws-A", "user-1", "create_publication", any()) } returns null
            coEvery { repository.find("ws-B", "user-1", "create_publication", any()) } returns null
            coEvery { repository.save(any()) } returnsArgument 0

            var called = 0
            guard.guard(
                workspaceId = "ws-A",
                principalId = "user-1",
                toolName = "create_publication",
                idempotencyKey = "k1",
                type = Result::class.java,
                execute = {
                    called += 1
                    Result(publicationId = "pub-A", value = 1)
                },
            )
            guard.guard(
                workspaceId = "ws-B",
                principalId = "user-1",
                toolName = "create_publication",
                idempotencyKey = "k1",
                type = Result::class.java,
                execute = {
                    called += 1
                    Result(publicationId = "pub-B", value = 2)
                },
            )

            assertThat(called).isEqualTo(2)
        }
    }

    @Test
    fun `principal isolation — different principal runs the execute path`() {
        runBlocking {
            coEvery { repository.find("ws-1", "user-A", "create_publication", any()) } returns null
            coEvery { repository.find("ws-1", "user-B", "create_publication", any()) } returns null
            coEvery { repository.save(any()) } returnsArgument 0

            var called = 0
            guard.guard(
                workspaceId = "ws-1",
                principalId = "user-A",
                toolName = "create_publication",
                idempotencyKey = "k1",
                type = Result::class.java,
                execute = {
                    called += 1
                    Result(publicationId = "pub-A", value = 1)
                },
            )
            guard.guard(
                workspaceId = "ws-1",
                principalId = "user-B",
                toolName = "create_publication",
                idempotencyKey = "k1",
                type = Result::class.java,
                execute = {
                    called += 1
                    Result(publicationId = "pub-B", value = 2)
                },
            )

            assertThat(called).isEqualTo(2)
        }
    }

    @Test
    fun `tool isolation — different tool runs the execute path`() {
        runBlocking {
            coEvery { repository.find(any(), any(), any(), any()) } returns null
            coEvery { repository.save(any()) } returnsArgument 0

            var called = 0
            guard.guard(
                workspaceId = "ws-1",
                principalId = "user-1",
                toolName = "create_publication",
                idempotencyKey = "k1",
                type = Result::class.java,
                execute = {
                    called += 1
                    Result(publicationId = "pub-X", value = 1)
                },
            )
            guard.guard(
                workspaceId = "ws-1",
                principalId = "user-1",
                toolName = "edit_publication",
                idempotencyKey = "k1",
                type = Result::class.java,
                execute = {
                    called += 1
                    Result(publicationId = "pub-Y", value = 2)
                },
            )

            assertThat(called).isEqualTo(2)
        }
    }

    @Test
    fun `malformed collision — same key with different payload returns cached result`() {
        runBlocking {
            val cachedJson = objectMapper.writeValueAsString(Result(publicationId = "pub-X", value = 42))
            coEvery { repository.find(any(), any(), any(), any()) } returns cachedJson

            var called = 0
            val result = guard.guard(
                workspaceId = "ws-1",
                principalId = "user-1",
                toolName = "create_publication",
                idempotencyKey = "shared-key",
                type = Result::class.java,
                execute = {
                    called += 1
                    Result(publicationId = "DIFFERENT-PUB", value = 999)
                },
            )

            assertThat(result.publicationId).isEqualTo("pub-X")
            assertThat(result.value).isEqualTo(42)
            assertThat(called).isEqualTo(0)
        }
    }

    data class Result(val publicationId: String, val value: Int)
}
