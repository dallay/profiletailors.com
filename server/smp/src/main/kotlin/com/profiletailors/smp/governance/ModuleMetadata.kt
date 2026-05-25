package com.profiletailors.smp.governance

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "authorization :: domain",
    ],
)
internal class ModuleMetadata
