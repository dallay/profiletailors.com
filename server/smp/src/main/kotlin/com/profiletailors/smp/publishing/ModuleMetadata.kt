package com.profiletailors.smp.publishing

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "audit :: application",
        "audit :: domain",
        "authorization :: domain",
        "identity :: application",
        "platform :: infrastructure",
        "tenancy :: application",
    ],
)
internal class ModuleMetadata
