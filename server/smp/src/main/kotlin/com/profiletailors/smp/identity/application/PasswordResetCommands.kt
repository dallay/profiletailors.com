package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.bus.command.CommandWithResult

data class RequestPasswordResetCommand(val email: String) : CommandWithResult<RequestPasswordResetResult>

data class RequestPasswordResetResult(val accepted: Boolean = true)

data class ResetPasswordCommand(val token: String, val newPassword: String) : CommandWithResult<ResetPasswordResult>

data class ResetPasswordResult(val passwordChanged: Boolean = true)
