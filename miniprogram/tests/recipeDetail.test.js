const test = require('node:test'), assert = require('node:assert/strict')
const { loadPage, makePage } = require('./page-helper')
const api = require('../services/api'), session = require('../services/session')
const definition = loadPage('../pages/recipe-detail/index')
const detail = {
  recipe: {
    id: 'r1', recipeName: '番茄炒蛋', ownerUserId: 'u1', ownerName: '小王',
    status: '上架', visibility: 'public', favoriteCount: 1, likeCount: 2,
    coverImage: 'recipe/a.webp', cookingTime: '20分钟', recipeVersion: '1.0',
  },
  ingredientList: [{ id: 'i1', ingredientName: '番茄', amount: '2', unit: '个', ingredientImage: 'ingredient/a.webp' }],
  seasoningList: [],
  recipeStepList: [{ id: 's1', stepNo: 1, stepTitle: '切', stepDesc: '切块', stepImage: 'recipe/step/a.webp' }],
}

function setup(calls) {
  session.get = () => ({ id: 'u1', token: 'token', userRole: 'user', memberRole: 'owner' })
  global.wx = {
    showToast() {}, showModal: options => { global.__modal = options },
    navigateTo() {}, navigateBack() {}, previewImage() {}, reLaunch() {}, switchTab() {},
  }
  api.post = async (path, data) => {
    calls.push({ path, data })
    if (path === '/recipe/getById') return detail
    if (path === '/favorite/check') return true
    if (path === '/want/check') return false
    if (path === '/favorite/deleteByRecipeId') return { favoriteCount: 0 }
    if (path === '/favorite/create') return {}
    if (path === '/recipe/toggleLike') return { liked: true, likeCount: 3 }
    if (path === '/want/create' || path === '/want/deleteByRecipeId') return {}
    if (path === '/recipe/delete' || path === '/recipe/setStatus') return null
    throw new Error('unexpected ' + path)
  }
  const page = makePage(definition)
  page.onLoad({ id: 'r1' })
  return page
}

const tick = () => new Promise(resolve => setImmediate(resolve))

test('detail loads favorite state and ownership', async () => {
  const calls = []
  const page = setup(calls)
  await tick()
  assert.equal(page.data.recipe.recipeName, '番茄炒蛋')
  assert.equal(page.data.isFavorite, true)
  assert.equal(page.data.isWanted, false)
  assert.equal(page.data.isOwner, true)
  assert.equal(page.data.ingredients.length, 1)
  assert.match(page.data.imageUrls[0], /recipe\/a\.webp$/)
})

test('favorite, like and want actions update state', async () => {
  const calls = []
  const page = setup(calls)
  await tick()
  await page.toggleFavorite()
  assert.equal(page.data.isFavorite, false)
  assert.equal(page.data.recipe.favoriteCount, 0)
  await page.toggleLike()
  assert.equal(page.data.recipe.liked, true)
  assert.equal(page.data.recipe.likeCount, 3)
  await page.toggleWant()
  assert.equal(page.data.isWanted, true)
  const wanted = calls.find(call => call.path === '/want/create')
  assert.equal(wanted.data.userId, 'u1')
  assert.equal(wanted.data.recipeId, 'r1')
  assert.match(wanted.data.plannedDate, /^\d{4}-\d{2}-\d{2}$/)
})

test('only the owner can delete and only the super admin can take down', async () => {
  const calls = []
  const page = setup(calls)
  await tick()
  page.setData({ isOwner: false })
  page.remove()
  assert.equal(calls.some(call => call.path === '/recipe/delete'), false)
  page.setData({ isOwner: true })
  page.remove()
  await global.__modal.success({ confirm: true })
  assert.equal(calls.some(call => call.path === '/recipe/delete'), true)
  page.setData({ isSuperAdmin: false })
  page.toggleStatus()
  assert.equal(calls.some(call => call.path === '/recipe/setStatus'), false)
  page.setData({ isSuperAdmin: true })
  page.toggleStatus()
  await global.__modal.success({ confirm: true })
  await tick()
  const status = calls.find(call => call.path === '/recipe/setStatus')
  assert.deepEqual(status.data, { id: 'r1', status: '下架' })
})

test('guests cannot mutate recipes', async () => {
  const calls = []
  const page = setup(calls)
  await tick()
  page.setData({ guest: true })
  const before = calls.length
  await page.toggleFavorite()
  await page.toggleLike()
  await page.toggleWant()
  assert.equal(calls.length, before)
})
