import type { ChangelogEntry } from '../types/changelog'

export const CHANGELOG_LAST_SEEN_KEY = 'pt_changelog_last_seen_id'

interface StorageLike {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
}

function resolveStorage(storage?: StorageLike | null): StorageLike | null {
  if (storage !== undefined) {
    return storage
  }
  if (typeof globalThis === 'undefined' || !('localStorage' in globalThis)) {
    return null
  }
  try {
    return globalThis.localStorage
  } catch {
    return null
  }
}

export function loadLastSeenChangelogId(storage?: StorageLike | null): string | null {
  const target = resolveStorage(storage)
  if (!target) return null
  try {
    const value = target.getItem(CHANGELOG_LAST_SEEN_KEY)
    const trimmed = value?.trim() ?? ''
    return trimmed.length > 0 ? trimmed : null
  } catch {
    return null
  }
}

export function saveLastSeenChangelogId(entryId: string, storage?: StorageLike | null): void {
  const trimmed = entryId.trim()
  if (trimmed.length === 0) return
  const target = resolveStorage(storage)
  if (!target) return
  try {
    target.setItem(CHANGELOG_LAST_SEEN_KEY, trimmed)
  } catch {
    return
  }
}

export function getUnreadChangelogCount(
  entries: readonly Pick<ChangelogEntry, 'id'>[],
  lastSeenId: string | null,
): number {
  if (entries.length === 0) return 0
  if (!lastSeenId) return entries.length
  const index = entries.findIndex((entry) => entry.id === lastSeenId)
  if (index === -1) return entries.length
  return index
}

export function hasUnreadChangelog(
  entries: readonly Pick<ChangelogEntry, 'id'>[],
  lastSeenId: string | null,
): boolean {
  return getUnreadChangelogCount(entries, lastSeenId) > 0
}
