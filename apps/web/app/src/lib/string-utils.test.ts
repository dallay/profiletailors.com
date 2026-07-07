import { describe, it, expect } from 'vitest'
import { toPascalCase } from './string-utils'

describe('string-utils', () => {
  it('converts kebab-case to PascalCase', () => {
    expect(toPascalCase('hello-world')).toBe('HelloWorld')
    expect(toPascalCase('my-cool-component')).toBe('MyCoolComponent')
    expect(toPascalCase('single')).toBe('Single')
  })
})
