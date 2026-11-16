const test=require('node:test'),assert=require('node:assert/strict')
const api=require('../services/api'),session=require('../services/session')
let definition;global.Page=value=>{definition=value};require('../pages/email-security/index');delete global.Page
function page(data={}){return {...definition,data:{...definition.data,...data},setData(update){Object.assign(this.data,update)}}}
test('email validation blocks invalid requests',async()=>{
 let calls=0;api.post=async()=>{calls++}
 const p=page({newEmail:'bad',currentPassword:'x'});await p.send()
 assert.equal(calls,0);assert.match(p.data.error,/邮箱/)
})
test('sending and confirming follow existing API contract',async()=>{
 const calls=[];let cleared=false
 api.post=async(path,data)=>{calls.push({path,data});return path.endsWith('/send')?{requestId:'r1',needsOldCode:true}:null}
 session.clear=()=>{cleared=true}
 const p=page({newEmail:'new@example.com',currentPassword:'fakepassword'});await p.send()
 assert.equal(p.data.currentPassword,'');p.setData({oldCode:'123456',newCode:'654321'});await p.confirm()
 assert.deepEqual(calls[1],{path:'/user/security/email/confirm',data:{requestId:'r1',oldCode:'123456',newCode:'654321'}})
 assert.equal(p.data.done,true);assert.equal(cleared,true)
})
test('invalid code never attempts verification',async()=>{
 let calls=0;api.post=async()=>{calls++}
 const p=page({request:{requestId:'r',needsOldCode:true},newCode:'123456',oldCode:'123'});await p.confirm()
 assert.equal(calls,0);assert.match(p.data.error,/验证码/)
})
