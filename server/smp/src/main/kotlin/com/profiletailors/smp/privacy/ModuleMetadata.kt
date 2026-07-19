package com.profiletailors.smp.privacy

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "audit :: domain",
        "credentials",
        "governance",
        "identity :: application",
        "leadcapture",
        "media",
        "publishing",
        "tenancy",
    ],
)
internal class ModuleMetadata
