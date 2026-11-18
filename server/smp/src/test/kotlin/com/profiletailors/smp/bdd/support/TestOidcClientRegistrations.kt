package com.profiletailors.smp.bdd.support

import org.springframework.test.context.DynamicPropertyRegistry

object TestOidcClientRegistrations {
    fun register(registry: DynamicPropertyRegistry) {
        val issuer = TestOidcProvider.start()
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri") { issuer }
        registerClient(
            registry,
            registrationId = "pt-app",
            clientId = TestOidcProvider.APP_CLIENT_ID,
            clientSecret = TestOidcProvider.APP_CLIENT_SECRET,
        )
        registerClient(
            registry,
            registrationId = "pt-admin",
            clientId = TestOidcProvider.ADMIN_CLIENT_ID,
            clientSecret = TestOidcProvider.ADMIN_CLIENT_SECRET,
        )
    }

    private fun registerClient(
        registry: DynamicPropertyRegistry,
        registrationId: String,
        clientId: String,
        clientSecret: String,
    ) {
        registry.add("spring.security.oauth2.client.registration.$registrationId.provider") { "keycloak" }
        registry.add("spring.security.oauth2.client.registration.$registrationId.client-id") { clientId }
        registry.add("spring.security.oauth2.client.registration.$registrationId.client-secret") { clientSecret }
        registry.add("spring.security.oauth2.client.registration.$registrationId.client-authentication-method") {
            "client_secret_basic"
        }
        registry.add("spring.security.oauth2.client.registration.$registrationId.redirect-uri") {
            "{baseUrl}/login/oauth2/code/{registrationId}"
        }
    }
}
