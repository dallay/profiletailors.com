package com.profiletailors.smp.governance

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "authorization :: domain",
        "audit :: domain",
        "tenancy :: *",
    ],
)
internal class ModuleMetadata
