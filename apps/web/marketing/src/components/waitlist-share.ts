export const WAITLIST_SHARE_ORIGIN = 'https://profiletailors.com'

export function waitlistShareUrl(locale: string): string {
  return locale === 'es' ? `${WAITLIST_SHARE_ORIGIN}/es/` : `${WAITLIST_SHARE_ORIGIN}/`
}

export async function shareWaitlist(input: {
  url: string
  title: string
  share?: (data: { title: string; url: string }) => Promise<void>
  writeText?: (text: string) => Promise<void>
}): Promise<'shared' | 'copied'> {
  if (input.share) {
    try {
      await input.share({ title: input.title, url: input.url })
      return 'shared'
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw error
      }
    }
  }

  if (!input.writeText) {
    throw new Error('share-unavailable')
  }

  await input.writeText(input.url)
  return 'copied'
}
