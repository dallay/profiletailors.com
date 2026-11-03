package com.profiletailors.smp.platformadmin.infrastructure.cli

import com.profiletailors.smp.platformadmin.application.BootstrapInvitationIssuer
import com.profiletailors.smp.platformadmin.application.IssueBootstrapInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.BootstrapCli
import com.profiletailors.smp.platformadmin.application.contracts.BootstrapConsole

class InteractivePlatformAdminBootstrapCli(
    private val invitationIssuer: BootstrapInvitationIssuer,
    private val console: BootstrapConsole,
) : BootstrapCli {
    override suspend fun run() {
        val email = console.readLine(EMAIL_PROMPT) ?: return
        val confirmedEmail = console.readLine(CONFIRM_EMAIL_PROMPT) ?: return
        val replacementConfirmation = console.readLine(REPLACEMENT_PROMPT) ?: return
        val invitation = invitationIssuer.handle(
            IssueBootstrapInvitationCommand(
                email = email,
                confirmedEmail = confirmedEmail,
                replacementConfirmed = replacementConfirmation == REPLACEMENT_CONFIRMATION,
            ),
        )
        console.writeLine(invitation.link)
    }

    private companion object {
        private const val EMAIL_PROMPT = "Email address: "
        private const val CONFIRM_EMAIL_PROMPT = "Confirm email address: "
        private const val REPLACEMENT_PROMPT = "Type REPLACE to replace a pending invitation, or press Enter: "
        private const val REPLACEMENT_CONFIRMATION = "REPLACE"
    }
}
