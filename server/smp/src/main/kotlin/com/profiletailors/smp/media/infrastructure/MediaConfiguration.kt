package com.profiletailors.smp.media.infrastructure

import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.smp.identity.application.EmailVerificationPolicy
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.media.application.AssetPreviewUrlResolver
import com.profiletailors.smp.media.application.ImportUnsplashPhotoHandler
import com.profiletailors.smp.media.application.MediaAssetRepository
import com.profiletailors.smp.media.application.MediaImportService
import com.profiletailors.smp.media.application.MediaPreviewTokenService
import com.profiletailors.smp.media.application.MediaRateLimitRepository
import com.profiletailors.smp.media.application.MediaStoragePort
import com.profiletailors.smp.media.application.SearchUnsplashPhotosHandler
import com.profiletailors.smp.media.application.UnsplashImportSettings
import com.profiletailors.smp.media.application.UnsplashPhotoProvider
import com.profiletailors.smp.media.infrastructure.unsplash.UnsplashProperties
import com.profiletailors.smp.media.infrastructure.unsplash.UnsplashWebClientAdapter
import com.profiletailors.storage.application.StorageApplicationService
import com.profiletailors.storage.domain.AttachmentsStorageBinding
import io.netty.channel.ChannelOption
import kotlinx.coroutines.flow.Flow
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

/**
 * Registers [MediaProperties] as a Spring-managed configuration properties bean.
 */
@Configuration
@EnableConfigurationProperties(MediaProperties::class, UnsplashProperties::class)
class MediaConfiguration {
    /**
     * Creates the Unsplash photo provider.
     *
     * @param properties Unsplash API configuration.
     * @return The configured Unsplash photo provider.
     */
    @Bean
    fun unsplashPhotoProvider(properties: UnsplashProperties): UnsplashPhotoProvider {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
            .responseTimeout(properties.timeout)
        val webClient = WebClient.builder()
            .baseUrl(properties.baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
        return UnsplashWebClientAdapter(webClient, properties)
    }

    /**
     * Creates the media storage port adapter.
     *
     * @param storageApplicationService Storage service implementation.
     * @return The configured media storage port.
     */
    @Bean
    fun mediaStoragePort(storageApplicationService: StorageApplicationService): MediaStoragePort =
        object : MediaStoragePort {
            override suspend fun upload(
                bucket: String,
                key: String,
                content: Flow<ByteArray>,
                uploaderId: String,
                metadata: Map<String, String>,
            ) {
                storageApplicationService.upload(bucket, key, content, uploaderId, metadata)
            }

            override suspend fun delete(bucket: String, key: String, deleterId: String) {
                storageApplicationService.delete(bucket, key, deleterId)
            }

            override fun download(bucket: String, key: String, downloaderId: String): Flow<ByteArray> =
                storageApplicationService.download(bucket, key, downloaderId)

            override suspend fun copyObject(bucket: String, sourceKey: String, destKey: String) {
                storageApplicationService.copyObject(bucket, sourceKey, destKey)
            }
        }

    /**
     * Creates the settings used to import Unsplash media.
     *
     * @param properties Unsplash import configuration.
     * @param mediaProperties Media creation limits.
     * @param attachmentsStorageBinding Attachment storage configuration.
     * @return The configured Unsplash import settings.
     */
    @Bean
    fun unsplashImportSettings(
        properties: UnsplashProperties,
        mediaProperties: MediaProperties,
        attachmentsStorageBinding: AttachmentsStorageBinding,
    ): UnsplashImportSettings = UnsplashImportSettings(
        storageBucket = attachmentsStorageBinding.bucketName,
        maxFileSizeBytes = properties.maxImportBytes,
        maxCreationsPerHour = mediaProperties.maxCreationsPerHour,
    )

    @Bean
    fun searchUnsplashPhotosHandler(provider: UnsplashPhotoProvider): SearchUnsplashPhotosHandler =
        SearchUnsplashPhotosHandler(provider)

    @Bean
    fun mediaImportService(
        provider: UnsplashPhotoProvider,
        mediaAssetRepository: MediaAssetRepository,
        storagePort: MediaStoragePort,
        settings: UnsplashImportSettings,
        assetPreviewUrlResolver: AssetPreviewUrlResolver,
        mediaPreviewTokenService: MediaPreviewTokenService,
    ): MediaImportService = MediaImportService(
        provider = provider,
        mediaAssetRepository = mediaAssetRepository,
        storagePort = storagePort,
        settings = settings,
        assetPreviewUrlResolver = assetPreviewUrlResolver,
        mediaPreviewTokenService = mediaPreviewTokenService,
    )

    @Bean
    fun importUnsplashPhotoHandler(
        mediaRateLimitRepository: MediaRateLimitRepository,
        mediaImportService: MediaImportService,
        settings: UnsplashImportSettings,
        principalContextProvider: PrincipalContextProvider,
        principalIdentityLookup: PrincipalIdentityLookup,
        emailVerificationPolicy: EmailVerificationPolicy,
    ): ImportUnsplashPhotoHandler = ImportUnsplashPhotoHandler(
        mediaRateLimitRepository = mediaRateLimitRepository,
        mediaImportService = mediaImportService,
        settings = settings,
        principalContextProvider = principalContextProvider,
        principalIdentityLookup = principalIdentityLookup,
        emailVerificationPolicy = emailVerificationPolicy,
    )

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
    }
}
