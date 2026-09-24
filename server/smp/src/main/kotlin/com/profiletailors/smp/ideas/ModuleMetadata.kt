package com.profiletailors.smp.ideas

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "authorization :: application",
        "identity :: application",
        "publishing :: application",
        "tenancy :: application",
    ],
)
internal class ModuleMetadata
