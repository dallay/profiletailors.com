package com.profiletailors.smp.ideas.domain

object IdeaPolicies {
    fun normalizeColumns(columns: List<IdeaColumn>): List<IdeaColumn> = columns
        .sortedBy { it.order }
        .mapIndexed { index, column ->
            column.copy(order = index)
        }

    fun normalizeIdeasInColumns(ideas: List<Idea>): List<Idea> {
        val grouped = ideas.groupBy { it.columnId }
        return grouped.flatMap { (_, columnIdeas) ->
            columnIdeas
                .sortedBy { it.orderInColumn }
                .mapIndexed { index, idea -> idea.copy(orderInColumn = index) }
        }
    }
}
