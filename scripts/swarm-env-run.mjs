#!/usr/bin/env node
import { existsSync, readFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'

const action = process.argv[2]
const service = process.argv[3]
const envFile = 'infra/apps/smp/swarm/.env'
const stackFile = 'infra/apps/smp/swarm/stack.yaml'
const isWin = process.platform === 'win32'

const env = { ...process.env }
if (existsSync(envFile)) {
  const lines = readFileSync(envFile, 'utf8').split(/\r?\n/)
  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const eq = trimmed.indexOf('=')
    if (eq === -1) continue
    const key = trimmed.slice(0, eq).trim()
    const value = trimmed.slice(eq + 1)
    env[key] = value
  }
}

const stackName = env.SWARM_STACK_NAME

const run = (command, args) =>
  spawnSync(command, args, {
    stdio: 'inherit',
    shell: isWin,
    env,
  })

if (action === 'config') {
  process.exit(run('docker', ['stack', 'config', '--compose-file', stackFile]).status ?? 1)
}

if (!stackName) {
  console.error('Set SWARM_STACK_NAME in infra/apps/smp/swarm/.env')
  process.exit(1)
}

if (action === 'status') {
  process.exit(run('docker', ['stack', 'services', stackName]).status ?? 1)
}

if (action === 'logs') {
  if (!service) {
    console.error('Service is required for logs')
    process.exit(2)
  }
  process.exit(
    run('docker', ['service', 'logs', '--follow', `${stackName}_${service}`]).status ?? 1,
  )
}

if (action === 'rollback') {
  if (service !== 'backend' && service !== 'dashboard') {
    console.error('Service must be backend or dashboard.')
    process.exit(2)
  }
  process.exit(run('docker', ['service', 'rollback', `${stackName}_${service}`]).status ?? 1)
}

console.error('Usage: node scripts/swarm-env-run.mjs <config|status|logs|rollback> [service]')
process.exit(2)
