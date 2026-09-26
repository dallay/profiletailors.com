package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OAuthStateReplayStorePostgresTest : PostgresDatabaseTestBase() {
    override val postgres = postgresContainer

    @Test
    fun `should accept a nonce once when separate instances consume concurrently`() = runTest {
        val now = Instant.parse("2026-09-26T12:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val stores = List(2) { R2dbcOAuthStateReplayStore(databaseClient, clock) }
        val nonce = UUID.randomUUID().toString()

        val results = coroutineScope {
            List(8) { index -> async { stores[index % 2].consume(nonce, now.plusSeconds(60)) } }.awaitAll()
        }

        results.count { it } shouldBe 1
        stores[1].consume(nonce, now.plusSeconds(120)) shouldBe false
    }

    @Test
    fun `should clean expired entries when consuming another nonce`() = runTest {
        val now = Instant.parse("2026-09-26T13:00:00Z")
        val nonce = UUID.randomUUID().toString()
        val earlier = R2dbcOAuthStateReplayStore(databaseClient, Clock.fixed(now, ZoneOffset.UTC))
        earlier.consume(nonce, now.plusSeconds(60)) shouldBe true
        val later = R2dbcOAuthStateReplayStore(databaseClient, Clock.fixed(now.plusSeconds(61), ZoneOffset.UTC))

        later.consume(UUID.randomUUID().toString(), now.plusSeconds(120)) shouldBe true
        later.consume(nonce, now.plusSeconds(60)) shouldBe false

        databaseClient.sql("SELECT COUNT(*) AS count FROM publishing_oauth_consumed_states WHERE nonce = :nonce")
            .bind("nonce", nonce)
            .map { row, _ -> requireNotNull(row.get("count", Long::class.javaObjectType)) }
            .one().awaitSingle() shouldBe 0L
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("oauth_replay")
    }
}
