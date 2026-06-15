/**
 * Shared provider-presenter mapping for social platforms.
 *
 * Used by both CalendarCell and SchedulerView so badge labels,
 * color classes, and platform metadata stay consistent.
 */

export interface ProviderStyle {
  color: string
  badge: string
}

const PROVIDER_STYLES: Record<string, ProviderStyle> = {
  linkedin: {
    color: 'bg-[#0077b5]/10 border-[#0077b5]/30 text-[#0077b5]',
    badge: 'in',
  },
  twitter: {
    color: 'bg-foreground/5 border-border-visible text-text-display',
    badge: '𝕏',
  },
  instagram: {
    color: 'bg-pink-500/10 border-pink-500/30 text-pink-500',
    badge: 'ig',
  },
  facebook: {
    color: 'bg-blue-600/10 border-blue-600/30 text-blue-600',
    badge: 'fb',
  },
}

const DEFAULT_STYLE: ProviderStyle = {
  color: 'bg-bg-primary border-border-visible text-text-secondary',
  badge: '•',
}

/** Look up a provider's CSS colour classes, falling back to a neutral default. */
export function getProviderColor(provider: string): string {
  return PROVIDER_STYLES[provider]?.color ?? DEFAULT_STYLE.color
}

/** Look up a provider's short badge label, falling back to a dot. */
export function getProviderBadge(provider: string): string {
  return PROVIDER_STYLES[provider]?.badge ?? DEFAULT_STYLE.badge
}
