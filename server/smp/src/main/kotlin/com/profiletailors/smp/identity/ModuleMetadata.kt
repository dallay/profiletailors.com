package com.profiletailors.smp.identity

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = [
        "audit :: application",
        "audit :: domain",
        "credentials :: application",
        "credentials :: domain",
        "credentials :: infrastructure",
        "governance :: application",
        "governance :: domain",
        "platform :: infrastructure",
        "platform :: domain",
        "tenancy :: application",
    ],
)
internal class ModuleMetadata
