import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { Tabs, TabsList, TabsTrigger, TabsContent } from './index'
import { defineComponent } from 'vue'

const TestComponent = defineComponent({
  components: { Tabs, TabsList, TabsTrigger, TabsContent },
  template: `
    <Tabs default-value="tab1">
      <TabsList>
        <TabsTrigger value="tab1" id="trigger1">Tab 1</TabsTrigger>
        <TabsTrigger value="tab2" id="trigger2">Tab 2</TabsTrigger>
      </TabsList>
      <TabsContent value="tab1" data-testid="content1">Content 1</TabsContent>
      <TabsContent value="tab2" data-testid="content2">Content 2</TabsContent>
    </Tabs>
  `,
})

describe('Tabs', () => {
  it('renders correctly', () => {
    const wrapper = mount(TestComponent)
    expect(wrapper.find('[data-testid="content1"]').exists()).toBe(true)
  })
})
