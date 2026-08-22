#!/usr/bin/env node
import { spawn, spawnSync } from 'node:child_process'

import { getComposeEnvironment, getWorktreeContext } from './worktree-context.mjs'

const context = getWorktreeContext()
const args = process.argv.slice(2)
const composeFiles = []
while (args[0] === '--file' || args[0] === '-f') {
  composeFiles.push(args.shift(), args.shift())
}
const command = args[0] || 'up'
const composeArgs = ['compose', '--project-name', context.composeProjectName, ...composeFiles]
const env = getComposeEnvironment(context)

if (command === 'ports') {
  const services = [
    ['postgresql', '5432'],
    ['mailpit', '1025'],
    ['mailpit', '8025'],
    ['linkedin-wiremock', '8080'],
    ['prometheus', '9090'],
    ['grafana', '3000'],
  ]
  for (const [service, port] of services) {
    const result = spawnSync('docker', [...composeArgs, 'port', service, port], {
      cwd: context.root,
      env,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    })
    if (result.status === 0 && result.stdout.trim())
      process.stdout.write(`${service}:${port} -> ${result.stdout.trim()}\n`)
  }
  process.exit(0)
}

const child = spawn('docker', [...composeArgs, ...args], {
  cwd: context.root,
  env,
  stdio: 'inherit',
})

child.on('exit', (code, signal) => {
  if (signal) process.kill(process.pid, signal)
  process.exit(code ?? 1)
})
