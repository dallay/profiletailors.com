package com.profiletailors.smp.shortlinks.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.shortlinks.application.LinkDeletedApplicationException
import com.profiletailors.smp.shortlinks.application.LinkDisabledApplicationException
import com.profiletailors.smp.shortlinks.application.LinkExpiredApplicationException
import com.profiletailors.smp.shortlinks.application.LinkNotFoundApplicationException
import com.profiletailors.smp.shortlinks.application.LinkQuarantinedApplicationException
import com.profiletailors.smp.shortlinks.application.RecordLinkClickCommand
import com.profiletailors.smp.shortlinks.application.ResolveLinkQuery
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
import java.util.UUID

@RestController
class RedirectController(private val mediator: Mediator) {
    private val logger = LoggerFactory.getLogger(RedirectController::class.java)

    private suspend fun recordClick(linkId: UUID) {
        try {
            val recorded = withTimeoutOrNull(CLICK_RECORD_TIMEOUT_MILLIS) {
                mediator.send(RecordLinkClickCommand(linkId))
                true
            }
            if (recorded == null) logger.warn(CLICK_RECORD_TIMEOUT, linkId)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: DataAccessException) {
            logger.warn(CLICK_RECORD_FAILURE, linkId, exception)
        } catch (exception: IllegalStateException) {
            logger.warn(CLICK_RECORD_FAILURE, linkId, exception)
        }
    }

    @GetMapping("/{shortCode:[0-9A-Za-z]{1,32}}")
    suspend fun redirect(@PathVariable shortCode: String, request: ServerHttpRequest): ResponseEntity<Void> {
        val domain = request.headers.host?.hostString ?: ""

        val response = try {
            val result = mediator.send(ResolveLinkQuery(domain = domain, shortCode = shortCode))
            recordClick(result.linkId)
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
        const val CLICK_RECORD_FAILURE = "Failed to record click for link {}"
        const val CLICK_RECORD_TIMEOUT = "Timed out recording click for link {}"
    }
}
