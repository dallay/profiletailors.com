package com.profiletailors.smp.shortlinks.infrastructure.persistence

import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.shortlinks.domain.DestinationUrl
import com.profiletailors.smp.shortlinks.domain.DomainId
import com.profiletailors.smp.shortlinks.domain.Link
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkStatus
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.shortlinks.domain.ShortCode
import com.profiletailors.smp.shortlinks.domain.ShortCodeCollisionException
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.liquibase.enabled=true",
        "spring.main.allow-bean-definition-overriding=true",
        "management.endpoint.health.group.readiness.include=readinessState",
        "management.endpoint.health.group.liveness.include=livenessState",
        "platform.storage.default=local",
        "platform.storage.providers.local.type=local",
        "platform.storage.providers.local.endpoint=http://localhost:9000",
        "platform.storage.providers.local.bucket=profile-tailors-test",
        "platform.storage.providers.local.region=us-east-1",
        "platform.storage.providers.local.access-key=test-access-key",
        "platform.storage.providers.local.secret-key=test-secret-key",
        "platform.storage.providers.local.path-style-access=true",
        "platform.storage.providers.local.public-base-url=http://localhost:9000/profile-tailors-test",
    ],
)
@Import(IntegrationTestBase.SharedTestConfiguration::class, TestStorageConfiguration::class)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class R2dbcShortLinksPostgresIntegrationTest : PostgresIntegrationTestBase() {
    override val postgresContainer: PostgreSQLContainer<*> = postgres

    @jakarta.annotation.Resource
    private lateinit var linkRepository: R2dbcLinkRepository

    @jakarta.annotation.Resource
    private lateinit var linkFinderRepository: R2dbcLinkFinderRepository

    @jakarta.annotation.Resource
    private lateinit var idempotencyAdapter: R2dbcIdempotencyAdapter

    @jakarta.annotation.Resource
    private lateinit var clickRecorder: R2dbcRedirectClickRecorder

    override suspend fun seedScenario() = Unit

    @Test
    fun `migration creates unique redirect key and durable owner scoped idempotency`() = runTest {
        val link = link(shortCode = "Ab123")
        linkRepository.save(link)
        assertEquals(link, linkRepository.findById(link.id, link.ownerId))
        assertNull(linkRepository.findById(link.id, OwnerId(UUID.randomUUID())))
        assertThrowsCollision { linkRepository.save(link.copy(id = LinkId.generate())) }
        assertThrowsDuplicate { linkRepository.save(link(shortCode = "Other1").copy(id = link.id)) }

        assertTrue(idempotencyAdapter.claim("request-1", link.ownerId, "payload-a"))
        assertFalse(idempotencyAdapter.claim("request-1", link.ownerId, "payload-a"))
        assertNull(idempotencyAdapter.findStoredResult("request-1", OwnerId(UUID.randomUUID())))
        idempotencyAdapter.store("request-1", link.ownerId, "payload-a", "result-a")
        val stored = idempotencyAdapter.findStoredResult("request-1", link.ownerId)
        assertNotNull(stored)
        assertEquals("payload-a", stored?.payloadHash?.trim())
        assertEquals("result-a", stored?.resultJson)
    }

    @Test
    fun `records each resolved redirect against its link`() = runTest {
        val link = link(shortCode = "ClickMe1")
        linkRepository.save(link)

        clickRecorder.record(link.id)
        clickRecorder.record(link.id)

        val rows = databaseClient.sql("SELECT id FROM link_clicks WHERE link_id = :linkId")
            .bind("linkId", link.id.value)
            .fetch()
            .all()
            .collectList()
            .awaitSingle()

        assertEquals(2, rows.size)
    }

    @Test
    fun `cleanup removes click records before their links`() = runTest {
        val link = link(shortCode = "Cleanup1")
        linkRepository.save(link)
        clickRecorder.record(link.id)

        cleanupStatements().forEach { statement ->
            databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
        }

        val clickCount = databaseClient.sql("SELECT COUNT(*) AS count FROM link_clicks")
            .map { row, _ -> requireNotNull(row.get("count", Long::class.javaObjectType)) }
            .one()
            .awaitSingle()
        val linkCount = databaseClient.sql("SELECT COUNT(*) AS count FROM links")
            .map { row, _ -> requireNotNull(row.get("count", Long::class.javaObjectType)) }
            .one()
            .awaitSingle()

        assertEquals(0, clickCount)
        assertEquals(0, linkCount)
    }

    @Test
    fun `concurrent idempotency claims have a single winner`() = runTest {
        val owner = OwnerId(UUID.randomUUID())
        val results = (1..8).map {
            async { idempotencyAdapter.claim("concurrent-request", owner, "payload") }
        }.awaitAll()

        assertEquals(1, results.count { it })
    }

    @Test
    fun `versioned update is restricted to owner and expected version`() = runTest {
        val initial = link(shortCode = "Update1")
        linkRepository.save(initial)
        val updated = initial.copy(
            destinationUrl = DestinationUrl("https://changed.example/path"),
            updatedAt = initial.updatedAt.plusSeconds(1),
            version = initial.version + 1,
        )

        assertFalse(linkRepository.updateWithVersion(updated, 0, OwnerId(UUID.randomUUID())))
        assertFalse(linkRepository.updateWithVersion(updated, 1, initial.ownerId))
        assertTrue(linkRepository.updateWithVersion(updated, 0, initial.ownerId))
        assertEquals(updated, linkRepository.findById(initial.id, initial.ownerId))
    }

    @Test
    fun `finder resolves by domain and short code`() = runTest {
        val link = link(shortCode = "FindMe1")
        linkRepository.save(link)

        assertEquals(link, linkFinderRepository.findByDomainAndShortCode(link.domainId, link.shortCode))
        assertNull(linkFinderRepository.findByDomainAndShortCode(link.domainId, ShortCode("Nope0000")))
        assertNull(
            linkFinderRepository.findByDomainAndShortCode(DomainId.fromHost("other.example"), link.shortCode),
        )
    }

    @Test
    fun `finder pages owner links by creation cursor`() = runTest {
        val owner = OwnerId(UUID.randomUUID())
        val base = Instant.parse("2026-01-01T00:00:00Z")
        val codes = listOf("Page0001", "Page0002", "Page0003")
        codes.forEachIndexed { index, code ->
            linkRepository.save(link(shortCode = code).copy(ownerId = owner, createdAt = base.plusSeconds(index * 60L)))
        }

        val firstPage = linkFinderRepository.findByOwner(owner, 2, null)

        assertEquals(listOf("Page0003", "Page0002"), firstPage.map { it.shortCode.value })
        val secondPage = linkFinderRepository.findByOwner(owner, 2, base.plusSeconds(60L))

        assertEquals(listOf("Page0001"), secondPage.map { it.shortCode.value })
        assertEquals(
            emptyList<String>(),
            linkFinderRepository.findByOwner(OwnerId(UUID.randomUUID()), 2, null)
                .map { it.shortCode.value },
        )
    }

    private suspend fun assertThrowsCollision(action: suspend () -> Unit) {
        var thrown = false
        try {
            action()
        } catch (_: ShortCodeCollisionException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    private suspend fun assertThrowsDuplicate(action: suspend () -> Unit) {
        var thrown = false
        try {
            action()
        } catch (_: DuplicateKeyException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    private fun link(shortCode: String): Link {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        return Link(
            id = LinkId.generate(),
            ownerId = OwnerId(UUID.randomUUID()),
            domainId = DomainId.fromHost("short.example"),
            shortCode = ShortCode(shortCode),
            destinationUrl = DestinationUrl("https://destination.example/path"),
            status = LinkStatus.ACTIVE,
            expiresAt = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            version = 0,
        )
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer()

        @DynamicPropertySource
        @JvmStatic
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}
