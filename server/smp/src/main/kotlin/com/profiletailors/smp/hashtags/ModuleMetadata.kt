package com.profiletailors.smp.hashtags

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "authorization :: application",
        "tenancy :: application",
    ],
)
internal class ModuleMetadata
