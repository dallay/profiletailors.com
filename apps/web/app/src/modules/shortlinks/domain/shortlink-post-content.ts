export function extractDistinctUrls(post: string): string[] {
  return [
    ...new Set(
      (post.match(/https?:\/\/[^\s<>()]+/g) ?? []).map((url) => url.replace(/[.,!?;:]+$/, '')),
    ),
  ]
}

export function replaceShortenedUrl(post: string, originalUrl: string, shortUrl: string): string {
  return post.replace(/https?:\/\/[^\s<>()]+/g, (candidate) => {
    const trailingPunctuation = candidate.match(/[.,!?;:]+$/)?.[0] ?? ''
    const url = candidate.slice(0, candidate.length - trailingPunctuation.length)
    return url === originalUrl ? `${shortUrl}${trailingPunctuation}` : candidate
  })
}
