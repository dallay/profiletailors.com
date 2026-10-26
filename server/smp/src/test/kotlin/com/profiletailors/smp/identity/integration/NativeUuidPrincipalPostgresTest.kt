package com.profiletailors.smp.identity.integration

import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.infrastructure.R2dbcIdentityRegistrationGateway
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.util.UUID

@Testcontainers
@Tag("postgres")
class NativeUuidPrincipalPostgresTest : PostgresDatabaseTestBase() {
    @Container
    override val postgres = PostgresTestContainerSupport.newContainer("native_uuid_principals")

    @Test
    fun `identity can be created with native UUID principal references`() = runTest {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DROP TABLE user_identities CASCADE")
                statement.execute("DROP TABLE principals CASCADE")
                statement.execute(
                    "CREATE TABLE principals (id UUID PRIMARY KEY, principal_type TEXT, subject TEXT, " +
                        "provider TEXT, display_identity TEXT)",
                )
                statement.execute(
                    "CREATE TABLE user_identities (principal_id UUID REFERENCES principals(id), email TEXT, " +
                        "username TEXT, email_status TEXT)",
                )
            }
        }
        val id = UUID.randomUUID()
        R2dbcIdentityRegistrationGateway(databaseClient).createUserIdentity(
            id.toString(),
            "external-subject",
            "person@example.test",
            "person",
            null,
            "person",
            EmailStatus.PENDING,
        )
        val stored = databaseClient.sql("SELECT principal_id FROM user_identities WHERE principal_id = :id")
            .bind("id", id)
            .map { row, _ -> requireNotNull(row.get("principal_id", UUID::class.java)) }
            .one().awaitSingle()
        assertEquals(id, stored)
    }
}
