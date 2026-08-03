#!/usr/bin/env node
import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'

const args = process.argv.slice(2)
const isWin = process.platform === 'win32'
const wrapper = isWin ? 'gradlew.bat' : './gradlew'

if (!existsSync(wrapper)) {
  console.error(`Gradle wrapper not found: ${wrapper}`)
  process.exit(1)
}

const result = spawnSync(wrapper, args, {
  stdio: 'inherit',
  shell: isWin,
})

if (result.error) {
  console.error(result.error.message)
  process.exit(1)
}

process.exit(result.status ?? 1)
