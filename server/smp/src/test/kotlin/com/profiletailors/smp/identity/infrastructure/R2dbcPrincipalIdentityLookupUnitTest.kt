package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import com.profiletailors.smp.identity.domain.UserAccountState
import io.kotest.assertions.throwables.shouldThrow
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
import java.util.function.BiFunction

class R2dbcPrincipalIdentityLookupUnitTest {

    private val databaseClient = mockk<DatabaseClient>()
    private val executeSpec = mockk<DatabaseClient.GenericExecuteSpec>()
    private val lookup = R2dbcPrincipalIdentityLookup(databaseClient)

    init {
        every { databaseClient.sql(any<String>()) } returns executeSpec
        every { executeSpec.bind(any<String>(), any<Any>()) } returns executeSpec
    }

    @Test
    fun `findByEmail maps every principal identity field`() = runTest {
        stubPrincipalQuery(userRow())

        val facts = lookup.findByEmail("person@example.com")

        assertEquals(
            PrincipalIdentityFacts(
                principalId = "principal-1",
                principalType = PrincipalType.USER,
                subject = "local:person@example.com",
                provider = null,
                displayIdentity = "person",
                email = "person@example.com",
                username = "person",
                emailStatus = EmailStatus.VERIFIED,
                accountState = UserAccountState.ACTIVE,
            ),
            facts,
        )
        verify { executeSpec.bind("email", "person@example.com") }
    }

    @Test
    fun `findByPrincipalId maps nullable user fields for a service account`() = runTest {
        val row = principalRow(
            principalType = PrincipalType.SERVICE_ACCOUNT,
            provider = "https://issuer.example",
            displayIdentity = "scheduler",
            email = null,
            username = null,
            emailStatus = null,
        )
        stubPrincipalQuery(row)

        val facts = lookup.findByPrincipalId("principal-1")

        assertEquals(PrincipalType.SERVICE_ACCOUNT, facts?.principalType)
        assertEquals("https://issuer.example", facts?.provider)
        assertEquals("scheduler", facts?.displayIdentity)
        assertNull(facts?.email)
        assertNull(facts?.username)
        assertNull(facts?.emailStatus)
        verify { executeSpec.bind("principalId", "principal-1") }
    }

    @Test
    fun `findBySubject maps a principal with a provider`() = runTest {
        stubPrincipalQuery(userRow(provider = "https://issuer.example"))

        val facts = lookup.findBySubject(
            principalType = PrincipalType.USER,
            subject = "external-subject",
            provider = "https://issuer.example",
        )

        assertEquals("principal-1", facts?.principalId)
        verify { executeSpec.bind("principalType", PrincipalType.USER.name) }
        verify { executeSpec.bind("subject", "external-subject") }
        verify { executeSpec.bind("provider", "https://issuer.example") }
    }

    @Test
    fun `findBySubject does not bind a provider for local identities`() = runTest {
        stubPrincipalQuery(userRow())

        val facts = lookup.findBySubject(
            principalType = PrincipalType.USER,
            subject = "local:person@example.com",
            provider = null,
        )

        assertEquals("principal-1", facts?.principalId)
        verify(exactly = 0) { executeSpec.bind("provider", any<Any>()) }
    }

    @Test
    fun `findByEmail returns null when the query has no row`() = runTest {
        val rowsSpec = mockk<RowsFetchSpec<PrincipalIdentityFacts>>()
        every { executeSpec.map(any<BiFunction<Row, RowMetadata, PrincipalIdentityFacts>>()) } returns rowsSpec
        every { rowsSpec.one() } returns Mono.empty()

        val facts = lookup.findByEmail("missing@example.com")

        assertNull(facts)
    }

    @Test
    fun `findByEmail rejects a row without its required principal type`() = runTest {
        val row = userRow()
        every { row.get("principal_type", String::class.java) } returns null
        stubPrincipalQuery(row)

        shouldThrow<IllegalArgumentException> {
            lookup.findByEmail("person@example.com")
        }
    }

    private fun stubPrincipalQuery(row: Row) {
        val rowsSpec = mockk<RowsFetchSpec<PrincipalIdentityFacts>>()
        val mapper = slot<BiFunction<Row, RowMetadata, PrincipalIdentityFacts>>()
        every { executeSpec.map(capture(mapper)) } returns rowsSpec
        every { rowsSpec.one() } answers {
            Mono.just(mapper.captured.apply(row, mockk()))
        }
    }

    private fun userRow(provider: String? = null): Row = principalRow(
        principalType = PrincipalType.USER,
        provider = provider,
        displayIdentity = "person",
        email = "person@example.com",
        username = "person",
        emailStatus = EmailStatus.VERIFIED,
    )

    private fun principalRow(
        principalType: PrincipalType,
        provider: String?,
        displayIdentity: String?,
        email: String?,
        username: String?,
        emailStatus: EmailStatus?,
    ): Row = mockk<Row>().also { row ->
        every { row.get("principal_type", String::class.java) } returns principalType.name
        every { row.get("id", String::class.java) } returns "principal-1"
        every { row.get("subject", String::class.java) } returns "local:person@example.com"
        every { row.get("provider", String::class.java) } returns provider
        every { row.get("display_identity", String::class.java) } returns displayIdentity
        every { row.get("email", String::class.java) } returns email
        every { row.get("username", String::class.java) } returns username
        every { row.get("email_status", String::class.java) } returns emailStatus?.name
        every { row.get("account_state", String::class.java) } returns UserAccountState.ACTIVE.name
    }

}
