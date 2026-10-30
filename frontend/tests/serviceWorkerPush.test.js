import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'

function setup({ failMatch = false } = {}) {
  const listeners = new Map()
  const messages = []
  const notifications = []
  const worker = {
    location: { origin: 'https://example.test' },
    addEventListener: (type, listener) => listeners.set(type, listener),
    registration: { showNotification: async (...args) => { notifications.push(args) } },
    clients: {
      matchAll: async () => {
        if (failMatch) throw new Error('browser unavailable')
        return [
          { url: 'https://example.test/#/todo', postMessage: (message) => messages.push(message) },
          { url: 'https://other.test/', postMessage: () => { throw new Error('unexpected origin') } },
        ]
      },
    },
  }
  runInNewContext(readFileSync(new URL('../public/sw.js', import.meta.url), 'utf8'), { self: worker, URL })
  return { listeners, messages, notifications }
}

test('push shows a system notification and signals same-origin pages', async () => {
  const state = setup()
  let work
  state.listeners.get('push')({
    data: { json: () => ({ title: 'Reminder', body: 'Content' }) },
    waitUntil: (promise) => { work = promise },
  })
  await work
  assert.equal(state.notifications.length, 1)
  assert.equal(state.notifications[0][0], 'Reminder')
  assert.equal(state.messages.length, 1)
  assert.equal(state.messages[0].type, 'notification-received')
})

test('failure to signal pages does not prevent system notification', async () => {
  const state = setup({ failMatch: true })
  let work
  state.listeners.get('push')({ data: null, waitUntil: (promise) => { work = promise } })
  await work
  assert.equal(state.notifications.length, 1)
})

test('activation removes old image caches without touching unrelated caches', async () => {
  const listeners = new Map()
  const deleted = []
  let claimed = false
  runInNewContext(readFileSync(new URL('../public/sw.js', import.meta.url), 'utf8'), {
    self: {
      addEventListener: (type, listener) => listeners.set(type, listener),
      clients: { claim: async () => { claimed = true } },
    },
    caches: {
      keys: async () => ['wgxhl-image-cache-v5', 'wgxhl-image-meta-v5', 'wgxhl-image-cache-v6', 'wgxhl-image-meta-v6', 'other-app'],
      delete: async key => { deleted.push(key) },
    },
  })
  let work
  listeners.get('activate')({ waitUntil: promise => { work = promise } })
  await work
  assert.deepEqual(deleted.sort(), ['wgxhl-image-cache-v5', 'wgxhl-image-meta-v5'])
  assert.equal(claimed, true)
})
