const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { present } = require('../utils/todo')
const config = require('../config')
const session = require('../services/session')
const { post } = require('../services/api')
test('todo display uses the server birthday date and safe fallbacks', () => {
  assert.equal(present({category:'BIRTHDAY',birthdaySolarLabel:'2026-12-26 星期六'}).displayDate, '2026-12-26 星期六')
  assert.equal(present({category:'OTHER'}).displayOwner, '未指定')
})
test('all declared native pages exist and API uses HTTPS', () => {
  const root=path.join(__dirname,'..'), app=JSON.parse(fs.readFileSync(path.join(root,'app.json'),'utf8'))
  for(const page of app.pages) for(const extension of ['.js','.json','.wxml','.wxss']) assert(fs.existsSync(path.join(root,page+extension)))
  assert.equal(require('../config').apiBaseUrl,'https://recipe.wgxhl.space/recipe')
  assert.equal(JSON.parse(fs.readFileSync(path.join(root,'project.config.json'),'utf8')).appid,'wx06b7c87bb5e4ffde')
})
test('only sends authenticated requests to the configured HTTPS API and clears expired sessions', async () => {
  const storage = new Map(); let last
  global.wx = {
    getStorageSync: key => storage.get(key), setStorageSync: (key,value) => storage.set(key,value), removeStorageSync: key => storage.delete(key),
    request: options => { last=options; options.success({statusCode:200,data:{status:200,data:{ok:true}}}) }
  }
  config.apiBaseUrl=''
  await assert.rejects(post('/todo/page'), /尚未配置/)
  config.apiBaseUrl='https://test.example.com/recipe'
  await assert.rejects(post('/todo/page'), /请先登录/)
  session.set({id:'user',token:'token'})
  assert.deepEqual(await post('/todo/page',{current:1}),{ok:true})
  assert.equal(last.url,'https://test.example.com/recipe/todo/page')
  assert.equal(last.header.Authorization,'Bearer token')
  global.wx.request = options => options.fail({errMsg:'request:fail url not in domain list'})
  await assert.rejects(post('/todo/page'), /请求域名未配置/)
  global.wx.request = options => options.fail({errMsg:'request:fail timeout'})
  await assert.rejects(post('/todo/page'), /连接超时/)
  global.wx.request = options => options.success({statusCode:401,data:{status:401}})
  await assert.rejects(post('/todo/page'), /登录已失效/)
  assert.equal(session.get(),null)
  config.apiBaseUrl='https://recipe.wgxhl.space/recipe'
  delete global.wx
})
