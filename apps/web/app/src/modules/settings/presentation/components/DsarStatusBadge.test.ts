import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DsarStatusBadge from './DsarStatusBadge.vue'
import type { DsarRequestStatus } from '@modules/settings/infrastructure/privacy.store'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

describe('DsarStatusBadge', () => {
  const statuses: DsarRequestStatus[] = ['PENDING', 'PROCESSING', 'COMPLETED', 'REJECTED', 'FAILED']

  statuses.forEach((status) => {
    it(`renders correctly for status ${status}`, () => {
      const wrapper = mount(DsarStatusBadge, {
        props: { status },
        global: {
          stubs: {
            Badge: {
              template: '<span :data-variant="variant"><slot /></span>',
              props: ['variant'],
            },
          },
        },
      })

      const badge = wrapper.find('[data-testid="dsar-status-badge"]')
      expect(badge.exists()).toBe(true)
      expect(badge.text()).toBe(`settings.privacy.status.${status}`)
    })
  })
})
