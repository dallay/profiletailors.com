package com.profiletailors.smp.media.infrastructure

import com.profiletailors.smp.media.application.MediaStoragePort
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
     * Creates the settings used to import Unsplash media.
     *
     * @param properties Unsplash import configuration.
     * @param mediaProperties Media creation limits.
     * @param attachmentsStorageBinding Attachment storage configuration.
     * @return The configured Unsplash import settings.
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
        }

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

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
    }
}
