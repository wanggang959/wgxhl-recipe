const test=require('node:test'),assert=require('node:assert/strict')
let definition;global.Page=value=>{definition=value}
require('../pages/profile/index');delete global.Page
test('password sheet hides and restores custom navigation without changing data',()=>{
 const states=[],calls=[]
 global.wx={hideTabBar:()=>calls.push('hide'),showTabBar:()=>calls.push('show')}
 const p={...definition,data:{isGuest:false,passwordOpen:false,passwordBusy:false},setData(update){Object.assign(this.data,update)},getTabBar(){return {setData:update=>states.push(update)}}}
 p.openPassword();assert.equal(p.data.passwordOpen,true);assert.deepEqual(states[0],{hidden:true})
 p.closePassword();assert.equal(p.data.passwordOpen,false);assert.deepEqual(states[1],{hidden:false})
 assert.deepEqual(calls,[]);delete global.wx
})

for(const route of ['home','favorites','wanted','todos','profile']) {
 test(`${route} notification panel restores only the custom navigation`,()=>{
  let pageDefinition
  global.Page=value=>{pageDefinition=value}
  delete require.cache[require.resolve(`../pages/${route}/index`)]
  require(`../pages/${route}/index`);delete global.Page
  const states=[],calls=[]
  global.wx={hideTabBar:()=>calls.push('hide'),showTabBar:()=>calls.push('show')}
  const p={...pageDefinition,data:{noticeOpen:false},setData(update){Object.assign(this.data,update)},getTabBar(){return {setData:update=>states.push(update)}},refreshUnread(){}}
  p.openNotifications();assert.equal(p.data.noticeOpen,true)
  p.closeNotifications();assert.equal(p.data.noticeOpen,false)
  assert.deepEqual(states,[{hidden:true},{hidden:false}])
  assert.deepEqual(calls,[])
  delete global.wx
 })
}

