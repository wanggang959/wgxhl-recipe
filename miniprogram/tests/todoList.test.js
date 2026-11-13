const test = require('node:test')
const assert = require('node:assert/strict')
const { groupItems } = require('../utils/todoList')
const now = new Date(2026, 11, 30, 12)
const todos = [{id:'a', category:'BIRTHDAY', dueTime:'2027-01-01 11:00:00'}, {id:'b',category:'LIFE',dueTime:'2026-12-30 18:00:00'}]
const plans = [{id:'c',plannedDate:'2026-12-30'}]
test('mixes cooking and todos in date groups and displays cross-year labels', () => {
  const result = groupItems(todos, plans, '', 'all', now)
  assert.deepEqual(result.groups.map(g=>g.label), ['今日','2027年1月1日'])
  assert.deepEqual(result.groups[0].items.map(i=>i.id), ['c','b'])
  assert.equal(result.todayCount, 2)
})
test('filters by category and time without changing overview counts', () => {
  const result = groupItems(todos, plans, 'COOK', 'today', now)
  assert.deepEqual(result.groups[0].items.map(i=>i.id), ['c'])
  assert.equal(result.todayCount, 2)
  assert.equal(groupItems(todos, plans, 'BIRTHDAY', 'three', now).groups.length, 1)
  assert.equal(groupItems(todos, plans, 'BIRTHDAY', 'today', now).groups.length, 0)
})
