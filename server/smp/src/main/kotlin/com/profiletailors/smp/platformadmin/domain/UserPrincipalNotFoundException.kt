package com.profiletailors.smp.platformadmin.domain

class UserPrincipalNotFoundException(val principalId: String) : RuntimeException("Principal '$principalId' not found")
