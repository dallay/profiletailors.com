package com.profiletailors.smp.platform

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "credentials :: application",
    ],
)
internal class ModuleMetadata
