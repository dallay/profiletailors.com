#!/usr/bin/env node
import { spawnSync } from 'node:child_process'
import { resolve } from 'node:path'

const [label, cwd, command, ...args] = process.argv.slice(2)

if (!label || !cwd || !command) {
  console.error('Usage: node scripts/ci-step.mjs <label> <cwd> <command> [...args]')
  process.exit(2)
}

const workingDirectory = resolve(process.cwd(), cwd)
const isWin = process.platform === 'win32'
const formatArg = (value) => (/\s/.test(value) ? JSON.stringify(value) : value)
const displayCommand = [command, ...args].map(formatArg).join(' ')
const separator = '─'.repeat(72)
const startedAt = Date.now()

console.log(`\n${separator}\n▶ ${label}\n  ${displayCommand}\n${separator}`)

const result = spawnSync(command, args, {
  cwd: workingDirectory,
  env: process.env,
  shell: isWin,
  stdio: 'inherit',
})

const duration = `${((Date.now() - startedAt) / 1000).toFixed(1)}s`

if (result.error) {
  console.error(`\n${separator}`)
  console.error(`❌ CI FAILED — ${label}`)
  console.error(`   Working directory: ${workingDirectory}`)
  console.error(`   Command: ${displayCommand}`)
  console.error(`   Error: ${result.error.message}`)
  console.error(`   Duration: ${duration}`)
  console.error(separator)
  process.exit(1)
}

if (result.status !== 0) {
  const status = result.signal ? `signal ${result.signal}` : `exit code ${result.status ?? 'unknown'}`
  console.error(`\n${separator}`)
  console.error(`❌ CI FAILED — ${label}`)
  console.error(`   Working directory: ${workingDirectory}`)
  console.error(`   Command: ${displayCommand}`)
  console.error(`   Result: ${status}`)
  console.error(`   Duration: ${duration}`)
  console.error(separator)
  process.exit(result.status || 1)
}

console.log(`✅ ${label} (${duration})`)
