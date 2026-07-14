package com.profiletailors.smp.media.infrastructure

import com.profiletailors.smp.media.application.UnsplashImportSettings
import com.profiletailors.smp.media.application.UnsplashPhotoProvider
import com.profiletailors.smp.media.infrastructure.unsplash.UnsplashProperties
import com.profiletailors.smp.media.infrastructure.unsplash.UnsplashWebClientAdapter
import com.profiletailors.storage.domain.AttachmentsStorageBinding
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
    @Bean
    fun unsplashPhotoProvider(properties: UnsplashProperties): UnsplashPhotoProvider {
        val httpClient = HttpClient.create().responseTimeout(properties.timeout)
        val webClient = WebClient.builder()
            .baseUrl(properties.baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
        return UnsplashWebClientAdapter(webClient, properties)
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
}
