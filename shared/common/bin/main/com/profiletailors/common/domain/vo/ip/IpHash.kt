package com.profiletailors.common.domain.vo.ip

@JvmInline
value class IpHash(val value: String) {
    init {
        require(value.length == SHA256_HASH_LENGTH) { "IP hash must be a SHA-256 hash (64 hex characters)" }
        require(value.matches(Regex("^[a-fA-F0-9]{64}$"))) { "IP hash must be a valid SHA-256 hex string" }
    }

    companion object {
        private const val SHA256_HASH_LENGTH = 64
        fun from(ipHashed: String): IpHash = IpHash(ipHashed)
    }
}
