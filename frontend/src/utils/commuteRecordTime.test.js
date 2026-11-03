import test from 'node:test'
import assert from 'node:assert/strict'
import { recordTimes, timePart } from './commuteRecordTime.js'

test('uses the selected date for both commute times', () => {
  assert.deepEqual(recordTimes('2026-10-26', '08:15', '09:00'), {
    departureTime: '2026-10-26T08:15', arrivalTime: '2026-10-26T09:00'
  })
})

test('reads saved times and rejects invalid same-day journeys', () => {
  assert.equal(timePart('2026-10-26 08:15:00'), '08:15')
  assert.equal(timePart('2026-10-26T09:00:00'), '09:00')
  assert.equal(timePart(null), '')
  assert.throws(() => recordTimes('2026-10-26', '09:00', '08:15'), /到达时间/)
  assert.throws(() => recordTimes('2026-10-26', '09:00', '09:00'), /到达时间/)
})
