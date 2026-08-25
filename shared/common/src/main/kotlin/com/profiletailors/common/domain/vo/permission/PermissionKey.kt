package com.profiletailors.common.domain.vo.permission

import com.profiletailors.common.domain.ValueObject

@ValueObject
data class PermissionKey(val value: String) {
    init {
        require(PATTERN.matches(value)) {
            "Permission keys must use the format <domain>:<resource>:<action>."
        }
    }

    companion object {
        private val PATTERN = Regex("^[a-z0-9-]+(?::[a-z0-9-]+){2}$")

        fun of(domain: String, resource: String, action: String): PermissionKey =
            PermissionKey("${domain.trim()}:${resource.trim()}:${action.trim()}")
    }
}
