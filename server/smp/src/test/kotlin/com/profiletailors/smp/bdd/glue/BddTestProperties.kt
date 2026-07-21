package com.profiletailors.smp.bdd.glue

/**
 * Shared test property constants reused across BDD Spring Boot configuration classes.
 *
 * These are declared as top-level [const val] so they can be referenced inside
 * annotation arguments (e.g. [org.springframework.boot.test.context.SpringBootTest]).
 */
object BddTestProperties {
    const val LINKEDIN_CLIENT_ID = "publishing.linkedin.client-id=test-client-id"
    const val LINKEDIN_CLIENT_SECRET = "publishing.linkedin.client-secret=test-client-secret"
    const val LINKEDIN_REDIRECT_URI = "publishing.linkedin.redirect-uri=http://localhost:9999/callback"
}
