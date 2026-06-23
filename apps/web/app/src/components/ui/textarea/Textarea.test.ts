import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import Textarea from './Textarea.vue'

describe('Textarea', () => {
  it('renders attrs, class, id and emits updates', async () => {
    const wrapper = mount(Textarea, {
      props: {
        modelValue: 'hello',
        class: 'custom-textarea',
        id: 'message-field',
        placeholder: 'Write here',
      },
    })

    const textarea = wrapper.get('textarea')
    expect((textarea.element as HTMLTextAreaElement).value).toBe('hello')
    expect(textarea.attributes('id')).toBe('message-field')
    expect(textarea.attributes('placeholder')).toBe('Write here')
    expect(textarea.classes()).toContain('custom-textarea')

    await textarea.setValue('updated text')

    expect(wrapper.emitted('update:modelValue')).toEqual([['updated text']])
  })

  it('uses defaultValue when modelValue is absent', () => {
    const wrapper = mount(Textarea, {
      props: {
        defaultValue: 'seeded text',
      },
    })

    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('seeded text')
  })
})
