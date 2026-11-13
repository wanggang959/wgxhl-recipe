const test=require('node:test'), assert=require('node:assert/strict')
const api=require('../services/api'), session=require('../services/session')
let definition
global.Page=value=>{definition=value}
require('../pages/todos/index')
delete global.Page
function page(item){const p={...definition,data:{items:[item],loading:false,loadingMore:false,actionBusy:false},setData(update){Object.assign(this.data,update)},load:async()=>{}};return p}
test('repeating completion calls complete, and non-repeating completion navigates to next-cycle form',async()=>{
  const calls=[];api.post=async(path,data)=>{calls.push({path,data})};let navigate=''
  global.wx={showModal:({success})=>success({confirm:true}),showToast:()=>{},navigateTo:({url})=>{navigate=url}}
  page({id:'a',title:'生日',category:'BIRTHDAY',canComplete:true}).finish({currentTarget:{dataset:{id:'a'}}})
  await new Promise(resolve=>setImmediate(resolve));assert.equal(calls[0].path,'/todo/complete');assert.deepEqual(calls[0].data,{id:'a'})
  page({id:'b',title:'续费',repeatType:'NONE',canComplete:true}).finish({currentTarget:{dataset:{id:'b'}}})
  assert.match(navigate,/id=b&nextCycle=1/);assert.equal(calls.length,1)
  delete global.wx
})
test('blocked completion and cancelled deletion never call the server',async()=>{
  let calls=0;api.post=async()=>{calls++};global.wx={showToast:()=>{},showModal:({success})=>success({confirm:false})}
  const p=page({id:'a',title:'生日',canComplete:false});p.finish({currentTarget:{dataset:{id:'a'}}});p.remove({currentTarget:{dataset:{id:'a'}}})
  await new Promise(resolve=>setImmediate(resolve));assert.equal(calls,0);delete global.wx
})
