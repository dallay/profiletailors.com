#!/usr/bin/env node
import { getRuntimeEnvironment, getWorktreeContext } from './worktree-context.mjs'
import { runManagedChild } from './process-supervisor.mjs'

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

runManagedChild({
  name: `frontend-${surface}`,
  command: 'pnpm',
  args: ['dev'],
  context,
  spawnOptions: {
    cwd: `${context.root}/${packagePath}`,
    env: getRuntimeEnvironment(context),
    stdio: 'inherit',
    shell: isWin,
  },
})
