package com.profiletailors.smp.media.application

import com.profiletailors.smp.media.application.port.MediaProvider
import com.profiletailors.smp.media.application.port.ProviderExternalAsset
import com.profiletailors.smp.media.application.port.ProviderExternalId
import com.profiletailors.smp.media.application.port.ProviderPageMeta
import com.profiletailors.smp.media.application.port.ProviderSearchPage

/**
 * No-op [MediaProvider] that satisfies the dependency when no real provider
 * (e.g. Unsplash) is registered.
 *
 * [search] returns an empty page; [import] throws to guard against any unexpected
 * call path — in normal operation the controller short-circuits with 404 before
 * the handler is ever invoked.
 */
class NoopMediaProvider : MediaProvider {
    override val providerId: String = "noop"

    override suspend fun search(query: String, page: Int): ProviderSearchPage =
        @Suppress("MagicNumber") // 20 is the conventional Unsplash page size; self-documenting here
        ProviderSearchPage(emptyList(), ProviderPageMeta(page, 20, 0))

    override suspend fun import(workspaceId: String, externalId: ProviderExternalId): ProviderExternalAsset =
        throw UnsupportedOperationException(
            "NoopMediaProvider.import should not be called — ensure the provider is registered before import",
        )
}
