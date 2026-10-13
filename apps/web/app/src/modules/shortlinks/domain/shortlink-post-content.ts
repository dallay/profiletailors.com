const URL_PATTERN = /https?:\/\/[^\s<>]+/g
const TRAILING_PUNCTUATION = /[.,!?;:]+$/

function normalizeUrlMatch(match: string): { url: string; suffix: string } {
  const punctuation = match.match(TRAILING_PUNCTUATION)?.[0] ?? ''
  let url = match.slice(0, match.length - punctuation.length)
  let closingParentheses = ''
  while (url.endsWith(')') && url.split(')').length > url.split('(').length) {
    url = url.slice(0, -1)
    closingParentheses += ')'
  }
  return { url, suffix: `${closingParentheses}${punctuation}` }
}

export function extractDistinctUrls(post: string): string[] {
  return [...new Set((post.match(URL_PATTERN) ?? []).map((match) => normalizeUrlMatch(match).url))]
}

export function replaceShortenedUrl(post: string, originalUrl: string, shortUrl: string): string {
  return post.replace(URL_PATTERN, (candidate) => {
    const { url, suffix } = normalizeUrlMatch(candidate)
    return url === originalUrl ? `${shortUrl}${suffix}` : candidate
  })
}
