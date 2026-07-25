import type { ChartConfig } from '.'
import { isClient } from '@vueuse/core'
import { useId } from 'reka-ui'
import { h, render } from 'vue'

// Simple cache using a Map to store serialized object keys
const cache = new Map<string, string>()

// Convert object to a consistent string key
function serializeKey(key: Record<string, unknown>): string {
  return JSON.stringify(
    key,
    Object.keys(key).sort((a, b) => a.localeCompare(b)),
  )
}

interface Constructor<P = unknown> {
  __isFragment?: never
  __isTeleport?: never
  __isSuspense?: never
  new (
    ...args: unknown[]
  ): {
    $props: P
  }
}

interface TooltipData {
  data: Record<string, unknown>
  [key: string]: unknown
}

export function componentToString<P extends Record<string, unknown>>(
  config: ChartConfig,
  component: Constructor<P>,
  props?: P,
) {
  if (!isClient) return

  // This function will be called once during mount lifecycle
  const id = useId()

  // https://unovis.dev/docs/auxiliary/Crosshair#component-props
  return (rawData: unknown, x: number | Date): string => {
    const data = (rawData as TooltipData | undefined)?.data ?? rawData
    
    if (typeof data !== 'object' || data === null) {
      return ''
    }

    const serializedKey = `${id}-${serializeKey(data as Record<string, unknown>)}`
    const cachedContent = cache.get(serializedKey)
    if (cachedContent) return cachedContent

    const vnode = h<P>(component, { 
      ...props, 
      payload: data, 
      config, 
      x 
    })
    const div = document.createElement('div')
    render(vnode, div)
    const html = div.innerHTML
    cache.set(serializedKey, html)
    return html
  }
}
