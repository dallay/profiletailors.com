package com.profiletailors.smp.shortlinks.infrastructure.persistence

import com.profiletailors.smp.shortlinks.domain.LinkClickRepository
import com.profiletailors.smp.shortlinks.domain.LinkId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class R2dbcRedirectClickRecorder(private val databaseClient: DatabaseClient) : LinkClickRepository {
    private val logger = LoggerFactory.getLogger(R2dbcRedirectClickRecorder::class.java)

    override suspend fun record(linkId: LinkId) {
        try {
            val recorded = withTimeoutOrNull(CLICK_RECORD_TIMEOUT_MILLIS) {
                databaseClient.sql("INSERT INTO link_clicks (id, link_id) VALUES (:id, :linkId)")
                    .bind("id", UUID.randomUUID())
                    .bind("linkId", linkId.value)
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()
                true
            }
            if (recorded == null) {
                logger.warn(CLICK_RECORD_TIMEOUT, linkId.value)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: DataAccessException) {
            logger.warn(CLICK_RECORD_FAILURE, linkId.value, exception)
        } catch (exception: IllegalStateException) {
            logger.warn("Failed to record click for link {}", linkId.value, exception)
        }
    }

    private companion object {
        const val CLICK_RECORD_TIMEOUT_MILLIS = 250L
        const val CLICK_RECORD_FAILURE = "Failed to record click for link {}"
        const val CLICK_RECORD_TIMEOUT = "Timed out recording click for link {}"
    }
}
