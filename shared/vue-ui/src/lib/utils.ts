export function cn(...values: unknown[]): string {
  return values
    .flatMap((value) => {
      if (typeof value === 'string') return value
      if (Array.isArray(value))
        return value.filter((item): item is string => typeof item === 'string')
      return []
    })
    .filter(Boolean)
    .join(' ')
}
