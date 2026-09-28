package com.profiletailors.smp.publishing.infrastructure.credentials

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.ReconnectReason
import com.profiletailors.smp.publishing.domain.ReconnectRequiredException
import com.profiletailors.smp.publishing.domain.RefreshAwareCredentialResolver
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpTransport
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInPublishingProperties
import com.profiletailors.smp.publishing.infrastructure.linkedin.formUrlEncoded
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpTimeoutException
import java.time.Clock
import java.util.UUID

@Component
class RefreshAwareCredentialResolverImpl @Autowired constructor(
    private val credentialGateway: ProviderCredentialGateway,
    private val socialConnectionRepository: SocialConnectionRepository,
    private val httpTransport: LinkedInHttpTransport,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    private val linkedInProperties: LinkedInPublishingProperties? = null,
    private val legacyLinkedInCredentialGateway: LinkedInCredentialGateway? = null,
) : RefreshAwareCredentialResolver {
    private val log = LoggerFactory.getLogger(javaClass)

    constructor(
        credentialGateway: LinkedInCredentialGateway,
        socialConnectionRepository: SocialConnectionRepository,
        properties: LinkedInPublishingProperties,
        httpTransport: LinkedInHttpTransport,
        objectMapper: ObjectMapper,
        clock: Clock,
    ) : this(
        credentialGateway = LegacyProviderCredentialGateway(credentialGateway),
        socialConnectionRepository = socialConnectionRepository,
        httpTransport = httpTransport,
        objectMapper = objectMapper,
        clock = clock,
        linkedInProperties = properties,
    )

    @Suppress("ThrowsCount")
    override suspend fun resolve(account: SocialAccount): String {
        val connection = socialConnectionRepository.findByWorkspaceAndId(
            account.workspaceId,
            account.socialConnectionId,
        ) ?: throw IllegalStateException("${account.provider} social connection was not found.")
        val credentialReference = connection.credentialReference
            ?: throw IllegalStateException("${account.provider} social connection is missing credentials.")
        val credentialId = UUID.fromString(credentialReference)
        val credentials = resolveCredentials(account.provider, credentialId)
        val nowEpoch = clock.instant().epochSecond
        val expiresAt = credentials.expiresAtEpochSeconds
        if (expiresAt == null || nowEpoch < expiresAt - REFRESH_AHEAD_SECONDS) {
            return credentials.accessToken
        }
        val refreshToken = credentials.refreshToken
        if (refreshToken.isNullOrBlank()) {
            throw ReconnectRequiredException(
                "No refresh token is available for ${account.provider} account.",
                ReconnectReason.REFRESH_UNAVAILABLE,
            )
        }
        val refreshExpiresAt = credentials.refreshTokenExpiresAtEpochSeconds
        if (refreshExpiresAt != null && nowEpoch >= refreshExpiresAt) {
            throw ReconnectRequiredException(
                "${account.provider} refresh token has expired.",
                ReconnectReason.REFRESH_TOKEN_EXPIRED,
            )
        }
        val refreshed = executeRefresh(refreshToken, account.provider)
            ?: throw ReconnectRequiredException(
                "${account.provider} token refresh failed.",
                ReconnectReason.INVALID_GRANT,
            )
        val updated = credentials.copy(
            accessToken = refreshed.accessToken,
            expiresAtEpochSeconds = refreshed.expiresAtEpochSeconds,
            refreshToken = refreshed.refreshToken ?: refreshToken,
            refreshTokenExpiresAtEpochSeconds = refreshed.refreshTokenExpiresAtEpochSeconds
                ?: credentials.refreshTokenExpiresAtEpochSeconds,
            lastRefreshAttemptAtEpochSeconds = nowEpoch,
            lastRefreshStatus = REFRESH_SUCCESS,
        )
        storeCredentials(account.provider, credentialId, updated)
        return updated.accessToken
    }

    private suspend fun resolveCredentials(provider: SocialProvider, id: UUID): ProviderCredentials {
        if (provider == SocialProvider.LINKEDIN && legacyLinkedInCredentialGateway != null) {
            return LegacyProviderCredentialGateway(legacyLinkedInCredentialGateway).resolveCredential(id)
        }
        return credentialGateway.resolveCredential(id)
    }

    private suspend fun storeCredentials(provider: SocialProvider, id: UUID, credentials: ProviderCredentials) {
        if (provider == SocialProvider.LINKEDIN && legacyLinkedInCredentialGateway != null) {
            LegacyProviderCredentialGateway(legacyLinkedInCredentialGateway).storeForOwner(
                "linkedin:user",
                id,
                credentials,
            )
            return
        }
        credentialGateway.storeForOwner("${provider.name.lowercase()}:user", id, credentials)
    }

    private suspend fun executeRefresh(refreshToken: String, provider: SocialProvider): RefreshResult? {
        val request = refreshRequest(refreshToken, provider)
        return try {
            val response = httpTransport.send(request)
            if (response.statusCode !in HTTP_SUCCESS_RANGE) {
                log.warn("{} credential refresh failed: status={}", provider, response.statusCode)
                return null
            }
            val tokenResponse = objectMapper.readTree(response.body)
            val accessToken = tokenResponse
                .get("access_token")
                ?.asText()
                ?.takeIf { it.isNotBlank() }
                ?: return null
            RefreshResult(
                accessToken = accessToken,
                expiresAtEpochSeconds = tokenResponse
                    .get("expires_in")
                    ?.asLong()
                    ?.let { clock.instant().epochSecond + it },
                refreshToken = tokenResponse.get("refresh_token")?.asText(),
                refreshTokenExpiresAtEpochSeconds = tokenResponse.get("refresh_token_expires_in")?.asLong()?.let {
                    clock.instant().epochSecond + it
                },
            )
        } catch (exception: HttpTimeoutException) {
            log.warn("{} credential refresh timed out: type={}", provider, exception::class.simpleName)
            null
        } catch (exception: IOException) {
            log.warn("{} credential refresh failed: type={}", provider, exception::class.simpleName)
            null
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warn("{} credential refresh interrupted: type={}", provider, exception::class.simpleName)
            null
        } catch (exception: com.fasterxml.jackson.core.JsonProcessingException) {
            log.warn("{} credential refresh response was malformed: type={}", provider, exception::class.simpleName)
            null
        } catch (exception: IllegalStateException) {
            log.warn("{} credential refresh failed: type={}", provider, exception::class.simpleName)
            null
        }
    }

    private fun refreshRequest(refreshToken: String, provider: SocialProvider): HttpRequest {
        val parts = when (provider) {
            SocialProvider.LINKEDIN -> listOf(
                "grant_type" to "refresh_token",
                "client_id" to requireNotNull(linkedInProperties).clientId,
                "client_secret" to requireNotNull(linkedInProperties).clientSecret,
                "refresh_token" to refreshToken,
            )
            SocialProvider.THREADS -> listOf(
                "grant_type" to "th_refresh_token",
                "access_token" to refreshToken,
            )
        }
        return HttpRequest.newBuilder(URI.create(refreshEndpoint(provider)))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formUrlEncoded(*parts.toTypedArray())))
            .build()
    }

    private fun refreshEndpoint(provider: SocialProvider): String = when (provider) {
        SocialProvider.LINKEDIN -> requireNotNull(linkedInProperties).tokenBaseUrl
        SocialProvider.THREADS -> "https://graph.threads.net/v1.0/refresh_access_token"
    }

    private data class RefreshResult(
        val accessToken: String,
        val expiresAtEpochSeconds: Long?,
        val refreshToken: String?,
        val refreshTokenExpiresAtEpochSeconds: Long?,
    )

    private companion object {
        const val REFRESH_AHEAD_SECONDS = 5 * 60L
        const val REFRESH_SUCCESS = "SUCCESS"
        val HTTP_SUCCESS_RANGE = 200..299
    }
}

