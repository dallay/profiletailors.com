import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import test from 'node:test'

const root = resolve(import.meta.dirname, '..')

const read = (path) => readFileSync(resolve(root, path), 'utf8')

test('backend runtime image provides its healthcheck client', () => {
  const dockerfile = read('server/smp/backend.Dockerfile')

  const runtimeStage = dockerfile.match(
    /FROM eclipse-temurin:[^\n]+ AS runtime([\s\S]*?)(?=\nFROM\b|$)/,
  )?.[1]
  assert.ok(runtimeStage)
  assert.match(runtimeStage, /USER root/)
  assert.match(runtimeStage, /RUN apt-get update[\s\S]*?apt-get install --no-install-recommends --yes wget=/)
  assert.match(runtimeStage, /USER 1002:1001/)
})

test('production smoke readiness follows the health endpoint contract', () => {
  const smokeTest = read('infra/apps/smp/production/smoke-test.sh')

  assert.match(smokeTest, /curl -fsS -o \/dev\/null "\$\{base_url\}\/healthz"/)
  assert.doesNotMatch(smokeTest, /status.*UP/)
  assert.match(smokeTest, /health=200/)
})

test('production compose healthcheck uses the installed client', () => {
  const compose = read('infra/apps/smp/production/compose.yaml')

  assert.match(compose, /test: \["CMD", "wget", "-q", "--spider", "http:\/\/127\.0\.0\.1:8080\/healthz"\]/)
})
