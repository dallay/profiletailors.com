package com.profiletailors.smp.shortlinks.infrastructure

import com.profiletailors.smp.shortlinks.application.ShortLinksConfigProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.time.Clock

@ConfigurationProperties(prefix = "shortlinks")
data class ShortLinksRuntimeConfiguration(override val publicHost: String = "go.profiletailors.com") :
    ShortLinksConfigProperties

@Configuration
@EnableConfigurationProperties(ShortLinksRuntimeConfiguration::class)
class ShortLinksConfiguration {
    @Bean
    @Primary
    fun shortLinksClock(): Clock = Clock.systemUTC()
}
