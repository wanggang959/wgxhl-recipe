const test = require('node:test'), assert = require('node:assert/strict')
const { loadPage, makePage, stubStorage } = require('./page-helper')
const api = require('../services/api'), session = require('../services/session')
const definition = loadPage('../pages/login/index')

test('wechat binding option matches the other login options', () => {
  const fs = require('node:fs')
  const styles = fs.readFileSync(require('node:path').join(__dirname, '../pages/login/index.wxss'), 'utf8')
  assert.match(styles, /\.wechat-bind\s*\{[^}]*color:\s*#8e7d70[^}]*font-size:\s*14px/)
})

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

test('wechat login sends the one-time code and stores the returned session', async () => {
  const calls = [], page = setup(calls)
  wx.login = ({ success }) => success({ code: 'temporary-code' })
  api.post = async (path, data, options) => { calls.push({ path, data, options }); return { id: 'u1', token: 'token' } }
  await withoutTimer(() => page.wechatLogin())
  assert.equal(calls[0].path, '/user/wechat/login')
  assert.equal(calls[0].data.code, 'temporary-code')
  assert.equal(calls[0].options.public, true)
  assert.equal(page.saved().id, 'u1')
})

test('binding occurs only after account login', async () => {
  const calls = [], page = setup(calls)
  wx.login = ({ success }) => success({ code: 'temporary-code' })
  api.post = async (path, data) => { calls.push({ path, data }); return path === '/user/login' ? { id: 'u1', token: 'token' } : null }
  page.setData({ username: 'u1', password: 'password', bindWechat: true })
  await withoutTimer(() => page.submit())
  assert.deepEqual(calls.map(item => item.path), ['/user/login', '/user/wechat/bind'])
  assert.equal(calls[1].data.code, 'temporary-code')
})
