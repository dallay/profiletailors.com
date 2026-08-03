package com.profiletailors.smp.hashtags

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "tenancy :: application",
    ],
)
internal class ModuleMetadata
