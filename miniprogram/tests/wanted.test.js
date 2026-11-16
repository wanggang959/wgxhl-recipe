const test = require('node:test'), assert = require('node:assert/strict')
const { loadPage, makePage, stubStorage } = require('./page-helper')
const api = require('../services/api'), session = require('../services/session')
const definition = loadPage('../pages/wanted/index')

function setup(calls) {
  session.get = () => ({ id: 'u1', token: 'token', nickname: '小王' })
  global.wx = {
    ...stubStorage(),
    showToast() {}, hideTabBar() {}, showTabBar() {}, switchTab() {}, navigateTo() {},
    showModal: options => { global.__modal = options },
  }
  api.post = async (path, data) => {
    calls.push({ path, data })
    if (path === '/want/dateList') return ['2026-11-15', '2026-11-16', '2026-11-18']
    if (path === '/want/page') {
      return { records: [{ id: 'w1', recipeId: 'r1', recipeName: '番茄炒蛋', plannedDate: data.plannedDate, coverImage: 'recipe/a.webp' }], total: 1 }
    }
    if (path === '/want/updateDate' || path === '/want/delete') return null
    if (path === '/want/notifyPreview') return { previewBody: '王师傅今天想吃番茄炒蛋' }
    if (path === '/user/members') return [{ id: 'admin-wangshifu', nickname: '王师傅' }, { id: 'u2', nickname: '家人' }]
    if (path === '/want/notify') return 1
    if (path === '/notification/unreadCount') return 0
    throw new Error('unexpected ' + path)
  }
  const page = makePage(definition)
  page.onLoad()
  page.setData({ today: '2026-11-16', selectedDate: '2026-11-16' })
  return page
}

test('date tabs hide past dates and keep today/tomorrow first', async () => {
  const calls = []
  const page = setup(calls)
  await page.loadDates()
  assert.deepEqual(page.data.dateTabs.map(item => item.label), ['今日', '明日', '11月18日'])
  assert.equal(page.data.selectedDate, '2026-11-16')
  assert.equal(page.data.hasWanted, true)
})

test('picking a date reloads that day and normalizes covers', async () => {
  const calls = []
  const page = setup(calls)
  await page.loadDates()
  await page.loadList(true)
  await page.pickDate({ currentTarget: { dataset: { value: '2026-11-18' } } })
  const last = calls.filter(call => call.path === '/want/page').pop()
  assert.equal(last.data.plannedDate, '2026-11-18')
  assert.equal(page.data.list[0].plannedLabel, '11月18日')
  assert.match(page.data.list[0].coverUrl, /recipe\/a\.webp$/)
})

test('editing the date rejects past dates without a request', async () => {
  const calls = []
  const page = setup(calls)
  page.setData({ editTarget: { id: 'w1', recipeName: '番茄炒蛋' }, editDate: '2026-11-15' })
  await page.submitEdit()
  assert.equal(calls.some(call => call.path === '/want/updateDate'), false)
})

test('editing the date saves a valid date', async () => {
  const calls = []
  const page = setup(calls)
  page.setData({ editTarget: { id: 'w1', recipeName: '番茄炒蛋' }, editDate: '2026-11-18' })
  await page.submitEdit()
  assert.deepEqual(calls.find(call => call.path === '/want/updateDate').data, { id: 'w1', plannedDate: '2026-11-18' })
})

test('removing only calls the server after confirmation', async () => {
  const calls = []
  const page = setup(calls)
  await page.loadDates()
  await page.loadList(true)
  page.remove({ currentTarget: { dataset: { id: 'w1' } } })
  global.__modal.success({ confirm: false })
  assert.equal(calls.some(call => call.path === '/want/delete'), false)
  page.remove({ currentTarget: { dataset: { id: 'w1' } } })
  await global.__modal.success({ confirm: true })
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(calls.some(call => call.path === '/want/delete'), true)
  assert.equal(page.data.list.length, 0)
})

test('notify defaults to Wang Shifu and sends selected member ids', async () => {
  const calls = []
  const page = setup(calls)
  page.setData({ hasWanted: true })
  await page.openNotify()
  assert.equal(page.data.notifyOpen, true)
  assert.deepEqual(page.data.selectedNotifyIds, ['admin-wangshifu'])
  assert.equal(page.data.notifyMembers[1].selected, false)
  await page.submitNotify()
  assert.deepEqual(calls.find(call => call.path === '/want/notify').data, { targetUserIds: ['admin-wangshifu'] })
})

test('empty want list offers no notify action', async () => {
  const calls = []
  const page = setup(calls)
  await page.loadDates()
  page.setData({ hasWanted: false })
  await page.openNotify()
  assert.equal(calls.some(call => call.path === '/want/notifyPreview'), false)
  assert.equal(page.data.notifyOpen, false)
})

test('guests do not load family want data', () => {
  const calls = []
  const page = setup(calls)
  page.setData({ guest: true })
  page.onShow()
  page.stopUnreadTimer()
  assert.equal(calls.some(call => call.path === '/want/page'), false)
})
