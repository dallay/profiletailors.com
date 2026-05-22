package com.profiletailors.smp.tenancy

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "audit :: application",
        "audit :: domain",
        "authorization :: domain",
        "platform :: infrastructure",
    ],
)
internal class ModuleMetadata
