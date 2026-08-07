package com.profiletailors.smp.publishing.infrastructure.socialcontent

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SocialContentProperties::class)
class SocialContentConfiguration
