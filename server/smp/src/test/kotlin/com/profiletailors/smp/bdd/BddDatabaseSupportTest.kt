package com.profiletailors.smp.bdd

import com.profiletailors.smp.bdd.glue.BddDatabaseSupport
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BddDatabaseSupportTest {
    @Test
    fun `deletes social content posts before social accounts`() {
        val statements = BddDatabaseSupport.BddDatabaseCleanup.statements
        val postsIndex = statements.indexOf("DELETE FROM social_content_posts")
        val accountsIndex = statements.indexOf("DELETE FROM social_accounts")

        assertTrue(postsIndex >= 0, "social_content_posts cleanup statement should exist")
        assertTrue(accountsIndex >= 0, "social_accounts cleanup statement should exist")
        assertTrue(
            postsIndex < accountsIndex,
            "social_content_posts must be deleted before social_accounts",
        )
    }
}
