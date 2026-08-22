#!/usr/bin/env node
import { getWorktreeContext } from './worktree-context.mjs'
import { listProcessRecords, terminateProcessRecord } from './process-supervisor.mjs'

const context = getWorktreeContext()
console.log(`Stopping dev servers owned by ${context.worktreeId}...`)

for (const path of listProcessRecords(context)) terminateProcessRecord(path)

console.log('Servers stopped')
