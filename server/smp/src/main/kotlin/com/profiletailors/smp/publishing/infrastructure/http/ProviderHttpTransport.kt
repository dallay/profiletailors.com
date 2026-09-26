package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.smp.publishing.domain.ProviderTransportUncertaintyException
import java.io.IOException
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

internal const val CONTENT_TYPE = "Content-Type"

fun interface ProviderHttpTransport {
    suspend fun send(request: HttpRequest): ProviderHttpResponse
}

data class ProviderHttpResponse(val statusCode: Int, val headers: HttpHeaders, val body: String)

class JdkProviderHttpTransport(private val httpClient: HttpClient) : ProviderHttpTransport {
    override suspend fun send(request: HttpRequest): ProviderHttpResponse = try {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        ProviderHttpResponse(
            statusCode = response.statusCode(),
            headers = response.headers(),
            body = response.body(),
        )
    } catch (exception: InterruptedException) {
        Thread.currentThread().interrupt()
        throw ProviderTransportUncertaintyException(exception)
    } catch (exception: IOException) {
        throw ProviderTransportUncertaintyException(exception)
    }
}

fun formUrlEncoded(vararg parts: Pair<String, String>): String = parts.joinToString("&") { (key, value) ->
    "${urlEncode(key)}=${urlEncode(value)}"
}

fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
