import { computed, ref } from 'vue'
import { createShortlink } from '../infrastructure/shortlinks-api'
import { extractDistinctUrls, replaceShortenedUrl } from '../domain/shortlink-post-content'

const REQUEST_TIMEOUT_MS = 8_000
const MAX_CONCURRENT_REQUESTS = 3

export function useComposerShortlinks() {
  const failedUrls = ref<string[]>([])
  const shortenedByUrl = new Map<string, string>()

  async function shorten(content: string, disabled = false): Promise<string> {
    failedUrls.value = []
    if (disabled) return content

    const urls = extractDistinctUrls(content).filter((url) => !shortenedByUrl.has(url))
    const failures: string[] = []
    let nextIndex = 0
    const workers = Array.from(
      { length: Math.min(MAX_CONCURRENT_REQUESTS, urls.length) },
      async () => {
        while (nextIndex < urls.length) {
          const url = urls[nextIndex++]
          if (url === undefined) return
          try {
            const result = await createShortlink(url, {
              signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
            })
            shortenedByUrl.set(url, result.shortUrl)
          } catch {
            failures.push(url)
          }
        }
      },
    )

    await Promise.all(workers)
    failedUrls.value = failures
    return [...shortenedByUrl].reduce(
      (text, [url, shortUrl]) => replaceShortenedUrl(text, url, shortUrl),
      content,
    )
  }

  function reset() {
    failedUrls.value = []
    shortenedByUrl.clear()
  }

  return {
    failedUrls,
    shortlinkWarning: computed(() => failedUrls.value.length > 0),
    shorten,
    reset,
  }
}
