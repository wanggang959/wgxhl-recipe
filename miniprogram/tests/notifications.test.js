const test = require('node:test')
const assert = require('node:assert/strict')
const api = require('../services/api')
let definition
global.Page = value => { definition = value }
require('../pages/notifications/index')
delete global.Page
function makePage() {
  return {...definition, data:{items:[], unread:0, page:0, total:0, loading:false, error:'', busy:false, readonly:false}, setData(update){Object.assign(this.data,update)}}
}
test('reads notifications and unread count without changing them', async () => {
  const calls=[]
  api.post=async (path) => {calls.push(path); return path.endsWith('/page') ? {records:[{id:'1',isRead:false,createTime:'2026-11-13T08:00:00'}],total:1} : 1}
  const page=makePage(); await page.load(true)
  assert.deepEqual(calls,['/notification/page','/notification/unreadCount'])
  assert.equal(page.data.unread,1); assert.equal(page.data.items[0].displayTime,'2026-11-13 08:00')
})
test('marks only the selected notification and reloads', async () => {
  const calls=[]
  api.post=async (path,data) => {calls.push({path,data}); return path.endsWith('/page') ? {records:[],total:0} : 0}
  const page=makePage(); page.data.items=[{id:'1',isRead:false}]
  await page.markRead({currentTarget:{dataset:{id:'1'}}})
  assert.deepEqual(calls[0],{path:'/notification/markRead',data:{id:'1'}})
  assert.equal(page.data.busy,false)
})
test('guest and cancelled deletion never mutate notifications', async () => {
  let calls=0; api.post=async()=>{calls++}
  global.wx={showModal:({success})=>success({confirm:false})}
  const page=makePage(); page.data.items=[{id:'1',isRead:false}]; page.data.readonly=true
  await page.markRead({currentTarget:{dataset:{id:'1'}}}); page.remove({currentTarget:{dataset:{id:'1'}}})
  page.data.readonly=false; page.remove({currentTarget:{dataset:{id:'1'}}})
  assert.equal(calls,0); delete global.wx
})

