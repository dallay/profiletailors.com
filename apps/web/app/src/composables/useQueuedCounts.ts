import { computed, type ComputedRef, type Ref } from 'vue'
import { usePublishingStore, type Publication } from '@/stores/publishing'

/**
 * Derive queue statistics from a publications list.
 *
 * Walks every publication; for each `status === 'QUEUED'` entry, increments `total` and
 * bumps the count for every provider listed in `publication.channels`. Non-QUEUED
 * entries are ignored.
 *
 * Accepts an optional `publications` ref for testability — when omitted, reads from
 * the live Pinia store. The two call shapes return the same shape:
 *
 * ```ts
 * const { total, byProvider } = useQueuedCounts()
 * const { total, byProvider } = useQueuedCounts(myPublicationsRef)
 * ```
 */
export function useQueuedCounts(
  publications?: ComputedRef<readonly Publication[]> | Ref<readonly Publication[]>,
): {
  total: ComputedRef<number>
  byProvider: ComputedRef<Map<string, number>>
} {
  const source: Ref<readonly Publication[]> | ComputedRef<readonly Publication[]> = (publications ??
    usePublishingStore().publications) as Ref<readonly Publication[]>

  const total = computed(() => {
    const pubs = source.value
    if (!pubs) return 0
    let n = 0
    for (const pub of pubs) {
      if (pub.status === 'QUEUED') n++
    }
    return n
  })

  const byProvider = computed(() => {
    const counts = new Map<string, number>()
    const pubs = source.value
    if (!pubs) return counts
    for (const pub of pubs) {
      if (pub.status !== 'QUEUED') continue
      for (const provider of pub.channels as string[]) {
        counts.set(provider, (counts.get(provider) ?? 0) + 1)
      }
    }
    return counts
  })

  return { total, byProvider }
}
