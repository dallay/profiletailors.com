package com.profiletailors.common.domain.vo.ip

/**
 * A validated SHA-256 hash of an IP address, used for privacy-safe tracking.
 *
 * Storing raw IP addresses raises privacy concerns; this value object ensures that
 * only the hashed representation is persisted. The system hashes the IP before
 * creating this value object.
 *
 * Validation ensures the value is exactly 64 lowercase or uppercase hex characters
 * — the expected output of a SHA-256 hash.
 *
 * @throws IllegalArgumentException if the value is not a valid SHA-256 hex string
 * @since 1.0.0
 */
@JvmInline
value class IpHash(val value: String) {
    init {
        require(value.length == SHA256_HASH_LENGTH) { "IP hash must be a SHA-256 hash (64 hex characters)" }
        require(value.matches(Regex("^[a-fA-F0-9]{64}$"))) { "IP hash must be a valid SHA-256 hex string" }
    }

    companion object {
        private const val SHA256_HASH_LENGTH = 64

        /**
         * Creates an [IpHash] from a pre-computed SHA-256 hexadecimal string.
         *
         * @param ipHashed the 64-character hex string
         * @return a validated [IpHash]
         */
        fun from(ipHashed: String): IpHash = IpHash(ipHashed)
    }
}
