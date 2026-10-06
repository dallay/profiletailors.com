import { computed, type ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'
import type { NavGroup, NavItem } from '@profiletailors/vue-ui/shell/ports'
import { NAV_REGISTRY, type NavGroupKey } from './registry'
import { resolveNavIcon } from './icons'

const groupLabelKeys: Record<NavGroupKey, string> = {
  primary: '',
  system: 'nav.system',
}

export function useNav(): { groups: ComputedRef<NavGroup[]> } {
  const { t } = useI18n()

  const groups = computed<NavGroup[]>(() => {
    const order: NavGroupKey[] = ['primary', 'system']
    return order
      .map((groupKey) => {
        const items: NavItem[] = NAV_REGISTRY.filter((entry) => entry.group === groupKey).map(
          (entry) => {
            const item: NavItem = {
              key: entry.key,
              labelKey: entry.labelKey,
              iconComponent: resolveNavIcon(entry.icon),
            }
            if (entry.to !== undefined) item.path = entry.to
            if (entry.badge !== undefined) item.badge = entry.badge
            return item
          },
        )
        return {
          key: groupKey,
          label: t(groupLabelKeys[groupKey]),
          items,
        }
      })
      .filter((group) => group.items.length > 0)
  })

  return { groups }
}
