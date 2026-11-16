const test = require('node:test'), assert = require('node:assert/strict')
const { loadPage, makePage } = require('./page-helper')
const api = require('../services/api'), session = require('../services/session')
const definition = loadPage('../pages/recipe-form/index')
const detail = {
  recipe: { id: 'r1', recipeName: '旧菜', recipeVersion: '1.0', cookingTime: '10分钟', categoryId: 'c1', status: '上架' },
  recipeStepList: [],
  ingredientList: [],
  seasoningList: [],
}

function setup(calls) {
  session.get = () => ({ id: 'u1', token: 'token', memberRole: 'owner', userRole: 'user' })
  global.wx = { showToast() {}, hideTabBar() {}, showTabBar() {}, setNavigationBarTitle() {}, redirectTo() {}, navigateBack() {}, switchTab() {} }
  api.post = async (path, data) => {
    calls.push({ path, data })
    if (path === '/category/page') return { records: [{ id: 'c1', categoryName: '家常' }], total: 1 }
    if (path === '/ingredient/page') return { records: [{ id: 'i1', ingredientName: '番茄', ingredientImage: 'ingredient/a.webp' }], total: 1 }
    if (path === '/seasoning/page') return { records: [{ id: 's1', seasoningName: '盐' }], total: 1 }
    if (path === '/recipe/getById') return detail
    if (path === '/recipe/create') return { recipe: { id: 'new1' } }
    if (path === '/recipe/update') return null
    if (path === '/ingredient/create') return { id: 'i2', ingredientName: '青椒' }
    throw new Error('unexpected ' + path)
  }
  const page = makePage(definition)
  return page
}

const tick = () => new Promise(resolve => setImmediate(resolve))

test('create bootstrap loads categories, bases and one empty row each', async () => {
  const calls = []
  const page = setup(calls)
  page.onLoad({})
  await tick()
  assert.equal(page.data.loading, false)
  assert.equal(page.data.categoryOptions.length, 2)
  assert.equal(page.data.steps.length, 1)
  assert.equal(page.data.ingredients.length, 1)
  assert.equal(page.data.seasonings.length, 1)
  assert.equal(page.data.ingredientUnits[0], '克')
})

test('invalid or unnamed recipes never reach the server', async () => {
  const calls = []
  const page = setup(calls)
  page.onLoad({})
  await tick()
  const before = calls.filter(call => call.path === '/recipe/create').length
  await page.submit()
  assert.equal(calls.filter(call => call.path === '/recipe/create').length, before)
  page.setData({ 'form.recipeName': '番茄炒蛋', 'form.recipeVersion': '1' })
  await page.submit()
  assert.equal(calls.filter(call => call.path === '/recipe/create').length, before)
})

test('create posts the same payload shape as the web app and opens the detail', async () => {
  const calls = []
  const page = setup(calls)
  page.onLoad({})
  await tick()
  page.setData({
    'form.recipeName': '番茄炒蛋',
    'form.recipeVersion': '1.0',
    'form.categoryId': 'c1',
    'form.categoryName': '家常',
    'form.cookingMinutes': '20',
    'ingredients[0].ingredientId': 'i1',
    'ingredients[0].ingredientName': '番茄',
    'ingredients[0].amount': '2',
    'steps[0].stepTitle': '切块',
  })
  let redirected = ''
  const original = global.setTimeout
  global.setTimeout = fn => { fn(); return 0 }
  global.wx.redirectTo = options => { redirected = options.url }
  await page.submit()
  global.setTimeout = original
  const create = calls.find(call => call.path === '/recipe/create')
  assert.equal(create.data.recipe.recipeName, '番茄炒蛋')
  assert.equal(create.data.recipe.cookingTime, '20分钟')
  assert.equal(create.data.recipe.categoryName, '家常')
  assert.equal(create.data.ingredientList.length, 1)
  assert.equal(create.data.ingredientList[0].sortNo, 1)
  assert.equal(create.data.recipeStepList.length, 1)
  assert.match(redirected, /id=new1/)
})

test('edit mode loads the recipe and updates instead of creating', async () => {
  const calls = []
  const page = setup(calls)
  page.onLoad({ id: 'r1' })
  await tick()
  assert.equal(page.data.isEdit, true)
  assert.equal(page.data.form.recipeName, '旧菜')
  assert.equal(page.data.form.cookingMinutes, '10')
  page.setData({ 'form.recipeName': '新名字' })
  const original = global.setTimeout
  global.setTimeout = fn => { fn(); return 0 }
  await page.submit()
  global.setTimeout = original
  assert.equal(calls.some(call => call.path === '/recipe/create'), false)
  const update = calls.find(call => call.path === '/recipe/update')
  assert.equal(update.data.recipe.id, 'r1')
  assert.equal(update.data.recipe.recipeName, '新名字')
})

test('quick create appends the base item and selects it', async () => {
  const calls = []
  const page = setup(calls)
  page.onLoad({})
  await tick()
  page.openQuick({ currentTarget: { dataset: { type: 'ingredient' } } })
  page.setData({ quickName: '青椒' })
  await page.submitQuick()
  assert.equal(calls.some(call => call.path === '/ingredient/create' && call.data.ingredientName === '青椒'), true)
  assert.equal(page.data.ingredients[0].ingredientId, 'i2')
  assert.equal(page.data.ingredients[0].ingredientName, '青椒')
  assert.equal(page.data.quickOpen, false)
})

test('saving waits for cover and step uploads to finish', async () => {
  const calls = []
  const page = setup(calls)
  page.onLoad({})
  await tick()
  page.setData({ 'form.recipeName': '番茄炒蛋', coverUploading: true })
  await page.submit()
  page.setData({ coverUploading: false, uploadingKey: 'step-0' })
  await page.submit()
  assert.equal(calls.some(call => call.path === '/recipe/create'), false)
})
