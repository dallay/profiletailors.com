package com.profiletailors.smp.analytics

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "authorization :: application",
        "tenancy :: application",
    ],
)
internal class ModuleMetadata
