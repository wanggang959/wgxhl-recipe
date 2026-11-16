const test=require('node:test'),assert=require('node:assert/strict')
const api=require('../services/api')
let definition;global.Page=value=>{definition=value};const {number}=require('../pages/expense/index');delete global.Page
function page(data={}){return {...definition,data:{...definition.data,user:{id:'u',memberRole:'owner'},project:{id:'p',createUserId:'u'},selectedId:'p',...data},setData(update){Object.assign(this.data,update)}}}
test('amount validation rejects invalid and excessive precision',()=>{assert.equal(number(''), '0');assert.equal(number('19.90'),'19.90');assert.throws(()=>number('-1'));assert.throws(()=>number('2.345'))})
test('invalid expense data is not submitted',async()=>{let calls=0;api.post=async()=>{calls++};const p=page({view:'project',projectForm:{id:'',projectName:'',totalBudget:'0'}});await p.save();assert.equal(calls,0);assert.match(p.data.error,/名称/)})
test('deletion requires confirmation',()=>{let calls=0;api.post=async()=>{calls++};global.wx={showModal:({success})=>success({confirm:false})};const p=page({items:[{id:'i',itemName:'x',canManage:true}]});p.remove({currentTarget:{dataset:{id:'i'}}});assert.equal(calls,0);delete global.wx})
test('cannot silently clear existing reminder',async()=>{let calls=0;api.post=async()=>{calls++};const p=page({view:'item',itemForm:{id:'i',itemName:'x',budgetAmount:'0',actualAmount:'0',plannedTime:'2026-12-01T10:00:00'},plannedDate:'',plannedClock:''});await p.save();assert.equal(calls,0);assert.match(p.data.error,/清除/)})
