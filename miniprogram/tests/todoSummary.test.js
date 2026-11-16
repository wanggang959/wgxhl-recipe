const test = require('node:test'), assert = require('node:assert/strict')
const { loadPage, makePage, stubStorage } = require('./page-helper')
const api = require('../services/api'), session = require('../services/session')
const definition = loadPage('../pages/todo-summary/index')

function setup(calls) {
  session.get = () => ({ id: 'u1', token: 'token' })
  global.wx = {
    ...stubStorage(),
    showToast() {}, switchTab() {}, navigateBack() {}, hideTabBar() {}, showTabBar() {},
  }
  api.post = async (path, data) => {
    calls.push({ path, data })
    if (path === '/want/page') {
      return {
        records: [
          { id: 'w1', recipeId: 'r1', recipeName: '番茄炒蛋', plannedDate: '2026-11-16' },
          { id: 'w2', recipeId: 'r2', recipeName: '糖拌番茄', plannedDate: '2026-11-20' },
        ],
        total: 2,
      }
    }
    if (path === '/recipe/getById') {
      if (data.id === 'r1') {
        return { recipe: { id: 'r1' }, ingredientList: [{ ingredientName: '番茄', amount: '2', unit: '个' }, { ingredientName: '盐', amount: '适量', unit: '' }], seasoningList: [{ seasoningName: '盐' }] }
      }
      return { recipe: { id: 'r2' }, ingredientList: [{ ingredientName: '番茄', amount: '3', unit: '个' }, { ingredientName: '糖', amount: '1', unit: '勺' }], seasoningList: [{ seasoningName: '糖' }] }
    }
    if (path === '/todo/create') return {}
    throw new Error('unexpected ' + path)
  }
  const page = makePage(definition, { guest: false, today: '2026-11-16', activeRange: 'today' })
  page.user = { id: 'u1' }
  return page
}

test('summary only counts wanted recipes inside the active range', async () => {
  const calls = []
  const page = setup(calls)
  await page.load()
  assert.equal(page.data.wantedCount, 1)
  const tomato = page.data.ingredients.find(item => item.name === '番茄')
  assert.equal(tomato.amount, '2个')
  assert.equal(page.data.seasonings.length, 1)
})

test('changing the range re-aggregates without refetching', async () => {
  const calls = []
  const page = setup(calls)
  await page.load()
  const pages = calls.filter(call => call.path === '/want/page').length
  page.changeRange({ currentTarget: { dataset: { range: 'all' } } })
  assert.equal(page.data.wantedCount, 2)
  assert.equal(page.data.ingredients.find(item => item.name === '番茄').amount, '5个')
  assert.equal(calls.filter(call => call.path === '/want/page').length, pages)
})

test('marks persist per range and can be edited', async () => {
  const calls = []
  const page = setup(calls)
  await page.load()
  const item = page.data.ingredients.find(entry => entry.name === '番茄')
  page.applyMark(item.key, 'buy', '2袋')
  assert.equal(page.data.ingredients.find(entry => entry.name === '番茄').statusText, '待购 2袋')
  page.applyMark(item.key, 'enough')
  assert.equal(page.data.ingredients.find(entry => entry.name === '番茄').statusText, '已够')
  page.applyMark(item.key, '')
  assert.equal(page.data.ingredients.find(entry => entry.name === '番茄').statusText, '')
})

test('purchase preview lists marked lines before creating the todo', async () => {
  const calls = []
  const page = setup(calls)
  await page.load()
  const item = page.data.ingredients.find(entry => entry.name === '番茄')
  page.applyMark(item.key, 'buy', '2袋')
  page.openPurchasePreview()
  assert.equal(page.data.previewOpen, true)
  assert.deepEqual(page.data.previewLines, ['番茄 2袋'])
  await page.confirmPurchaseTodo()
  assert.equal(page.data.previewOpen, false)
  const create = calls.find(call => call.path === '/todo/create')
  assert.equal(create.data.title, '采购任务')
  assert.equal(create.data.category, 'GROCERY')
  assert.match(create.data.description, /番茄 2袋/)
  assert.match(create.data.dueTime, /T18:00:00$/)
})

test('the buy editor saves a custom quantity', async () => {
  const calls = []
  const page = setup(calls)
  await page.load()
  const item = page.data.ingredients.find(entry => entry.name === '番茄')
  page.openBuyEditor(item)
  assert.equal(page.data.buyEditorOpen, true)
  page.setData({ buyQuantity: ' 2袋 ' })
  page.submitBuyEditor()
  assert.equal(page.data.buyEditorOpen, false)
  assert.equal(page.data.ingredients.find(entry => entry.name === '番茄').statusText, '待购 2袋')
})

test('guests cannot load the family summary', async () => {
  const calls = []
  const page = setup(calls)
  page.setData({ guest: true })
  page.onShow()
  page.openPurchasePreview()
  assert.equal(calls.length, 0)
})
