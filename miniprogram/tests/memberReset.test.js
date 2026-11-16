const test=require('node:test'),assert=require('node:assert/strict')
const api=require('../services/api')
let definition;global.Page=value=>{definition=value};require('../pages/member-reset/index');delete global.Page
function page(data={}){return {...definition,data:{...definition.data,userId:'m',...data},setData(update){Object.assign(this.data,update)}}}
test('invalid passwords cannot submit',()=>{let calls=0;api.post=async()=>{calls++};const p=page({mode:'DIRECT',currentPassword:'owner',password:'short',confirmation:'short'});p.submit();assert.equal(calls,0);assert.match(p.data.error,/8 至 64/)})
test('cancelling reset does not call server',()=>{let calls=0;api.post=async()=>{calls++};global.wx={showModal:({success})=>success({confirm:false})};page({mode:'EMAIL'}).submit();assert.equal(calls,0);delete global.wx})
test('email reset calls correct endpoint when confirmed',async()=>{const calls=[];api.post=async(path,data)=>{calls.push({path,data})};global.wx={showModal:({success})=>success({confirm:true})};const p=page({mode:'EMAIL'});p.submit();await new Promise(resolve=>setImmediate(resolve));assert.deepEqual(calls,[{path:'/user/security/member/requestReset',data:{userId:'m'}}]);assert.equal(p.data.done,true);delete global.wx})
