const test=require('node:test'),assert=require('node:assert/strict')
const api=require('../services/api')
let definition;global.Page=value=>{definition=value};require('../pages/profile/index');delete global.Page
function page(user={userRole:'super_admin'}){return {...definition,data:{user,guestStatusBusy:false,members:[{id:'guest',username:'guest',status:'normal'}]},setData(update){Object.assign(this.data,update)}}}
const event={currentTarget:{dataset:{id:'guest'}}}
test('ordinary users cannot change guest status',()=>{
 let modals=0;global.wx={showModal:()=>modals++};const p=page({memberRole:'owner'})
 p.toggleGuestStatus(event);assert.equal(modals,0);delete global.wx
})
test('cancelling confirmation does not call the server',()=>{
 let calls=0;api.post=async()=>{calls++};global.wx={showModal:({success})=>success({confirm:false})}
 const p=page();p.toggleGuestStatus(event);assert.equal(calls,0);assert.equal(p.data.guestStatusBusy,false);delete global.wx
})
test('confirmed disable and enable use correct statuses',async()=>{
 const calls=[];api.post=async(path,payload)=>{calls.push({path,payload})}
 global.wx={showModal:({success})=>success({confirm:true}),showToast:()=>{}}
 const p=page();p.toggleGuestStatus(event);await new Promise(resolve=>setImmediate(resolve))
 assert.deepEqual(calls[0],{path:'/user/setStatus',payload:{id:'guest',status:'disabled'}});assert.equal(p.data.members[0].statusLabel,'已禁用')
 p.toggleGuestStatus(event);await new Promise(resolve=>setImmediate(resolve))
 assert.equal(calls[1].payload.status,'normal');assert.equal(p.data.members[0].statusLabel,'正常');delete global.wx
})
test('server failure preserves original guest status',async()=>{
 api.post=async()=>{throw Error('拒绝操作')};let toast=''
 global.wx={showModal:({success})=>success({confirm:true}),showToast:o=>{toast=o.title}}
 const p=page();p.toggleGuestStatus(event);await new Promise(resolve=>setImmediate(resolve))
 assert.equal(p.data.members[0].status,'normal');assert.equal(p.data.guestStatusBusy,false);assert.equal(toast,'拒绝操作');delete global.wx
})
