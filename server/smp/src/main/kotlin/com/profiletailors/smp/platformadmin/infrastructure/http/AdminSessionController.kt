package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.ports.AdminUserQuery
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/session")
class AdminSessionController(
    private val operatorAccessResolver: OperatorAccessResolver,
    private val userQuery: AdminUserQuery,
    private val requestContextStore: RequestContextStore,
) {
    @GetMapping
    suspend fun getSession(): ResponseEntity<AdminSessionResponse> {
        val ctx = requestContextStore.currentPrincipalContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val operator = operatorAccessResolver.resolve(ctx)

        if (operator.roles.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val user = userQuery.findById(ctx.principalId)

        return ResponseEntity.ok(
            AdminSessionResponse(
                principalId = ctx.principalId,
                email = user?.email ?: ctx.subject,
                displayName = user?.displayIdentity,
                platformRoles = operator.roles.map { it.name },
            ),
        )
    }

    data class AdminSessionResponse(
        val principalId: String,
        val email: String,
        val displayName: String?,
        val platformRoles: List<String>,
    )
}
