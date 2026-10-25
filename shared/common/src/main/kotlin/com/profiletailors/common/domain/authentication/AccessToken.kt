package com.profiletailors.common.domain.authentication

import com.profiletailors.common.domain.ValueObject
import com.profiletailors.common.domain.bus.query.Response

@ValueObject
data class AccessToken(
    val token: String,
    val expiresIn: Long,
    val refreshToken: String,
    val refreshExpiresIn: Long,
    val tokenType: String,
    val notBeforePolicy: Int? = null,
    val sessionState: String? = null,
    val scope: String? = null,
) : Response {
    init {
        require(token.isNotBlank()) { "Token must not be blank" }
        require(tokenType.isNotBlank()) { "Token type must not be blank" }
    }
}
