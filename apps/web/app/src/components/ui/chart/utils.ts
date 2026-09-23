import type { ChartConfig } from '.'
import { isClient } from '@vueuse/core'
import { useId } from 'reka-ui'
import { h, render } from 'vue'

// Simple cache using a Map to store serialized object keys
const cache = new Map<string, string>()

import type { Component } from 'vue'

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

export function componentToString<P>(
  config: ChartConfig,
  component: Constructor<P> | Component | Record<string, unknown>,
  props?: P,
) {
  if (!isClient) return

  // This function will be called once during mount lifecycle
  const id = useId()

  // https://unovis.dev/docs/auxiliary/Crosshair#component-props
  return (_data: Record<string, unknown> | { data: Record<string, unknown> }, x: number | Date) => {
    const data = _data && typeof _data === 'object' && 'data' in _data ? _data.data : _data
    const keyData = data && typeof data === 'object' ? (data as Record<string, unknown>) : {}
    const serializedKey = `${id}-${serializeKey(keyData)}`
    const cachedContent = cache.get(serializedKey)
    if (cachedContent) return cachedContent

    const vnode = h<unknown>(component, { ...props, payload: data, config, x })
    const div = document.createElement('div')
    render(vnode, div)
    cache.set(serializedKey, div.innerHTML)
    return div.innerHTML
  }
}
