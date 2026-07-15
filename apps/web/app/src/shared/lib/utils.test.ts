import { describe, it, expect } from 'vitest'
import { cn } from '@/lib/utils'

describe('@/lib/utils', () => {
  it('cn merges tailwind classes', () => {
    expect(cn('bg-red-500', 'p-4')).toBe('bg-red-500 p-4')
    expect(cn('bg-red-500 bg-blue-500')).toBe('bg-blue-500')
  })
})
