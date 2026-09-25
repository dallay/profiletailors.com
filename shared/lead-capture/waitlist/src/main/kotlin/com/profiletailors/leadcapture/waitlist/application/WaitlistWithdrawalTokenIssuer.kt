package com.profiletailors.leadcapture.waitlist.application

import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistEntryRepository.WithdrawalToken
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

fun interface WaitlistWithdrawalTokenIssuer {
    fun issue(now: Instant): IssuedToken

    data class IssuedToken(val raw: String, val persisted: WithdrawalToken)

    companion object {
        val secure: WaitlistWithdrawalTokenIssuer = WaitlistWithdrawalTokenIssuer { now ->
            val raw = ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes).let { bytes ->
                Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            }
            IssuedToken(
                raw = raw,
                persisted = WithdrawalToken(
                    candidate = raw.take(CANDIDATE_LENGTH),
                    hash = sha256(raw),
                    expiresAt = now.plus(TOKEN_LIFETIME),
                ),
            )
        }

        private const val TOKEN_BYTES = 32
        private const val CANDIDATE_LENGTH = 16
        private val TOKEN_LIFETIME: Duration = Duration.ofDays(90)

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
