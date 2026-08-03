package com.profiletailors.smp.analytics

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "tenancy :: application",
    ],
)
internal class ModuleMetadata
