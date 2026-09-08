package com.profiletailors.smp.administrative

import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories

@Configuration
@EnableR2dbcRepositories(basePackages = ["com.profiletailors.smp.administrative"])
class AdministrativePersistenceConfig
