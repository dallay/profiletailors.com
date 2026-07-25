package com.profiletailors.smp.tenancy

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "audit :: domain",
        "authorization :: domain",
        "platform :: domain",
    ],
)
internal class ModuleMetadata
