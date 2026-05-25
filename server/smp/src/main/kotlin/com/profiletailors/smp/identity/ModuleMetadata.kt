package com.profiletailors.smp.identity

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "audit :: application",
        "audit :: domain",
        "credentials :: application",
        "credentials :: domain",
        "platform :: infrastructure",
    ],
)
internal class ModuleMetadata
