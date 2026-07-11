package com.profiletailors.smp.audit

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "authorization :: domain",
    ],
)
internal class ModuleMetadata
