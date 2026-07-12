package com.profiletailors.smp.authorization

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "audit :: domain",
    ],
)
internal class ModuleMetadata
