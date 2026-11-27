import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  getUnreadChangelogCount,
  loadLastSeenChangelogId,
  saveLastSeenChangelogId,
  validateChangelogFeed,
  type ChangelogEntry,
  type ChangelogLocale,
} from '@profiletailors/shared-web'

export type ChangelogStatus = 'idle' | 'loading' | 'ready' | 'error'

export interface UseChangelogOptions {
  marketingOrigin?: string
  fetcher?: typeof fetch
  autoFetch?: boolean
}

const DEFAULT_MARKETING_ORIGIN = 'https://profiletailors.com'

function resolveMarketingOrigin(explicitOrigin?: string): string {
  const configured =
    explicitOrigin ??
    (import.meta.env.VITE_MARKETING_URL as string | undefined) ??
    DEFAULT_MARKETING_ORIGIN
  return configured.replace(/\/+$/, '')
}

function normalizeLocale(value: string): ChangelogLocale {
  return value.toLowerCase().startsWith('es') ? 'es' : 'en'
}

export function buildChangelogFeedUrl(origin: string, locale: ChangelogLocale): string {
  const base = origin.replace(/\/+$/, '')
  return locale === 'es' ? `${base}/es/changelog.json` : `${base}/changelog.json`
}

export function buildPublicChangelogUrl(origin: string, locale: ChangelogLocale): string {
  const base = origin.replace(/\/+$/, '')
  return locale === 'es' ? `${base}/es/changelog/` : `${base}/changelog/`
}

export function formatShortChangelogDate(isoDate: string, locale: ChangelogLocale): string {
  const parsed = new Date(`${isoDate}T00:00:00Z`)
  if (Number.isNaN(parsed.getTime())) return isoDate
  return new Intl.DateTimeFormat(locale === 'es' ? 'es-ES' : 'en-US', {
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  }).format(parsed)
}

function rewriteAssetUrl(value: string, feedSiteOrigin: string, targetOrigin: string): string {
  if (value.startsWith('/')) {
    return `${targetOrigin}${value}`
  }
  if (feedSiteOrigin && value.startsWith(feedSiteOrigin)) {
    return `${targetOrigin}${value.slice(feedSiteOrigin.length)}`
  }
  return value
}

export function normalizeEntryUrls(
  entries: readonly ChangelogEntry[],
  feedSiteUrl: string,
  targetOrigin: string,
): readonly ChangelogEntry[] {
  let feedSiteOrigin = ''
  try {
    feedSiteOrigin = new URL(feedSiteUrl).origin
  } catch {
    feedSiteOrigin = ''
  }

  if (!feedSiteOrigin || feedSiteOrigin === targetOrigin) {
    return entries
  }

  return entries.map((entry) => ({
    ...entry,
    url: rewriteAssetUrl(entry.url, feedSiteOrigin, targetOrigin),
    ...(entry.image ? { image: rewriteAssetUrl(entry.image, feedSiteOrigin, targetOrigin) } : {}),
    sections: entry.sections.map((section) => ({
      ...section,
      ...(section.image
        ? { image: rewriteAssetUrl(section.image, feedSiteOrigin, targetOrigin) }
        : {}),
    })),
  }))
}

export function useChangelog(options: UseChangelogOptions = {}) {
  const i18n = useI18n()
  const origin = resolveMarketingOrigin(options.marketingOrigin)
  const fetcher = options.fetcher ?? globalThis.fetch

  const entries = ref<readonly ChangelogEntry[]>([])
  const status = ref<ChangelogStatus>('idle')
  const activeIndex = ref(0)
  const lastSeenId = ref<string | null>(loadLastSeenChangelogId())

  let requestSequence = 0

  const currentLocale = computed<ChangelogLocale>(() =>
    normalizeLocale(String(i18n?.locale?.value ?? 'en')),
  )
  const fullChangelogUrl = computed(() => buildPublicChangelogUrl(origin, currentLocale.value))
  const activeEntry = computed<ChangelogEntry | null>(
    () => entries.value[activeIndex.value] ?? null,
  )
  const hasPrevious = computed(() => activeIndex.value < entries.value.length - 1)
  const hasNext = computed(() => activeIndex.value > 0)
  const unreadCount = computed(() => getUnreadChangelogCount(entries.value, lastSeenId.value))
  const hasUnread = computed(() => unreadCount.value > 0)

  async function refresh(): Promise<void> {
    if (typeof fetcher !== 'function') {
      status.value = 'error'
      return
    }

    const requestId = ++requestSequence
    status.value = 'loading'
    const feedUrl = buildChangelogFeedUrl(origin, currentLocale.value)

    try {
      const response = await fetcher(feedUrl, {
        headers: { Accept: 'application/json' },
      })
      if (requestId !== requestSequence) return

      if (!response.ok) {
        status.value = 'error'
        return
      }

      const json: unknown = await response.json()
      if (requestId !== requestSequence) return

      const validated = validateChangelogFeed(json)
      if (!validated) {
        status.value = 'error'
        return
      }

      entries.value = normalizeEntryUrls(validated.entries, validated.siteUrl, origin)
      activeIndex.value = 0
      status.value = 'ready'
    } catch {
      if (requestId !== requestSequence) return
      status.value = 'error'
    }
  }

  function markAsRead(): void {
    const latest = entries.value[0]
    if (!latest) return
    saveLastSeenChangelogId(latest.id)
    lastSeenId.value = latest.id
  }

  function selectPrevious(): void {
    if (hasPrevious.value) {
      activeIndex.value += 1
    }
  }

  function selectNext(): void {
    if (hasNext.value) {
      activeIndex.value -= 1
    }
  }

  if (options.autoFetch !== false) {
    watch(
      currentLocale,
      () => {
        void refresh()
      },
      { immediate: true },
    )
  }

  return {
    entries,
    status,
    activeIndex,
    activeEntry,
    hasPrevious,
    hasNext,
    unreadCount,
    hasUnread,
    currentLocale,
    fullChangelogUrl,
    refresh,
    markAsRead,
    selectPrevious,
    selectNext,
  }
}
