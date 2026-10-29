import test from 'node:test'
import assert from 'node:assert/strict'
import { loadAllPages } from '../src/utils/pagination.js'

test('loads all pages without exceeding the server page limit', async () => {
  const calls = []
  const fetchPage = async (params) => {
    calls.push(params)
    const start = (params.current - 1) * params.size
    return { status: 200, data: { total: 103, records: Array.from({ length: Math.max(0, Math.min(params.size, 103 - start)) }, (_, i) => start + i) } }
  }
  const result = await loadAllPages(fetchPage, { status: 'TODO', size: 500 })
  assert.equal(result.data.records.length, 103)
  assert.deepEqual(calls.map((item) => item.current), [1, 2, 3])
  assert.ok(calls.every((item) => item.size === 50 && item.status === 'TODO'))
})

test('stops on an empty page even if the reported total is stale', async () => {
  let count = 0
  const result = await loadAllPages(async () => ({ data: { total: 100, records: count++ ? [] : [1] } }))
  assert.deepEqual(result.data.records, [1])
  assert.equal(count, 2)
})

test('propagates failures instead of returning partial results', async () => {
  let count = 0
  await assert.rejects(loadAllPages(async () => {
    if (count++) throw new Error('network')
    return { data: { total: 100, records: [1] } }
  }), /network/)
})
