export function channelInitials(name: string | null | undefined): string {
  const trimmed = name?.trim()
  if (!trimmed) return 'PT'

  const parts = trimmed.split(/\s+/).filter(Boolean)
  if (parts.length === 1) {
    return (parts[0]?.slice(0, 2) ?? 'PT').toUpperCase()
  }

  return parts
    .slice(0, 2)
    .map((part) => part[0] ?? '')
    .join('')
    .toUpperCase()
}
