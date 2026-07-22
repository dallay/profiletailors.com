package com.profiletailors.smp.identity.application

/**
 * Command to close a user account permanently.
 *
 * @property principalId The authenticated principal requesting account closure.
 * @property confirmation Must equal "DELETE" to confirm. Required to prevent accidental closure.
 */
data class CloseAccountCommand(val principalId: String, val confirmation: String)
