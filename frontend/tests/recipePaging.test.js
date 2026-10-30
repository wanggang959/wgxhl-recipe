import test from 'node:test'
import assert from 'node:assert/strict'
import { beginRecipeRefresh } from '../src/utils/recipePaging.js'

test('each random refresh starts a new bounded pivot while paging preserves it', () => {
  const query = { current: 4, sort: 'random', randomPivot: 1 }
  beginRecipeRefresh(query, () => 0.25)
  assert.equal(query.current, 1)
  assert.equal(query.randomPivot, 250000001)
  query.current += 1
  assert.equal(query.randomPivot, 250000001)
  beginRecipeRefresh(query, () => 0.75)
  assert.equal(query.current, 1)
  assert.equal(query.randomPivot, 750000001)
  beginRecipeRefresh(query, () => 0)
  assert.equal(query.randomPivot, 1)
  beginRecipeRefresh(query, () => 1 - Number.EPSILON)
  assert.equal(query.randomPivot, 1000000000)
})

test('non-random refresh resets the page without generating a pivot', () => {
  const query = { current: 5, sort: 'latest', randomPivot: 42 }
  beginRecipeRefresh(query, () => { throw new Error('unexpected random call') })
  assert.equal(query.current, 1)
  assert.equal(query.randomPivot, 42)
})
