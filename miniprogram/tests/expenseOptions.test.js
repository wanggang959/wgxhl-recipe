const test=require('node:test'),assert=require('node:assert/strict')
let definition;global.Page=value=>{definition=value};const {optionsForItem}=require('../pages/expense/index');delete global.Page
test('preserves unavailable payer and owner for edit',()=>{
 const options=optionsForItem([{id:'',name:'不指定'},{id:'a',name:'现有成员'}],{ownerId:'b',ownerName:'原负责人',payerId:'c',payerName:''})
 assert.deepEqual(options.slice(2),[{id:'b',name:'原负责人'},{id:'c',name:'原成员（不可用）'}])
})
