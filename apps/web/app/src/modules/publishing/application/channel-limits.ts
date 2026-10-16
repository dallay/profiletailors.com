export const CHANNEL_CHAR_LIMITS: Record<string, number> = {
  linkedin: 3000,
  threads: 500,
}

export const DEFAULT_CHAR_LIMIT = 3000

export function channelCharLimit(provider: string): number {
  return CHANNEL_CHAR_LIMITS[provider.toLowerCase()] ?? DEFAULT_CHAR_LIMIT
}
