const PROVIDER_CATALOG_STATES = {
  AVAILABLE: 'AVAILABLE',
  LOCKED: 'LOCKED',
  HIDDEN: 'HIDDEN',
} as const

const PROVIDER_LOCK_REASONS = {
  NOT_ENTITLED: 'NOT_ENTITLED',
  CAPACITY_REACHED: 'CAPACITY_REACHED',
} as const

export const PROVIDER_ACTIONS = {
  CONNECT_LINKEDIN_PERSONAL_PROFILE: 'CONNECT_LINKEDIN_PERSONAL_PROFILE',
} as const

type ProviderLockReason = (typeof PROVIDER_LOCK_REASONS)[keyof typeof PROVIDER_LOCK_REASONS]
type ProviderAction = (typeof PROVIDER_ACTIONS)[keyof typeof PROVIDER_ACTIONS]

type ProviderCatalogBase = {
  provider: string
  accountKinds: string[]
  channelLimit: null
  connectedChannelCount: number
  canConnectMore: boolean
}

type AvailableProviderCatalogItem = ProviderCatalogBase & {
  state: typeof PROVIDER_CATALOG_STATES.AVAILABLE
  reason: null
}

type LockedProviderCatalogItem = ProviderCatalogBase & {
  state: typeof PROVIDER_CATALOG_STATES.LOCKED
  reason: ProviderLockReason
}

type HiddenProviderCatalogItem = ProviderCatalogBase & {
  state: typeof PROVIDER_CATALOG_STATES.HIDDEN
  reason: null
}

export type ProviderCatalogItem =
  | AvailableProviderCatalogItem
  | LockedProviderCatalogItem
  | HiddenProviderCatalogItem

type KnownProvider =
  | 'linkedin'
  | 'instagram'
  | 'facebook'
  | 'threads'
  | 'bluesky'
  | 'twitter'
  | 'tiktok'
  | 'pinterest'
  | 'youtube'

export type ProviderPresentation = {
  label: string
  icon: KnownProvider | 'neutral'
  badge: string
  action: ProviderAction | null
}

const PROVIDER_PRESENTATIONS = {
  linkedin: {
    label: 'LinkedIn',
    icon: 'linkedin',
    badge: 'in',
    action: PROVIDER_ACTIONS.CONNECT_LINKEDIN_PERSONAL_PROFILE,
  },
  instagram: { label: 'Instagram', icon: 'instagram', badge: 'ig', action: null },
  facebook: { label: 'Facebook', icon: 'facebook', badge: 'fb', action: null },
  threads: { label: 'Threads', icon: 'threads', badge: '@', action: null },
  bluesky: { label: 'Bluesky', icon: 'bluesky', badge: 'b', action: null },
  twitter: { label: 'X', icon: 'twitter', badge: '𝕏', action: null },
  tiktok: { label: 'TikTok', icon: 'tiktok', badge: 'tt', action: null },
  pinterest: { label: 'Pinterest', icon: 'pinterest', badge: 'p', action: null },
  youtube: { label: 'YouTube', icon: 'youtube', badge: 'yt', action: null },
} as const satisfies Record<KnownProvider, ProviderPresentation>

const UNKNOWN_PROVIDER_PRESENTATION: ProviderPresentation = {
  label: 'Unknown provider',
  icon: 'neutral',
  badge: '•',
  action: null,
}

function isKnownProvider(provider: string): provider is KnownProvider {
  return Object.hasOwn(PROVIDER_PRESENTATIONS, provider)
}

export function getProviderPresentation(provider: string): ProviderPresentation {
  return isKnownProvider(provider)
    ? PROVIDER_PRESENTATIONS[provider]
    : UNKNOWN_PROVIDER_PRESENTATION
}
