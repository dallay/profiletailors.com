import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useContentPipelineStore } from './contentPipeline'

describe('contentPipeline store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  describe('initial state', () => {
    it('loads mock columns on creation', () => {
      const store = useContentPipelineStore()
      expect(store.columns.length).toBe(4)
      expect(store.columns[0]?.id).toBe('ideas')
    })

    it('computes total cards across columns', () => {
      const store = useContentPipelineStore()
      expect(store.totalCards).toBe(6)
    })

    it('computes scheduled count', () => {
      const store = useContentPipelineStore()
      expect(store.scheduledCount).toBe(2)
    })
  })

  describe('moveCard', () => {
    it('moves a card between columns', () => {
      const store = useContentPipelineStore()
      const cardId = store.columns[0]?.cards[0]?.id
      expect(cardId).toBeTruthy()

      store.moveCard(cardId!, 'ideas', 'drafting')

      expect(store.columns[0]?.cards.find((c) => c.id === cardId)).toBeUndefined()
      expect(store.columns[1]?.cards.find((c) => c.id === cardId)).toBeTruthy()
    })

    it('moves a card to a specific index', () => {
      const store = useContentPipelineStore()
      const cardId = store.columns[0]?.cards[0]?.id
      store.moveCard(cardId!, 'ideas', 'drafting', 0)

      expect(store.columns[1]?.cards[0]?.id).toBe(cardId)
    })

    it('does nothing for unknown card id', () => {
      const store = useContentPipelineStore()
      const initialTotal = store.totalCards
      store.moveCard('nonexistent', 'ideas', 'drafting')
      expect(store.totalCards).toBe(initialTotal)
    })

    it('does nothing for unknown column id', () => {
      const store = useContentPipelineStore()
      const cardId = store.columns[0]?.cards[0]?.id
      const initialTotal = store.totalCards
      store.moveCard(cardId!, 'nonexistent', 'drafting')
      expect(store.totalCards).toBe(initialTotal)
    })
  })

  describe('addCard', () => {
    it('adds a card to a column', () => {
      const store = useContentPipelineStore()
      const initialCount = store.columns[0]?.cards.length ?? 0
      store.addCard('ideas', {
        id: 'new-card',
        title: 'New idea',
        content: 'Test content',
        platform: 'twitter',
        author: 'Test',
        tags: ['test'],
      })
      expect(store.columns[0]?.cards.length).toBe(initialCount + 1)
      expect(store.columns[0]?.cards.at(-1)?.id).toBe('new-card')
    })
  })

  describe('removeCard', () => {
    it('removes a card from a column', () => {
      const store = useContentPipelineStore()
      const cardId = store.columns[0]?.cards[0]?.id
      store.removeCard('ideas', cardId!)
      expect(store.columns[0]?.cards.find((c) => c.id === cardId)).toBeUndefined()
    })
  })

  describe('refreshAll', () => {
    it('completes and resets loading state', async () => {
      const store = useContentPipelineStore()
      await store.refreshAll()
      expect(store.isLoading).toBe(false)
    })
  })
})
