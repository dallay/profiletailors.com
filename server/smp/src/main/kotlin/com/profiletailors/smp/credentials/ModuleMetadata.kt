package com.profiletailors.smp.credentials

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "identity :: application",
    ],
)
internal class ModuleMetadata
