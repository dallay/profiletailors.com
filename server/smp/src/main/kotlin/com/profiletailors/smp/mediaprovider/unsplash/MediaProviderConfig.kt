package com.profiletailors.smp.mediaprovider.unsplash

import com.profiletailors.smp.media.application.port.MediaProvider
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.util.concurrent.TimeUnit

@Configuration
@EnableConfigurationProperties(UnsplashProperties::class)
class MediaProviderConfig {

    /**
     * Reactive WebClient for Unsplash with explicit connect/read timeout.
     *
     * The timeout mirrors [UnsplashProperties.timeout] so a slow Unsplash response
     * fails fast and surfaces as 504 PROVIDER_UNREACHABLE instead of hanging the
     * caller.
     */
    @Bean
    @ConditionalOnProperty(prefix = "mediaprovider.unsplash", name = ["enabled"], havingValue = "true")
    fun unsplashWebClient(properties: UnsplashProperties): WebClient {
        val timeoutMillis = properties.timeout.toMillis()
        require(timeoutMillis <= Int.MAX_VALUE.toLong()) {
            "mediaprovider.unsplash.timeout must be <= ${Int.MAX_VALUE}ms"
        }
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMillis.toInt())
            .responseTimeout(properties.timeout)
            .doOnConnected { connection ->
                connection.addHandlerLast(
                    ReadTimeoutHandler(timeoutMillis, TimeUnit.MILLISECONDS),
                )
            }
        return WebClient.builder()
            .baseUrl(properties.baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }

    /**
     * Thin reactive client bound to [unsplashWebClient] and the typed properties.
     *
     * Exposed via the [UnsplashClient] port so the adapter can be tested with
     * in-memory / mocked implementations.
     */
    @Bean
    @ConditionalOnProperty(prefix = "mediaprovider.unsplash", name = ["enabled"], havingValue = "true")
    fun unsplashClient(unsplashWebClient: WebClient, properties: UnsplashProperties): UnsplashClient =
        UnsplashWebClient(unsplashWebClient, properties)

    /**
     * Wires the [UnsplashAdapter] implementation of [MediaProvider] when the
     * feature flag is enabled.
     *
     * When the flag is off, no [MediaProvider] bean is registered and the
     * downstream controller layer returns 404. Invalid access-key configuration
     * does not skip bean registration; it fails fast during
     * [UnsplashProperties] validation at application startup.
     */
    @Bean
    @ConditionalOnProperty(prefix = "mediaprovider.unsplash", name = ["enabled"], havingValue = "true")
    fun mediaProvider(unsplashClient: UnsplashClient, properties: UnsplashProperties): MediaProvider =
        UnsplashAdapter(unsplashClient, properties.pageSize)
}
