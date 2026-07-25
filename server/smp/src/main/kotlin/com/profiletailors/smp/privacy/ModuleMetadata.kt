package com.profiletailors.smp.privacy

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "audit :: domain",
    ],
)
internal class ModuleMetadata
