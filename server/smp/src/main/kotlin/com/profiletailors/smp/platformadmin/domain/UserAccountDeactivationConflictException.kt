package com.profiletailors.smp.platformadmin.domain

class UserAccountDeactivationConflictException(
    val principalId: String,
    val expectedVersion: Long,
    val actualVersion: Long,
) : RuntimeException(
    "Principal '$principalId' version conflict: expected $expectedVersion but found $actualVersion",
)
