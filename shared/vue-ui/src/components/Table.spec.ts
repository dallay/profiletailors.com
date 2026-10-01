import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import Table from './Table.vue'

describe('Table', () => {
  it('preserves caller-provided semantic headers and rows', () => {
    const wrapper = mount(Table, {
      slots: {
        default: '<thead><tr><th scope="col">Name</th></tr></thead><tbody><tr><td>Ada</td></tr></tbody>',
      },
    })

    expect(wrapper.find('table thead th[scope="col"]').text()).toBe('Name')
    expect(wrapper.find('table tbody td').text()).toBe('Ada')
  })
})
