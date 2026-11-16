const test=require('node:test'),assert=require('node:assert/strict')
const api=require('../services/api'),session=require('../services/session')
let definition;global.Page=value=>{definition=value};require('../pages/member-form/index');delete global.Page
function page(user){return {...definition,user,data:{...definition.data,form:{...definition.data.form}},setData(update){for(const [key,value] of Object.entries(update)){if(key.startsWith('form.'))this.data.form[key.slice(5)]=value;else this.data[key]=value}}}}
test('regular member cannot open another member for editing',async()=>{
 const p=page({id:'a',memberRole:'member'});p.id='b'
 api.post=async()=>({records:[{id:'b',username:'family',memberRole:'member'}],total:1})
 await p.load();assert.equal(p.data.ready,false);assert.match(p.data.error,/无权/)
})
test('editing a member loads roles from paged user data',async()=>{
 const p=page({id:'owner',memberRole:'owner'});p.id='b'
 api.post=async()=>({records:[{id:'b',username:'family',memberRole:'admin',notificationPreference:'site,push'}],total:1})
 await p.load();assert.equal(p.data.ready,true);assert.equal(p.data.form.memberRole,'admin');assert.equal(p.data.roleEditable,true);assert.equal(p.data.form.notificationPreference,'site,push')
})
test('invalid input never reaches create endpoint',async()=>{
 let calls=0;api.post=async()=>{calls++}
 const p=page({id:'owner',memberRole:'owner'});p.setData({ready:true})
 await p.save();assert.equal(calls,0);assert.match(p.data.error,/用户名/)
})
