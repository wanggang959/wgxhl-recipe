const test=require('node:test'),assert=require('node:assert/strict')
const {loadPage,makePage}=require('./page-helper'),api=require('../services/api'),session=require('../services/session')
const {fromTodo,toPayload}=require('../utils/todoForm')
let completion;global.Component=value=>{completion=value};require('../components/ledger-completion/index');delete global.Component
const ledgerPage=loadPage('../pages/ledger/index')

test('todo ledger preset is optional and keeps the estimate',()=>{
 const form=fromTodo({title:'续费',ledgerSetting:{enabled:true,category:'订阅',description:'服务器',estimatedAmount:100}})
 const result=toPayload(form)
 assert.deepEqual(result.ledgerSetting,{enabled:true,category:'订阅',description:'服务器',estimatedAmount:100})
 form.ledgerEstimate='1.234';assert.throws(()=>toPayload(form),/金额/)
 form.ledgerEstimate='';form.ledgerDescription='';assert.throws(()=>toPayload(form),/账单内容/)
})

test('completion prefills estimate and current payer, rejects invalid amounts and sends the cycle',async()=>{
 const calls=[];session.get=()=>({id:'me',token:'x'});api.post=async(path,payload)=>{calls.push([path,payload]);if(path==='/user/members')return [{id:'me',nickname:'我'}]}
 global.wx={showToast(){}}
 const todo={id:'t',dueTime:'2026-11-18T08:00:00',ledgerSetting:{enabled:true,category:'订阅',description:'续费',estimatedAmount:100}}
 const page=makePage({...completion,...completion.methods});page.properties={todo};page.triggerEvent=()=>{};await page.open(todo);assert.equal(page.data.amount,'100');assert.equal(page.data.payerId,'me')
 page.setData({amount:'0'});await page.finish({currentTarget:{dataset:{record:'yes'}}});assert.equal(calls.length,1)
 page.setData({amount:'98.50'});await page.finish({currentTarget:{dataset:{record:'yes'}}});assert.deepEqual(calls[1],['/todo/complete-with-ledger',{id:'t',expectedDueTime:'2026-11-18T08:00:00',payerId:'me',actualAmount:98.5}]);delete global.wx
})

test('linked bill deletion asks for confirmation and leaves its source alone',async()=>{
 const calls=[];let confirm;api.post=async(path,payload)=>{calls.push([path,payload]);if(path==='/ledger/page')return {records:[],total:0};if(path==='/ledger/summary')return {totals:[],categories:[]}}
 global.wx={showModal:o=>{confirm=o.success},showToast(){}}
 const page=makePage(ledgerPage,{items:[{id:'bill',description:'续费',sourceType:'TODO',canManage:true}],user:{id:'me'}})
 page.remove({currentTarget:{dataset:{id:'bill'}}});await confirm({confirm:false});assert.equal(calls.length,0)
 page.remove({currentTarget:{dataset:{id:'bill'}}});await confirm({confirm:true});assert.deepEqual(calls[0],['/ledger/delete',{id:'bill'}]);assert.ok(!calls.some(x=>x[0]==='/todo/delete'));delete global.wx
})
