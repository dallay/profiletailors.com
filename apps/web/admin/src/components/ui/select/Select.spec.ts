import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { NativeSelect } from '../native-select'

describe('NativeSelect primitive', () => {
  it('renders select element with options and binds modelValue', async () => {
    const wrapper = mount({
      components: { NativeSelect },
      data() {
        return { selected: 'opt1' }
      },
      template: `
        <NativeSelect v-model="selected" data-testid="select">
          <option value="opt1">Option 1</option>
          <option value="opt2">Option 2</option>
        </NativeSelect>
      `,
    })
    const select = wrapper.find('select')
    expect(select.exists()).toBe(true)
    expect((select.element as HTMLSelectElement).value).toBe('opt1')

    await select.setValue('opt2')
    expect(wrapper.vm.selected).toBe('opt2')
  })
})
