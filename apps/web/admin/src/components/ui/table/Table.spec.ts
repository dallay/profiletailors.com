import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from './index'

describe('Table primitive', () => {
  it('renders table markup structure correctly', () => {
    const wrapper = mount({
      components: { Table, TableHeader, TableBody, TableRow, TableHead, TableCell },
      template: `
        <Table aria-label="Users Table">
          <TableHeader>
            <TableRow>
              <TableHead>Email</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow>
              <TableCell>user@example.com</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      `,
    })
    expect(wrapper.find('table').exists()).toBe(true)
    expect(wrapper.find('th').text()).toBe('Email')
    expect(wrapper.find('td').text()).toBe('user@example.com')
  })
})
