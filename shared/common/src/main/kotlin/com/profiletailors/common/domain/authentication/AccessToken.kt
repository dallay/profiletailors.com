package com.profiletailors.common.domain.authentication

import com.profiletailors.common.domain.bus.query.Response

/**
 * OAuth2 token response from the authentication provider.
 *
 * Maps the standard OAuth2 token endpoint response containing the access token,
 * refresh token, expiration details, and optional metadata.
 *
 * @property token the JWT access token string
 * @property expiresIn lifetime of the access token in seconds
 * @property refreshToken the refresh token for obtaining new access tokens
 * @property refreshExpiresIn lifetime of the refresh token in seconds
 * @property tokenType the type of token (typically "Bearer")
 * @property notBeforePolicy timestamp before which the token is not valid
 * @property sessionState optional session identifier
 * @property scope the granted scopes
 * @since 1.0.0
 */
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
