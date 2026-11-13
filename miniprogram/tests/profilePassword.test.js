const test=require('node:test'),assert=require('node:assert/strict')
const api=require('../services/api'),session=require('../services/session')
let definition;global.Page=value=>{definition=value};require('../pages/profile/index');delete global.Page
function page(data={}) {return {...definition,data:{isGuest:false,passwordBusy:false,oldPassword:'',newPassword:'',confirmPassword:'',...data},setData(update){Object.assign(this.data,update)},getTabBar(){return {setData(){}}}}}
test('password validation rejects incomplete or mismatched input without a request',async()=>{
 let calls=0;api.post=async()=>{calls++}
 const p=page();await p.submitPassword();assert.equal(p.data.passwordError,'请输入原密码')
 p.setData({oldPassword:'old',newPassword:'short'});await p.submitPassword();assert.match(p.data.passwordError,/8 至 64/)
 p.setData({newPassword:'longpassword',confirmPassword:'different'});await p.submitPassword();assert.match(p.data.passwordError,/不一致/)
 assert.equal(calls,0)
})
test('successful change sends the existing contract and clears session',async()=>{
 const calls=[];api.post=async(path,payload)=>{calls.push({path,payload})}
 let cleared=false;session.clear=()=>{cleared=true}
 global.wx={showTabBar:()=>{},showToast:()=>{},reLaunch:({url})=>calls.push(url)}
 const p=page({oldPassword:'oldpassword',newPassword:'newpassword',confirmPassword:'newpassword'})
 await p.submitPassword()
 assert.deepEqual(calls[0],{path:'/user/changePassword',payload:{oldPassword:'oldpassword',newPassword:'newpassword'}})
 assert.equal(cleared,true);assert.equal(p.data.oldPassword,'');assert.equal(calls[1],'/pages/login/index')
 delete global.wx
})
test('failed change does not clear the login',async()=>{
 let cleared=false;session.clear=()=>{cleared=true};api.post=async()=>{throw new Error('原密码错误')}
 const p=page({oldPassword:'wrongpass',newPassword:'newpassword',confirmPassword:'newpassword'})
 await p.submitPassword();assert.equal(cleared,false);assert.equal(p.data.passwordError,'原密码错误');assert.equal(p.data.passwordBusy,false)
})

