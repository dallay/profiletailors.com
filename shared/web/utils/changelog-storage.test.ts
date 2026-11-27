import { describe, expect, it } from 'vitest'
import {
  CHANGELOG_LAST_SEEN_KEY,
  getUnreadChangelogCount,
  hasUnreadChangelog,
  loadLastSeenChangelogId,
  saveLastSeenChangelogId,
} from './changelog-storage'

function createMemoryStorage() {
  const store = new Map<string, string>()
  return {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => {
      store.set(key, value)
    },
  }
}

describe('changelog storage utilities', () => {
  it('persists and loads the last seen entry id', () => {
    const storage = createMemoryStorage()
    expect(loadLastSeenChangelogId(storage)).toBeNull()

    saveLastSeenChangelogId('2026-10-09-shortlinks', storage)
    expect(storage.getItem(CHANGELOG_LAST_SEEN_KEY)).toBe('2026-10-09-shortlinks')
    expect(loadLastSeenChangelogId(storage)).toBe('2026-10-09-shortlinks')
  })

  it('computes unread counts relative to newest-first entries', () => {
    const entries = [{ id: 'release-3' }, { id: 'release-2' }, { id: 'release-1' }]

    expect(getUnreadChangelogCount([], null)).toBe(0)
    expect(getUnreadChangelogCount(entries, null)).toBe(3)
    expect(getUnreadChangelogCount(entries, 'release-3')).toBe(0)
    expect(hasUnreadChangelog(entries, 'release-3')).toBe(false)
    expect(getUnreadChangelogCount(entries, 'release-2')).toBe(1)
    expect(hasUnreadChangelog(entries, 'release-2')).toBe(true)
    expect(getUnreadChangelogCount(entries, 'older-release')).toBe(3)
  })

  it('degrades gracefully when storage throws', () => {
    const brokenStorage = {
      getItem: () => {
        throw new Error('denied')
      },
      setItem: () => {
        throw new Error('quota exceeded')
      },
    }

    expect(loadLastSeenChangelogId(brokenStorage)).toBeNull()
    expect(() => saveLastSeenChangelogId('release-1', brokenStorage)).not.toThrow()
  })
})
