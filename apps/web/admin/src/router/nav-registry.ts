import type { PlatformPermission } from '@/stores/auth.store'

export type NavStatus = 'live' | 'planned'

export interface NavEntry {
  key: string
  routeName: string
  path: string
  permission: PlatformPermission
  status: NavStatus
  icon: string
  group: 'operations' | 'observability' | 'trust' | 'system'
  labelKey: string
}

type NavEntryDefinition = Omit<NavEntry, 'status'> & { status?: NavStatus }

function defineNavEntry(entry: NavEntryDefinition): NavEntry {
  return { status: 'live', ...entry }
}

export const NAV_REGISTRY: readonly NavEntry[] = [
  defineNavEntry({
    key: 'dashboard',
    routeName: 'dashboard',
    path: '',
    permission: 'platform.dashboard.read',
    icon: 'LayoutDashboard',
    group: 'operations',
    labelKey: 'nav.dashboard',
  }),
  defineNavEntry({
    key: 'waitlist',
    routeName: 'waitlist',
    path: 'waitlist',
    permission: 'platform.waitlist.read',
    icon: 'ListChecks',
    group: 'operations',
    labelKey: 'nav.waitlist',
  }),
  defineNavEntry({
    key: 'direct-invitations',
    routeName: 'direct-invitations',
    path: 'direct-invitations',
    permission: 'platform.invitations.read',
    icon: 'MailPlus',
    group: 'operations',
    labelKey: 'nav.directInvitations',
  }),
  defineNavEntry({
    key: 'users',
    routeName: 'users',
    path: 'users',
    permission: 'platform.users.read',
    icon: 'Users',
    group: 'operations',
    labelKey: 'nav.users',
  }),
  defineNavEntry({
    key: 'audit',
    routeName: 'audit',
    path: 'audit',
    permission: 'platform.audit.read',
    icon: 'ScrollText',
    group: 'observability',
    labelKey: 'nav.audit',
  }),
  defineNavEntry({
    key: 'overview',
    routeName: 'overview',
    path: 'overview',
    permission: 'platform.dashboard.read',
    status: 'planned',
    icon: 'PanelsTopLeft',
    group: 'operations',
    labelKey: 'nav.overview',
  }),
  defineNavEntry({
    key: 'notifications',
    routeName: 'notifications',
    path: 'notifications',
    permission: 'platform.notifications.read',
    icon: 'Bell',
    group: 'observability',
    labelKey: 'nav.notifications',
  }),
  defineNavEntry({
    key: 'governance',
    routeName: 'governance',
    path: 'governance',
    permission: 'platform.governance.read',
    icon: 'ShieldAlert',
    group: 'trust',
    labelKey: 'nav.governance',
  }),
  defineNavEntry({
    key: 'configuration',
    routeName: 'configuration',
    path: 'configuration',
    permission: 'platform.configuration.read',
    icon: 'Settings2',
    group: 'system',
    labelKey: 'nav.configuration',
  }),
]

export function plannedNavEntries(): NavEntry[] {
  return NAV_REGISTRY.filter((entry) => entry.status === 'planned')
}

export function visibleNavEntries(
  hasPermission: (permission: PlatformPermission) => boolean,
): NavEntry[] {
  return NAV_REGISTRY.filter((entry) => entry.status === 'live' && hasPermission(entry.permission))
}
