package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandHandler
import com.profiletailors.smp.shortlinks.domain.LinkClickRepository
import com.profiletailors.smp.shortlinks.domain.LinkId
import java.util.UUID

data class RecordLinkClickCommand(val linkId: UUID) : Command

@Service
internal class RecordLinkClickCommandHandler(
    private val linkClickRepository: LinkClickRepository,
) : CommandHandler<RecordLinkClickCommand> {

    override suspend fun handle(command: RecordLinkClickCommand) {
        linkClickRepository.record(LinkId(command.linkId))
    }
}
