import { test } from 'node:test'
import assert from 'node:assert/strict'
import { fuelOverview, spendOverview, expenseSummary } from './vehicleOverview.js'

const now = new Date(2026, 10, 19)
const fuels = [
  { occurredAt: '2026-11-11T08:00', mileage: 170, paidAmount: 40.2, displayedAmount: 45.2 },
  { occurredAt: '2026-10-11T08:00', mileage: 100, paidAmount: 20.1 },
]
const expenses = [
  { occurredAt: '2026-11-01T08:00', category: '维修保养', amount: 30.5 },
  { occurredAt: '2025-12-01T08:00', category: '洗车', amount: 15 },
]

test('fuel totals, discounts and cost per kilometre use the recorded amounts', () => {
  const result = fuelOverview(fuels, now)
  assert.equal(result.distance, 70)
  assert.equal(result.dailyMileage, 70 / 31)
  assert.equal(result.total, 60.3)
  assert.equal(result.discount, 5)
  assert.equal(result.months.at(-1).amount, 40.2)
  assert.equal(fuelOverview([fuels[0]], now).costPerKm, null)
  assert.equal(fuelOverview([fuels[0], { ...fuels[1], mileage: 180 }], now).distance, null)
  const summary = expenseSummary(result, expenses, '2026-10-19', now)
  assert.equal(summary.total, 105.8)
  assert.equal(summary.ownedDays, 31)
})

test('monthly stacks and donut reconcile, and ranges exclude out-of-period rows', () => {
  const current = spendOverview(fuels, expenses, 'year', now)
  const rolling = spendOverview(fuels, expenses, 'rolling', now)
  assert.equal(current.total, 90.8)
  assert.equal(rolling.total, 105.8)
  assert.equal(current.bars.at(-1).amount, 70.7)
  assert.deepEqual(current.bars.at(-1).segments.map(x => x.name), ['油费', '维修保养'])
  assert.ok(Math.abs(current.slices.at(-1).end - 100) < 1e-8)
  assert.deepEqual(spendOverview([], [], 'year', now).slices, [])
})

test('ranges and configured colors flow through bar segments and pie slices', () => {
  const old = { occurredAt: '2024-04-01', category: '停车', amount: 10 }
  assert.equal(spendOverview([], [old], '2y', now).total, 0)
  const all = spendOverview([], [old], 'all', now, [{name:'停车',color:'#654321'}])
  assert.equal(all.bars[0].key, '2024-04')
  assert.equal(all.bars[0].segments[0].color, '#654321')
  assert.equal(all.slices[0].end, 100)
})
