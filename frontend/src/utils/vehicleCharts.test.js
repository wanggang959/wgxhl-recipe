import { test } from 'node:test'
import assert from 'node:assert/strict'
import { monthlyFuelTotal, monthlyConsumption, chartGeometry } from './vehicleCharts.js'

test('monthly statistics use actual dates and do not invent missing months', () => {
  const now = new Date(2026, 10, 19)
  assert.equal(monthlyFuelTotal([{ occurredAt:'2026-11-01T12:00', paidAmount:'20.50' }, { occurredAt:'2026-10-31T12:00', paidAmount:'99' }], now), 20.5)
  const result=monthlyConsumption([{date:'2026-10-02',value:4},{date:'2026-10-20',value:6},{date:'2026-11-01',value:7},{date:'2025-01-01',value:3}],6,now)
  assert.deepEqual(result,[{month:'2026-10',value:5},{month:'2026-11',value:7}])
  assert.deepEqual(chartGeometry([{month:'2026-11',value:7}]).map(x=>[x.x,x.y]),[[150,65]])
})
