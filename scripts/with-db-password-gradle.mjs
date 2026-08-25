#!/usr/bin/env node
import { existsSync, readFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'

import { getRuntimeEnvironment, getWorktreeContext } from './worktree-context.mjs'

const args = process.argv.slice(2)
const isWin = process.platform === 'win32'
const wrapper = isWin ? 'gradlew.bat' : './gradlew'

if (!existsSync(wrapper)) {
  console.error(`Gradle wrapper not found: ${wrapper}`)
  process.exit(1)
}

const context = getWorktreeContext()
const env = getRuntimeEnvironment(context)
if (!env.SMP_DB_TEST_PASSWORD && existsSync('.env')) {
  const lines = readFileSync('.env', 'utf8').split(/\r?\n/)
  for (const line of lines) {
    if (line.startsWith('SMP_DB_TEST_PASSWORD=')) {
      env.SMP_DB_TEST_PASSWORD = line.slice('SMP_DB_TEST_PASSWORD='.length)
      break
    }
  }
}

if (!env.SMP_DB_TEST_PASSWORD) {
  console.error('SMP_DB_TEST_PASSWORD must be set in the environment or .env')
  process.exit(1)
}

const result = spawnSync(wrapper, args, {
  stdio: 'inherit',
  shell: isWin,
  cwd: context.root,
  env,
})

if (result.error) {
  console.error(result.error.message)
  process.exit(1)
}

process.exit(result.status ?? 1)
