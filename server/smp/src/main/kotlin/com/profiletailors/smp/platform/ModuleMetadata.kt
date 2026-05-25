package com.profiletailors.smp.platform

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "audit :: application",
        "audit :: domain",
        "credentials :: application",
        "observability :: application",
    ],
)
internal class ModuleMetadata
