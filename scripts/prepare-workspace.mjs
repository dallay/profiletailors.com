#!/usr/bin/env node
import { existsSync } from 'node:fs'
import { spawnSync } from 'node:child_process'

if (process.env.CI || !existsSync('.git')) {
  process.exit(0)
}

const run = (command, args) =>
  spawnSync(command, args, {
    stdio: 'inherit',
    shell: process.platform === 'win32',
  })

const lefthook = run('pnpm', ['exec', 'lefthook', 'install'])
if (lefthook.status !== 0) {
  process.exit(0)
}

run('pnpm', ['dlx', '@dallay/agentsync', 'apply'])
process.exit(0)
