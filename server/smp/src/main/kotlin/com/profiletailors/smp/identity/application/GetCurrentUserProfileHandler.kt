package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.QueryHandler

@Service
internal class GetCurrentUserProfileHandler(
    private val service: GetCurrentUserProfileService,
) : QueryHandler<GetCurrentUserProfileQuery, CurrentUserProfile> {
    override suspend fun handle(query: GetCurrentUserProfileQuery): CurrentUserProfile =
        service.execute()
}
