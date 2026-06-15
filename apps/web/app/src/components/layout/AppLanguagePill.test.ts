import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AppLanguagePill from './AppLanguagePill.vue'

describe('AppLanguagePill', () => {
  it('exposes a radiogroup with aria-label "Language"', () => {
    const wrapper = mount(AppLanguagePill, { props: { current: 'en' } })
    const group = wrapper.find('[role="radiogroup"]')
    expect(group.exists()).toBe(true)
    expect(group.attributes('aria-label')).toBe('Language')
  })

  it('marks the active option with aria-checked="true" and the inactive one with "false"', () => {
    const wrapper = mount(AppLanguagePill, { props: { current: 'es' } })
    const radios = wrapper.findAll('[role="radio"]')
    expect(radios.length).toBe(2)

    const enBtn = radios[0]!
    const esBtn = radios[1]!
    expect(enBtn.attributes('aria-checked')).toBe('false')
    expect(esBtn.attributes('aria-checked')).toBe('true')
  })

  it('emits change(locale) when an option is activated', async () => {
    const wrapper = mount(AppLanguagePill, { props: { current: 'es' } })
    const enBtn = wrapper.findAll('[role="radio"]')[0]!

    await enBtn.trigger('click')
    expect(wrapper.emitted('change')).toBeTruthy()
    expect(wrapper.emitted('change')?.[0]).toEqual(['en'])
  })

  it('renders the literal EN / ES labels', () => {
    const wrapper = mount(AppLanguagePill, { props: { current: 'en' } })
    const text = wrapper.text()
    expect(text).toContain('EN')
    expect(text).toContain('ES')
  })
})