private class LegacyProviderCredentialGateway(private val delegate: LinkedInCredentialGateway) :
    ProviderCredentialGateway {
    override suspend fun storeForOwner(ownerType: String, ownerId: UUID, credentials: ProviderCredentials): UUID =
        delegate.storeForOwner(
            ownerType,
            ownerId,
            LinkedInCredentials(
                accessToken = credentials.accessToken,
                refreshToken = credentials.refreshToken,
                expiresAtEpochSeconds = credentials.expiresAtEpochSeconds,
                refreshTokenExpiresAtEpochSeconds = credentials.refreshTokenExpiresAtEpochSeconds,
                lastRefreshAttemptAtEpochSeconds = credentials.lastRefreshAttemptAtEpochSeconds,
                lastRefreshStatus = credentials.lastRefreshStatus,
                grantedScopes = credentials.grantedScopes,
                scope = credentials.scope,
            ),
        )

    override suspend fun resolveCredential(id: UUID): ProviderCredentials {
        val credentials = delegate.resolveCredential(id)
        return ProviderCredentials(
            provider = SocialProvider.LINKEDIN,
            accessToken = credentials.accessToken,
            refreshToken = credentials.refreshToken,
            expiresAtEpochSeconds = credentials.expiresAtEpochSeconds,
            refreshTokenExpiresAtEpochSeconds = credentials.refreshTokenExpiresAtEpochSeconds,
            lastRefreshAttemptAtEpochSeconds = credentials.lastRefreshAttemptAtEpochSeconds,
            lastRefreshStatus = credentials.lastRefreshStatus,
            grantedScopes = credentials.grantedScopes,
            scope = credentials.scope,
        )
    }

    override suspend fun invalidateCredential(id: UUID) = Unit
}
