import { test } from 'node:test'
import assert from 'node:assert/strict'
import { restoreBackup } from './deploy-rollback.mjs'

test('restores frontend even if restoring the backend fails', () => {
  const calls = []
  const result = restoreBackup({
    restoreBackend: () => { calls.push('backend'); return false },
    restoreFrontend: () => { calls.push('frontend'); return true },
    restartBackend: () => { calls.push('restart'); return true },
    checkBackend: () => { calls.push('health'); return true },
  })
  assert.deepEqual(calls, ['backend', 'frontend'])
  assert.equal(result.backend, false)
  assert.equal(result.frontend, true)
})

test('reports an unhealthy restored backend', () => {
  const result = restoreBackup({
    restoreBackend: () => true,
    restartBackend: () => true,
    checkBackend: () => false,
  })
  assert.equal(result.backend, true)
  assert.equal(result.healthy, false)
})
