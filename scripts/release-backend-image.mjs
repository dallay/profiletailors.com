#!/usr/bin/env node
import { spawnSync } from 'node:child_process'

const isWin = process.platform === 'win32'
const version = process.argv[2] || '0.1.0'
const imageRepository = process.argv[3] || 'profiletailors/smp'
const gradleWrapper = isWin ? 'gradlew.bat' : './gradlew'

const status = spawnSync('git', ['status', '--porcelain'], { encoding: 'utf8', shell: isWin })
if ((status.stdout || '').trim().length > 0) {
  console.error('Release images must be built from a clean worktree.')
  process.exit(1)
}

const revisionResult = spawnSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8', shell: isWin })
const revision = (revisionResult.stdout || '').trim()
if (!revision) {
  console.error('Unable to resolve git revision.')
  process.exit(1)
}

const shortRevision = revision.slice(0, 12)
const imageName = `${imageRepository}:${version}-${shortRevision}`

const build = spawnSync(
  gradleWrapper,
  [
    ':server:smp:bootBuildImage',
    `-PreleaseVersion=${version}`,
    `--imageName=${imageName}`,
    '--no-daemon',
  ],
  {
    stdio: 'inherit',
    shell: isWin,
    env: {
      ...process.env,
      BP_OCI_REVISION: revision,
      BP_OCI_VERSION: version,
    },
  },
)

if ((build.status ?? 1) !== 0) {
  process.exit(build.status ?? 1)
}

const inspect = spawnSync(
  'docker',
  ['image', 'inspect', imageName, '--format', 'image={{.RepoTags}} id={{.Id}}'],
  {
    stdio: 'inherit',
    shell: isWin,
  },
)

process.exit(inspect.status ?? 1)
