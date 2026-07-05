package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.LocalPasswordCredentialGateway
import com.profiletailors.smp.identity.application.PasswordHasher
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Seeds the local password credential for the dev user when the `local-fixtures` profile is active.
 *
 * The password is intentionally hardcoded because this runner ONLY activates under `local-fixtures`.
 * No hash is stored in version control — the hash is generated at runtime using the active [PasswordHasher].
 */
@Component
@Profile("local-fixtures")
class LocalFixturesRunner(
    private val credentialGateway: LocalPasswordCredentialGateway,
    private val passwordHasher: PasswordHasher,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(LocalFixturesRunner::class.java)

    override fun run(args: ApplicationArguments) {
        kotlinx.coroutines.runBlocking { seedDevCredential() }
    }

    internal suspend fun seedDevCredential() {
        val existing = credentialGateway.findByEmail(DEV_EMAIL)
        if (existing != null) {
            log.info("Local fixture credential for {} already exists — skipping.", DEV_EMAIL)
            return
        }

        val hash = passwordHasher.hash(DEV_PASSWORD)
        credentialGateway.create(DEV_PRINCIPAL_ID, hash)
        log.info(
            "Local fixture credential created for {} (principal={}, algorithm={}).",
            DEV_EMAIL,
            DEV_PRINCIPAL_ID,
            passwordHasher.algorithm,
        )
    }

    private companion object {
        private const val DEV_EMAIL = "dev@profiletailors.com"
        private const val DEV_PRINCIPAL_ID = "dev-user-001"
        private const val DEV_PASSWORD = "S3cr3tP@ssw0rd*123"
    }
}
