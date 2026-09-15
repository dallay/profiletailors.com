package com.profiletailors.smp.platformadmin

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "audit :: domain",
        "identity :: application",
        "identity :: domain",
        "platform :: domain",
        "publishing :: application",
        "tenancy :: application",
    ],
)
internal class ModuleMetadata
