import test from 'node:test'
import assert from 'node:assert/strict'
import { mergePageRecords, overlapStartPage } from '../src/utils/basePagination.js'

test('deletions overlap previous pages and preserve every remaining record', () => {
  const server = Array.from({ length: 102 }, (_, index) => ({ id: String(index + 1) }))
  const existing = server.slice(0, 100).filter((item) => item.id !== '1')
  const afterDelete = server.filter((item) => item.id !== '1')
  const start = overlapStartPage(2, 1, 50)
  const merged = mergePageRecords(existing, afterDelete.slice((start - 1) * 50, 150))
  assert.equal(start, 2)
  assert.deepEqual(merged, afterDelete)
  assert.equal(new Set(merged.map((item) => item.id)).size, 101)
})

test('large numbers of deletions increase overlap; normal loading does not', () => {
  assert.equal(overlapStartPage(4, 0, 50), 5)
  assert.equal(overlapStartPage(4, 51, 50), 3)
  assert.equal(overlapStartPage(1, 500, 50), 1)
})
