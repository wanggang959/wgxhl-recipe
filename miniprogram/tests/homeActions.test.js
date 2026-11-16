const test = require('node:test'), assert = require('node:assert/strict')
const { loadPage, makePage, stubStorage } = require('./page-helper')
const api = require('../services/api'), session = require('../services/session')
const definition = loadPage('../pages/home/index')

function setup(calls) {
  session.get = () => ({ id: 'u1', token: 'token' })
  global.wx = { ...stubStorage(), showToast() {}, reLaunch() {}, navigateTo() {} }
  api.post = async (path, data) => {
    calls.push({ path, data })
    if (path === '/favorite/create') return {}
    if (path === '/favorite/deleteByRecipeId') return { favoriteCount: 0 }
    if (path === '/recipe/toggleLike') return { liked: true, likeCount: 5 }
    throw new Error('unexpected ' + path)
  }
  return makePage(definition, {
    user: { id: 'u1', token: 'token' },
    recipes: [
      { id: 'r1', recipeName: '番茄炒蛋', favorite: false, favoriteCount: 0, liked: false, likeCount: 4, status: '上架' },
      { id: 'r2', recipeName: '土豆丝', favorite: true, favoriteCount: 1, liked: true, likeCount: 2, status: '上架' },
    ],
  })
}

test('favoriting from a card creates the favorite and updates the count', async () => {
  const calls = []
  const page = setup(calls)
  await page.toggleFavorite({ currentTarget: { dataset: { id: 'r1' } } })
  assert.deepEqual(calls[0].data, { userId: 'u1', recipeId: 'r1' })
  const item = page.data.recipes.find(entry => entry.id === 'r1')
  assert.equal(item.favorite, true)
  assert.equal(item.favoriteCount, 1)
})

test('unfavoriting uses the current user and recipe id', async () => {
  const calls = []
  const page = setup(calls)
  await page.toggleFavorite({ currentTarget: { dataset: { id: 'r2' } } })
  assert.equal(calls[0].path, '/favorite/deleteByRecipeId')
  const item = page.data.recipes.find(entry => entry.id === 'r2')
  assert.equal(item.favorite, false)
  assert.equal(item.favoriteCount, 0)
})

test('liking updates the card state from the server result', async () => {
  const calls = []
  const page = setup(calls)
  await page.toggleLike({ currentTarget: { dataset: { id: 'r1' } } })
  const item = page.data.recipes.find(entry => entry.id === 'r1')
  assert.equal(item.liked, true)
  assert.equal(item.likeCount, 5)
})

test('guests cannot favorite or like', async () => {
  const calls = []
  const page = setup(calls)
  page.setData({ guest: true })
  await page.toggleFavorite({ currentTarget: { dataset: { id: 'r1' } } })
  await page.toggleLike({ currentTarget: { dataset: { id: 'r1' } } })
  assert.equal(calls.length, 0)
})

test('adding a recipe to wanted requires a valid date and uses the selected user', async () => {
  const calls=[];const page=setup(calls);global.wx.hideTabBar=()=>{};global.wx.showTabBar=()=>{}
  api.post=async(path,data)=>{calls.push({path,data})}
  page.openWant({currentTarget:{dataset:{id:'r1'}}})
  await page.submitWant('2000-01-01');assert.equal(calls.length,0)
  await page.submitWant('2099-01-01');assert.deepEqual(calls[0],{path:'/want/create',data:{userId:'u1',recipeId:'r1',plannedDate:'2099-01-01'}});assert.equal(page.data.wantOpen,false);assert.equal(page.data.recipes[0].wanted,true)
})
