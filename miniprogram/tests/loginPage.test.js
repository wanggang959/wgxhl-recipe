const test = require('node:test'), assert = require('node:assert/strict')
const { loadPage, makePage, stubStorage } = require('./page-helper')
const api = require('../services/api'), session = require('../services/session')
const definition = loadPage('../pages/login/index')

function setup(calls) {
  session.get = () => null
  let saved = null
  session.set = user => { saved = user }
  global.wx = {
    ...stubStorage(),
    switchTab() {}, showToast() {}, reLaunch() {}, showModal() {}, navigateTo() {},
  }
  const page = makePage(definition)
  page.saved = () => saved
  api.post = async (path, data, options) => {
    calls.push({ path, data, options })
    if (path === '/user/login') return { id: 'u1', token: 'token', username: 'u1' }
    if (path === '/user/guestLogin') return { id: 'guest', token: 'guest-token', username: 'guest' }
    throw new Error('unexpected ' + path)
  }
  return page
}

function withoutTimer(fn) {
  const original = global.setTimeout
  global.setTimeout = callback => { callback(); return 0 }
  return Promise.resolve(fn()).finally(() => { global.setTimeout = original })
}

test('empty credentials never call the login API', async () => {
  const calls = []
  const page = setup(calls)
  await page.submit()
  assert.equal(calls.length, 0)
  assert.match(page.data.statusText, /请输入/)
  assert.equal(page.data.statusType, 'error')
})

test('login stores the session and enters the app', async () => {
  const calls = []
  const page = setup(calls)
  page.setData({ username: 'u1', password: 'password' })
  await withoutTimer(() => page.submit())
  assert.equal(calls[0].path, '/user/login')
  assert.deepEqual(calls[0].options, { public: true })
  assert.equal(page.saved().token, 'token')
  assert.match(page.data.statusText, /登录成功/)
})

test('guest login uses the public endpoint and stores a guest session', async () => {
  const calls = []
  const page = setup(calls)
  await withoutTimer(() => page.guestLogin())
  assert.equal(calls[0].path, '/user/guestLogin')
  assert.deepEqual(calls[0].options, { public: true })
  assert.equal(page.saved().id, 'guest')
})
