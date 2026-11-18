package com.profiletailors.smp.bdd.support

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object TestOidcProvider {
    const val APP_CLIENT_ID = "pt-app"
    const val APP_CLIENT_SECRET = "test-pt-app-secret"
    const val ADMIN_CLIENT_ID = "pt-admin"
    const val ADMIN_CLIENT_SECRET = "test-pt-admin-secret"

    private const val ISSUER_PATH = "/realms/pt"
    private const val KEY_ID = "bdd-test-signing-key"
    private val signingKey: RSAKey = RSAKeyGenerator(2048).keyID(KEY_ID).generate()
    private val codes = ConcurrentHashMap<String, AuthorizationCode>()
    private val accessTokens = ConcurrentHashMap<String, Identity>()
    private val identities = ConcurrentHashMap<String, Identity>()
    private var server: HttpServer? = null
    private var issuerUri: String? = null

    @Synchronized
    fun start(): String {
        issuerUri?.let { return it }
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "bdd-oidc-provider").apply { isDaemon = true }
        }
        httpServer.createContext(ISSUER_PATH) { exchange -> handle(exchange) }
        httpServer.start()
        val issuer = "http://127.0.0.1:${httpServer.address.port}$ISSUER_PATH"
        server = httpServer
        issuerUri = issuer
        Runtime.getRuntime().addShutdownHook(Thread(::stop))
        return issuer
    }

    fun setIdentity(clientId: String, subject: String, email: String) {
        require(clientId == APP_CLIENT_ID || clientId == ADMIN_CLIENT_ID)
        identities[clientId] = Identity(subject, email)
    }

    @Synchronized
    fun stop() {
        server?.stop(0)
        server = null
        issuerUri = null
        codes.clear()
        accessTokens.clear()
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val path = exchange.requestURI.path.removePrefix(ISSUER_PATH)
            when (path) {
                "/.well-known/openid-configuration" -> discovery(exchange)
                "/authorize" -> authorize(exchange)
                "/token" -> token(exchange)
                "/userinfo" -> userinfo(exchange)
                "/jwks" -> jwks(exchange)
                else -> respond(exchange, 404, "{}")
            }
        } finally {
            exchange.close()
        }
    }

    private fun discovery(exchange: HttpExchange) {
        val issuer = requireIssuer()
        respond(
            exchange,
            200,
            """
                {"issuer":"$issuer",
                "authorization_endpoint":"$issuer/authorize",
                "token_endpoint":"$issuer/token",
                "userinfo_endpoint":"$issuer/userinfo",
                "jwks_uri":"$issuer/jwks",
                "response_types_supported":["code"],
                "subject_types_supported":["public"],
                "id_token_signing_alg_values_supported":["RS256"],
                "scopes_supported":["openid","profile","email"],
                "token_endpoint_auth_methods_supported":["client_secret_basic"]}
            """.trimIndent(),
        )
    }

    private fun authorize(exchange: HttpExchange) {
        val parameters = decodeParameters(exchange.requestURI.rawQuery.orEmpty())
        val clientId = parameters["client_id"]
        val redirectUri = parameters["redirect_uri"]
        val state = parameters["state"]
        val nonce = parameters["nonce"]
        if (clientId !in setOf(APP_CLIENT_ID, ADMIN_CLIENT_ID) ||
            redirectUri == null ||
            state == null ||
            nonce == null
        ) {
            respond(exchange, 400, "{}")
            return
        }
        val validatedClientId = requireNotNull(clientId)
        val code = UUID.randomUUID().toString()
        codes[code] = AuthorizationCode(validatedClientId, redirectUri, nonce)
        exchange.responseHeaders.add("Location", "$redirectUri?code=$code&state=${encode(state)}")
        exchange.sendResponseHeaders(302, -1)
    }

    private fun token(exchange: HttpExchange) {
        val credentials = basicCredentials(exchange)
        val params = decodeParameters(exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8))
        val grantCode = params["code"]
        val authorization = grantCode?.let(codes::remove)
        val secret = when (credentials.first) {
            APP_CLIENT_ID -> APP_CLIENT_SECRET
            ADMIN_CLIENT_ID -> ADMIN_CLIENT_SECRET
            else -> null
        }
        if (authorization == null) {
            respond(exchange, 401, "{}")
            return
        }
        val clientIsAuthenticated = credentials.second == secret && authorization.clientId == credentials.first
        val grantRequestIsValid = params["grant_type"] == "authorization_code" &&
            params["redirect_uri"] == authorization.redirectUri
        if (!clientIsAuthenticated || !grantRequestIsValid) {
            respond(exchange, 401, "{}")
            return
        }
        val identity = identities[authorization.clientId] ?: Identity(
            subject = "bdd-${authorization.clientId}",
            email = "${authorization.clientId}@example.test",
        )
        val accessToken = UUID.randomUUID().toString()
        accessTokens[accessToken] = identity
        val jwt = idToken(authorization.clientId, identity, authorization.nonce)
        respond(
            exchange,
            200,
            """
                {"access_token":"$accessToken",
                "token_type":"Bearer",
                "expires_in":300,
                "id_token":"$jwt",
                "scope":"openid profile email"}
            """.trimIndent(),
        )
    }

    private fun userinfo(exchange: HttpExchange) {
        val token = exchange.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")
        val identity = token?.let(accessTokens::get)
        if (identity == null) {
            respond(exchange, 401, "{}")
            return
        }
        respond(
            exchange,
            200,
            """{"sub":"${json(identity.subject)}","email":"${json(identity.email)}","email_verified":true}""",
        )
    }

    private fun jwks(exchange: HttpExchange) {
        respond(exchange, 200, JWKSet(signingKey.toPublicJWK()).toString())
    }

    private fun idToken(clientId: String, identity: Identity, nonce: String): String {
        val now = Instant.now()
        val claims = JWTClaimsSet.Builder()
            .issuer(requireIssuer())
            .subject(identity.subject)
            .audience(clientId)
            .claim("azp", clientId)
            .claim("email", identity.email)
            .claim("email_verified", true)
            .claim("nonce", nonce)
            .issueTime(java.util.Date.from(now))
            .expirationTime(java.util.Date.from(now.plusSeconds(300)))
            .also { builder ->
                if (clientId == ADMIN_CLIENT_ID) {
                    builder.claim("acr", "2")
                        .claim("auth_time", java.util.Date.from(now))
                        .claim("amr", listOf("otp"))
                }
            }
            .build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), claims)
            .apply { sign(RSASSASigner(signingKey)) }
            .serialize()
    }

    private fun basicCredentials(exchange: HttpExchange): Pair<String?, String?> {
        val header = exchange.requestHeaders.getFirst("Authorization") ?: return null to null
        if (!header.startsWith("Basic ")) return null to null
        val decoded = Base64.getDecoder().decode(header.removePrefix("Basic ")).toString(StandardCharsets.UTF_8)
        val separator = decoded.indexOf(':')
        if (separator < 0) return null to null
        return decoded.substring(0, separator) to decoded.substring(separator + 1)
    }

    private fun decodeParameters(value: String): Map<String, String> = value.split('&')
        .filter(String::isNotEmpty)
        .associate { item ->
            val separator = item.indexOf('=')
            val key = if (separator < 0) item else item.substring(0, separator)
            val entryValue = if (separator < 0) "" else item.substring(separator + 1)
            URLDecoder.decode(key, StandardCharsets.UTF_8) to URLDecoder.decode(entryValue, StandardCharsets.UTF_8)
        }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
    private fun json(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
    private fun requireIssuer(): String = checkNotNull(issuerUri)

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private data class AuthorizationCode(val clientId: String, val redirectUri: String, val nonce: String)
    private data class Identity(val subject: String, val email: String)
}
