package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.UserPreferencesGateway
import com.profiletailors.smp.identity.domain.UserPreferences
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class R2dbcUserPreferencesGateway(private val databaseClient: DatabaseClient) : UserPreferencesGateway {

    override suspend fun findByPrincipalId(principalId: String): UserPreferences? = databaseClient.sql(
        """
        SELECT principal_id, locale, timezone, time_format, date_format, week_starts_on, theme, updated_at
        FROM user_preferences
        WHERE principal_id = :principalId
        """.trimIndent(),
    )
        .bind("principalId", principalId)
        .map { row, _ ->
            UserPreferences(
                principalId = requireNotNull(row.get("principal_id", String::class.java)),
                locale = requireNotNull(row.get("locale", String::class.java)),
                timezone = requireNotNull(row.get("timezone", String::class.java)),
                timeFormat = requireNotNull(row.get("time_format", String::class.java)),
                dateFormat = requireNotNull(row.get("date_format", String::class.java)),
                weekStartsOn = requireNotNull(row.get("week_starts_on", String::class.java)),
                theme = requireNotNull(row.get("theme", String::class.java)),
                updatedAt = row.get("updated_at", OffsetDateTime::class.java)?.toInstant() ?: Instant.now(),
            )
        }
        .one()
        .awaitSingleOrNull()

    override suspend fun save(preferences: UserPreferences): UserPreferences {
        val now = Instant.now()
        val updatedAtOffset = OffsetDateTime.ofInstant(now, ZoneOffset.UTC)

        databaseClient.sql(
            """
            INSERT INTO user_preferences (principal_id, locale, timezone, time_format, date_format, week_starts_on, theme, updated_at)
            VALUES (:principalId, :locale, :timezone, :timeFormat, :dateFormat, :weekStartsOn, :theme, :updatedAt)
            ON CONFLICT (principal_id) DO UPDATE SET
                locale = EXCLUDED.locale,
                timezone = EXCLUDED.timezone,
                time_format = EXCLUDED.time_format,
                date_format = EXCLUDED.date_format,
                week_starts_on = EXCLUDED.week_starts_on,
                theme = EXCLUDED.theme,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
        )
            .bind("principalId", preferences.principalId)
            .bind("locale", preferences.locale)
            .bind("timezone", preferences.timezone)
            .bind("timeFormat", preferences.timeFormat)
            .bind("dateFormat", preferences.dateFormat)
            .bind("weekStartsOn", preferences.weekStartsOn)
            .bind("theme", preferences.theme)
            .bind("updatedAt", updatedAtOffset)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        return preferences.copy(updatedAt = now)
    }
}
