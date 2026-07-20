package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import java.time.Clock
import java.util.Locale
import java.util.UUID

@Service
internal class CreateAssetHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val publicationAssetRepository: PublicationAssetRepository,
    private val clock: Clock,
) : CommandWithResultHandler<CreateAssetCommand, CreateAssetResult> {
    override suspend fun handle(command: CreateAssetCommand): CreateAssetResult {
        val principal = principalContextProvider.require()
        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)
        val now = clock.instant()

        val assetId = "pa-${UUID.randomUUID()}"
        val storageKey = if (command.sourceType == AssetSourceType.UPLOADED) {
            "assets/$workspaceId/$assetId"
        } else {
            null
        }

        val asset = PublicationAsset(
            id = assetId,
            workspaceId = workspaceId,
            sourceType = command.sourceType,
            mediaType = command.mediaType.uppercase(Locale.ROOT),
            storageKey = storageKey,
            externalUrl = command.externalUrl,
            originalFilename = command.originalFilename,
            status = PublicationAssetStatus.READY,
            createdByPrincipalId = principal.principalId,
            createdAt = now,
        )

        publicationAssetRepository.create(asset)

        return CreateAssetResult(
            assetId = asset.id,
            workspaceId = asset.workspaceId,
            sourceType = asset.sourceType,
            mediaType = asset.mediaType,
            status = asset.status,
        )
    }
}
