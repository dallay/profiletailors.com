package com.profiletailors.smp.media

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "identity :: application",
        "tenancy :: application",
    ],
)
internal class ModuleMetadata
