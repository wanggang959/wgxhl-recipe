const test = require('node:test'), assert = require('node:assert/strict')
const { buildIngredientSummary, buildSeasoningSummary, summarySignature, isInRange, formatPurchaseLine, statusLabel } = require('../utils/purchaseSummary')

const details = [
  {
    ingredientList: [
      { ingredientName: '番茄', amount: '2', unit: '个', ingredientImage: 'ingredient/a.jpg' },
      { ingredientName: '番茄', amount: '1', unit: '个' },
      { ingredientName: '盐', amount: '适量', unit: '' },
      { ingredientName: '葱', amount: '一把', unit: '' },
    ],
    seasoningList: [{ seasoningName: '生抽' }, { seasoningName: '盐' }],
  },
  {
    ingredientList: [{ ingredientName: '番茄', amount: '500', unit: '克' }],
    seasoningList: [{ seasoningName: '生抽' }],
  },
]

test('ingredients aggregate numeric amounts per unit and keep text amounts', () => {
  const summary = buildIngredientSummary(details)
  const tomato = summary.find(item => item.name === '番茄')
  assert.equal(tomato.amount, '3个+500克')
  assert.equal(tomato.image, 'ingredient/a.jpg')
  assert.equal(summary.find(item => item.name === '葱').amount, '一把')
  assert.equal(summary.find(item => item.name === '盐').amount, '适量')
})

test('seasonings dedupe by name and keep a stable key', () => {
  const summary = buildSeasoningSummary(details)
  assert.deepEqual(summary.map(item => item.name), ['生抽', '盐'])
  assert.equal(summary[0].key, 'seasoning:生抽')
})

test('signature changes when amounts change but ignores order', () => {
  const a = buildIngredientSummary(details)
  const b = [...buildIngredientSummary(details)].reverse()
  const s = buildSeasoningSummary(details)
  assert.equal(summarySignature(a, s), summarySignature(b, s))
  assert.notEqual(summarySignature(a, s), summarySignature(a.map(item => ({ ...item, amount: '0' })), s))
})

test('date ranges cover today, tomorrow, near days and all', () => {
  const today = '2026-11-16'
  assert.equal(isInRange('2026-11-16', 'today', today), true)
  assert.equal(isInRange('2026-11-17', 'today', today), false)
  assert.equal(isInRange('2026-11-17', 'tomorrow', today), true)
  assert.equal(isInRange('2026-11-17', 'next2', today), true)
  assert.equal(isInRange('2026-11-18', 'next2', today), false)
  assert.equal(isInRange('2026-11-18', 'next3', today), true)
  assert.equal(isInRange('2099-01-01', 'all', today), true)
})

test('purchase lines and marks use the edited quantity first', () => {
  const item = { name: '番茄', amount: '3个' }
  assert.equal(formatPurchaseLine(item), '番茄 3个')
  assert.equal(formatPurchaseLine(item, { status: 'buy', quantity: '2袋' }), '番茄 2袋')
  assert.equal(statusLabel({ status: 'enough' }), '已够')
  assert.equal(statusLabel({ status: 'buy', quantity: '2袋' }), '待购 2袋')
  assert.equal(statusLabel({}), '')
})
