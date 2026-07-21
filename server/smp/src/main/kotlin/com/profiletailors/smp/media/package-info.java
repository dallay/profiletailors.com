@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"authorization :: domain", "tenancy :: application", "audit :: domain", "identity :: application"},
    displayName = "Media"
)
@org.springframework.modulith.NamedInterface("application")
package com.profiletailors.smp.media;
