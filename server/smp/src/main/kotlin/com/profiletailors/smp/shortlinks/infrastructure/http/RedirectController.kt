package com.profiletailors.smp.shortlinks.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.shortlinks.application.LinkDeletedApplicationException
import com.profiletailors.smp.shortlinks.application.LinkDisabledApplicationException
import com.profiletailors.smp.shortlinks.application.LinkExpiredApplicationException
import com.profiletailors.smp.shortlinks.application.LinkNotFoundApplicationException
import com.profiletailors.smp.shortlinks.application.LinkQuarantinedApplicationException
import com.profiletailors.smp.shortlinks.application.ResolveLinkQuery
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class RedirectController(private val mediator: Mediator) {

    @GetMapping("/{shortCode:[0-9A-Za-z]{1,32}}")
    suspend fun redirect(@PathVariable shortCode: String, request: ServerHttpRequest): ResponseEntity<Void> {
        val domain = request.headers.host?.hostString ?: ""

        val response = try {
            val result = mediator.send(ResolveLinkQuery(domain = domain, shortCode = shortCode))
            ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, result.destinationUrl)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build<Void>()
        } catch (_: LinkNotFoundApplicationException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        } catch (_: LinkExpiredApplicationException) {
            ResponseEntity.status(HttpStatus.GONE).build()
        } catch (_: LinkDisabledApplicationException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        } catch (_: LinkDeletedApplicationException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        } catch (_: LinkQuarantinedApplicationException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return response
    }
}
