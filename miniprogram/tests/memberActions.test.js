const test=require('node:test'),assert=require('node:assert/strict')
const api=require('../services/api')
let definition;global.Page=value=>{definition=value};require('../pages/profile/index');delete global.Page
function page(user={userRole:'super_admin'}){return {...definition,data:{user,canManage:true,guestStatusBusy:false,members:[{id:'m',username:'member',nickname:'家人',status:'normal',canDelete:true}]},setData(update){Object.assign(this.data,update)},loadMembers:async()=>{}}}
const event={currentTarget:{dataset:{id:'m'}}}
test('ordinary member cannot change account status',()=>{let modal=0;global.wx={showModal:()=>modal++};page({memberRole:'member'}).toggleMemberStatus(event);assert.equal(modal,0);delete global.wx})
test('cancelled member deletion does not mutate',()=>{let calls=0;api.post=async()=>{calls++};global.wx={showModal:({success})=>success({confirm:false})};const p=page();p.removeMember(event);assert.equal(calls,0);assert.equal(p.data.memberDeleteBusy,false);delete global.wx})
test('confirmed status change uses backend endpoint',async()=>{let call;api.post=async(path,data)=>{call={path,data}};global.wx={showModal:({success})=>success({confirm:true}),showToast:()=>{}};const p=page();p.toggleMemberStatus(event);await new Promise(resolve=>setImmediate(resolve));assert.deepEqual(call,{path:'/user/setStatus',data:{id:'m',status:'disabled'}});assert.equal(p.data.members[0].statusLabel,'已禁用');delete global.wx})
