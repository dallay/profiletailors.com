package com.profiletailors.common.domain.context

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PrincipalContextTest {

    @Test
    fun `should create with required fields`() {
        val ctx = PrincipalContext(
            principalId = "user-1",
            principalType = PrincipalType.USER,
            subject = "user@example.com",
        )

        assertThat(ctx.principalId).isEqualTo("user-1")
        assertThat(ctx.principalType).isEqualTo(PrincipalType.USER)
        assertThat(ctx.subject).isEqualTo("user@example.com")
    }

    @Test
    fun `should default optional fields`() {
        val ctx = PrincipalContext(
            principalId = "user-1",
            principalType = PrincipalType.USER,
            subject = "user@example.com",
        )

        assertThat(ctx.provider).isNull()
        assertThat(ctx.displayIdentity).isNull()
        assertThat(ctx.authenticationMethod).isNull()
        assertThat(ctx.issuedCredentialReference).isNull()
        assertThat(ctx.attributes).isEmpty()
    }

    @Test
    fun `should support data class equality`() {
        val ctx1 = PrincipalContext("id", PrincipalType.USER, "sub")
        val ctx2 = PrincipalContext("id", PrincipalType.USER, "sub")

        assertThat(ctx1).isEqualTo(ctx2)
        assertThat(ctx1.hashCode()).isEqualTo(ctx2.hashCode())
    }

    @Test
    fun `should distinguish different contexts`() {
        val ctx1 = PrincipalContext("id-1", PrincipalType.USER, "sub")
        val ctx2 = PrincipalContext("id-2", PrincipalType.USER, "sub")

        assertThat(ctx1).isNotEqualTo(ctx2)
    }
}
