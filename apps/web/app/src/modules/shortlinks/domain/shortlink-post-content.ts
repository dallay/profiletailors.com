const URL_PATTERN = /https?:\/\/[^\s<>]+/g
const TRAILING_PUNCTUATION = new Set(['.', ',', '!', '?', ';', ':'])

function splitTrailingPunctuation(match: string): { body: string; punctuation: string } {
  let end = match.length
  while (end > 0 && TRAILING_PUNCTUATION.has(match.charAt(end - 1))) {
    end -= 1
  }
  return { body: match.slice(0, end), punctuation: match.slice(end) }
}

function normalizeUrlMatch(match: string): { url: string; suffix: string } {
  const { body, punctuation } = splitTrailingPunctuation(match)
  let url = body
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
