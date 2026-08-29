package com.profiletailors.smp.mcp.infrastructure.security

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipAccessChecker
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class McpWorkspaceMembershipCheckerTest {

    private val accessChecker: WorkspaceMembershipAccessChecker = mockk()
    private val checker = McpWorkspaceMembershipChecker(accessChecker)

    @Test
    fun `returns true when the principal is an active member`() {
        runBlocking {
            coEvery { accessChecker.isActiveMember(any(), any()) } returns true

            val result = checker.checkMembership("ws-1", "user-1").awaitSingleOrNull()
            assertThat(result).isTrue()
        }
    }

    @Test
    fun `returns false when the principal is not a member`() {
        runBlocking {
            coEvery { accessChecker.isActiveMember(any(), any()) } returns false

            val result = checker.checkMembership("ws-1", "user-1").awaitSingleOrNull()
            assertThat(result).isFalse()
        }
    }

    @Test
    fun `returns false when the lookup throws — fail closed on errors`() {
        runBlocking {
            coEvery { accessChecker.isActiveMember(any(), any()) } throws RuntimeException("db unreachable")

            val result = checker.checkMembership("ws-1", "user-1").awaitSingleOrNull()
            assertThat(result).isFalse()
        }
    }

    @Test
    fun `no longer returns Mono just true as a stub`() {
        runBlocking {
            coEvery { accessChecker.isActiveMember(any(), any()) } returns false

            val stub = McpWorkspaceMembershipChecker(accessChecker)
            assertThat(stub).isNotNull
            val memberResult = stub.checkMembership("ws-1", "user-1").awaitSingleOrNull()
            assertThat(memberResult).isFalse()
        }
    }

    @Test
    fun `passes workspace context to the membership checker`() {
        runBlocking {
            var capturedContext: ResourceContext? = null
            coEvery { accessChecker.isActiveMember(any(), any()) } answers {
                capturedContext = secondArg()
                true
            }

            checker.checkMembership("ws-A", "user-1").awaitSingleOrNull()

            assertThat(capturedContext).isNotNull
            assertThat(capturedContext!!.workspaceId).isEqualTo("ws-A")
        }
    }
}
