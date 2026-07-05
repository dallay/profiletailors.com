package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.bus.query.Query

/**
 * Query to get the profile of the current authenticated user.
 */
class GetCurrentUserProfileQuery : Query<CurrentUserProfile>
