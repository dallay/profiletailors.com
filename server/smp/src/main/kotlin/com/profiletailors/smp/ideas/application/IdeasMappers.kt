package com.profiletailors.smp.ideas.application

import com.profiletailors.smp.ideas.domain.Idea

internal fun Idea.toResult(): IdeaResult = IdeaResult(
    id = id,
    workspaceId = workspaceId,
    title = title,
    notes = notes,
    tags = tags,
    links = links,
    columnId = columnId,
    orderInColumn = orderInColumn,
    convertedToPublicationId = convertedToPublicationId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
