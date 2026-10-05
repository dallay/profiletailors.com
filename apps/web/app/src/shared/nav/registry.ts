export type NavGroupKey = 'primary' | 'system'

export interface NavEntry {
  key: string
  to: string
  labelKey: string
  icon: string
  group: NavGroupKey
  badge?: string
}

export const NAV_REGISTRY: readonly NavEntry[] = [
  {
    key: 'dashboard',
    to: '/',
    labelKey: 'nav.dashboard',
    icon: 'LayoutGrid',
    group: 'primary',
  },
  {
    key: 'scheduler',
    to: '/scheduler',
    labelKey: 'nav.scheduler',
    icon: 'CalendarDays',
    group: 'primary',
  },
  {
    key: 'analytics',
    to: '/analytics',
    labelKey: 'nav.analytics',
    icon: 'BarChart3',
    group: 'primary',
    badge: 'Live',
  },
  {
    key: 'media',
    to: '/media',
    labelKey: 'nav.media',
    icon: 'Images',
    group: 'primary',
  },
  {
    key: 'ideas',
    to: '/ideas',
    labelKey: 'nav.ideas',
    icon: 'Lightbulb',
    group: 'primary',
  },
  {
    key: 'governance',
    to: '/governance/takedown',
    labelKey: 'nav.governance',
    icon: 'Shield',
    group: 'primary',
  },
  {
    key: 'settings',
    to: '/settings',
    labelKey: 'nav.settings',
    icon: 'Settings',
    group: 'system',
  },
]
