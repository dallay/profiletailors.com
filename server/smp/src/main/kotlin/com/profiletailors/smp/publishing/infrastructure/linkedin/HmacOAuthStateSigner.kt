package com.profiletailors.smp.publishing.infrastructure.linkedin

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.ExpiredOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.LinkedInOAuthStatePayload
import com.profiletailors.smp.publishing.domain.OAuthStateSigner
import com.profiletailors.smp.publishing.domain.SocialProvider
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// Prefixes that identify well-known dev/placeholder values. A secret matching any of these
// would be guessable and allow an attacker to forge valid OAuth state tokens (OAuth CSRF).
private val INSECURE_PLACEHOLDER_PREFIXES = listOf("CHANGE_ME", "change_me", "changeme", "placeholder", "test-")

class HmacOAuthStateSigner(
    private val secret: String,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : OAuthStateSigner {
    init {
        require(secret.isNotBlank()) { "OAuth state signing secret is required." }
        require(INSECURE_PLACEHOLDER_PREFIXES.none { secret.startsWith(it, ignoreCase = true) }) {
            "OAuth state signing secret appears to be a placeholder (starts with a known insecure prefix). " +
                "Set SMP_LINKEDIN_STATE_SIGNING_SECRET to a strong random secret before enabling LinkedIn integration."
        }
    }

    override fun sign(payload: LinkedInOAuthStatePayload): String {
        val encodedPayload = base64UrlEncoder.encodeToString(objectMapper.writeValueAsBytes(payload.toStateMap()))
        val encodedSignature = signEncodedPayload(encodedPayload)
        return "$encodedPayload.$encodedSignature"
    }

    @Suppress("ThrowsCount", "TooGenericExceptionCaught")
    override fun verify(state: String): LinkedInOAuthStatePayload {
        val parts = state.split('.')
        if (parts.size != 2 || parts.any { it.isBlank() }) {
            throw InvalidOAuthStateException("OAuth state is malformed.")
        }
        val (encodedPayload, encodedSignature) = parts
        val expectedSignature = signEncodedPayload(encodedPayload)
        if (!constantTimeEquals(encodedSignature, expectedSignature)) {
            throw InvalidOAuthStateException("OAuth state signature is invalid.")
        }
        val payload = try {
            objectMapper.readValue(base64UrlDecoder.decode(encodedPayload), Map::class.java).toOAuthStatePayload()
        } catch (exception: RuntimeException) {
            throw InvalidOAuthStateException("OAuth state payload is invalid.", exception)
        }
        if (!payload.expiresAt.isAfter(clock.instant())) {
            throw ExpiredOAuthStateException()
        }
        return payload
    }

    private fun signEncodedPayload(encodedPayload: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM))
        return base64UrlEncoder.encodeToString(mac.doFinal(encodedPayload.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(StandardCharsets.UTF_8),
        right.toByteArray(StandardCharsets.UTF_8),
    )

    private fun LinkedInOAuthStatePayload.toStateMap(): Map<String, String> = linkedMapOf(
        "provider" to provider.name,
        "workspaceId" to workspaceId,
        "principalId" to principalId,
        "redirectUri" to redirectUri,
        "nonce" to nonce,
        "issuedAt" to issuedAt.toString(),
        "expiresAt" to expiresAt.toString(),
    )

    private fun Map<*, *>.toOAuthStatePayload(): LinkedInOAuthStatePayload = LinkedInOAuthStatePayload(
        provider = SocialProvider.valueOf(requiredString("provider")),
        workspaceId = requiredString("workspaceId", "workspace_id"),
        principalId = requiredString("principalId", "principal_id"),
        redirectUri = requiredString("redirectUri", "redirect_uri"),
        nonce = requiredString("nonce"),
        issuedAt = Instant.parse(requiredString("issuedAt", "issued_at")),
        expiresAt = Instant.parse(requiredString("expiresAt", "expires_at")),
    )

    private fun Map<*, *>.requiredString(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
        (this[key] as? String)?.takeIf { it.isNotBlank() }
            ?: entries.firstOrNull { normalizeKey(it.key?.toString()) == normalizeKey(key) }
                ?.value
                ?.toString()
                ?.takeIf { it.isNotBlank() }
    } ?: throw InvalidOAuthStateException("OAuth state payload is missing '${keys.first()}'.")

    private fun normalizeKey(key: String?): String = key.orEmpty().filter { it.isLetterOrDigit() }.lowercase()

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
        val base64UrlEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        val base64UrlDecoder: Base64.Decoder = Base64.getUrlDecoder()
    }
}
