const test=require('node:test'),assert=require('node:assert/strict')
const api=require('../services/api'),session=require('../services/session')
let definition;global.Page=value=>{definition=value};require('../pages/household/index');delete global.Page
function page(data={}){return {...definition,data:{...definition.data,user:{id:'u',memberRole:'owner'},...data},setData(update){Object.assign(this.data,update)}}}
test('switch updates session only with valid returned token',async()=>{
 let saved=null;session.set=user=>{saved=user};global.wx={switchTab:()=>{}}
 api.post=async()=>({id:'u'});const p=page();await p.doChange('/household/switch',{id:'h'})
 assert.equal(saved,null);assert.match(p.data.error,/重新登录/)
 api.post=async()=>({id:'u',token:'new-token'});await p.doChange('/household/switch',{id:'h'});assert.equal(saved.token,'new-token')
 delete global.wx
})
test('cancelled destructive actions do not call backend',()=>{
 let calls=0;api.post=async()=>{calls++};global.wx={showModal:({success})=>success({confirm:false})}
 const p=page({isOwner:true,transferOptions:[{id:'u2',nickname:'test'}]});p.dissolve();p.transfer()
 const q=page({isOwner:false});q.leave();assert.equal(calls,0);delete global.wx
})
test('empty household names are rejected without request',()=>{
 let calls=0;api.post=async()=>{calls++};const p=page({name:'   '});p.create();assert.equal(calls,0);assert.match(p.data.error,/家庭名称/)
})
