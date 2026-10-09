package com.profiletailors.smp.shortlinks.infrastructure.persistence

import com.profiletailors.smp.shortlinks.domain.LinkClickRepository
import com.profiletailors.smp.shortlinks.domain.LinkId
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class R2dbcRedirectClickRecorder(private val databaseClient: DatabaseClient) : LinkClickRepository {
    override suspend fun record(linkId: LinkId) {
        databaseClient.sql("INSERT INTO link_clicks (id, link_id) VALUES (:id, :linkId)")
            .bind("id", UUID.randomUUID())
            .bind("linkId", linkId.value)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}
