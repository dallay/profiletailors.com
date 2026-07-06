package com.profiletailors.smp.media.application.port

import com.profiletailors.common.domain.bus.query.Query
import kotlinx.coroutines.flow.Flow

data class ProviderExternalId(val value: String)

data class ProviderSearchItem(
    val externalId: ProviderExternalId,
    val previewUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
    val authorName: String,
    val authorUrl: String,
    val sourceUrl: String,
)

data class ProviderPageMeta(val number: Int, val size: Int, val total: Int)

data class ProviderSearchPage(val items: List<ProviderSearchItem>, val page: ProviderPageMeta)

data class ProviderExternalAsset(
    val externalId: ProviderExternalId,
    val mediaType: String,
    val contentLength: Long,
    val bytes: Flow<ByteArray>,
    val sourceProvider: String,
    val sourceUrl: String,
    val authorName: String,
    val authorUrl: String,
    val metadata: Map<String, Any?> = emptyMap(),
)

interface MediaProvider {
    /**
     * Identifier this provider was registered under (e.g. "unsplash").
     *
     * Used by the controller to map a feature-flagged provider route to a registered
     * port implementation. When this matches no registered adapter the request must
     * be rejected with 404.
     */
    val providerId: String

    suspend fun search(query: String, page: Int): ProviderSearchPage

    suspend fun import(workspaceId: String, externalId: ProviderExternalId): ProviderExternalAsset
}

/**
 * Query: Search provider photos for a workspace.
 *
 * The available providers are looked up in the application layer (this query is the
 * single seam between the controller and the registered `MediaProvider` adapters).
 * When no provider is registered for the requested id, the query handler must throw
 * an [UnsupportedProviderException] which the controller maps to 404.
 *
 * @property providerId stable provider identifier (e.g. "unsplash").
 */
data class SearchProviderPhotosQuery(
    val providerId: String,
    val workspaceId: String,
    val query: String,
    val page: Int,
) : Query<ProviderSearchPage>

/**
 * Exception raised by the application layer when no `MediaProvider` is registered
 * for the requested provider id. Mapped to HTTP 404 by the controller layer.
 */
class UnsupportedProviderException(val providerId: String) :
    RuntimeException("No media provider registered for '$providerId'")

/**
 * Query: Resolve a fully-qualified external id into a [ProviderExternalAsset].
 *
 * Companion to the [com.profiletailors.smp.media.application.ImportExternalAssetCommand]
 * write: the controller fetches the provider payload first so the import handler
 * receives a self-contained asset and has no direct port dependency.
 */
data class ImportProviderAssetQuery(val providerId: String, val workspaceId: String, val externalId: String) :
    Query<ProviderExternalAsset>
