#!/usr/bin/env node
import { spawn } from 'node:child_process'

import { getRuntimeEnvironment, getWorktreeContext } from './worktree-context.mjs'
import { removeProcessRecord, writeProcessRecord } from './process-supervisor.mjs'

const surface = process.argv[2]
const packages = {
  app: 'apps/web/app',
  admin: 'apps/web/admin',
  marketing: 'apps/web/marketing',
}
const packagePath = packages[surface]

if (!packagePath) {
  console.error('Usage: node scripts/frontend-run.mjs <app|admin|marketing>')
  process.exit(2)
}

const context = getWorktreeContext()
const isWin = process.platform === 'win32'
const child = spawn('pnpm', ['dev'], {
  cwd: `${context.root}/${packagePath}`,
  env: getRuntimeEnvironment(context),
  stdio: 'inherit',
  shell: isWin,
  detached: !isWin,
})

writeProcessRecord(
  `frontend-${surface}`,
  [
    { pid: process.pid, detached: false },
    { pid: child.pid, detached: !isWin },
  ],
  context,
)

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
  removeProcessRecord(`frontend-${surface}`, context)
  process.exit(code)
}

process.on('SIGINT', () => shutdown())
process.on('SIGTERM', () => shutdown())
child.on('error', () => shutdown(1))
child.on('exit', (code) => shutdown(code ?? 1))
