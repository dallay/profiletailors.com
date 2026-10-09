package com.profiletailors.smp.shortlinks.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.shortlinks.application.LinkDeletedApplicationException
import com.profiletailors.smp.shortlinks.application.LinkDisabledApplicationException
import com.profiletailors.smp.shortlinks.application.LinkExpiredApplicationException
import com.profiletailors.smp.shortlinks.application.LinkNotFoundApplicationException
import com.profiletailors.smp.shortlinks.application.LinkQuarantinedApplicationException
import com.profiletailors.smp.shortlinks.application.ResolveLinkQuery
import com.profiletailors.smp.shortlinks.domain.LinkClickRepository
import com.profiletailors.smp.shortlinks.domain.LinkId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class RedirectController(private val mediator: Mediator, private val clickRecorder: LinkClickRepository) {
    private val logger = LoggerFactory.getLogger(RedirectController::class.java)

    private suspend fun recordClick(linkId: LinkId) {
        try {
            val recorded = withTimeoutOrNull(CLICK_RECORD_TIMEOUT_MILLIS) {
                clickRecorder.record(linkId)
                true
            }
            if (recorded == null) logger.warn("Timed out recording click for link {}", linkId.value)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: DataAccessException) {
            logger.warn("Failed to record click for link {}", linkId.value, exception)
        }
    }

    @GetMapping("/{shortCode:[0-9A-Za-z]{1,32}}")
    suspend fun redirect(@PathVariable shortCode: String, request: ServerHttpRequest): ResponseEntity<Void> {
        val domain = request.headers.host?.hostString ?: ""

        val response = try {
            val result = mediator.send(ResolveLinkQuery(domain = domain, shortCode = shortCode))
            recordClick(LinkId(result.linkId))
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

    private companion object {
        const val CLICK_RECORD_TIMEOUT_MILLIS = 250L
    }
}
