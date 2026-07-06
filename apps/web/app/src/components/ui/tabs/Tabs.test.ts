import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { Tabs, TabsList, TabsTrigger, TabsContent } from './index'
import { defineComponent } from 'vue'

const TestComponent = defineComponent({
  components: { Tabs, TabsList, TabsTrigger, TabsContent },
  template: `
    <Tabs default-value="tab1">
      <TabsList>
        <TabsTrigger value="tab1">Tab 1</TabsTrigger>
        <TabsTrigger value="tab2">Tab 2</TabsTrigger>
      </TabsList>
      <TabsContent value="tab1">Content 1</TabsContent>
      <TabsContent value="tab2">Content 2</TabsContent>
    </Tabs>
  `,
})

describe('Tabs', () => {
  it('renders tabs and content correctly', () => {
    const wrapper = mount(TestComponent)
    expect(wrapper.find('[data-slot="tabs"]').exists()).toBe(true)
    expect(wrapper.find('[data-slot="tabs-list"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Tab 1')
    expect(wrapper.text()).toContain('Content 1')
  })
})
