package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.RegistrationMode

interface RegistrationModeGateway {
    suspend fun currentMode(): RegistrationMode

    suspend fun changeMode(newMode: RegistrationMode): RegistrationModeChange
}

data class RegistrationModeChange(val previousMode: RegistrationMode, val newMode: RegistrationMode)
