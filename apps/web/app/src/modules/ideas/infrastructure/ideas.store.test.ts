import { describe, it, expect } from 'vitest'
import { normalizeColumnOrder, normalizeIdeas, reorderWithinList } from './ideas.store'
import type { Idea, IdeaColumn } from '@modules/ideas/domain'

function makeColumn(id: string, order: number): IdeaColumn {
  return { id, name: id, order }
}

function makeIdea(id: string, columnId: string, orderInColumn: number): Idea {
  return {
    id,
    workspaceId: 'ws-1',
    title: id,
    notes: null,
    tags: [],
    links: [],
    columnId,
    orderInColumn,
    convertedToPublicationId: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }
}

describe('ideas store helpers', () => {
  it('reorderWithinList moves an item to target index', () => {
    const list = ['a', 'b', 'c', 'd']
    expect(reorderWithinList(list, 0, 2)).toEqual(['b', 'c', 'a', 'd'])
  })

  it('normalizeColumnOrder sorts by order and reindexes', () => {
    const columns = [makeColumn('done', 2), makeColumn('raw', 0), makeColumn('in-progress', 1)]
    expect(normalizeColumnOrder(columns)).toEqual([
      makeColumn('raw', 0),
      makeColumn('in-progress', 1),
      makeColumn('done', 2),
    ])
  })

  it('normalizeIdeas reindexes each column from 0', () => {
    const ideas = [makeIdea('c', 'done', 9), makeIdea('a', 'raw', 4), makeIdea('b', 'raw', 8)]

    const normalized = normalizeIdeas(ideas)
    const rawIdeas = normalized
      .filter((idea) => idea.columnId === 'raw')
      .sort((left, right) => left.orderInColumn - right.orderInColumn)

    const doneIdeas = normalized.filter((idea) => idea.columnId === 'done')

    expect(rawIdeas.map((idea) => idea.id)).toEqual(['a', 'b'])
    expect(rawIdeas.map((idea) => idea.orderInColumn)).toEqual([0, 1])
    expect(doneIdeas[0]?.orderInColumn).toBe(0)
  })
})
