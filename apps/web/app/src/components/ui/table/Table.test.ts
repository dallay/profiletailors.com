import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import Table from './Table.vue'

describe('Table', () => {
  it('renders head slot when provided and applies class', () => {
    const wrapper = mount(Table, {
      props: { class: 'custom-table' },
      slots: {
        head: '<tr><th>Title</th></tr>',
        default: '<tbody><tr><td>Row 1</td></tr></tbody>',
      },
    })

    expect(wrapper.find('[data-slot="table-container"]').exists()).toBe(true)
    expect(wrapper.get('thead').text()).toContain('Title')
    expect(wrapper.get('table').classes()).toContain('custom-table')
    expect(wrapper.text()).toContain('Row 1')
  })

  it('omits thead when head slot is not provided', () => {
    const wrapper = mount(Table, {
      slots: {
        default: '<tbody><tr><td>Only body</td></tr></tbody>',
      },
    })

    expect(wrapper.find('thead').exists()).toBe(false)
    expect(wrapper.text()).toContain('Only body')
  })
})
