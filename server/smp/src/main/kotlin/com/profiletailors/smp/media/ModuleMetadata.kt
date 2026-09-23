package com.profiletailors.smp.media

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "authorization :: application",
        "identity :: application",
        "tenancy :: application",
    ],
)
internal class ModuleMetadata
