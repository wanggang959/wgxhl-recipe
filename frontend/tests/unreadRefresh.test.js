import test from 'node:test'
import assert from 'node:assert/strict'
import { startUnreadRefresh } from '../src/utils/unreadRefresh.js'

function setup(hidden = false, withWorker = true) {
  const doc = new EventTarget()
  doc.hidden = hidden
  const worker = withWorker ? new EventTarget() : undefined
  const timers = new Map()
  let id = 0
  let refreshes = 0
  const stop = startUnreadRefresh({
    refresh: () => { refreshes += 1 },
    document: doc,
    serviceWorker: worker,
    setInterval: (callback, delay) => { const next = ++id; timers.set(next, { callback, delay }); return next },
    clearInterval: (timer) => timers.delete(timer),
  })
  return { doc, worker, timers, stop, count: () => refreshes }
}

test('refreshes on mount and every 60 seconds while visible', () => {
  const state = setup()
  assert.equal(state.count(), 1)
  assert.equal(state.timers.size, 1)
  const timer = [...state.timers.values()][0]
  assert.equal(timer.delay, 60_000)
  timer.callback()
  assert.equal(state.count(), 2)
  state.stop()
})

test('pauses while hidden and refreshes immediately on return', () => {
  const state = setup()
  state.doc.hidden = true
  state.doc.dispatchEvent(new Event('visibilitychange'))
  assert.equal(state.timers.size, 0)
  state.doc.hidden = false
  state.doc.dispatchEvent(new Event('visibilitychange'))
  assert.equal(state.count(), 2)
  assert.equal(state.timers.size, 1)
  state.stop()
})

test('a hidden initial page waits until it becomes visible', () => {
  const state = setup(true, false)
  assert.equal(state.count(), 0)
  assert.equal(state.timers.size, 0)
  state.doc.hidden = false
  state.doc.dispatchEvent(new Event('visibilitychange'))
  assert.equal(state.count(), 1)
  state.stop()
})

test('only push signals in the foreground trigger a refresh', () => {
  const state = setup()
  state.worker.dispatchEvent(new MessageEvent('message', { data: { type: 'other' } }))
  assert.equal(state.count(), 1)
  state.worker.dispatchEvent(new MessageEvent('message', { data: { type: 'notification-received' } }))
  assert.equal(state.count(), 2)
  state.doc.hidden = true
  state.doc.dispatchEvent(new Event('visibilitychange'))
  state.worker.dispatchEvent(new MessageEvent('message', { data: { type: 'notification-received' } }))
  assert.equal(state.count(), 2)
  state.doc.hidden = false
  state.doc.dispatchEvent(new Event('visibilitychange'))
  assert.equal(state.count(), 3)
  state.stop()
})

test('cleanup stops the timer and removes listeners', () => {
  const state = setup()
  const callback = [...state.timers.values()][0].callback
  state.stop()
  assert.equal(state.timers.size, 0)
  callback()
  state.doc.dispatchEvent(new Event('visibilitychange'))
  state.worker.dispatchEvent(new MessageEvent('message', { data: { type: 'notification-received' } }))
  assert.equal(state.count(), 1)
})
