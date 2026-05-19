package com.profiletailors.smp.authorization.infrastructure.http

import com.profiletailors.smp.authorization.application.GetResourcePreviewQuery
import com.profiletailors.smp.authorization.application.ResourcePreview
import com.profiletailors.smp.platform.application.Mediator
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/authorization/resources")
class ResourcePreviewController(
    private val mediator: Mediator,
) {

    @GetMapping("/{resourceId}/preview")
    suspend fun getResourcePreview(
        @PathVariable resourceId: String,
    ): ResourcePreview = mediator.dispatch(GetResourcePreviewQuery(resourceId))
}
