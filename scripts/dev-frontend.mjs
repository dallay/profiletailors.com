#!/usr/bin/env node
import { spawnSync } from 'node:child_process'

import { getRuntimeEnvironment, getWorktreeContext } from './worktree-context.mjs'
import { runManagedChild } from './process-supervisor.mjs'

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

runManagedChild({
  name: 'frontend',
  command: 'pnpm',
  args: ['--parallel', '--filter', 'marketing', '--filter', 'app', 'dev'],
  context,
  spawnOptions: {
    stdio: 'inherit',
    shell: isWin,
    cwd: context.root,
    env: getRuntimeEnvironment(context),
  },
})
