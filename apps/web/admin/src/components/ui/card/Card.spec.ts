import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { Card, CardHeader, CardTitle, CardContent, CardFooter } from './index'

describe('Card primitive', () => {
  it('renders card sections correctly', () => {
    const wrapper = mount({
      components: { Card, CardHeader, CardTitle, CardContent, CardFooter },
      template: `
        <Card>
          <CardHeader>
            <CardTitle>Card Title</CardTitle>
          </CardHeader>
          <CardContent>Card body content</CardContent>
          <CardFooter>Footer</CardFooter>
        </Card>
      `,
    })
    expect(wrapper.find('[data-slot="card-title"]').text()).toBe('Card Title')
    expect(wrapper.find('[data-slot="card-content"]').text()).toBe('Card body content')
    expect(wrapper.find('[data-slot="card-footer"]').text()).toBe('Footer')
  })
})
