import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { useNav } from './useNav'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => `translated:${key}` }),
}))

vi.mock('./icons', () => ({
  resolveNavIcon: vi.fn(() => 'nav-icon'),
}))

const Harness = defineComponent({
  setup() {
    return useNav()
  },
  template: '<output>{{ JSON.stringify(groups) }}</output>',
})

describe('useNav', () => {
  it('translates and groups registry entries, including optional badges', () => {
    const wrapper = mount(Harness)
    const groups = JSON.parse(wrapper.get('output').text()) as Array<{
      key: string
      label: string
      items: Array<{ key: string; badge?: string }>
    }>

    expect(groups.map(({ key, label }) => [key, label])).toEqual([
      ['primary', 'translated:'],
      ['system', 'translated:nav.system'],
    ])
    expect(groups[0].items.find(({ key }) => key === 'analytics')?.badge).toBe('Live')
    expect(groups[0].items.every(({ badge }) => badge === undefined || badge === 'Live')).toBe(true)
    expect(groups[1].items.map(({ key }) => key)).toEqual(['settings'])
  })
})
