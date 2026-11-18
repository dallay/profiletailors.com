import { describe, it, expect } from 'vitest'

const SOURCE_FILES = import.meta.glob<string>('./**/*.{ts,vue}', {
  query: '?raw',
  import: 'default',
  eager: true,
})

const STEP_UP_LOGIC_PATTERN = /(otp|totp|acr|mfa|step[-_ ]?up)/i
const ONE_TIME_CODE_PATTERN = /one[- ]?time[- ]code/i

describe('admin authentication boundary', () => {
  it('keeps level of assurance and one time code decisions out of the admin SPA', () => {
    const offenders = sourceFiles().filter(([, content]) => STEP_UP_LOGIC_PATTERN.test(content))

    expect(offenders.map(([path]) => path)).toEqual([])
  })

  it('mentions the one time code only in translated copy', () => {
    const mentions = sourceFiles().filter(([, content]) => ONE_TIME_CODE_PATTERN.test(content))

    expect(mentions.map(([path]) => path)).toEqual(['i18n/index.ts'])
  })
})

function sourceFiles(): Array<[string, string]> {
  return Object.entries(SOURCE_FILES)
    .filter(([path]) => !/\.(spec|test)\.ts$/.test(path))
    .map(([path, content]) => [path.replace(/^\.\//, ''), content])
}
