package com.profiletailors.smp.authorization.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.authorization.application.resource.getpreview.GetResourcePreviewQuery
import com.profiletailors.smp.authorization.application.resource.getpreview.ResourcePreview
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(value = ["/api/authorization/resources"])
@Tag(name = "Resource Preview", description = "Resource preview and metadata endpoints")
class ResourcePreviewController(private val mediator: Mediator) {
    @Operation(summary = "Get resource preview by ID")
    @GetMapping("/{resourceId}/preview", version = "1")
    suspend fun getResourcePreview(
        @Parameter(description = "Resource id", example = "res_abc123xyz")
        @PathVariable resourceId: String,
    ): ResourcePreview = mediator.send(GetResourcePreviewQuery(resourceId))
}
