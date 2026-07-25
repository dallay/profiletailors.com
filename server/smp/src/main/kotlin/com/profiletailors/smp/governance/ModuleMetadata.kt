package com.profiletailors.smp.governance

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "authorization :: domain",
        "audit :: domain",
        "tenancy :: application",
        "tenancy :: domain",
    ],
)
internal class ModuleMetadata
