#!/usr/bin/env node
import { spawnSync } from 'node:child_process'

const isWin = process.platform === 'win32'

const hasCommand = (name) => {
  const cmd = isWin ? 'where' : 'command'
  const args = isWin ? [name] : ['-v', name]
  const result = spawnSync(cmd, args, { stdio: 'ignore', shell: isWin })
  return result.status === 0
}

if (!hasCommand('portless')) {
  const install = spawnSync('pnpm', ['add', '-g', 'portless'], { stdio: 'inherit', shell: isWin })
  if ((install.status ?? 1) !== 0) {
    process.exit(install.status ?? 1)
  }
}

if (hasCommand('codegraph')) {
  const init = spawnSync('codegraph', ['init'], { stdio: 'inherit', shell: isWin })
  if ((init.status ?? 1) !== 0) {
    process.exit(init.status ?? 1)
  }
} else {
  console.log('codegraph not found - skipping index init')
}
