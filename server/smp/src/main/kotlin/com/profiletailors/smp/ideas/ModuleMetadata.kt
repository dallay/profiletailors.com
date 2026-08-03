package com.profiletailors.smp.ideas

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "identity :: application",
        "publishing :: application",
        "tenancy :: application",
    ],
)
internal class ModuleMetadata
