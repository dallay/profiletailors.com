import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { Field, FieldLabel, FieldError } from './index'

describe('Field component family', () => {
  it('renders field group, label, and error', () => {
    const wrapper = mount({
      components: { Field, FieldLabel, FieldError },
      template: `
        <Field>
          <FieldLabel for="test-input">Email</FieldLabel>
          <FieldError id="test-error">Invalid email</FieldError>
        </Field>
      `,
    })
    expect(wrapper.find('[data-slot="field"]').exists()).toBe(true)
    expect(wrapper.find('[data-slot="field-label"]').text()).toBe('Email')
    expect(wrapper.find('[data-slot="field-error"]').text()).toBe('Invalid email')
  })
})
