export function buildLoginRedirect(path: string, query: Record<string, string>): string {
  const keys = Object.keys(query)
  if (keys.length === 0) return path
  return `${path}?${new URLSearchParams(query).toString()}`
}
