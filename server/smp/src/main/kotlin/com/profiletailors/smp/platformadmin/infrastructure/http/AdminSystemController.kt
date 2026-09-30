package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/system")
class AdminSystemController(
    private val buildInfo: BackendBuildInfo,
    private val operatorAccessResolver: OperatorAccessResolver,
    private val requestContextStore: RequestContextStore,
) {
    @GetMapping("/build-info")
    suspend fun getBuildInfo(): ResponseEntity<BackendBuildInfoResponse> {
        val context = requestContextStore.currentPrincipalContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val operator = operatorAccessResolver.resolve(context)
        if (PlatformPermission.SYSTEM_BUILD_INFO_READ !in operator.roles.effectivePermissions()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return ResponseEntity.ok(
            BackendBuildInfoResponse(
                service = "smp",
                version = buildInfo.version,
                revision = buildInfo.revision,
                builtAt = buildInfo.builtAt,
            ),
        )
    }
}

data class BackendBuildInfoResponse(
    val service: String,
    val version: String,
    val revision: String,
    val builtAt: String,
)
