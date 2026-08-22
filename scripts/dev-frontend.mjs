#!/usr/bin/env node
import { spawn, spawnSync } from 'node:child_process'

import { getRuntimeEnvironment, getWorktreeContext } from './worktree-context.mjs'
import { removeProcessRecord, writeProcessRecord } from './process-supervisor.mjs'

const force = process.argv[2] || ''
const isWin = process.platform === 'win32'
const context = getWorktreeContext()

if (force && force !== '--force') {
  console.error(`Unknown option: ${force}`)
  console.error('Usage: just dev-frontend [--force]')
  process.exit(2)
}

if (force === '--force') {
  const kill = spawnSync(process.execPath, ['scripts/kill-servers.mjs'], {
    stdio: 'inherit',
    shell: isWin,
  })
  if ((kill.status ?? 1) !== 0) process.exit(kill.status ?? 1)
}

console.log(`Starting frontend dev servers for ${context.appUrl} and ${context.marketingUrl}...`)
const child = spawn('pnpm', ['--parallel', '--filter', 'marketing', '--filter', 'app', 'dev'], {
  stdio: 'inherit',
  shell: isWin,
  cwd: context.root,
  env: getRuntimeEnvironment(context),
  detached: !isWin,
})

writeProcessRecord('frontend', [{ pid: child.pid, detached: !isWin }], context)

let shuttingDown = false
const shutdown = (code = 0) => {
  if (shuttingDown) return
  shuttingDown = true
  if (child.pid) {
    try {
      if (isWin) child.kill('SIGTERM')
      else process.kill(-child.pid, 'SIGTERM')
    } catch {}
  }
  removeProcessRecord('frontend', context)
  process.exit(code)
}

process.on('SIGINT', () => shutdown())
process.on('SIGTERM', () => shutdown())
child.on('exit', (code) => shutdown(code ?? 1))
