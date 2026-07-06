import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import Accordion from './Accordion.vue'
import AccordionItem from './AccordionItem.vue'
import AccordionTrigger from './AccordionTrigger.vue'
import AccordionContent from './AccordionContent.vue'

describe('Accordion components', () => {
  it('renders accordion structure', async () => {
    const wrapper = mount({
      components: { Accordion, AccordionItem, AccordionTrigger, AccordionContent },
      template: `
        <Accordion type="single" collapsible>
          <AccordionItem value="item-1">
            <AccordionTrigger>Is it accessible?</AccordionTrigger>
            <AccordionContent>Yes. It adheres to the WAI-ARIA design pattern.</AccordionContent>
          </AccordionItem>
        </Accordion>
      `
    })

    expect(wrapper.text()).toContain('Is it accessible?')
    const trigger = wrapper.find('button')
    await trigger.trigger('click')
    expect(wrapper.text()).toContain('Yes. It adheres to the WAI-ARIA design pattern.')
  })
})
