const test = require('node:test'), assert = require('node:assert/strict')
const { loadPage, makePage, stubStorage } = require('./page-helper')
const api = require('../services/api'), session = require('../services/session')
const definition = loadPage('../pages/favorites/index')

function setup(calls) {
  session.get = () => ({ id: 'u1', token: 'token' })
  global.wx = { ...stubStorage(), showToast() {}, reLaunch() {} }
  api.post = async (path, data) => {
    calls.push({ path, data })
    if (path === '/favorite/page') {
      return { records: [{ id: 'f1', recipeId: 'r1' }, { id: 'f2', recipeId: 'r2' }], total: 2 }
    }
    if (path === '/recipe/batch') {
      return [
        { id: 'r1', recipeName: '番茄炒蛋', coverImage: 'recipe/a.webp', likeCount: 2 },
        { id: 'r2', recipeName: '土豆丝', coverImage: 'recipe/b.webp' },
      ]
    }
    if (path === '/favorite/delete') return null
    if (path === '/recipe/toggleLike') return { liked: true, likeCount: 3 }
    if (path === '/notification/unreadCount') return 0
    throw new Error('unexpected ' + path)
  }
  const page = makePage(definition, { guest: false })
  page.user = { id: 'u1', token: 'token' }
  return page
}

test('favorites load merges recipe data and marks them favorite', async () => {
  const calls = []
  const page = setup(calls)
  await page.load(true)
  assert.equal(calls[0].path, '/favorite/page')
  assert.deepEqual(calls[1], { path: '/recipe/batch', data: { ids: ['r1', 'r2'] } })
  assert.equal(page.data.list.length, 2)
  assert.equal(page.data.list[0].favoriteId, 'f1')
  assert.equal(page.data.list[0].favorite, true)
  assert.equal(page.data.list[0].recipeName, '番茄炒蛋')
  assert.match(page.data.list[0].coverUrl, /recipe\/a\.webp$/)
  assert.equal(page.data.total, 2)
})

test('removing a favorite deletes by favoriteId and updates the count', async () => {
  const calls = []
  const page = setup(calls)
  await page.load(true)
  await page.removeFavorite({ currentTarget: { dataset: { id: 'f1' } } })
  assert.equal(calls.some(call => call.path === '/favorite/delete' && call.data.id === 'f1'), true)
  assert.deepEqual(page.data.list.map(item => item.favoriteId), ['f2'])
  assert.equal(page.data.total, 1)
})

test('liking from the favorite card updates the recipe state', async () => {
  const calls = []
  const page = setup(calls)
  await page.load(true)
  await page.toggleLike({ currentTarget: { dataset: { id: 'r1' } } })
  const target = page.data.list.find(item => item.recipeId === 'r1')
  assert.equal(target.liked, true)
  assert.equal(target.likeCount, 3)
})

test('guests never request favorites', () => {
  const calls = []
  const page = setup(calls)
  session.get = () => ({ id: 'guest', token: 'guest-token', username: 'guest' })
  page.onShow()
  page.stopUnreadTimer()
  assert.equal(page.data.guest, true)
  assert.equal(calls.some(call => call.path === '/favorite/page'), false)
})
