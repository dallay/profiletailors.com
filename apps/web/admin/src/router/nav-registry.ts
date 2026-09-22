import type { PlatformPermission } from '@/stores/auth.store'

export type NavStatus = 'live' | 'planned'

export interface NavEntry {
  key: string
  routeName: string
  path: string
  permission: PlatformPermission
  status: NavStatus
  icon: string
  labelKey: string
}

export const NAV_REGISTRY: readonly NavEntry[] = [
  {
    key: 'dashboard',
    routeName: 'dashboard',
    path: '',
    permission: 'platform.dashboard.read',
    status: 'live',
    icon: '◈',
    labelKey: 'nav.dashboard',
  },
  {
    key: 'waitlist',
    routeName: 'waitlist',
    path: 'waitlist',
    permission: 'platform.waitlist.read',
    status: 'live',
    icon: '≡',
    labelKey: 'nav.waitlist',
  },
  {
    key: 'direct-invitations',
    routeName: 'direct-invitations',
    path: 'direct-invitations',
    permission: 'platform.invitations.read',
    status: 'live',
    icon: '✉',
    labelKey: 'nav.directInvitations',
  },
  {
    key: 'users',
    routeName: 'users',
    path: 'users',
    permission: 'platform.users.read',
    status: 'live',
    icon: '◎',
    labelKey: 'nav.users',
  },
  {
    key: 'audit',
    routeName: 'audit',
    path: 'audit',
    permission: 'platform.audit.read',
    status: 'live',
    icon: '▤',
    labelKey: 'nav.audit',
  },
  {
    key: 'overview',
    routeName: 'overview',
    path: 'overview',
    permission: 'platform.dashboard.read',
    status: 'planned',
    icon: '▦',
    labelKey: 'nav.overview',
  },
  {
    key: 'notifications',
    routeName: 'notifications',
    path: 'notifications',
    permission: 'platform.notifications.read',
    status: 'live',
    icon: '▣',
    labelKey: 'nav.notifications',
  },
  {
    key: 'governance',
    routeName: 'governance',
    path: 'governance',
    permission: 'platform.governance.read',
    status: 'live',
    icon: '⬢',
    labelKey: 'nav.governance',
  },
  {
    key: 'configuration',
    routeName: 'configuration',
    path: 'configuration',
    permission: 'platform.configuration.read',
    status: 'live',
    icon: '⚙',
    labelKey: 'nav.configuration',
  },
]

export function plannedNavEntries(): NavEntry[] {
  return NAV_REGISTRY.filter((entry) => entry.status === 'planned')
}

export function visibleNavEntries(
  hasPermission: (permission: PlatformPermission) => boolean,
): NavEntry[] {
  return NAV_REGISTRY.filter((entry) => hasPermission(entry.permission))
}
