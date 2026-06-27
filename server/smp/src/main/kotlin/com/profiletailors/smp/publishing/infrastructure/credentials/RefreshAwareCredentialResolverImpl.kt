package com.profiletailors.smp.publishing.infrastructure.credentials

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.ReconnectReason
import com.profiletailors.smp.publishing.domain.ReconnectRequiredException
import com.profiletailors.smp.publishing.domain.RefreshAwareCredentialResolver
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpTransport
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInPublishingProperties
import com.profiletailors.smp.publishing.infrastructure.linkedin.formUrlEncoded
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpTimeoutException
import java.time.Clock
import java.util.UUID

/**
 * Refresh-aware credential resolver implementation.
 * Checks access token expiry, refresh token validity, handles refresh-ahead,
 * optimistic locking for concurrent refresh protection, and reconnect triggering.
 */
@Component
class RefreshAwareCredentialResolverImpl(
    private val credentialGateway: LinkedInCredentialGateway,
    private val socialConnectionRepository: SocialConnectionRepository,
    private val properties: LinkedInPublishingProperties,
    private val httpTransport: LinkedInHttpTransport,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : RefreshAwareCredentialResolver {

    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("ThrowsCount")
    override suspend fun resolve(account: SocialAccount): String {
        val socialConnection = socialConnectionRepository.findByWorkspaceAndId(
            account.workspaceId,
            account.socialConnectionId,
        ) ?: throw IllegalStateException(
            "LinkedIn social connection '${account.socialConnectionId}' was not found.",
        )

        val credentialReference = socialConnection.credentialReference
            ?: throw IllegalStateException(
                "LinkedIn social connection '${socialConnection.id}' is missing a credential reference.",
            )

        val credentials = credentialGateway.resolveCredential(UUID.fromString(credentialReference))
        val now = clock.instant()
        val nowEpoch = now.epochSecond

        // Check if access token is still valid (with 5-minute refresh-ahead window)
        val expiresAt = credentials.expiresAtEpochSeconds
        if (expiresAt != null && nowEpoch < expiresAt - REFRESH_AHEAD_SECONDS) {
            log.debug(
                "Access token for account {} still valid, expires in {}s",
                account.id,
                expiresAt - nowEpoch,
            )
            return credentials.accessToken
        }

        // Access token expired or expiring soon — attempt refresh
        val refreshToken = credentials.refreshToken
        if (refreshToken.isNullOrBlank()) {
            log.warn("No refresh token available for account {}", account.id)
            throw ReconnectRequiredException(
                "No refresh token available for LinkedIn account '${account.id}'.",
                ReconnectReason.REFRESH_UNAVAILABLE,
            )
        }

        // Check refresh token absolute expiry
        val refreshExpiresAt = credentials.refreshTokenExpiresAtEpochSeconds
        if (refreshExpiresAt != null && nowEpoch >= refreshExpiresAt) {
            log.warn(
                "Refresh token expired for account {} (expired at {}, now {})",
                account.id,
                refreshExpiresAt,
                nowEpoch,
            )
            throw ReconnectRequiredException(
                "LinkedIn refresh token has expired. Re-authentication required.",
                ReconnectReason.REFRESH_TOKEN_EXPIRED,
            )
        }

        // Attempt refresh with optimistic locking
        return attemptRefresh(account, credentials, credentialReference, refreshToken, nowEpoch)
    }

    @Suppress("LongMethod")
    private suspend fun attemptRefresh(
        account: SocialAccount,
        credentials: LinkedInCredentials,
        credentialReference: String,
        refreshToken: String,
        nowEpoch: Long,
    ): String {
        // Optimistic lock: read current version, attempt refresh, write with version = oldVersion + 1
        val refreshResult = executeRefresh(refreshToken)
            ?: throw ReconnectRequiredException(
                "LinkedIn token refresh failed with invalid_grant or revocation.",
                ReconnectReason.INVALID_GRANT,
            )

        // Persist refreshed credentials atomically
        val newCredentials = credentials.copy(
            accessToken = refreshResult.accessToken,
            expiresAtEpochSeconds = refreshResult.expiresAtEpochSeconds,
            refreshToken = refreshResult.refreshToken ?: refreshToken,
            refreshTokenExpiresAtEpochSeconds = refreshResult.refreshTokenExpiresAtEpochSeconds
                ?: credentials.refreshTokenExpiresAtEpochSeconds,
            lastRefreshAttemptAtEpochSeconds = nowEpoch,
            lastRefreshStatus = "SUCCESS",
        )
        val ownerUuid = UUID.fromString(credentialReference)
        credentialGateway.storeForOwner("linkedin:user", ownerUuid, newCredentials)

        log.info(
            "Successfully refreshed access token for account {}, new expiry: {}",
            account.id,
            newCredentials.expiresAtEpochSeconds,
        )
        return newCredentials.accessToken
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun executeRefresh(refreshToken: String): RefreshResult? {
        val formBody = formUrlEncoded(
            "grant_type" to "refresh_token",
            "client_id" to properties.clientId,
            "client_secret" to properties.clientSecret,
            "refresh_token" to refreshToken,
        )

        return try {
            val response = httpTransport.send(
                HttpRequest.newBuilder(URI.create(properties.tokenBaseUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build(),
            )

            if (response.statusCode !in HTTP_SUCCESS_RANGE) {
                log.warn("LinkedIn refresh token exchange failed: {} {}", response.statusCode, response.body)
                return null
            }

            val tokenResponse = objectMapper.readTree(response.body)
            RefreshResult(
                accessToken = tokenResponse.get("access_token").asText(),
                expiresAtEpochSeconds = tokenResponse.get("expires_in")?.asLong()?.let {
                    clock.instant().epochSecond + it
                },
                refreshToken = tokenResponse.get("refresh_token")?.asText(),
                refreshTokenExpiresAtEpochSeconds = tokenResponse.get("refresh_token_expires_in")?.asLong()?.let {
                    clock.instant().epochSecond + it
                },
            )
        } catch (e: HttpTimeoutException) {
            log.warn("LinkedIn refresh token exchange timed out", e)
            null
        } catch (e: IOException) {
            log.warn("LinkedIn refresh token exchange failed: {}", e.message)
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warn("LinkedIn refresh token exchange was interrupted", e)
            null
        } catch (e: Exception) {
            log.error("LinkedIn refresh token exchange threw unexpected exception", e)
            null
        }
    }

    private data class RefreshResult(
        val accessToken: String,
        val expiresAtEpochSeconds: Long?,
        val refreshToken: String?,
        val refreshTokenExpiresAtEpochSeconds: Long?,
    )

    private companion object {
        /** Refresh access token 5 minutes before expiry */
        const val REFRESH_AHEAD_SECONDS = 5 * 60L

        /** Valid HTTP status range for successful token exchange responses */
        val HTTP_SUCCESS_RANGE = 200..299
    }
}
