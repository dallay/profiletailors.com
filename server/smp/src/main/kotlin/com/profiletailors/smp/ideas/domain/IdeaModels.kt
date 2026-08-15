package com.profiletailors.smp.ideas.domain

import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.DomainEntity
import java.time.Instant

data class IdeaLink(val url: String, val label: String? = null)

@DomainEntity
data class IdeaColumn(val id: String, val name: String, val color: String? = null, val order: Int)

@AggregateRoot
data class Idea(
    val id: String,
    val workspaceId: String,
    val title: String,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val links: List<IdeaLink> = emptyList(),
    val columnId: String,
    val orderInColumn: Int,
    val convertedToPublicationId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@AggregateRoot
data class IdeaBoardConfig(val workspaceId: String, val columns: List<IdeaColumn>)

object IdeaBoardDefaults {
    val columns: List<IdeaColumn> = listOf(
        IdeaColumn(id = "raw", name = "Raw", order = 0),
        IdeaColumn(id = "in-progress", name = "In Progress", order = 1),
        IdeaColumn(id = "done", name = "Done", order = 2),
    )
}
