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

    @Test
    fun `deletes social content comments before social content posts`() {
        val statements = BddDatabaseSupport.BddDatabaseCleanup.statements
        val commentsIndex = statements.indexOf("DELETE FROM social_content_comments")
        val postsIndex = statements.indexOf("DELETE FROM social_content_posts")

        assertTrue(commentsIndex >= 0, "social_content_comments cleanup statement should exist")
        assertTrue(postsIndex >= 0, "social_content_posts cleanup statement should exist")
        assertTrue(
            commentsIndex < postsIndex,
            "social_content_comments must be deleted before social_content_posts " +
                "(fk_social_content_comments_workspace_post)",
        )
    }
}
