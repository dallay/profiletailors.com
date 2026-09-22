package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.EmailVerificationTokenData
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.RowsFetchSpec
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.function.BiFunction

class R2dbcIdentityRegistrationGatewayUnitTest {

    private val databaseClient = mockk<DatabaseClient>()
    private val executeSpec = mockk<DatabaseClient.GenericExecuteSpec>()
    private val gateway = R2dbcIdentityRegistrationGateway(databaseClient)

    init {
        every { databaseClient.sql(any<String>()) } returns executeSpec
        every { executeSpec.bind(any<String>(), any<Any>()) } returns executeSpec
    }

    @Test
    fun `verifyEmailToken maps every token field`() = runTest {
        val expiresAt = Instant.parse("2026-09-22T10:00:00Z")
        val usedAt = Instant.parse("2026-09-22T09:30:00Z")
        val row = tokenRow(expiresAt = expiresAt, usedAt = usedAt)
        stubTokenQuery(row)

        val token = gateway.verifyEmailToken("token-hash")

        assertEquals(
            EmailVerificationTokenData(
                email = "person@example.com",
                tokenHash = "token-hash",
                expiresAt = expiresAt,
                usedAt = usedAt,
            ),
            token,
        )
        verify { executeSpec.bind("tokenHash", "token-hash") }
    }

    @Test
    fun `findActiveTokenByEmail maps an unused token`() = runTest {
        val expiresAt = Instant.parse("2026-09-22T10:00:00Z")
        val row = tokenRow(expiresAt = expiresAt, usedAt = null)
        stubTokenQuery(row)

        val token = gateway.findActiveTokenByEmail("person@example.com")

        assertEquals("person@example.com", token?.email)
        assertEquals("token-hash", token?.tokenHash)
        assertEquals(expiresAt, token?.expiresAt)
        assertNull(token?.usedAt)
        verify { executeSpec.bind("email", "person@example.com") }
    }

    @Test
    fun `verifyEmailToken returns null when the query has no row`() = runTest {
        val rowsSpec = mockk<RowsFetchSpec<EmailVerificationTokenData>>()
        every { executeSpec.map(any<BiFunction<Row, RowMetadata, EmailVerificationTokenData>>()) } returns rowsSpec
        every { rowsSpec.one() } returns Mono.empty()

        val token = gateway.verifyEmailToken("missing-token")

        assertNull(token)
    }

    private fun stubTokenQuery(row: Row) {
        val rowsSpec = mockk<RowsFetchSpec<EmailVerificationTokenData>>()
        val mapper = slot<BiFunction<Row, RowMetadata, EmailVerificationTokenData>>()
        every { executeSpec.map(capture(mapper)) } returns rowsSpec
        every { rowsSpec.one() } answers {
            Mono.just(mapper.captured.apply(row, mockk()))
        }
    }

    private fun tokenRow(expiresAt: Instant, usedAt: Instant?): Row = mockk<Row>().also { row ->
        every { row.get("email", String::class.java) } returns "person@example.com"
        every { row.get("token_hash", String::class.java) } returns "token-hash"
        every { row.get("expires_at", Instant::class.java) } returns expiresAt
        every { row.get("used_at", Instant::class.java) } returns usedAt
    }

}
