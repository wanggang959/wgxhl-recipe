import test from 'node:test'
import assert from 'node:assert/strict'
import { expenseImageLayout, expenseTotal, wrapCanvasText } from './expenseListImage.js'

const context = { font: '', measureText: (text) => ({ width: [...text].length * 10 }) }
test('wraps long names and quantities without losing content', () => {
  assert.deepEqual(wrapCanvasText(context, 'abcdef', 20), ['ab', 'cd', 'ef'])
  const layout = expenseImageLayout(context, '专项采购', [{ itemName: '很'.repeat(40), quantity: '箱'.repeat(20), actualAmount: 12.5 }])
  assert.equal(layout.rows[0].name.join(''), '很'.repeat(40))
  assert.equal(layout.rows[0].quantity.join(''), '箱'.repeat(20))
  assert.equal(layout.rows[0].amount.join(''), '¥12.5')
})
test('keeps blank quantities explicit and rejects images that exceed the safe height', () => {
  assert.equal(expenseImageLayout(context, '测试', [{ itemName: '水', quantity: '', actualAmount: 0 }]).rows[0].quantity[0], '—')
  assert.throws(() => expenseImageLayout(context, '测试', Array.from({ length: 300 }, () => ({ itemName: '项目' }))), /清单过长/)
})
test('adds actual amounts accurately for the exported entries, including cancelled items', () => {
  const items = [{ actualAmount: 0.1 }, { actualAmount: 0.2 }, { actualAmount: 1234.56, status: 'CANCELLED' }, { actualAmount: null }]
  assert.equal(expenseTotal(items), 1234.86)
  assert.equal(expenseImageLayout(context, '测试专项', items).total.join(''), '¥1,234.86')
})
