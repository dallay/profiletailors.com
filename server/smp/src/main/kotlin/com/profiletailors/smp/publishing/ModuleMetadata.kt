package com.profiletailors.smp.publishing

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "identity :: application",
        "tenancy :: application",
        "media :: application",
        "media :: domain",
    ],
)
internal class ModuleMetadata
