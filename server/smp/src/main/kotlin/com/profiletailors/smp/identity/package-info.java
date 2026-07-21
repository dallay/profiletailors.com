@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"authorization :: domain", "tenancy :: application", "audit :: domain", "governance :: application", "credentials :: application"},
    displayName = "Identity"
)
@org.springframework.modulith.NamedInterface("application")
package com.profiletailors.smp.identity;
