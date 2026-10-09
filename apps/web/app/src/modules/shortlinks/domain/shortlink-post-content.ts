export function extractDistinctUrls(post: string): string[] {
  return [
    ...new Set(
      (post.match(/https?:\/\/[^\s<>()]+/g) ?? []).map((url) => url.replace(/[.,!?;:]+$/, '')),
    ),
  ]
}

export function replaceShortenedUrl(post: string, originalUrl: string, shortUrl: string): string {
  return post.replaceAll(originalUrl, shortUrl)
}
