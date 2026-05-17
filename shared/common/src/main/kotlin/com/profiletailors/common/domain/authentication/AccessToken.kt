package com.profiletailors.common.domain.authentication

import com.profiletailors.common.domain.bus.query.Response

data class AccessToken(
    val token: String,
    val expiresIn: Long,
    val refreshToken: String,
    val refreshExpiresIn: Long,
    val tokenType: String,
    val notBeforePolicy: Int? = null,
    val sessionState: String? = null,
    val scope: String? = null
) : Response
