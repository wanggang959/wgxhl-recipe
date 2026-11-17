const test = require('node:test'), assert = require('node:assert/strict')
const { loadPage, makePage } = require('./page-helper')
const api = require('../services/api'), session = require('../services/session')
const definition = loadPage('../pages/register/index')

function setup(calls) {
  session.get = () => null
  let saved = null
  session.set = user => { saved = user }
  global.wx = { switchTab() {}, showToast() {}, navigateBack() {}, reLaunch() {} }
  const page = makePage(definition)
  page.saved = () => saved
  api.post = async (path, data, options) => {
    calls.push({ path, data, options })
    if (path === '/user/register/sendCode') return null
    if (path === '/user/register/inviteInfo') return { householdName: '王师傅家', inviterName: '王师傅', memberRole: 'member' }
    if (path === '/user/register/email') return { id: 'u1', token: 'token', username: 'newuser' }
    if (path === '/user/register/invite') return { id: 'u2', token: 'token2', username: 'invited' }
    throw new Error('unexpected ' + path)
  }
  return page
}

test('invalid email never requests a verification code', async () => {
  const calls = []
  const page = setup(calls)
  page.setData({ email: 'bad' })
  await page.sendCode()
  assert.equal(calls.length, 0)
  assert.match(page.data.error, /邮箱/)
})

test('verification codes are requested through the public endpoint', async () => {
  const calls = []
  const page = setup(calls)
  page.setData({ email: 'New@Example.com' })
  await page.sendCode()
  assert.equal(calls[0].path, '/user/register/sendCode')
  assert.equal(calls[0].data.email, 'new@example.com')
  assert.deepEqual(calls[0].options, { public: true })
  page.onUnload()
})

test('password mismatch blocks registration', async () => {
  const calls = []
  const page = setup(calls)
  page.setData({ email: 'new@example.com', code: '123456', username: 'newuser', password: 'password1', confirmPassword: 'password2' })
  await page.submit()
  assert.equal(calls.length, 0)
  assert.match(page.data.error, /不一致/)
})

test('email registration stores the returned session', async () => {
  const calls = []
  const page = setup(calls)
  page.setData({ email: 'new@example.com', code: '123456', username: 'newuser', nickname: '小王', password: 'password1', confirmPassword: 'password1', inviteCode: ' ABC ' })
  await page.submit()
  const request = calls.find(call => call.path === '/user/register/email')
  assert.equal(request.data.email, 'new@example.com')
  assert.equal(request.data.inviteCode, 'ABC')
  assert.deepEqual(request.options, { public: true })
  assert.equal(page.saved().token, 'token')
})

test('invite preview and invite registration use the public endpoint', async () => {
  const calls = []
  const page = setup(calls)
  page.setData({ mode: 'invite', inviteInput: 'ab12' })
  await page.checkInvite()
  assert.equal(calls[0].data.inviteCode, 'AB12')
  assert.equal(page.data.inviteInfo.householdName, '王师傅家')
  page.setData({ username: 'invited', password: 'password1', confirmPassword: 'password1' })
  await page.submit()
  const request = calls.find(call => call.path === '/user/register/invite')
  assert.deepEqual(request.data, { inviteCode: 'AB12', username: 'invited', nickname: '', password: 'password1' })
  assert.equal(page.saved().token, 'token2')
})

test('wechat registration binds using a fresh code after registration', async () => {
  const calls=[],page=setup(calls);let codeCalls=0
  wx.login=({success})=>{codeCalls++;success({code:'fresh-code'})}
  api.post=async(path,data,options)=>{calls.push({path,data,options});if(path==='/user/register/email')return {id:'u1',token:'token',username:'newuser'};if(path==='/user/wechat/bind')return null;throw Error('unexpected')}
  page.setData({bindWechat:true,email:'new@example.com',code:'123456',username:'newuser',password:'password1',confirmPassword:'password1'})
  await page.submit()
  assert.deepEqual(calls.map(call=>call.path),['/user/register/email','/user/wechat/bind'])
  assert.equal(codeCalls,1);assert.equal(calls[1].data.code,'fresh-code');assert.equal(page.saved().id,'u1');assert.equal(page.data.password,'')
})

test('binding failure keeps the registered account and does not repeat registration', async () => {
  const calls=[],page=setup(calls);let modal
  wx.login=({success})=>success({code:'fresh-code'});wx.showModal=options=>{modal=options}
  api.post=async(path,data)=>{calls.push(path);if(path==='/user/register/email')return {id:'u1',token:'token'};throw Error('binding failed')}
  page.setData({bindWechat:true,email:'new@example.com',code:'123456',username:'newuser',password:'password1',confirmPassword:'password1'})
  await page.submit()
  assert.deepEqual(calls,['/user/register/email','/user/wechat/bind']);assert.equal(page.saved().id,'u1');assert.match(modal.content,/账号已创建/)
})
