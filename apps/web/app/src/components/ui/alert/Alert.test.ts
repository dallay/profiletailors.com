import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import Alert from './Alert.vue'
import AlertTitle from './AlertTitle.vue'
import AlertDescription from './AlertDescription.vue'

describe('Alert components', () => {
  it('renders alert with title and description', () => {
    const wrapper = mount({
      components: { Alert, AlertTitle, AlertDescription },
      template: `
        <Alert variant="destructive">
          <AlertTitle>Error</AlertTitle>
          <AlertDescription>Something went wrong.</AlertDescription>
        </Alert>
      `,
    })

    expect(wrapper.classes()).toContain('text-destructive')
    expect(wrapper.text()).toContain('Error')
    expect(wrapper.text()).toContain('Something went wrong.')
  })
})
