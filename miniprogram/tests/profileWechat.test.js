const test=require('node:test'),assert=require('node:assert/strict')
const api=require('../services/api')
let definition;global.Page=value=>{definition=value};require('../pages/profile/index');delete global.Page
function page(data={}) {
  return {...definition,data:{isGuest:false,user:{id:'u1',username:'one'},wechatBound:null,wechatBusy:false,wechatPassword:'',...data},setData(update){Object.assign(this.data,update)},getTabBar(){return {setData(){}}}}
}
function wxMock(){global.wx={showTabBar(){},hideTabBar(){},showToast(){},login({success}){success({code:'one-time-code'})}}}
test('loads binding state without exposing identity',async()=>{
  api.post=async path=>{assert.equal(path,'/user/wechat/status');return {bound:true}}
  const p=page();await p.loadWechatStatus();assert.equal(p.data.wechatBound,true);assert.equal(p.data.wechatStatusLoading,false)
})
test('status failure is not displayed as unbound',async()=>{
  api.post=async()=>{throw new Error('offline')}
  const p=page();await p.loadWechatStatus();assert.equal(p.data.wechatBound,null);assert.equal(p.data.wechatStatusError,true)
})
test('unbind requires a password and uses only the current account',async()=>{
  wxMock();let calls=[];api.post=async(path,data)=>{calls.push({path,data})}
  const p=page({wechatBound:true,wechatOpen:true});await p.submitWechat()
  assert.equal(calls.length,0);assert.match(p.data.wechatError,/密码/)
  p.setData({wechatPassword:'password'});await p.submitWechat()
  assert.deepEqual(calls,[{path:'/user/wechat/unbind',data:{password:'password'}}])
  assert.equal(p.data.wechatBound,false);assert.equal(p.data.wechatPassword,'')
})
test('binding requires an authenticated password and fresh wechat code',async()=>{
  wxMock();let calls=[];api.post=async(path,data)=>{calls.push({path,data})}
  const p=page({wechatBound:false,wechatOpen:true,wechatPassword:'password'});await p.submitWechat()
  assert.deepEqual(calls,[{path:'/user/wechat/bind',data:{code:'one-time-code',username:'one',password:'password'}}])
  assert.equal(p.data.wechatBound,true)
})
test('failed unbind retains binding state and reports error',async()=>{
  wxMock();api.post=async()=>{throw new Error('密码错误')}
  const p=page({wechatBound:true,wechatOpen:true,wechatPassword:'wrong'});await p.submitWechat()
  assert.equal(p.data.wechatBound,true);assert.equal(p.data.wechatError,'密码错误');assert.equal(p.data.wechatBusy,false)
})
