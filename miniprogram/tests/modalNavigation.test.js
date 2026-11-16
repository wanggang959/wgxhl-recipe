const test=require('node:test'),assert=require('node:assert/strict')
let definition;global.Page=value=>{definition=value}
require('../pages/profile/index');delete global.Page
test('password sheet hides and restores custom navigation without changing data',()=>{
 const states=[],calls=[]
 global.wx={hideTabBar:()=>calls.push('hide'),showTabBar:()=>calls.push('show')}
 const p={...definition,data:{isGuest:false,passwordOpen:false,passwordBusy:false},setData(update){Object.assign(this.data,update)},getTabBar(){return {setData:update=>states.push(update)}}}
 p.openPassword();assert.equal(p.data.passwordOpen,true);assert.deepEqual(states[0],{hidden:true})
 p.closePassword();assert.equal(p.data.passwordOpen,false);assert.deepEqual(states[1],{hidden:false})
 assert.deepEqual(calls,['hide','show']);delete global.wx
})

