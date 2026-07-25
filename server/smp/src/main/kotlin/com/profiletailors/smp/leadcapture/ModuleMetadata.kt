package com.profiletailors.smp.leadcapture

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "governance :: application",
        "governance :: domain",
    ],
)
internal class ModuleMetadata
