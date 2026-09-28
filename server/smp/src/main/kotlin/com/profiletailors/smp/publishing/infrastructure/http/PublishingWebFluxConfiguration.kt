package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.smp.publishing.domain.SocialProvider
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.format.FormatterRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer
import java.util.Locale

@Configuration
class PublishingWebFluxConfiguration : WebFluxConfigurer {
    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverter(SocialProviderPathConverter())
    }
}

private class SocialProviderPathConverter : Converter<String, SocialProvider> {
    override fun convert(source: String): SocialProvider = SocialProvider.valueOf(source.uppercase(Locale.ROOT))
}
