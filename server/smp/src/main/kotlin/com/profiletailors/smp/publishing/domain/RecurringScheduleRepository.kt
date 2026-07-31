package com.profiletailors.smp.publishing.domain

interface RecurringScheduleRepository {
    suspend fun create(schedule: RecurringSchedule): RecurringSchedule
    suspend fun update(schedule: RecurringSchedule): RecurringSchedule
    suspend fun findByWorkspaceAndId(workspaceId: String, id: String): RecurringSchedule?
    suspend fun findByWorkspace(workspaceId: String): List<RecurringSchedule>
    suspend fun pauseByTemplatePost(workspaceId: String, templatePostId: String)
    suspend fun delete(workspaceId: String, id: String): Boolean
}

/** Test-friendly no-op adapter used by legacy publication handler constructors. */
object NoOpRecurringScheduleRepository : RecurringScheduleRepository {
    override suspend fun create(schedule: RecurringSchedule) = schedule
    override suspend fun update(schedule: RecurringSchedule) = schedule
    override suspend fun findByWorkspaceAndId(workspaceId: String, id: String): RecurringSchedule? = null
    override suspend fun findByWorkspace(workspaceId: String): List<RecurringSchedule> = emptyList()
    override suspend fun pauseByTemplatePost(workspaceId: String, templatePostId: String) = Unit
    override suspend fun delete(workspaceId: String, id: String): Boolean = false
}
