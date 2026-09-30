import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import InputGroupTextarea from './InputGroupTextarea.vue'

describe('InputGroupTextarea', () => {
  it('renders textarea element and forwards practical attrs/classes', () => {
    const wrapper = mount(InputGroupTextarea, {
      props: { class: 'custom-group-textarea' },
      attrs: {
        placeholder: 'Post body',
      },
    })

    const textarea = wrapper.get('textarea')
    expect(textarea.classes()).toContain('custom-group-textarea')
    expect(textarea.attributes('placeholder')).toBe('Post body')
  })
})
