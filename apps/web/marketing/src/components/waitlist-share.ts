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

export function readWaitlistShareAttributes(form: HTMLFormElement): {
  url: string
  title: string
  copied: string
} {
  const url = form.dataset.waitlistShareUrl
  const title = form.dataset.waitlistShareTitle
  const copied = form.dataset.waitlistShareCopied
  if (!url || !title || !copied) {
    throw new Error('waitlist-share-attributes-missing')
  }
  return { url, title, copied }
}

export function bindWaitlistShare(
  share: HTMLButtonElement,
  attrs: { url: string; title: string; copied: string },
  apis?: {
    share?: (data: { title: string; url: string }) => Promise<void>
    writeText?: (text: string) => Promise<void>
  },
): void {
  const shareLabel = share.textContent ?? ''
  share.addEventListener('click', () => {
    const shareFn =
      apis?.share ??
      (typeof navigator.share === 'function'
        ? (data: { title: string; url: string }) => navigator.share(data)
        : undefined)
    const writeText = apis?.writeText ?? navigator.clipboard?.writeText.bind(navigator.clipboard)
    void shareWaitlist({
      url: attrs.url,
      title: attrs.title,
      share: shareFn,
      writeText,
    })
      .then((result) => {
        if (result === 'copied') {
          share.textContent = attrs.copied
        }
      })
      .catch(() => {
        share.textContent = shareLabel
      })
  })
}
