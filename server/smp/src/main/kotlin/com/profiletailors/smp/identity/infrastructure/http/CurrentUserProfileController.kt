package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.smp.identity.application.CurrentUserProfile
import com.profiletailors.smp.identity.application.GetCurrentUserProfileService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class CurrentUserProfileController(
    private val service: GetCurrentUserProfileService,
) {

    @GetMapping("/me")
    suspend fun currentUser(): CurrentUserProfile = service.execute()
}
