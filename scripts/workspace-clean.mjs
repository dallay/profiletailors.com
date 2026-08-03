#!/usr/bin/env node
import { rmSync } from 'node:fs'
import { spawnSync } from 'node:child_process'

const paths = [
  'apps/web/marketing/dist',
  'apps/web/marketing/coverage',
  'apps/web/app/dist',
  '.gradle/build-cache',
]

for (const path of paths) {
  rmSync(path, { recursive: true, force: true })
}

const isWin = process.platform === 'win32'
const wrapper = isWin ? 'gradlew.bat' : './gradlew'
const result = spawnSync(wrapper, ['clean', '--no-daemon'], {
  stdio: 'inherit',
  shell: isWin,
})

if (result.error) {
  console.warn(`Gradle clean skipped: ${result.error.message}`)
  process.exit(0)
}

process.exit(result.status ?? 0)
